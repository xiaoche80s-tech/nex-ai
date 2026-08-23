package com.gkht.ai.nexai.module.ai.channel.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelConnectivityTestCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelCreateCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelUpdateCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.channel.application.dto.ChannelDTO;
import com.gkht.ai.nexai.module.ai.channel.application.dto.ConnectivityTestDTO;
import com.gkht.ai.nexai.module.ai.channel.application.query.ChannelPageQuery;
import com.gkht.ai.nexai.module.ai.channel.domain.gateway.ModelConnectivityGateway;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.channel.domain.repository.ChannelRepository;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelOwnerType;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelProvider;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.converter.ChannelConverter;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.dataobject.ChannelDO;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.mapper.ChannelMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.CHANNEL_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.CHANNEL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.CHANNEL_PROVIDER_INVALID;

/**
 * 渠道管理应用服务实现。写走聚合（Repository 端口），读按轻量读写分离经 Mapper 直查。
 * 归属维度 MVP 固定租户侧（BYOK），创建命令不接收该参数。
 */
@Service
@Validated
public class ChannelServiceImpl implements ChannelService {

    @Resource
    private ChannelRepository channelRepository;

    @Resource
    private ChannelMapper channelMapper;

    @Resource
    private ChannelConverter channelConverter;

    @Resource
    private ModelConnectivityGateway connectivityGateway;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createChannel(ChannelCreateCommand command) {
        Channel channel;
        try {
            channel = Channel.create(command.getName(), parseProvider(command.getProvider()),
                    command.getBaseUrl(), command.getApiKey(), ChannelOwnerType.TENANT);
        } catch (IllegalArgumentException ex) {
            throw exception(CHANNEL_INVALID, ex.getMessage());
        }
        return channelRepository.save(channel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateChannel(ChannelUpdateCommand command) {
        Channel channel = requireChannel(command.getId());
        try {
            channel.update(command.getName(), parseProvider(command.getProvider()),
                    command.getBaseUrl(), command.getApiKey());
        } catch (IllegalArgumentException ex) {
            throw exception(CHANNEL_INVALID, ex.getMessage());
        }
        channelRepository.save(channel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateChannelStatus(ChannelUpdateStatusCommand command) {
        Channel channel = requireChannel(command.getId());
        if (command.getEnabled()) {
            channel.enable();
        } else {
            channel.disable();
        }
        channelRepository.save(channel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChannel(Long id) {
        requireChannel(id);
        channelRepository.deleteByIdCascade(id);
    }

    @Override
    public PageResult<ChannelDTO> getChannelPage(ChannelPageQuery query) {
        return channelConverter.toDTOPage(
                channelMapper.selectPage(query, query.getName(), query.getProvider(), query.getEnabled()));
    }

    @Override
    public ChannelDTO getChannel(Long id) {
        return channelConverter.toDTO(requireChannel(id));
    }

    @Override
    public List<ChannelDTO> getEnabledChannelList() {
        return channelConverter.toDTOList(
                channelMapper.selectList(ChannelDO::getEnabled, true));
    }

    @Override
    public ConnectivityTestDTO testChannelConnectivity(ChannelConnectivityTestCommand command) {
        ChannelProvider provider = parseProvider(command.getProvider());
        // 编辑已存渠道且密钥留空时，回退已存密钥（探测凭据不落库）；
        // 渠道编号携带但不存在/跨租户不可见时显式报错——静默以空密钥探测会误导「测的就是已存渠道」
        String apiKey = command.getApiKey();
        if ((apiKey == null || apiKey.isBlank()) && command.getChannelId() != null) {
            Channel existing = channelRepository.findById(command.getChannelId());
            if (existing == null) {
                throw exception(CHANNEL_NOT_EXISTS);
            }
            apiKey = existing.getApiKey();
        }
        Channel probeTarget = Channel.create("connectivity-probe", provider, command.getBaseUrl(),
                apiKey, ChannelOwnerType.TENANT);
        return ConnectivityTestDTO.from(connectivityGateway.probe(probeTarget, command.getModelId()));
    }

    private Channel requireChannel(Long id) {
        Channel channel = channelRepository.findById(id);
        if (channel == null) {
            throw exception(CHANNEL_NOT_EXISTS);
        }
        return channel;
    }

    /**
     * 提供商编码 → 值对象；无效编码报业务异常而非 500
     */
    private ChannelProvider parseProvider(String code) {
        ChannelProvider provider = ChannelProvider.of(code);
        if (provider == null) {
            throw exception(CHANNEL_PROVIDER_INVALID);
        }
        return provider;
    }

}
