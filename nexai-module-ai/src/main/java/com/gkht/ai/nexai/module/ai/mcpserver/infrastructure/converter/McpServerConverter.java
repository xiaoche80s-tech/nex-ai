package com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.common.util.json.JsonUtils;
import com.gkht.ai.nexai.module.ai.mcpserver.application.dto.McpServerDTO;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.model.McpServer;
import com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.dataobject.McpServerDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 转换器：领域模型 → DO（反方向在 RepositoryImpl 经 reconstitute）、
 * 领域模型/DO → 出参 DTO（分页直查用 DO → DTO）。列表/Map 字段在 DO 侧为 JSON 字符串、
 * 领域侧为集合，互转集中于此（domain 零框架依赖）；DTO 不暴露认证头与环境变量（凭证面）。
 */
@Mapper(componentModel = "spring")
public interface McpServerConverter {

    // —— 领域 → DO ——

    @Mapping(target = "transport", source = "transport", qualifiedByName = "enumToString")
    @Mapping(target = "ownerType", source = "ownerType", qualifiedByName = "enumToString")
    @Mapping(target = "args", source = "args", qualifiedByName = "stringListToJson")
    @Mapping(target = "env", source = "env", qualifiedByName = "stringMapToJson")
    @Mapping(target = "headers", source = "headers", qualifiedByName = "headersMapToJson")
    @Mapping(target = "allowedTools", source = "allowedTools", qualifiedByName = "stringListToJson")
    @Mapping(target = "availableTools", source = "availableTools", qualifiedByName = "stringListToJson")
    McpServerDO toDataObject(McpServer server);

    // —— 领域 → DTO（详情走聚合） ——

    @Mapping(target = "transport", source = "transport", qualifiedByName = "enumToString")
    @Mapping(target = "ownerType", source = "ownerType", qualifiedByName = "enumToString")
    @Mapping(target = "headersConfigured", source = "headers", qualifiedByName = "headersConfigured")
    McpServerDTO toDTO(McpServer server);

    // —— DO → DTO（分页直查） ——

    @Mapping(target = "args", source = "args", qualifiedByName = "jsonToStringList")
    @Mapping(target = "allowedTools", source = "allowedTools", qualifiedByName = "jsonToStringList")
    @Mapping(target = "availableTools", source = "availableTools", qualifiedByName = "jsonToStringList")
    @Mapping(target = "headersConfigured", source = "headers", qualifiedByName = "jsonToConfigured")
    McpServerDTO toDTOFromDO(McpServerDO dataObject);

    List<McpServerDTO> toDTOListFromDO(List<McpServerDO> list);

    default PageResult<McpServerDTO> toDTOPageFromDO(PageResult<McpServerDO> page) {
        return new PageResult<>(toDTOListFromDO(page.getList()), page.getTotal());
    }

    // —— 通用编解码 ——

    @Named("enumToString")
    default String enumToString(Enum<?> value) {
        return value == null ? null : value.name();
    }

    @Named("stringListToJson")
    default String stringListToJson(List<String> values) {
        // 空列表写显式空 JSON：updateById 忽略 null 列，null 会错失「清空」语义
        return JsonUtils.toJsonString(values == null ? List.of() : values);
    }

    @Named("jsonToStringList")
    default List<String> jsonToStringList(String json) {
        List<String> parsed = json == null || json.isBlank()
                ? null : JsonUtils.parseArray(json, String.class);
        return parsed == null ? List.of() : parsed;
    }

    @Named("stringMapToJson")
    default String stringMapToJson(Map<String, String> values) {
        return JsonUtils.toJsonString(values == null ? Map.of() : values);
    }

    /** 认证头序列化：空 Map 写 null（配合 updateById 忽略 null 列 = 凭证保留语义，同 Channel apiKey） */
    @Named("headersMapToJson")
    default String headersMapToJson(Map<String, String> values) {
        return values == null || values.isEmpty() ? null : JsonUtils.toJsonString(values);
    }

    /** Map JSON 解码（Repository reconstitute 用；null/解析失败归空 Map） */
    default Map<String, String> jsonToStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, String> parsed = JsonUtils.parseObject(json,
                new tools.jackson.core.type.TypeReference<Map<String, String>>() {});
        return parsed == null ? Map.of() : parsed;
    }

    /** 认证头是否已配置（DTO 出参只露布尔，不回传凭证内容） */
    @Named("headersConfigured")
    default boolean headersConfigured(Map<String, String> headers) {
        return headers != null && !headers.isEmpty();
    }

    /** 认证头密文是否已配置（DO 侧 JSON 为 null/空数组时视为未配置） */
    @Named("jsonToConfigured")
    default boolean jsonToConfigured(String headersJson) {
        return headersJson != null && !headersJson.isBlank() && !"{}".equals(headersJson);
    }

}
