package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * 智能体规格配置值对象（不可变，按值判等）：草稿与已发布版本快照共用同一结构，
 * 按 agentscope 的分层组织为「三层 + 执行环境层」——agent 层（模型引用 / 自描述 / 系统提示 /
 * 迭代上限）、模型调用层（{@link GenerateOptions}）、挂载层（技能 / 工具，MVP 仅建模不露编辑面；
 * 工具挂载统一为 {@link ToolMount}，MCP 与平台工具库共用）、
 * 执行环境层（{@link ExecutionEnvConfig}：workspace / 沙箱 / 执行能力）。
 *
 * <p>分层判据：影响智能体行为的配置进快照（本值对象全部字段），纯管理元数据
 * （名称 / 编码 / 图标 / 归属）留规格主体。模型引用与自描述在草稿态可空
 * （创建时模型管理可能尚未配置），发布时由聚合校验补齐。</p>
 */
public final class AgentSpecConfig {

    /** 自描述长度上限（字符；Command 校验注解共用） */
    public static final int DESCRIPTION_MAX_LENGTH = 1024;
    /** 系统提示长度上限（字符；Command 校验注解共用） */
    public static final int SYSTEM_PROMPT_MAX_LENGTH = 16_384;

    // —— agent 层 ——
    /** 模型引用（ai_model.id，外部聚合引用），草稿态可空，发布时必须非空 */
    private final Long modelId;
    /** 自描述（列表展示用），可空 */
    private final String description;
    /** 系统提示，可空 */
    private final String systemPrompt;
    /** 推理参数：最大迭代轮数（reasoning-acting 循环上限），可空表示运行时取默认 */
    private final Integer maxIters;

    // —— 模型调用层 ——
    /** 调用参数（temperature / topP / maxTokens），可空 = 全默认 */
    private final GenerateOptions generateOptions;

    // —— 挂载层（MVP 仅建模，编辑面后置）——
    /** 技能引用列表 */
    private final List<Long> skillIds;
    /** 工具挂载列表（来源 + 引用 + 可选白名单，MCP 与平台工具库共用） */
    private final List<ToolMount> tools;
    /** 规格私有文件夹挂载列表（ASSET 资料文件夹 / TOOLSET 工具集文件夹，工单 18） */
    private final List<FolderMount> folders;

    // —— 执行环境层 ——
    /** 执行环境配置，null 视为全关（纯对话智能体） */
    private final ExecutionEnvConfig executionEnv;

    private AgentSpecConfig(Long modelId, String description, String systemPrompt, Integer maxIters,
                            GenerateOptions generateOptions, List<Long> skillIds,
                            List<ToolMount> tools, List<FolderMount> folders,
                            ExecutionEnvConfig executionEnv) {
        this.modelId = modelId;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.maxIters = maxIters;
        this.generateOptions = generateOptions;
        this.skillIds = skillIds;
        this.tools = tools;
        this.folders = folders;
        this.executionEnv = executionEnv;
    }

    /**
     * 构建规格配置
     *
     * @param modelId         模型引用，草稿态可空
     * @param description     自描述，可空，去除首尾空白
     * @param systemPrompt    系统提示，可空，去除首尾空白
     * @param maxIters        最大迭代轮数，可空，须 >= 1
     * @param generateOptions 调用参数组，可空 = 全默认
     * @param skillIds        技能引用列表，可空
     * @param tools           工具挂载列表，可空
     * @param folders         规格私有文件夹挂载列表，可空；非空时须启用 workspace
     * @param executionEnv    执行环境配置，可空 = 全关（纯对话）
     */
    public static AgentSpecConfig of(Long modelId, String description, String systemPrompt, Integer maxIters,
                                     GenerateOptions generateOptions, List<Long> skillIds,
                                     List<ToolMount> tools, List<FolderMount> folders,
                                     ExecutionEnvConfig executionEnv) {
        String strippedDescription = NullableTexts.normalizeNullable(description);
        if (strippedDescription != null && strippedDescription.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("规格自描述不能超过 " + DESCRIPTION_MAX_LENGTH + " 个字符");
        }
        String prompt = NullableTexts.normalizeNullable(systemPrompt);
        if (prompt != null && prompt.length() > SYSTEM_PROMPT_MAX_LENGTH) {
            throw new IllegalArgumentException("系统提示不能超过 " + SYSTEM_PROMPT_MAX_LENGTH + " 个字符");
        }
        if (maxIters != null && maxIters < 1) {
            throw new IllegalArgumentException("最大迭代轮数不能小于 1");
        }
        // 挂载层语义：同一工具来源条目只挂一次（重复挂载的 whitelist 合并语义有歧义，直接拒绝）
        List<ToolMount> mounts = tools == null ? List.of() : List.copyOf(tools);
        if (mounts.stream().map(tool -> tool.source() + ":" + tool.sourceId()).distinct().count()
                != mounts.size()) {
            throw new IllegalArgumentException("同一工具来源条目不能重复挂载");
        }
        // 文件夹挂载语义（工单 18）：同规格文件夹名唯一；文件须落 workspace，须启用 workspace 才可挂载
        List<FolderMount> folderMounts = folders == null ? List.of() : List.copyOf(folders);
        if (!folderMounts.isEmpty()) {
            if (folderMounts.stream().map(FolderMount::name).distinct().count()
                    != folderMounts.size()) {
                throw new IllegalArgumentException("同规格文件夹名不能重复");
            }
            if (executionEnv == null || !executionEnv.isWorkspaceEnabled()) {
                throw new IllegalArgumentException("挂载私有文件夹须启用 workspace（文件物化落 workspace）");
            }
        }
        return new AgentSpecConfig(modelId, strippedDescription, prompt, maxIters, generateOptions,
                snapshotIds(skillIds), mounts, folderMounts, executionEnv);
    }

    /**
     * 快照读路径的信任构造（Repository/Converter 专用，与 {@link AgentSpec#reconstitute}
     * 同惯例）：写时已经 {@link #of} 校验，历史快照按固化时规则成立——读路径不重跑当前
     * 校验（校验规则收紧不使既有快照不可读），仅做文本规范化与不可变拷贝。
     */
    public static AgentSpecConfig reconstitute(Long modelId, String description, String systemPrompt,
                                               Integer maxIters, GenerateOptions generateOptions,
                                               List<Long> skillIds, List<ToolMount> tools,
                                               List<FolderMount> folders,
                                               ExecutionEnvConfig executionEnv) {
        return new AgentSpecConfig(modelId, NullableTexts.normalizeNullable(description),
                NullableTexts.normalizeNullable(systemPrompt), maxIters, generateOptions,
                snapshotIds(skillIds), tools == null ? List.of() : List.copyOf(tools),
                folders == null ? List.of() : List.copyOf(folders), executionEnv);
    }

    /** 引用列表规范化为不可变快照（null 视为空列表） */
    private static List<Long> snapshotIds(List<Long> ids) {
        return ids == null ? List.of() : List.copyOf(ids);
    }

    public Long getModelId() {
        return modelId;
    }

    public String getDescription() {
        return description;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public Integer getMaxIters() {
        return maxIters;
    }

    public GenerateOptions getGenerateOptions() {
        return generateOptions;
    }

    public List<Long> getSkillIds() {
        return skillIds;
    }

    public List<ToolMount> getTools() {
        return tools;
    }

    /** 规格私有文件夹挂载列表，空 = 无文件夹挂载 */
    public List<FolderMount> getFolders() {
        return folders;
    }

    /** 执行环境配置，null = 全关（纯对话智能体） */
    public ExecutionEnvConfig getExecutionEnv() {
        return executionEnv;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentSpecConfig other)) {
            return false;
        }
        return Objects.equals(modelId, other.modelId)
                && Objects.equals(description, other.description)
                && Objects.equals(systemPrompt, other.systemPrompt)
                && Objects.equals(maxIters, other.maxIters)
                && Objects.equals(generateOptions, other.generateOptions)
                && Objects.equals(skillIds, other.skillIds)
                && Objects.equals(tools, other.tools)
                && Objects.equals(folders, other.folders)
                && Objects.equals(executionEnv, other.executionEnv);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelId, description, systemPrompt, maxIters, generateOptions,
                skillIds, tools, folders, executionEnv);
    }

}
