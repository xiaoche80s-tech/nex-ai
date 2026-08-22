package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * 智能体规格配置值对象（不可变，按值判等）：草稿与已发布版本快照共用同一结构，
 * 按 agentscope 的分层组织为「三层 + 执行环境层」——agent 层（模型引用 / 自描述 / 系统提示 /
 * 迭代上限）、模型调用层（{@link GenerateOptions}）、挂载层（技能 / MCP / 子智能体，M2 预留）、
 * 执行环境层（{@link ExecutionEnvConfig}：workspace / 沙箱 / 执行能力）。
 *
 * <p>分层判据：影响智能体行为的配置进快照（本值对象全部字段），纯管理元数据
 * （名称 / 编码 / 图标 / 归属）留规格主体。挂载层的统一模式是「引用 + 可选工具白名单过滤」
 * （McpServerMount / SubagentMount），对齐 agentscope 的 Toolkit 白名单语义。</p>
 */
public final class AgentSpecConfig {

    /** 自描述长度上限（字符）：给 LLM 的路由决策依据（agentscope 语义），须精炼 */
    static final int DESCRIPTION_MAX_LENGTH = 1024;
    /** 系统提示长度上限（字符） */
    static final int SYSTEM_PROMPT_MAX_LENGTH = 16_384;

    // —— agent 层 ——
    /** 模型引用（ai_model.id，外部聚合引用） */
    private final Long modelId;
    /** 给 LLM 的自描述：M2 子智能体挂载时作为 spawn 路由决策依据，亦用于列表展示（必填） */
    private final String description;
    /** 系统提示，可空 */
    private final String systemPrompt;
    /** 推理参数：最大迭代轮数（reasoning-acting 循环上限），可空表示运行时取默认 */
    private final Integer maxIters;

    // —— 模型调用层 ——
    /** 调用参数（temperature / topP / maxTokens），可空 = 全默认 */
    private final GenerateOptions generateOptions;

    // —— 挂载层（M2 预留）——
    /** 技能引用列表 */
    private final List<Long> skillIds;
    /** MCP 服务挂载列表（服务 + 工具白名单） */
    private final List<McpServerMount> mcpServers;
    /** 子智能体挂载列表（规格 + 工具白名单，运行时经 agent_spawn 动态实例化） */
    private final List<SubagentMount> subagents;

    // —— 执行环境层 ——
    /** 执行环境配置，null 视为全关（纯对话智能体，兼容无该字段的存量 JSON） */
    private final ExecutionEnvConfig executionEnv;

    private AgentSpecConfig(Long modelId, String description, String systemPrompt, Integer maxIters,
                            GenerateOptions generateOptions, List<Long> skillIds,
                            List<McpServerMount> mcpServers, List<SubagentMount> subagents,
                            ExecutionEnvConfig executionEnv) {
        this.modelId = modelId;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.maxIters = maxIters;
        this.generateOptions = generateOptions;
        this.skillIds = skillIds;
        this.mcpServers = mcpServers;
        this.subagents = subagents;
        this.executionEnv = executionEnv;
    }

    /**
     * 构建规格配置
     *
     * @param modelId         模型引用，不能为 null
     * @param description     给 LLM 的自描述，不能为空白
     * @param systemPrompt    系统提示，可空，去除首尾空白
     * @param maxIters        最大迭代轮数，可空，须 >= 1
     * @param generateOptions 调用参数组，可空 = 全默认
     * @param skillIds        技能引用列表，可空
     * @param mcpServers      MCP 服务挂载列表，可空
     * @param subagents       子智能体挂载列表，可空
     * @param executionEnv    执行环境配置，可空 = 全关（纯对话）
     */
    public static AgentSpecConfig of(Long modelId, String description, String systemPrompt, Integer maxIters,
                                     GenerateOptions generateOptions, List<Long> skillIds,
                                     List<McpServerMount> mcpServers, List<SubagentMount> subagents,
                                     ExecutionEnvConfig executionEnv) {
        if (modelId == null) {
            throw new IllegalArgumentException("规格必须引用一个模型");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("规格自描述不能为空");
        }
        String strippedDescription = description.strip();
        if (strippedDescription.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("规格自描述不能超过 " + DESCRIPTION_MAX_LENGTH + " 个字符");
        }
        String prompt = systemPrompt == null || systemPrompt.isBlank() ? null : systemPrompt.strip();
        if (prompt != null && prompt.length() > SYSTEM_PROMPT_MAX_LENGTH) {
            throw new IllegalArgumentException("系统提示不能超过 " + SYSTEM_PROMPT_MAX_LENGTH + " 个字符");
        }
        if (maxIters != null && maxIters < 1) {
            throw new IllegalArgumentException("最大迭代轮数不能小于 1");
        }
        return new AgentSpecConfig(modelId, strippedDescription, prompt, maxIters, generateOptions,
                snapshotIds(skillIds),
                mcpServers == null ? List.of() : List.copyOf(mcpServers),
                subagents == null ? List.of() : List.copyOf(subagents),
                executionEnv);
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

    public List<McpServerMount> getMcpServers() {
        return mcpServers;
    }

    public List<SubagentMount> getSubagents() {
        return subagents;
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
                && Objects.equals(mcpServers, other.mcpServers)
                && Objects.equals(subagents, other.subagents)
                && Objects.equals(executionEnv, other.executionEnv);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelId, description, systemPrompt, maxIters, generateOptions,
                skillIds, mcpServers, subagents, executionEnv);
    }

}
