package com.gkht.ai.nexai.module.ai.channel.infrastructure.repository;

import com.gkht.ai.nexai.module.ai.channel.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Model;
import com.gkht.ai.nexai.module.ai.channel.domain.repository.ChannelRepository;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelOwnerType;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelProvider;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.converter.ChannelConverter;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.converter.ModelConverter;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.dataobject.ChannelDO;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.dataobject.ModelDO;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.mapper.ChannelMapper;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.mapper.ModelMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

/**
 * 渠道聚合 Repository 实现：Channel 根与 Model 实体的 DO ↔ 领域模型适配，
 * 聚合/实体重建经 reconstitute；删除渠道级联删除其下模型。
 */
@Repository
public class ChannelRepositoryImpl implements ChannelRepository {

    @Resource
    private ChannelMapper channelMapper;

    @Resource
    private ModelMapper modelMapper;

    @Resource
    private ChannelConverter channelConverter;

    @Resource
    private ModelConverter modelConverter;

    @Override
    public Long save(Channel channel) {
        ChannelDO dataObject = channelConverter.toDataObject(channel);
        if (dataObject.getId() == null) {
            channelMapper.insert(dataObject);
        } else {
            // updateById 走 DO 的 resultMap/TypeHandler（api_key 加解密只在实体绑定路径生效，
            // LambdaUpdateWrapper.set 会绕过 TypeHandler 写明文）；null 列被忽略与领域语义一致：
            // 密钥只能保留（null）或更换（非 null），不存在清除
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
    public void deleteByIdCascade(Long id) {
        channelMapper.deleteById(id);
        modelMapper.delete(ModelDO::getChannelId, id);
    }

    @Override
    public Long saveModel(Model model) {
        ModelDO dataObject = modelConverter.toDataObject(model);
        if (dataObject.getId() == null) {
            modelMapper.insert(dataObject);
        } else {
            modelMapper.updateById(dataObject);
        }
        return dataObject.getId();
    }

    @Override
    public Model findModelById(Long id) {
        ModelDO dataObject = modelMapper.selectById(id);
        return dataObject == null ? null : Model.reconstitute(dataObject.getId(),
                dataObject.getChannelId(), dataObject.getModelId(), dataObject.getName(),
                dataObject.getContextWindow(), dataObject.getInputPrice(),
                dataObject.getOutputPrice(), Boolean.TRUE.equals(dataObject.getEnabled()),
                dataObject.getCreateTime());
    }

    @Override
    public void deleteModelById(Long id) {
        modelMapper.deleteById(id);
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
                dataObject.getBaseUrl(), dataObject.getApiKey(),
                Boolean.TRUE.equals(dataObject.getEnabled()), ownerType,
                dataObject.getCreateTime());
    }

}
