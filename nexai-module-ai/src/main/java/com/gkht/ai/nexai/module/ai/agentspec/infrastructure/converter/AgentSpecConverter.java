package com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.common.util.json.JsonUtils;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDTO;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionCapability;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionEnvConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.GenerateOptions;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolMount;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolSource;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecDO;
import lombok.Data;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * 智能体规格转换器：领域模型 → DO、领域模型/DO → 出参 DTO。
 * DO → 领域模型（聚合重建）在 RepositoryImpl 经 reconstitute 完成。
 * draft 在 DO 侧为 JSON 字符串、领域侧为 {@link AgentSpecConfig}，互转集中于此。
 */
@Mapper(componentModel = "spring")
public interface AgentSpecConverter {

    @Mapping(target = "draft", source = "draft", qualifiedByName = "configToJson")
    @Mapping(target = "ownerLevel", source = "ownerLevel", qualifiedByName = "ownerLevelToString")
    AgentSpecDO toDataObject(AgentSpec spec);

    @Mapping(target = "hasDraft", source = "draft", qualifiedByName = "draftToPresent")
    @Mapping(target = "description", source = "draft", qualifiedByName = "draftToDescription")
    AgentSpecDTO toDTO(AgentSpecDO specDO);

    List<AgentSpecDTO> toDTOList(List<AgentSpecDO> list);

    default PageResult<AgentSpecDTO> toDTOPage(PageResult<AgentSpecDO> page) {
        return new PageResult<>(toDTOList(page.getList()), page.getTotal());
    }

    /** 归属层级枚举 → 存储字符串 */
    @Named("ownerLevelToString")
    default String ownerLevelToString(OwnerLevel level) {
        return level == null ? null : level.name();
    }

    /** 配置值对象 → JSON 字符串；null 草稿保持 null（表示无草稿） */
    @Named("configToJson")
    default String configToJson(AgentSpecConfig config) {
        return config == null ? null : JsonUtils.toJsonString(ConfigJSON.from(config));
    }

    /** JSON 字符串 → 配置值对象；null / 空白返回 null */
    @Named("jsonToConfig")
    default AgentSpecConfig jsonToConfig(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        ConfigJSON parsed = JsonUtils.parseObject(json, ConfigJSON.class);
        return parsed == null ? null : parsed.toDomain();
    }

    /** 草稿 JSON 字符串是否存在 → hasDraft 布尔（DO 侧 String source 无条件调用） */
    @Named("draftToPresent")
    default Boolean draftToPresent(String draftJson) {
        return draftJson != null && !draftJson.isBlank();
    }

    /** 草稿 JSON → 自描述（列表展示用；无草稿或解析失败返回 null） */
    @Named("draftToDescription")
    default String draftToDescription(String draftJson) {
        AgentSpecConfig config = jsonToConfig(draftJson);
        return config == null ? null : config.getDescription();
    }

    /**
     * JSON 编解码桥接 POJO：domain 值对象零框架依赖（无 Jackson 注解与默认构造器），
     * 经此同构 POJO 完成序列化，字段与 {@link AgentSpecConfig} 分层结构一一对应。
     */
    @Data
    class ConfigJSON {

        private Long modelId;
        private String description;
        private String systemPrompt;
        private Integer maxIters;
        private GenerateOptionsJSON generateOptions;
        private List<Long> skillIds;
        private List<ToolMountJSON> tools;
        private ExecutionEnvJSON executionEnv;

        static ConfigJSON from(AgentSpecConfig config) {
            ConfigJSON json = new ConfigJSON();
            json.setModelId(config.getModelId());
            json.setDescription(config.getDescription());
            json.setSystemPrompt(config.getSystemPrompt());
            json.setMaxIters(config.getMaxIters());
            json.setGenerateOptions(config.getGenerateOptions() == null ? null
                    : GenerateOptionsJSON.from(config.getGenerateOptions()));
            json.setSkillIds(config.getSkillIds());
            json.setTools(config.getTools() == null ? null
                    : config.getTools().stream().map(ToolMountJSON::from).toList());
            json.setExecutionEnv(ExecutionEnvJSON.from(config.getExecutionEnv()));
            return json;
        }

        AgentSpecConfig toDomain() {
            return AgentSpecConfig.of(modelId, description, systemPrompt, maxIters,
                    generateOptions == null ? null : generateOptions.toDomain(),
                    skillIds,
                    tools == null ? null : tools.stream().map(ToolMountJSON::toDomain).toList(),
                    executionEnv == null ? null : executionEnv.toDomain());
        }

    }

    @Data
    class ExecutionEnvJSON {

        private Boolean workspaceEnabled;
        private Boolean sandboxEnabled;
        private List<String> capabilities;

        static ExecutionEnvJSON from(ExecutionEnvConfig env) {
            if (env == null) {
                return null;
            }
            ExecutionEnvJSON json = new ExecutionEnvJSON();
            json.setWorkspaceEnabled(env.isWorkspaceEnabled());
            json.setSandboxEnabled(env.isSandboxEnabled());
            json.setCapabilities(env.getCapabilities().stream().map(Enum::name).toList());
            return json;
        }

        ExecutionEnvConfig toDomain() {
            return ExecutionEnvConfig.of(Boolean.TRUE.equals(workspaceEnabled),
                    Boolean.TRUE.equals(sandboxEnabled),
                    capabilities == null ? null
                            : capabilities.stream().map(ExecutionCapability::valueOf).toList());
        }

    }

    @Data
    class GenerateOptionsJSON {

        private Double temperature;
        private Double topP;
        private Integer maxTokens;

        static GenerateOptionsJSON from(GenerateOptions options) {
            GenerateOptionsJSON json = new GenerateOptionsJSON();
            json.setTemperature(options.getTemperature());
            json.setTopP(options.getTopP());
            json.setMaxTokens(options.getMaxTokens());
            return json;
        }

        GenerateOptions toDomain() {
            return GenerateOptions.of(temperature, topP, maxTokens);
        }

    }

    @Data
    class ToolMountJSON {

        private String source;
        private Long sourceId;
        private List<String> allowedTools;

        static ToolMountJSON from(ToolMount mount) {
            ToolMountJSON json = new ToolMountJSON();
            json.setSource(mount.getSource().name());
            json.setSourceId(mount.getSourceId());
            json.setAllowedTools(mount.getAllowedTools());
            return json;
        }

        ToolMount toDomain() {
            return ToolMount.of(source == null ? null : ToolSource.valueOf(source),
                    sourceId, allowedTools);
        }

    }

}
