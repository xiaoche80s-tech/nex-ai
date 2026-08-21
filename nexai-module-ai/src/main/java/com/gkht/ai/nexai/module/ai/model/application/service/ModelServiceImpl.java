package com.gkht.ai.nexai.module.ai.model.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.model.application.command.ModelCreateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ModelUpdateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ModelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.model.application.dto.ConnectivityTestDTO;
import com.gkht.ai.nexai.module.ai.model.application.dto.ModelDTO;
import com.gkht.ai.nexai.module.ai.model.application.query.ModelPageQuery;
import com.gkht.ai.nexai.module.ai.model.domain.gateway.ModelConnectivityGateway;
import com.gkht.ai.nexai.module.ai.model.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.model.domain.model.Model;
import com.gkht.ai.nexai.module.ai.model.domain.repository.ChannelRepository;
import com.gkht.ai.nexai.module.ai.model.domain.repository.ModelRepository;
import com.gkht.ai.nexai.module.ai.model.infrastructure.converter.ModelConverter;
import com.gkht.ai.nexai.module.ai.model.infrastructure.dataobject.ChannelDO;
import com.gkht.ai.nexai.module.ai.model.infrastructure.dataobject.ModelDO;
import com.gkht.ai.nexai.module.ai.model.infrastructure.mapper.ChannelMapper;
import com.gkht.ai.nexai.module.ai.model.infrastructure.mapper.ModelMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.CHANNEL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MODEL_DUPLICATE_MODEL_ID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MODEL_NOT_EXISTS;

/**
 * 模型管理应用服务实现。写走聚合（Repository 端口），读按轻量读写分离经 Mapper 直查并补充渠道信息。
 */
@Service
@Validated
public class ModelServiceImpl implements ModelService {

    @Resource
    private ModelRepository modelRepository;

    @Resource
    private ChannelRepository channelRepository;

    @Resource
    private ModelMapper modelMapper;

    @Resource
    private ChannelMapper channelMapper;

    @Resource
    private ModelConverter modelConverter;

    @Resource
    private ModelConnectivityGateway connectivityGateway;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createModel(ModelCreateCommand command) {
        requireChannel(command.getChannelId());
        checkModelIdUnique(command.getChannelId(), command.getModelId(), null);
        Model model = Model.create(command.getChannelId(), command.getModelId(), command.getName(),
                command.getContextWindow(), command.getInputPrice(), command.getOutputPrice(),
                command.getCapabilities());
        return modelRepository.save(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModel(ModelUpdateCommand command) {
        Model model = requireModel(command.getId());
        requireChannel(command.getChannelId());
        checkModelIdUnique(command.getChannelId(), command.getModelId(), command.getId());
        model.update(command.getChannelId(), command.getModelId(), command.getName(),
                command.getContextWindow(), command.getInputPrice(), command.getOutputPrice(),
                command.getCapabilities());
        modelRepository.save(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModelStatus(ModelUpdateStatusCommand command) {
        Model model = requireModel(command.getId());
        if (command.getEnabled()) {
            model.enable();
        } else {
            model.disable();
        }
        modelRepository.save(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(Long id) {
        requireModel(id);
        modelRepository.deleteById(id);
    }

    @Override
    public PageResult<ModelDTO> getModelPage(ModelPageQuery query) {
        PageResult<ModelDO> page = modelMapper.selectPage(query, query.getChannelId(), query.getModelId(),
                query.getName(), query.getCapability(), query.getEnabled());
        PageResult<ModelDTO> result = modelConverter.toDTOPage(page);
        enrichChannelInfo(result.getList());
        return result;
    }

    @Override
    public ModelDTO getModel(Long id) {
        Model model = requireModel(id);
        ModelDTO dto = modelConverter.toDTOFromDomain(model);
        fillChannelInfo(dto);
        return dto;
    }

    @Override
    public List<ModelDTO> getEnabledModelList() {
        List<ModelDO> models = modelMapper.selectList(ModelDO::getEnabled, true);
        return modelConverter.toDTOList(models);
    }

    @Override
    public ConnectivityTestDTO testConnectivity(Long id) {
        Model model = requireModel(id);
        Channel channel = requireChannel(model.getChannelId());
        return ConnectivityTestDTO.from(connectivityGateway.probe(channel, model.getModelId()));
    }

    private Model requireModel(Long id) {
        Model model = modelRepository.findById(id);
        if (model == null) {
            throw exception(MODEL_NOT_EXISTS);
        }
        return model;
    }

    private Channel requireChannel(Long id) {
        Channel channel = channelRepository.findById(id);
        if (channel == null) {
            throw exception(CHANNEL_NOT_EXISTS);
        }
        return channel;
    }

    /**
     * 同渠道下模型标识唯一性校验；excludeId 用于更新时排除自身
     */
    private void checkModelIdUnique(Long channelId, String modelId, Long excludeId) {
        if (modelRepository.existsByChannelIdAndModelId(channelId, modelId, excludeId)) {
            throw exception(MODEL_DUPLICATE_MODEL_ID);
        }
    }

    /**
     * 批量补充列表项的渠道名称与提供商编码
     */
    private void enrichChannelInfo(Collection<ModelDTO> models) {
        if (models == null || models.isEmpty()) {
            return;
        }
        Map<Long, ChannelDO> channels = modelChannelMap(models);
        for (ModelDTO dto : models) {
            ChannelDO channel = channels.get(dto.getChannelId());
            if (channel != null) {
                dto.setChannelName(channel.getName());
                dto.setChannelProvider(channel.getProvider());
            }
        }
    }

    private void fillChannelInfo(ModelDTO dto) {
        ChannelDO channel = channelMapper.selectById(dto.getChannelId());
        if (channel != null) {
            dto.setChannelName(channel.getName());
            dto.setChannelProvider(channel.getProvider());
        }
    }

    private Map<Long, ChannelDO> modelChannelMap(Collection<ModelDTO> models) {
        return channelMapper.selectByIds(models.stream()
                        .map(ModelDTO::getChannelId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(ChannelDO::getId, Function.identity(), (a, b) -> a));
    }

}
