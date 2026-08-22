package com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.common.util.json.JsonUtils;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecConfigDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDetailDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecVersionDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.ExecutionEnvDTO;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionCapability;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionEnvConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.GenerateOptions;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.McpServerMount;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.SubagentMount;
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
    @Mapping(target = "ownerLevel", source = "ownerLevel", qualifiedByName = "ownerLevelToString")
    AgentSpecDO toDataObject(AgentSpec spec);

    @Mapping(target = "snapshot", source = "config", qualifiedByName = "configToJson")
    AgentSpecVersionDO toVersionDataObject(AgentSpecVersion version);

    @Mapping(target = "hasDraft", source = "draft", qualifiedByName = "draftToPresent")
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "modelName", ignore = true)
    AgentSpecDTO toDTO(AgentSpecDO specDO);

    List<AgentSpecDTO> toDTOList(List<AgentSpecDO> list);

    default PageResult<AgentSpecDTO> toDTOPage(PageResult<AgentSpecDO> page) {
        return new PageResult<>(toDTOList(page.getList()), page.getTotal());
    }

    /**
     * 聚合根 → 详情出参：草稿 / 当前版本快照 / 模型名由服务层补充。
     * hasDraft 亦由服务层显式置位——MapStruct 对可空对象 source 的 qualifiedByName
     * 映射会做条件包裹（draft 为 null 时跳过 set，出参残留 null 而非 false）。
     */
    @Mapping(target = "hasDraft", ignore = true)
    @Mapping(target = "draft", ignore = true)
    @Mapping(target = "currentVersion", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "modelName", ignore = true)
    AgentSpecDetailDTO toDetailDTO(AgentSpec spec);

    // config → AgentSpecConfigDTO 自动复用 toConfigDTO（modelName 在服务层补充）
    AgentSpecVersionDTO toVersionDTO(AgentSpecVersion version);

    @Mapping(target = "modelName", ignore = true)
    @Mapping(target = "executionEnv", source = "executionEnv", qualifiedByName = "toExecutionEnvDTO")
    AgentSpecConfigDTO toConfigDTO(AgentSpecConfig config);

    /** 执行环境值对象 → 出参 DTO（null 保持 null，表示纯对话默认） */
    @Named("toExecutionEnvDTO")
    default ExecutionEnvDTO toExecutionEnvDTO(ExecutionEnvConfig env) {
        if (env == null) {
            return null;
        }
        ExecutionEnvDTO dto = new ExecutionEnvDTO();
        dto.setWorkspaceEnabled(env.isWorkspaceEnabled());
        dto.setSandboxEnabled(env.isSandboxEnabled());
        dto.setCapabilities(env.getCapabilities().stream().map(Enum::name).toList());
        return dto;
    }

    /** 归属层级枚举 → 存储字符串 */
    @Named("ownerLevelToString")
    default String ownerLevelToString(OwnerLevel level) {
        return level == null ? null : level.name();
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
     * 草稿 JSON 字符串是否存在 → hasDraft 布尔（DO 侧，String source 无条件调用）
     */
    @Named("draftToPresent")
    default Boolean draftToPresent(String draftJson) {
        return draftJson != null && !draftJson.isBlank();
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
        private List<McpServerMountJSON> mcpServers;
        private List<SubagentMountJSON> subagents;
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
            json.setMcpServers(config.getMcpServers() == null ? null
                    : config.getMcpServers().stream().map(McpServerMountJSON::from).toList());
            json.setSubagents(config.getSubagents() == null ? null
                    : config.getSubagents().stream().map(SubagentMountJSON::from).toList());
            json.setExecutionEnv(ExecutionEnvJSON.from(config.getExecutionEnv()));
            return json;
        }

        AgentSpecConfig toDomain() {
            return AgentSpecConfig.of(modelId, description, systemPrompt, maxIters,
                    generateOptions == null ? null : generateOptions.toDomain(),
                    skillIds,
                    mcpServers == null ? null : mcpServers.stream().map(McpServerMountJSON::toDomain).toList(),
                    subagents == null ? null : subagents.stream().map(SubagentMountJSON::toDomain).toList(),
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
    class McpServerMountJSON {

        private Long serverId;
        private List<String> allowedTools;

        static McpServerMountJSON from(McpServerMount mount) {
            McpServerMountJSON json = new McpServerMountJSON();
            json.setServerId(mount.getServerId());
            json.setAllowedTools(mount.getAllowedTools());
            return json;
        }

        McpServerMount toDomain() {
            return McpServerMount.of(serverId, allowedTools);
        }

    }

    @Data
    class SubagentMountJSON {

        private Long specId;
        private List<String> tools;

        static SubagentMountJSON from(SubagentMount mount) {
            SubagentMountJSON json = new SubagentMountJSON();
            json.setSpecId(mount.getSpecId());
            json.setTools(mount.getTools());
            return json;
        }

        SubagentMount toDomain() {
            return SubagentMount.of(specId, tools);
        }

    }

}
