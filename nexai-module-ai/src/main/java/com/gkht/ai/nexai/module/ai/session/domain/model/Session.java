package com.gkht.ai.nexai.module.ai.session.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 会话聚合根（充血模型，零框架依赖）：一条与智能体的对话记录——发起人（租户管理员/终端用户）、
 * 绑定的规格与版本（运行寻址用，版本号参与常驻实例版本戳）、会话类型（调试/终端）与当前状态。
 *
 * <p>会话状态与上下文不落本表：agentscope 的 {@code PostgresAgentStateStore} 按
 * (userId, sessionId) 槽位持久化智能体状态（本表只记录元数据 + 消息轮次 + HITL 挂起上下文）。
 * sessionKey 为平台侧业务键（唯一），同时作为 agentscope 槽位 sessionId。</p>
 *
 * <p>消息轮次（每条用户消息一轮）以 JSON 追加进本聚合的 {@link #rounds} 快照列：
 * 内容、事件流摘要与调用时间——用于会话历史恢复（工单 09）与调试台历史消息加载（工单 06）。</p>
 */
public class Session {

    /** 标题长度上限（字符） */
    static final int TITLE_MAX_LENGTH = 128;

    /** 编号，未落库时为 null */
    private Long id;
    /** 会话业务键（唯一，同时为 agentscope 槽位 sessionId）：dbg-{uuid}（调试）/chat-{uuid}（终端） */
    private final String sessionKey;
    /** 标题 */
    private String title;
    /** 会话类型：调试（DEBUG）或终端（CHAT） */
    private final SessionType type;
    /** 发起用户编号（租户管理员/终端用户），null = 匿名 */
    private Long userId;
    /** 绑定规格编号 */
    private final Long specId;
    /** 绑定版本号（运行寻址；null = 当前版本，装配时解析） */
    private Integer versionNo;
    /** 会话状态 */
    private SessionStatus status;
    /** 消息轮次 JSON 快照（数组：{content, replyId, time, eventSummary}） */
    private String rounds;
    /** HITL 挂起上下文 JSON（RequireUserConfirmEvent 的 ToolUseBlock 列表），无挂起为 null */
    private String pendingConfirmations;
    /** 创建时间，由持久化填充，新建时为 null */
    private LocalDateTime createTime;

    private Session(Long id, String sessionKey, String title, SessionType type, Long userId,
                    Long specId, Integer versionNo, SessionStatus status, String rounds,
                    String pendingConfirmations, LocalDateTime createTime) {
        this.id = id;
        this.sessionKey = sessionKey;
        this.title = title;
        this.type = type;
        this.userId = userId;
        this.specId = specId;
        this.versionNo = versionNo;
        this.status = status;
        this.rounds = rounds;
        this.pendingConfirmations = pendingConfirmations;
        this.createTime = createTime;
    }

    /**
     * 发起一条调试会话（会话状态初始为 READY，消息轮次为空）
     *
     * @param sessionKey 会话业务键，不能为空白
     * @param title      标题，不能为空白
     * @param userId     发起用户编号，不能为 null（调试台为租户管理员）
     * @param specId     规格编号，不能为 null
     * @param versionNo  版本号，null = 当前版本
     */
    public static Session createDebug(String sessionKey, String title, Long userId, Long specId,
                                      Integer versionNo) {
        validate(sessionKey, title, specId);
        if (userId == null) {
            throw new IllegalArgumentException("调试会话必须由登录用户发起");
        }
        return new Session(null, sessionKey, title.strip(), SessionType.DEBUG, userId, specId,
                versionNo, SessionStatus.READY, "[]", null, null);
    }

    /**
     * 从持久化数据重建聚合（Repository 专用，字段原样恢复）
     */
    public static Session reconstitute(Long id, String sessionKey, String title, SessionType type,
                                       Long userId, Long specId, Integer versionNo,
                                       SessionStatus status, String rounds,
                                       String pendingConfirmations, LocalDateTime createTime) {
        return new Session(id, sessionKey, title, type, userId, specId, versionNo, status,
                rounds, pendingConfirmations, createTime);
    }

    /** 追加一轮消息记录（rounds JSON 由应用层维护，本聚合只做状态推进并接受新快照） */
    public void appendRound(String roundsJson) {
        this.rounds = roundsJson == null ? "[]" : roundsJson;
        if (status == SessionStatus.READY || status == SessionStatus.ACTIVE) {
            this.status = SessionStatus.ACTIVE;
        }
    }

    /** 记录 HITL 挂起上下文（RequireUserConfirmEvent 的 ToolUseBlock 列表 JSON），状态置 ASKING */
    public void markPendingConfirmations(String confirmationsJson) {
        this.pendingConfirmations = confirmationsJson;
        this.status = SessionStatus.ASKING;
    }

    /** 清除 HITL 挂起上下文（审批回传后），状态回 ACTIVE */
    public void clearPendingConfirmations() {
        this.pendingConfirmations = null;
        this.status = SessionStatus.ACTIVE;
    }

    /** 会话结束（正常收尾或失败），状态置 CLOSED */
    public void close() {
        this.status = SessionStatus.CLOSED;
    }

    private static void validate(String sessionKey, String title, Long specId) {
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new IllegalArgumentException("会话业务键不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("会话标题不能为空");
        }
        if (title.strip().length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("会话标题不能超过 " + TITLE_MAX_LENGTH + " 个字符");
        }
        if (specId == null) {
            throw new IllegalArgumentException("会话必须绑定规格");
        }
    }

    public Long getId() {
        return id;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public String getTitle() {
        return title;
    }

    public SessionType getType() {
        return type;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getSpecId() {
        return specId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public SessionStatus getStatus() {
        return status;
    }

    /** 消息轮次 JSON 快照（数组），无轮次时为 "[]" */
    public String getRounds() {
        return rounds;
    }

    /** HITL 挂起上下文 JSON，无挂起为 null */
    public String getPendingConfirmations() {
        return pendingConfirmations;
    }

    public boolean hasPendingConfirmations() {
        return pendingConfirmations != null && !pendingConfirmations.isBlank();
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Session other)) {
            return false;
        }
        // 聚合根按身份（编号）判等；未落库的聚合只与自身相等
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

}
