package com.gkht.ai.nexai.module.ai.model.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.model.application.dto.ChannelDTO;
import com.gkht.ai.nexai.module.ai.model.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ApiKeyMasker;
import com.gkht.ai.nexai.module.ai.model.infrastructure.dataobject.ChannelDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * 渠道转换器：领域模型 → DO、DO/领域模型 → 出参 DTO。
 * DO → 领域模型（聚合重建）在 RepositoryImpl 经 Channel.reconstitute 完成。
 * 出参侧密钥一律脱敏（{@link ApiKeyMasker}），明文不离开基础设施层与领域层。
 */
@Mapper(componentModel = "spring")
public interface ChannelConverter {

    @Mapping(source = "provider.code", target = "provider")
    @Mapping(source = "ownerType.code", target = "ownerType")
    ChannelDO toDataObject(Channel channel);

    @Mapping(target = "apiKeyMasked", source = "apiKey", qualifiedByName = "maskApiKey")
    ChannelDTO toDTO(ChannelDO channelDO);

    @Mapping(source = "provider.code", target = "provider")
    @Mapping(source = "ownerType.code", target = "ownerType")
    @Mapping(target = "apiKeyMasked", source = "apiKey", qualifiedByName = "maskApiKey")
    ChannelDTO toDTOFromDomain(Channel channel);

    List<ChannelDTO> toDTOList(List<ChannelDO> list);

    default PageResult<ChannelDTO> toDTOPage(PageResult<ChannelDO> page) {
        return new PageResult<>(toDTOList(page.getList()), page.getTotal());
    }

    @Named("maskApiKey")
    default String maskApiKey(String apiKey) {
        return ApiKeyMasker.mask(apiKey);
    }

}
