package com.gkht.ai.nexai.module.ai.model.infrastructure.repository;

import com.gkht.ai.nexai.module.ai.model.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.model.domain.repository.ChannelRepository;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ChannelOwnerType;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ChannelProvider;
import com.gkht.ai.nexai.module.ai.model.infrastructure.converter.ChannelConverter;
import com.gkht.ai.nexai.module.ai.model.infrastructure.dataobject.ChannelDO;
import com.gkht.ai.nexai.module.ai.model.infrastructure.mapper.ChannelMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

/**
 * 渠道 Repository 实现：DO ↔ 领域模型适配，聚合重建经 Channel.reconstitute。
 */
@Repository
public class ChannelRepositoryImpl implements ChannelRepository {

    @Resource
    private ChannelMapper channelMapper;

    @Resource
    private ChannelConverter channelConverter;

    @Override
    public Long save(Channel channel) {
        ChannelDO dataObject = channelConverter.toDataObject(channel);
        if (dataObject.getId() == null) {
            channelMapper.insert(dataObject);
        } else {
            channelMapper.updateById(dataObject);
        }
        return dataObject.getId();
    }

    @Override
    public Channel findById(Long id) {
        ChannelDO dataObject = channelMapper.selectById(id);
        return dataObject == null ? null : reconstitute(dataObject);
    }

    @Override
    public void deleteById(Long id) {
        channelMapper.deleteById(id);
    }

    private Channel reconstitute(ChannelDO dataObject) {
        ChannelProvider provider = ChannelProvider.of(dataObject.getProvider());
        ChannelOwnerType ownerType = ChannelOwnerType.of(dataObject.getOwnerType());
        if (provider == null || ownerType == null) {
            // 编码非法属于脏数据，直接失败暴露而非静默吞掉
            throw new IllegalStateException(String.format("渠道 #%s 的编码非法：provider=%s, ownerType=%s",
                    dataObject.getId(), dataObject.getProvider(), dataObject.getOwnerType()));
        }
        return Channel.reconstitute(dataObject.getId(), dataObject.getName(), provider,
                dataObject.getBaseUrl(), dataObject.getApiKey(), dataObject.getEnabled(), ownerType,
                dataObject.getCreateTime());
    }

}
