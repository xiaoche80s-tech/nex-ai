package com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.common.util.json.JsonUtils;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDetailDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecFlatConfigDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecVersionDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecVersionDetailDTO;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionCapability;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionEnvConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.FolderFile;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.FolderMount;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.FolderType;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.GenerateOptions;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolMount;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolSource;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecDO;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecVersionDO;
import lombok.Data;import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * 智能体规格转换器：领域模型 → DO、领域模型/DO → 出参 DTO。
 * DO → 领域模型（聚合重建）在 RepositoryImpl 经 reconstitute 完成。
 * draft 在 DO 侧为 JSON 字符串、领域侧为 {@link AgentSpecConfig}，互转集中于此；
 * 版本快照的 config 与草稿 JSON 同构，复用同一组编解码。
 */
@Mapper(componentModel = "spring")
public interface AgentSpecConverter {

    @Mapping(target = "draft", source = "draft", qualifiedByName = "configToJson")
    @Mapping(target = "ownerLevel", source = "ownerLevel", qualifiedByName = "ownerLevelToString")
    AgentSpecDO toDataObject(AgentSpec spec);

    @Mapping(target = "config", source = "config", qualifiedByName = "configToJson")
    AgentSpecVersionDO toVersionDataObject(AgentSpecVersion version);

    @Mapping(target = "hasDraft", source = "draft", qualifiedByName = "draftToPresent")
    @Mapping(target = "description", source = "draft", qualifiedByName = "draftToDescription")
    AgentSpecDTO toDTO(AgentSpecDO specDO);

    List<AgentSpecDTO> toDTOList(List<AgentSpecDO> list);

    /**
     * 版本快照 → 列表 DTO（不含全量配置；current 当前版本标识由应用服务按版本指针设置）
     */
    AgentSpecVersionDTO toVersionDTO(AgentSpecVersion version);

    /**
     * 版本快照 DO → 列表 DTO（工单 25 发布人链路：creator 为 DO 审计字段，String 自动转 Long；
     * current/publisherName 由应用服务按指针设置/经 AdminUserApi 解析，此处忽略）
     */
    @Mapping(target = "current", ignore = true)
    @Mapping(target = "publisherName", ignore = true)
    AgentSpecVersionDTO toVersionDTO(AgentSpecVersionDO versionDO);

    /**
     * 聚合 → 详情 DTO（编辑面回填）：配置平铺来源由应用服务解析（草稿优先，已发布无草稿时
     * 取当前生效快照——ADR 0004），分层配置平铺为与命令同构的表单形态；来源为 null 时配置项为 null。
     */
    default AgentSpecDetailDTO toDetailDTO(AgentSpec spec, AgentSpecConfig backfill) {
        AgentSpecDetailDTO dto = new AgentSpecDetailDTO();
        dto.setId(spec.getId());
        dto.setName(spec.getName());
        dto.setSpecCode(spec.getSpecCode());
        dto.setOwnerLevel(ownerLevelToString(spec.getOwnerLevel()));
        dto.setOwnerUserId(spec.getOwnerUserId());
        dto.setIcon(spec.getIcon());
        dto.setHasDraft(spec.hasDraft());
        dto.setCurrentVersionNo(spec.getCurrentVersionNo());
        dto.setCreateTime(spec.getCreateTime());
        fillFlatConfig(dto, backfill);
        return dto;
    }

    /**
     * 版本快照 → 详情 DTO（只读预览，工单 24）：版本元信息 + 全量四层配置平铺
     *（快照与草稿同构，固化保真）；current 标记由应用服务按版本指针设置。
     */
    default AgentSpecVersionDetailDTO toVersionDetailDTO(AgentSpecVersion version, boolean current) {
        AgentSpecVersionDetailDTO dto = new AgentSpecVersionDetailDTO();
        dto.setId(version.getId());
        dto.setVersionNo(version.getVersionNo());
        dto.setNote(version.getNote());
        dto.setCreateTime(version.getCreateTime());
        dto.setCurrent(current);
        fillFlatConfig(dto, version.getConfig());
        return dto;
    }

    /**
     * 分层配置值对象 → 平铺表单形态（草稿回填与版本预览共用；config 为 null 时各配置项保持 null）
     */
    default void fillFlatConfig(AgentSpecFlatConfigDTO target, AgentSpecConfig config) {
        if (config == null) {
            return;
        }
        target.setModelId(config.getModelId());
        target.setDescription(config.getDescription());
        target.setSystemPrompt(config.getSystemPrompt());
        target.setMaxIters(config.getMaxIters());
        if (config.getGenerateOptions() != null) {
            target.setTemperature(config.getGenerateOptions().getTemperature());
            target.setTopP(config.getGenerateOptions().getTopP());
            target.setMaxTokens(config.getGenerateOptions().getMaxTokens());
        }
        target.setSkillIds(config.getSkillIds());
        if (config.getTools() != null) {
            target.setTools(config.getTools().stream().map(mount -> {
                AgentSpecFlatConfigDTO.ToolMountDTO mountDTO = new AgentSpecFlatConfigDTO.ToolMountDTO();
                mountDTO.setSource(mount.source().name());
                mountDTO.setSourceId(mount.sourceId());
                mountDTO.setAllowedTools(mount.allowedTools());
                mountDTO.setSensitiveTools(mount.sensitiveTools());
                return mountDTO;
            }).toList());
        }
        if (config.getFolders() != null) {
            target.setFolders(config.getFolders().stream().map(folder -> {
                AgentSpecFlatConfigDTO.FolderMountDTO folderDTO = new AgentSpecFlatConfigDTO.FolderMountDTO();
                folderDTO.setType(folder.type().name());
                folderDTO.setName(folder.name());
                folderDTO.setFiles(folder.files().stream().map(file -> {
                    AgentSpecFlatConfigDTO.FolderFileDTO fileDTO = new AgentSpecFlatConfigDTO.FolderFileDTO();
                    fileDTO.setPath(file.path());
                    fileDTO.setUrl(file.url());
                    fileDTO.setContentHash(file.contentHash());
                    fileDTO.setSize(file.size());
                    return fileDTO;
                }).toList());
                return folderDTO;
            }).toList());
        }
        if (config.getExecutionEnv() != null) {
            target.setWorkspaceEnabled(config.getExecutionEnv().isWorkspaceEnabled());
            target.setSandboxEnabled(config.getExecutionEnv().isSandboxEnabled());
            target.setCapabilities(config.getExecutionEnv().getCapabilities().stream()
                    .map(Enum::name).toList());
        }
    }

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

    /** JSON 字符串 → 配置值对象；null / 空白返回 null（草稿与版本快照 config 共用） */
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
        private List<FolderMountJSON> folders;
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
            json.setFolders(config.getFolders() == null || config.getFolders().isEmpty() ? null
                    : config.getFolders().stream().map(FolderMountJSON::from).toList());
            json.setExecutionEnv(ExecutionEnvJSON.from(config.getExecutionEnv()));
            return json;
        }

        AgentSpecConfig toDomain() {
            // 快照读走信任构造：写时已经 of() 校验，历史快照按固化时规则成立（不重跑当前校验）
            return AgentSpecConfig.reconstitute(modelId, description, systemPrompt, maxIters,
                    generateOptions == null ? null : generateOptions.toDomain(),
                    skillIds,
                    tools == null ? null : tools.stream().map(ToolMountJSON::toDomain).toList(),
                    folders == null ? null : folders.stream().map(FolderMountJSON::toDomain).toList(),
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
        private List<String> sensitiveTools;

        static ToolMountJSON from(ToolMount mount) {
            ToolMountJSON json = new ToolMountJSON();
            json.setSource(mount.source().name());
            json.setSourceId(mount.sourceId());
            json.setAllowedTools(mount.allowedTools());
            json.setSensitiveTools(mount.sensitiveTools());
            return json;
        }

        ToolMount toDomain() {
            return new ToolMount(source == null ? null : ToolSource.valueOf(source),
                    sourceId, allowedTools, sensitiveTools);
        }

    }

    /** 文件夹挂载 JSON 桥接（工单 18 folders 通道：type/name/files 清单含内容哈希） */
    @Data
    class FolderMountJSON {

        private String type;
        private String name;
        private List<FolderFileJSON> files;

        static FolderMountJSON from(FolderMount mount) {
            FolderMountJSON json = new FolderMountJSON();
            json.setType(mount.type().name());
            json.setName(mount.name());
            json.setFiles(mount.files().stream().map(FolderFileJSON::from).toList());
            return json;
        }

        FolderMount toDomain() {
            return new FolderMount(type == null ? null : FolderType.valueOf(type), name,
                    files == null ? null : files.stream().map(FolderFileJSON::toDomain).toList());
        }

    }

    @Data
    class FolderFileJSON {

        private String path;
        private String url;
        private String contentHash;
        private Long size;

        static FolderFileJSON from(FolderFile file) {
            FolderFileJSON json = new FolderFileJSON();
            json.setPath(file.path());
            json.setUrl(file.url());
            json.setContentHash(file.contentHash());
            json.setSize(file.size());
            return json;
        }

        FolderFile toDomain() {
            return FolderFile.of(path, url, contentHash, size == null ? 0L : size);
        }

    }

}
