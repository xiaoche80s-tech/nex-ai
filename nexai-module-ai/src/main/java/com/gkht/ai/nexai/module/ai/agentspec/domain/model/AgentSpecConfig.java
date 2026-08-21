package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * 智能体规格配置值对象（不可变，按值判等）：草稿与已发布版本快照共用同一结构。
 *
 * <p>核心字段为模型引用（按元数据引用而非硬编码）与系统提示、推理参数（maxIters / 温度）；
 * 技能 / 知识库 / MCP / 子智能体引用列表为 M2 预留，M1 全程可空。</p>
 */
public final class AgentSpecConfig {

    /** 系统提示长度上限（字符） */
    static final int SYSTEM_PROMPT_MAX_LENGTH = 16_384;
    /** 温度上限（业界提供商普遍接受 0 ~ 2） */
    static final double TEMPERATURE_MAX = 2.0d;

    /** 模型引用（ai_model.id，外部聚合引用） */
    private final Long modelId;
    /** 系统提示，可空 */
    private final String systemPrompt;
    /** 推理参数：最大迭代轮数，可空表示运行时取默认 */
    private final Integer maxIters;
    /** 推理参数：温度（0 ~ 2），可空表示运行时取默认 */
    private final Double temperature;
    /** 技能引用列表（M2 预留，可空） */
    private final List<Long> skillIds;
    /** 知识库引用列表（M2 预留，可空） */
    private final List<Long> knowledgeBaseIds;
    /** MCP 服务引用列表（M2 预留，可空） */
    private final List<Long> mcpServerIds;
    /** 子智能体规格引用列表（M2 预留，可空） */
    private final List<Long> subagentSpecIds;

    private AgentSpecConfig(Long modelId, String systemPrompt, Integer maxIters, Double temperature,
                            List<Long> skillIds, List<Long> knowledgeBaseIds,
                            List<Long> mcpServerIds, List<Long> subagentSpecIds) {
        this.modelId = modelId;
        this.systemPrompt = systemPrompt;
        this.maxIters = maxIters;
        this.temperature = temperature;
        this.skillIds = skillIds;
        this.knowledgeBaseIds = knowledgeBaseIds;
        this.mcpServerIds = mcpServerIds;
        this.subagentSpecIds = subagentSpecIds;
    }

    /**
     * 构建规格配置
     *
     * @param modelId      模型引用，不能为 null
     * @param systemPrompt 系统提示，可空，去除首尾空白
     * @param maxIters     最大迭代轮数，可空，须 >= 1
     * @param temperature  温度，可空，须在 [0, 2] 内
     * @param skillIds             技能引用列表，可空
     * @param knowledgeBaseIds     知识库引用列表，可空
     * @param mcpServerIds         MCP 服务引用列表，可空
     * @param subagentSpecIds      子智能体规格引用列表，可空
     */
    public static AgentSpecConfig of(Long modelId, String systemPrompt, Integer maxIters,
                                     Double temperature, List<Long> skillIds, List<Long> knowledgeBaseIds,
                                     List<Long> mcpServerIds, List<Long> subagentSpecIds) {
        if (modelId == null) {
            throw new IllegalArgumentException("规格必须引用一个模型");
        }
        String prompt = systemPrompt == null || systemPrompt.isBlank() ? null : systemPrompt.strip();
        if (prompt != null && prompt.length() > SYSTEM_PROMPT_MAX_LENGTH) {
            throw new IllegalArgumentException("系统提示不能超过 " + SYSTEM_PROMPT_MAX_LENGTH + " 个字符");
        }
        if (maxIters != null && maxIters < 1) {
            throw new IllegalArgumentException("最大迭代轮数不能小于 1");
        }
        if (temperature != null && (temperature < 0d || temperature > TEMPERATURE_MAX)) {
            throw new IllegalArgumentException("温度必须在 0 ~ " + TEMPERATURE_MAX + " 之间");
        }
        return new AgentSpecConfig(modelId, prompt, maxIters, temperature,
                snapshotIds(skillIds), snapshotIds(knowledgeBaseIds),
                snapshotIds(mcpServerIds), snapshotIds(subagentSpecIds));
    }

    /** 引用列表规范化为不可变快照（null 视为空列表） */
    private static List<Long> snapshotIds(List<Long> ids) {
        return ids == null ? List.of() : List.copyOf(ids);
    }

    public Long getModelId() {
        return modelId;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public Integer getMaxIters() {
        return maxIters;
    }

    public Double getTemperature() {
        return temperature;
    }

    public List<Long> getSkillIds() {
        return skillIds;
    }

    public List<Long> getKnowledgeBaseIds() {
        return knowledgeBaseIds;
    }

    public List<Long> getMcpServerIds() {
        return mcpServerIds;
    }

    public List<Long> getSubagentSpecIds() {
        return subagentSpecIds;
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
                && Objects.equals(systemPrompt, other.systemPrompt)
                && Objects.equals(maxIters, other.maxIters)
                && Objects.equals(temperature, other.temperature)
                && Objects.equals(skillIds, other.skillIds)
                && Objects.equals(knowledgeBaseIds, other.knowledgeBaseIds)
                && Objects.equals(mcpServerIds, other.mcpServerIds)
                && Objects.equals(subagentSpecIds, other.subagentSpecIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelId, systemPrompt, maxIters, temperature,
                skillIds, knowledgeBaseIds, mcpServerIds, subagentSpecIds);
    }

}
