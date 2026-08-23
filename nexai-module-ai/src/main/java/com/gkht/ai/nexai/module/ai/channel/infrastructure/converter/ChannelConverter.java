package com.gkht.ai.nexai.module.ai.channel.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.channel.application.dto.ChannelDTO;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ApiKeyMasker;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelOwnerType;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelProvider;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.dataobject.ChannelDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * 渠道转换器：领域模型 → DO、领域模型 → 出参 DTO。
 * DO → 领域模型（聚合重建）在 RepositoryImpl 经 reconstitute 完成。
 * 密钥明文只进 DO（加密列）与探测网关，DTO 一律脱敏。
 */
@Mapper(componentModel = "spring")
public interface ChannelConverter {

    @Mapping(target = "provider", source = "provider", qualifiedByName = "providerToCode")
    @Mapping(target = "ownerType", source = "ownerType", qualifiedByName = "ownerTypeToCode")
    ChannelDO toDataObject(Channel channel);

    @Mapping(target = "apiKeyMasked", source = "apiKey", qualifiedByName = "apiKeyToMasked")
    @Mapping(target = "apiKeyConfigured", source = "apiKey", qualifiedByName = "apiKeyToConfigured")
    @Mapping(target = "provider", source = "provider", qualifiedByName = "providerToCode")
    @Mapping(target = "ownerType", source = "ownerType", qualifiedByName = "ownerTypeToCode")
    ChannelDTO toDTO(Channel channel);

    /** 读路径（Mapper 直查 DO → DTO），密钥同样脱敏 */
    @Mapping(target = "apiKeyMasked", source = "apiKey", qualifiedByName = "apiKeyToMasked")
    @Mapping(target = "apiKeyConfigured", source = "apiKey", qualifiedByName = "apiKeyToConfigured")
    ChannelDTO toDTO(ChannelDO channelDO);

    List<ChannelDTO> toDTOList(List<ChannelDO> list);

    default PageResult<ChannelDTO> toDTOPage(PageResult<ChannelDO> page) {
        return new PageResult<>(toDTOList(page.getList()), page.getTotal());
    }

    /** 提供商枚举 → 落库编码 */
    @Named("providerToCode")
    default String providerToCode(ChannelProvider provider) {
        return provider == null ? null : provider.getCode();
    }

    /** 归属枚举 → 落库编码 */
    @Named("ownerTypeToCode")
    default String ownerTypeToCode(ChannelOwnerType ownerType) {
        return ownerType == null ? null : ownerType.getCode();
    }

    /** 密钥明文 → 脱敏展示（null 保持 null） */
    @Named("apiKeyToMasked")
    default String apiKeyToMasked(String apiKey) {
        return ApiKeyMasker.mask(apiKey);
    }

    /** 密钥明文 → 是否已配置（编辑界面提示「已配置，留空保留」） */
    @Named("apiKeyToConfigured")
    default Boolean apiKeyToConfigured(String apiKey) {
        return apiKey != null && !apiKey.isBlank();
    }

}
