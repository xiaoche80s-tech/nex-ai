package com.gkht.ai.nexai.module.ai.audit.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 审计事件实体（工单 14）：「谁-何时-哪个会话-调了什么工具-结果摘要」——运行侧治理
 * 的最小问责单元。先采集不计价；由审计 middleware 在 onActing 拦截点采集，
 * 每个工具调用一条，acting 流终止时批量落库（采集失败仅告警不阻断会话）。
 *
 * <p>维度四元组：租户（tenantId）/ 会话（sessionKey）/ 规格（specId + versionNo）/ 用户（userId）。
 * 入参与结果以<b>脱敏摘要</b>落库（{@link SensitiveMasker}，保留期限与脱敏规则默认值可配）。</p>
 */
public final class AuditEvent {

    /** 工具名长度上限 */
    static final int TOOL_NAME_MAX_LENGTH = 128;
    /** 摘要长度上限（硬顶，防超长 payload 撑爆表） */
    static final int DIGEST_MAX_LENGTH = 2048;

    private final Long id;              // 落库后回填（domain 侧可空）
    private final Long tenantId;        // 租户编号（维度：租户）
    private final String sessionKey;    // 会话业务键（维度：会话，= agentscope 槽位 sessionId）
    private final Long specId;          // 规格编号（维度：规格）
    private final Integer versionNo;    // 规格版本号（维度：规格）
    private final String agentId;       // agentId（= specCode）
    private final String userId;        // 谁（agentscope 槽位用户，匿名 = anonymous）
    private final String toolCallId;    // 工具调用编号
    private final String toolName;      // 调了什么工具
    private final ToolOutcome outcome;  // 结果（SUCCESS/ERROR/DENIED/…）
    private final String argumentsDigest; // 入参摘要（脱敏后）
    private final String resultDigest;  // 结果摘要（脱敏后）
    private final Long durationMs;      // acting 段耗时（毫秒）
    private final LocalDateTime occurredAt; // 何时

    private AuditEvent(Long id, Long tenantId, String sessionKey, Long specId, Integer versionNo,
                       String agentId, String userId, String toolCallId, String toolName,
                       ToolOutcome outcome, String argumentsDigest, String resultDigest,
                       Long durationMs, LocalDateTime occurredAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.sessionKey = sessionKey;
        this.specId = specId;
        this.versionNo = versionNo;
        this.agentId = agentId;
        this.userId = userId;
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.outcome = outcome;
        this.argumentsDigest = argumentsDigest;
        this.resultDigest = resultDigest;
        this.durationMs = durationMs;
        this.occurredAt = occurredAt;
    }

    /**
     * 记录一次工具调用审计事件
     *
     * @param tenantId        租户编号，不能为 null
     * @param sessionKey      会话业务键，不能为空白
     * @param specId          规格编号，可空（维度缺失防御）
     * @param versionNo       规格版本号，可空
     * @param agentId         agentId（= specCode），可空
     * @param userId          槽位用户，可空
     * @param toolCallId      工具调用编号，可空
     * @param toolName        工具名，不能为空白且最多 {@value TOOL_NAME_MAX_LENGTH} 字符
     * @param outcome         结果，不能为 null
     * @param argumentsDigest 入参摘要（应经 {@link SensitiveMasker} 脱敏）
     * @param resultDigest    结果摘要（应经 {@link SensitiveMasker} 脱敏）
     * @param durationMs      acting 段耗时毫秒，可空
     * @param occurredAt      发生时间，不能为 null
     */
    public static AuditEvent record(Long tenantId, String sessionKey, Long specId, Integer versionNo,
                                    String agentId, String userId, String toolCallId, String toolName,
                                    ToolOutcome outcome, String argumentsDigest, String resultDigest,
                                    Long durationMs, LocalDateTime occurredAt) {
        if (tenantId == null) {
            throw new IllegalArgumentException("审计事件必须携带租户维度");
        }
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new IllegalArgumentException("审计事件必须携带会话维度");
        }
        if (toolName == null || toolName.isBlank() || toolName.length() > TOOL_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("审计事件必须携带工具名（最多 "
                    + TOOL_NAME_MAX_LENGTH + " 字符）");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("审计事件必须携带结果口径");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("审计事件必须携带发生时间");
        }
        return new AuditEvent(null, tenantId, sessionKey.strip(), specId, versionNo, agentId,
                userId, toolCallId, toolName, outcome, truncate(argumentsDigest),
                truncate(resultDigest), durationMs, occurredAt);
    }

    /** 摘要截断（硬顶保护，配置级截断在脱敏阶段完成） */
    private static String truncate(String digest) {
        if (digest == null || digest.length() <= DIGEST_MAX_LENGTH) {
            return digest;
        }
        return digest.substring(0, DIGEST_MAX_LENGTH);
    }

    /** 落库回填编号（不可变重建） */
    public AuditEvent withId(Long assignedId) {
        return new AuditEvent(assignedId, tenantId, sessionKey, specId, versionNo, agentId,
                userId, toolCallId, toolName, outcome, argumentsDigest, resultDigest,
                durationMs, occurredAt);
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

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public ToolOutcome getOutcome() {
        return outcome;
    }

    public String getArgumentsDigest() {
        return argumentsDigest;
    }

    public String getResultDigest() {
        return resultDigest;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuditEvent other)) {
            return false;
        }
        return Objects.equals(id, other.id) && Objects.equals(tenantId, other.tenantId)
                && Objects.equals(sessionKey, other.sessionKey) && Objects.equals(specId, other.specId)
                && Objects.equals(versionNo, other.versionNo) && Objects.equals(agentId, other.agentId)
                && Objects.equals(userId, other.userId) && Objects.equals(toolCallId, other.toolCallId)
                && Objects.equals(toolName, other.toolName) && outcome == other.outcome
                && Objects.equals(argumentsDigest, other.argumentsDigest)
                && Objects.equals(resultDigest, other.resultDigest)
                && Objects.equals(durationMs, other.durationMs)
                && Objects.equals(occurredAt, other.occurredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, sessionKey, specId, versionNo, agentId, userId,
                toolCallId, toolName, outcome, argumentsDigest, resultDigest, durationMs, occurredAt);
    }

}
