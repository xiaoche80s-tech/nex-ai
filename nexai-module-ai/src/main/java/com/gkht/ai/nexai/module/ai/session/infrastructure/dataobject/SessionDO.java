package com.gkht.ai.nexai.module.ai.session.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话 DO（贫血模型）。rounds 列存消息轮次 JSON 数组快照（{content, replyId, time, eventSummary}），
 * pending_confirmations 列存 HITL 挂起上下文 JSON（RequireUserConfirmEvent 的 ToolUseBlock 列表），
 * 两者均为 text 类型、随会话聚合持久化。
 *
 * <p>session_key 为平台侧业务键（同时为 agentscope 槽位 sessionId），全局唯一（生产库唯一索引）；
 * 智能体状态本身由 agentscope PostgresAgentStateStore 管理，不落本表。</p>
 */
@TableName("ai_session")
@KeySequence("ai_session_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
public class SessionDO extends TenantBaseDO {

    /**
     * 会话编号
     */
    @TableId
    private Long id;
    /**
     * 会话业务键（全局唯一，同时为 agentscope 槽位 sessionId）
     */
    private String sessionKey;
    /**
     * 标题
     */
    private String title;
    /**
     * 会话类型（DEBUG/CHAT）
     */
    private String type;
    /**
     * 发起用户编号（null = 匿名）
     */
    private Long userId;
    /**
     * 绑定规格编号
     */
    private Long specId;
    /**
     * 绑定版本号（null = 当前版本）
     */
    private Integer versionNo;
    /**
     * 会话状态（READY/ACTIVE/ASKING/CLOSED）
     */
    private String status;
    /**
     * 消息轮次 JSON 快照（数组）
     */
    private String rounds;
    /**
     * HITL 挂起上下文 JSON（无挂起为 null）
     */
    private String pendingConfirmations;

}
