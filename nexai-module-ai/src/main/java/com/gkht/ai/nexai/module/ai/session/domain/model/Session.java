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
    /** 克隆会话默认标题后缀 */
    private static final String CLONE_TITLE_SUFFIX = "（克隆）";
    /** 推理参数覆盖取值上限（与规格侧约束一致） */
    private static final int MAX_ITERS_LIMIT = 100;
    private static final double TEMPERATURE_MIN = 0.0d;
    private static final double TEMPERATURE_MAX = 2.0d;

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
    /** 推理参数覆盖：最大迭代轮数，null 表示沿用版本快照值（克隆重跑的"微调参数"落点） */
    private final Integer overrideMaxIters;
    /** 推理参数覆盖：温度，null 表示沿用版本快照值 */
    private final Double overrideTemperature;
    /** 已发送的消息轮数（每次发消息 +1，调试台列表按此展示对话量） */
    private int messageRounds;
    /** 创建者用户标识（状态存储 userId 槽位的持久化痕迹，克隆复制历史状态时寻址用） */
    private final String creatorUserId;
    /** 创建时间，由持久化填充，新建时为 null */
    private final LocalDateTime createTime;

    private Session(Long id, String sessionKey, SessionType type, Long specId, int versionNo,
                    String title, Integer overrideMaxIters, Double overrideTemperature,
                    int messageRounds, String creatorUserId, LocalDateTime createTime) {
        this.id = id;
        this.sessionKey = sessionKey;
        this.type = type;
        this.specId = specId;
        this.versionNo = versionNo;
        this.title = title;
        this.overrideMaxIters = overrideMaxIters;
        this.overrideTemperature = overrideTemperature;
        this.messageRounds = messageRounds;
        this.creatorUserId = creatorUserId;
        this.createTime = createTime;
    }

    /**
     * 发起调试会话：绑定规格的指定已发布版本（无参数覆盖，推理参数全取版本快照）
     *
     * @param specId    规格编号，不能为 null
     * @param versionNo 规格版本号，须 >= 1（存在性由应用层校验，聚合只守护形式约束）
     * @param title     显示标题，可空
     */
    public static Session startDebug(Long specId, int versionNo, String title) {
        return startDebug(specId, versionNo, title, null, null);
    }

    /**
     * 发起调试会话并携带推理参数覆盖（克隆重跑入口）
     *
     * @param overrideMaxIters     最大迭代轮数覆盖，null 沿用快照
     * @param overrideTemperature  温度覆盖，null 沿用快照
     */
    public static Session startDebug(Long specId, int versionNo, String title,
                                     Integer overrideMaxIters, Double overrideTemperature) {
        if (specId == null) {
            throw new IllegalArgumentException("调试会话必须绑定一个规格");
        }
        if (versionNo < 1) {
            throw new IllegalArgumentException("调试会话绑定的版本号必须大于等于 1");
        }
        checkOverrides(overrideMaxIters, overrideTemperature);
        return new Session(null, DEBUG_KEY_PREFIX + UUID.randomUUID(), SessionType.DEBUG,
                specId, versionNo, normalizeTitle(title), overrideMaxIters, overrideTemperature,
                0, null, null);
    }

    /**
     * 从本会话克隆出新的调试会话（CONTEXT.md：克隆产物恒为 type=debug）：
     * 新会话标识（全新对话状态槽位）、继承规格版本绑定与参数覆盖，消息轮数归零。
     * 对话历史是否随克隆复制由应用层经运行时端口另行处理，聚合只负责元数据。
     *
     * @param title                新标题，null 时取「源标题（克隆）」并防超长截断
     * @param overrideMaxIters     最大迭代轮数覆盖，null 沿用源会话的覆盖值
     * @param overrideTemperature  温度覆盖，null 沿用源会话的覆盖值
     */
    public Session cloneAsDebug(String title, Integer overrideMaxIters, Double overrideTemperature) {
        Integer newMaxIters = overrideMaxIters != null ? overrideMaxIters : this.overrideMaxIters;
        Double newTemperature = overrideTemperature != null ? overrideTemperature : this.overrideTemperature;
        return startDebug(specId, versionNo,
                title != null && !title.isBlank() ? title : defaultCloneTitle(),
                newMaxIters, newTemperature);
    }

    /**
     * 从持久化数据重建聚合（Repository 专用，字段原样恢复）
     */
    public static Session reconstitute(Long id, String sessionKey, SessionType type, Long specId,
                                       int versionNo, String title, Integer overrideMaxIters,
                                       Double overrideTemperature, int messageRounds,
                                       String creatorUserId, LocalDateTime createTime) {
        return new Session(id, sessionKey, type, specId, versionNo, normalizeTitle(title),
                overrideMaxIters, overrideTemperature, messageRounds, creatorUserId, createTime);
    }

    /**
     * 记录一轮消息发送（轮次 +1）
     */
    public void recordMessageRound() {
        this.messageRounds++;
    }

    private static void checkOverrides(Integer maxIters, Double temperature) {
        if (maxIters != null && (maxIters < 1 || maxIters > MAX_ITERS_LIMIT)) {
            throw new IllegalArgumentException("最大迭代轮数覆盖必须在 1-" + MAX_ITERS_LIMIT + " 之间");
        }
        if (temperature != null && (temperature < TEMPERATURE_MIN || temperature > TEMPERATURE_MAX)) {
            throw new IllegalArgumentException("温度覆盖必须在 " + TEMPERATURE_MIN + "-" + TEMPERATURE_MAX + " 之间");
        }
    }

    /** 克隆默认标题：源标题（或会话标识兜底）加后缀，防超长时从前截断保留后缀 */
    private String defaultCloneTitle() {
        String base = title != null ? title : sessionKey;
        String wanted = base + CLONE_TITLE_SUFFIX;
        if (wanted.length() <= TITLE_MAX_LENGTH) {
            return wanted;
        }
        return wanted.substring(wanted.length() - TITLE_MAX_LENGTH);
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

    public Integer getOverrideMaxIters() {
        return overrideMaxIters;
    }

    public Double getOverrideTemperature() {
        return overrideTemperature;
    }

    public int getMessageRounds() {
        return messageRounds;
    }

    public String getCreatorUserId() {
        return creatorUserId;
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
