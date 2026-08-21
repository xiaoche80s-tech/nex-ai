package com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.common.util.json.JsonUtils;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecConfigDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecVersionDTO;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecDO;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecVersionDO;
import lombok.Data;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * 智能体规格转换器：领域模型 → DO、领域模型 → 出参 DTO。
 * DO → 领域模型（聚合重建）在 RepositoryImpl 经 reconstitute 完成。
 * draft / snapshot 在 DO 侧为 JSON 字符串、领域侧为 {@link AgentSpecConfig}，互转集中于此。
 */
@Mapper(componentModel = "spring")
public interface AgentSpecConverter {

    @Mapping(target = "draft", source = "draft", qualifiedByName = "configToJson")
    AgentSpecDO toDataObject(AgentSpec spec);

    @Mapping(target = "snapshot", source = "config", qualifiedByName = "configToJson")
    AgentSpecVersionDO toVersionDataObject(AgentSpecVersion version);

    @Mapping(target = "hasDraft", source = "draft", qualifiedByName = "draftToPresent")
    @Mapping(target = "modelName", ignore = true)
    AgentSpecDTO toDTO(AgentSpecDO specDO);

    List<AgentSpecDTO> toDTOList(List<AgentSpecDO> list);

    default PageResult<AgentSpecDTO> toDTOPage(PageResult<AgentSpecDO> page) {
        return new PageResult<>(toDTOList(page.getList()), page.getTotal());
    }

    // config → AgentSpecConfigDTO 自动复用 toConfigDTO（modelName 在服务层补充）
    AgentSpecVersionDTO toVersionDTO(AgentSpecVersion version);

    @Mapping(target = "modelName", ignore = true)
    AgentSpecConfigDTO toConfigDTO(AgentSpecConfig config);

    /**
     * 草稿 JSON 是否存在 → hasDraft 布尔
     */
    @Named("draftToPresent")
    default Boolean draftToPresent(String draftJson) {
        return draftJson != null && !draftJson.isBlank();
    }

    /**
     * 配置值对象 → JSON 字符串；null 草稿保持 null（表示无草稿）
     */
    @Named("configToJson")
    default String configToJson(AgentSpecConfig config) {
        return config == null ? null : JsonUtils.toJsonString(ConfigJSON.from(config));
    }

    /**
     * JSON 字符串 → 配置值对象；null / 空白返回 null
     */
    @Named("jsonToConfig")
    default AgentSpecConfig jsonToConfig(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        ConfigJSON parsed = JsonUtils.parseObject(json, ConfigJSON.class);
        return parsed == null ? null : parsed.toDomain();
    }

    /**
     * JSON 编解码桥接 POJO：domain 值对象零框架依赖（无 Jackson 注解与默认构造器），
     * 经此同构 POJO 完成序列化，字段与 {@link AgentSpecConfig} 一一对应。
     */
    @Data
    class ConfigJSON {

        private Long modelId;
        private String systemPrompt;
        private Integer maxIters;
        private Double temperature;
        private List<Long> skillIds;
        private List<Long> knowledgeBaseIds;
        private List<Long> mcpServerIds;
        private List<Long> subagentSpecIds;

        static ConfigJSON from(AgentSpecConfig config) {
            ConfigJSON json = new ConfigJSON();
            json.setModelId(config.getModelId());
            json.setSystemPrompt(config.getSystemPrompt());
            json.setMaxIters(config.getMaxIters());
            json.setTemperature(config.getTemperature());
            json.setSkillIds(config.getSkillIds());
            json.setKnowledgeBaseIds(config.getKnowledgeBaseIds());
            json.setMcpServerIds(config.getMcpServerIds());
            json.setSubagentSpecIds(config.getSubagentSpecIds());
            return json;
        }

        AgentSpecConfig toDomain() {
            return AgentSpecConfig.of(modelId, systemPrompt, maxIters, temperature,
                    skillIds, knowledgeBaseIds, mcpServerIds, subagentSpecIds);
        }

    }

}
