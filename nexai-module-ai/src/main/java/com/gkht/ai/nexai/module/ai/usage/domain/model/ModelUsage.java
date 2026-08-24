package com.gkht.ai.nexai.module.ai.usage.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 模型用量实体（工单 14）：单次模型调用的用量快照——token（输入/输出/缓存命中）与耗时，
 * 维度含租户/会话/规格。由用量 middleware 在 onModelCall 拦截点从
 * {@code ModelCallEndEvent.getUsage()}（ChatUsage）采集，先采集不计价
 * （第三波计价时数据不可回补的部分已就位）。
 */
public final class ModelUsage {

    /** 模型名长度上限 */
    static final int MODEL_NAME_MAX_LENGTH = 256;

    private final Long id;              // 落库后回填
    private final Long tenantId;        // 维度：租户
    private final String sessionKey;    // 维度：会话（= agentscope 槽位 sessionId）
    private final Long specId;          // 维度：规格
    private final Integer versionNo;    // 维度：规格版本
    private final String agentId;       // agentId（= specCode）
    private final String userId;        // 槽位用户
    private final String modelName;     // 模型名（agentscope Model.getModelName）
    private final int messageCount;     // 本次调用消息数
    private final int inputTokens;      // 输入 token
    private final int outputTokens;     // 输出 token
    private final int cachedTokens;     // 缓存命中 token（inputTokens 子集，未报告为 0）
    private final double durationSeconds; // 模型调用耗时（秒）
    private final LocalDateTime occurredAt; // 何时

    private ModelUsage(Long id, Long tenantId, String sessionKey, Long specId, Integer versionNo,
                       String agentId, String userId, String modelName, int messageCount,
                       int inputTokens, int outputTokens, int cachedTokens,
                       double durationSeconds, LocalDateTime occurredAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.sessionKey = sessionKey;
        this.specId = specId;
        this.versionNo = versionNo;
        this.agentId = agentId;
        this.userId = userId;
        this.modelName = modelName;
        this.messageCount = messageCount;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cachedTokens = cachedTokens;
        this.durationSeconds = durationSeconds;
        this.occurredAt = occurredAt;
    }

    /**
     * 记录一次模型调用的用量
     *
     * @param tenantId        租户编号，不能为 null
     * @param sessionKey      会话业务键，不能为空白
     * @param specId          规格编号，可空
     * @param versionNo       规格版本号，可空
     * @param agentId         agentId（= specCode），可空
     * @param userId          槽位用户，可空
     * @param modelName       模型名，不能为空白且最多 {@value MODEL_NAME_MAX_LENGTH} 字符
     * @param messageCount    本次调用消息数（&gt;= 0）
     * @param inputTokens     输入 token（&gt;= 0）
     * @param outputTokens    输出 token（&gt;= 0）
     * @param cachedTokens    缓存命中 token（&gt;= 0，须为 inputTokens 子集口径）
     * @param durationSeconds 耗时秒（&gt;= 0）
     * @param occurredAt      发生时间，不能为 null
     */
    public static ModelUsage record(Long tenantId, String sessionKey, Long specId, Integer versionNo,
                                    String agentId, String userId, String modelName, int messageCount,
                                    int inputTokens, int outputTokens, int cachedTokens,
                                    double durationSeconds, LocalDateTime occurredAt) {
        if (tenantId == null) {
            throw new IllegalArgumentException("模型用量必须携带租户维度");
        }
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new IllegalArgumentException("模型用量必须携带会话维度");
        }
        if (modelName == null || modelName.isBlank() || modelName.length() > MODEL_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("模型用量必须携带模型名（最多 "
                    + MODEL_NAME_MAX_LENGTH + " 字符）");
        }
        if (messageCount < 0 || inputTokens < 0 || outputTokens < 0 || cachedTokens < 0
                || durationSeconds < 0) {
            throw new IllegalArgumentException("模型用量的数量字段不能为负");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("模型用量必须携带发生时间");
        }
        return new ModelUsage(null, tenantId, sessionKey.strip(), specId, versionNo, agentId,
                userId, modelName, messageCount, inputTokens, outputTokens, cachedTokens,
                durationSeconds, occurredAt);
    }

    /** 总 token（派生：输入 + 输出） */
    public int getTotalTokens() {
        return inputTokens + outputTokens;
    }

    /** 落库回填编号（不可变重建） */
    public ModelUsage withId(Long assignedId) {
        return new ModelUsage(assignedId, tenantId, sessionKey, specId, versionNo, agentId,
                userId, modelName, messageCount, inputTokens, outputTokens, cachedTokens,
                durationSeconds, occurredAt);
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public Long getSpecId() {
        return specId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getUserId() {
        return userId;
    }

    public String getModelName() {
        return modelName;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public int getCachedTokens() {
        return cachedTokens;
    }

    public double getDurationSeconds() {
        return durationSeconds;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModelUsage other)) {
            return false;
        }
        return Objects.equals(id, other.id) && Objects.equals(tenantId, other.tenantId)
                && Objects.equals(sessionKey, other.sessionKey) && Objects.equals(specId, other.specId)
                && Objects.equals(versionNo, other.versionNo) && Objects.equals(agentId, other.agentId)
                && Objects.equals(userId, other.userId) && Objects.equals(modelName, other.modelName)
                && messageCount == other.messageCount && inputTokens == other.inputTokens
                && outputTokens == other.outputTokens && cachedTokens == other.cachedTokens
                && Double.compare(durationSeconds, other.durationSeconds) == 0
                && Objects.equals(occurredAt, other.occurredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, sessionKey, specId, versionNo, agentId, userId,
                modelName, messageCount, inputTokens, outputTokens, cachedTokens,
                durationSeconds, occurredAt);
    }

}
