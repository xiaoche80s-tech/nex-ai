package com.gkht.ai.nexai.module.ai.channel.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.channel.application.command.ModelCreateCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ModelUpdateCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ModelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.channel.application.dto.ModelDTO;
import com.gkht.ai.nexai.module.ai.channel.application.query.ModelPageQuery;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Model;
import com.gkht.ai.nexai.module.ai.channel.domain.repository.ChannelRepository;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.converter.ModelConverter;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.dataobject.ChannelDO;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.dataobject.ModelDO;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.mapper.ChannelMapper;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.mapper.ModelMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.CHANNEL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MODEL_DUPLICATE_MODEL_ID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MODEL_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MODEL_NOT_EXISTS;

/**
 * 模型管理应用服务实现。写走聚合（Channel 聚合的实体端口），读经 Mapper 直查并补充渠道信息。
 */
@Service
@Validated
public class ModelServiceImpl implements ModelService {

    @Resource
    private ChannelRepository channelRepository;

    @Resource
    private ModelMapper modelMapper;

    @Resource
    private ChannelMapper channelMapper;

    @Resource
    private ModelConverter modelConverter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createModel(ModelCreateCommand command) {
        requireChannel(command.getChannelId());
        checkModelIdUnique(command.getChannelId(), command.getModelId(), null);
        Model model;
        try {
            model = Model.create(command.getChannelId(), command.getModelId(), command.getName(),
                    command.getContextWindow(), command.getInputPrice(), command.getOutputPrice());
        } catch (IllegalArgumentException ex) {
            throw exception(MODEL_INVALID, ex.getMessage());
        }
        return channelRepository.saveModel(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModel(ModelUpdateCommand command) {
        Model model = requireModel(command.getId());
        requireChannel(command.getChannelId());
        checkModelIdUnique(command.getChannelId(), command.getModelId(), command.getId());
        try {
            model.update(command.getChannelId(), command.getModelId(), command.getName(),
                    command.getContextWindow(), command.getInputPrice(), command.getOutputPrice());
        } catch (IllegalArgumentException ex) {
            throw exception(MODEL_INVALID, ex.getMessage());
        }
        channelRepository.saveModel(model);
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
        channelRepository.saveModel(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(Long id) {
        requireModel(id);
        channelRepository.deleteModelById(id);
    }

    @Override
    public PageResult<ModelDTO> getModelPage(ModelPageQuery query) {
        PageResult<ModelDO> page = modelMapper.selectPage(query, query.getChannelId(),
                query.getModelId(), query.getName(), query.getEnabled());
        PageResult<ModelDTO> result = modelConverter.toDTOPage(page);
        fillChannelNames(result.getList());
        return result;
    }

    @Override
    public ModelDTO getModel(Long id) {
        ModelDTO dto = modelConverter.toDTO(requireModel(id));
        fillChannelNames(List.of(dto));
        return dto;
    }

    private void requireChannel(Long channelId) {
        if (channelRepository.findById(channelId) == null) {
            throw exception(CHANNEL_NOT_EXISTS);
        }
    }

    private Model requireModel(Long id) {
        Model model = channelRepository.findModelById(id);
        if (model == null) {
            throw exception(MODEL_NOT_EXISTS);
        }
        return model;
    }

    /**
     * 同渠道下模型标识唯一性预校验（排除自身）；生产库唯一索引兜底并发
     */
    private void checkModelIdUnique(Long channelId, String modelId, Long excludeId) {
        boolean duplicated = modelMapper.selectList(ModelDO::getModelId, modelId).stream()
                .anyMatch(existing -> existing.getChannelId().equals(channelId)
                        && !Objects.equals(existing.getId(), excludeId));
        if (duplicated) {
            throw exception(MODEL_DUPLICATE_MODEL_ID, modelId);
        }
    }

    /** 批量补充渠道显示名（一次查询避免 N+1） */
    private void fillChannelNames(List<ModelDTO> list) {
        if (list.isEmpty()) {
            return;
        }
        List<Long> channelIds = list.stream().map(ModelDTO::getChannelId).distinct().toList();
        Map<Long, String> channelNames = channelMapper.selectBatchIds(channelIds).stream()
                .collect(Collectors.toMap(ChannelDO::getId, ChannelDO::getName));
        list.forEach(dto -> dto.setChannelName(channelNames.get(dto.getChannelId())));
    }

}
