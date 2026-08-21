package com.gkht.ai.nexai.module.ai.session.domain.model;

import com.gkht.ai.nexai.module.ai.session.domain.valueobject.SessionType;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 会话聚合根（充血模型，零框架依赖）：用户与某个智能体（某版本规格实例化）的一次连续对话。
 *
 * <p>会话绑定规格的不可变版本快照（specId + versionNo），发消息时按该快照装配运行时，
 * 保证问题可复现；对话消息本体不经本聚合落库，复用 agentscope 状态存储语义
 * （PostgresAgentStateStore 主存储，spec 总体架构节），跨轮次上下文恢复以 {@code sessionKey} 寻址。</p>
 */
public class Session {

    /** 会话标识长度上限（"dbg-" + UUID） */
    static final int SESSION_KEY_MAX_LENGTH = 64;
    /** 标题长度上限（字符） */
    static final int TITLE_MAX_LENGTH = 128;
    /** 调试会话标识前缀 */
    private static final String DEBUG_KEY_PREFIX = "dbg-";

    /** 编号，未落库时为 null */
    private final Long id;
    /** 会话标识：agentscope 状态存储的 sessionId，全局唯一，创建后不可变 */
    private final String sessionKey;
    /** 会话类型 */
    private final SessionType type;
    /** 绑定的规格编号（外部聚合引用） */
    private final Long specId;
    /** 绑定的规格版本号（不可变快照的定位键） */
    private final int versionNo;
    /** 显示标题，可空 */
    private String title;
    /** 已发送的消息轮数（每次发消息 +1，调试台列表按此展示对话量） */
    private int messageRounds;
    /** 创建时间，由持久化填充，新建时为 null */
    private final LocalDateTime createTime;

    private Session(Long id, String sessionKey, SessionType type, Long specId, int versionNo,
                    String title, int messageRounds, LocalDateTime createTime) {
        this.id = id;
        this.sessionKey = sessionKey;
        this.type = type;
        this.specId = specId;
        this.versionNo = versionNo;
        this.title = title;
        this.messageRounds = messageRounds;
        this.createTime = createTime;
    }

    /**
     * 发起调试会话：绑定规格的指定已发布版本
     *
     * @param specId    规格编号，不能为 null
     * @param versionNo 规格版本号，须 >= 1（存在性由应用层校验，聚合只守护形式约束）
     * @param title     显示标题，可空
     */
    public static Session startDebug(Long specId, int versionNo, String title) {
        if (specId == null) {
            throw new IllegalArgumentException("调试会话必须绑定一个规格");
        }
        if (versionNo < 1) {
            throw new IllegalArgumentException("调试会话绑定的版本号必须大于等于 1");
        }
        return new Session(null, DEBUG_KEY_PREFIX + UUID.randomUUID(), SessionType.DEBUG,
                specId, versionNo, normalizeTitle(title), 0, null);
    }

    /**
     * 从持久化数据重建聚合（Repository 专用，字段原样恢复）
     */
    public static Session reconstitute(Long id, String sessionKey, SessionType type, Long specId,
                                       int versionNo, String title, int messageRounds,
                                       LocalDateTime createTime) {
        return new Session(id, sessionKey, type, specId, versionNo, normalizeTitle(title),
                messageRounds, createTime);
    }

    /**
     * 记录一轮消息发送（轮次 +1）
     */
    public void recordMessageRound() {
        this.messageRounds++;
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String stripped = title.strip();
        if (stripped.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("会话标题不能超过 " + TITLE_MAX_LENGTH + " 个字符");
        }
        return stripped;
    }

    public Long getId() {
        return id;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public SessionType getType() {
        return type;
    }

    public Long getSpecId() {
        return specId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getTitle() {
        return title;
    }

    public int getMessageRounds() {
        return messageRounds;
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
