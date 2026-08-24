package com.gkht.ai.nexai.module.ai.apikey.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.common.util.json.JsonUtils;
import com.gkht.ai.nexai.module.ai.apikey.application.dto.TenantApiKeyDTO;
import com.gkht.ai.nexai.module.ai.apikey.infrastructure.dataobject.TenantApiKeyDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * 租户 API Key 转换器（DO → 出参 DTO，轻量读写分离路径；密文不回传——哈希字段无映射）。
 */
@Mapper(componentModel = "spring")
public interface ApiKeyConverter {

    @Mapping(target = "specCodes", source = "specCodes", qualifiedByName = "specCodesFromJson")
    TenantApiKeyDTO toDTO(TenantApiKeyDO dataObject);

    List<TenantApiKeyDTO> toDTOList(List<TenantApiKeyDO> list);

    default PageResult<TenantApiKeyDTO> toDTOPage(PageResult<TenantApiKeyDO> page) {
        return new PageResult<>(toDTOList(page.getList()), page.getTotal());
    }

    /** spec_codes JSON 数组 → 列表（空/空白 = 空列表 = 本租户全部规格） */
    @Named("specCodesFromJson")
    default List<String> specCodesFromJson(String json) {
        return json == null || json.isBlank()
                ? List.of() : JsonUtils.parseArray(json, String.class);
    }

}
