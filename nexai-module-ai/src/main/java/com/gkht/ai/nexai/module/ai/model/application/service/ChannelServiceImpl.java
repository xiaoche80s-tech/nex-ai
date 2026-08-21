package com.gkht.ai.nexai.module.ai.model.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelCreateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelUpdateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.model.application.dto.ChannelDTO;
import com.gkht.ai.nexai.module.ai.model.application.query.ChannelPageQuery;
import com.gkht.ai.nexai.module.ai.model.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.model.domain.repository.ChannelRepository;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ChannelOwnerType;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ChannelProvider;
import com.gkht.ai.nexai.module.ai.model.infrastructure.converter.ChannelConverter;
import com.gkht.ai.nexai.module.ai.model.infrastructure.dataobject.ChannelDO;
import com.gkht.ai.nexai.module.ai.model.infrastructure.mapper.ChannelMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.CHANNEL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.CHANNEL_PROVIDER_INVALID;

/**
 * 渠道管理应用服务实现。写走聚合（Repository 端口），读按轻量读写分离经 Mapper 直查。
 * 归属维度 M1 固定租户侧，创建命令不接收该参数。
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createChannel(ChannelCreateCommand command) {
        Channel channel = Channel.create(command.getName(), parseProvider(command.getProvider()),
                command.getBaseUrl(), command.getApiKey(), ChannelOwnerType.TENANT);
        return channelRepository.save(channel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateChannel(ChannelUpdateCommand command) {
        Channel channel = requireChannel(command.getId());
        channel.update(command.getName(), parseProvider(command.getProvider()),
                command.getBaseUrl(), command.getApiKey());
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
        channelRepository.deleteById(id);
    }

    @Override
    public PageResult<ChannelDTO> getChannelPage(ChannelPageQuery query) {
        PageResult<ChannelDO> page = channelMapper.selectPage(query, query.getName(),
                query.getProvider(), query.getEnabled());
        return channelConverter.toDTOPage(page);
    }

    @Override
    public ChannelDTO getChannel(Long id) {
        Channel channel = requireChannel(id);
        return channelConverter.toDTOFromDomain(channel);
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
