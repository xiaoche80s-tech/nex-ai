package com.gkht.ai.nexai.module.ai.audit.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审计事件 DO（append-only，追加写不更新）：谁-何时-哪个会话-调了什么工具-结果摘要。
 * 继承 TenantBaseDO——按租户隔离（tenant_id 由租户插件注入）。
 */
@TableName("ai_audit_event")
@KeySequence("ai_audit_event_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditEventDO extends TenantBaseDO {

    @TableId
    private Long id;
    /** 会话业务键（= agentscope 槽位 sessionId） */
    private String sessionKey;
    /** 规格编号（维度：规格） */
    private Long specId;
    /** 规格版本号（维度：规格） */
    private Integer versionNo;
    /** agentId（= specCode） */
    private String agentId;
    /** 谁（agentscope 槽位用户） */
    private String userId;
    /** 工具调用编号 */
    private String toolCallId;
    /** 调了什么工具 */
    private String toolName;
    /** 结果（SUCCESS/ERROR/DENIED/INTERRUPTED/UNKNOWN） */
    private String outcome;
    /** 入参摘要（脱敏后） */
    private String argumentsDigest;
    /** 结果摘要（脱敏后） */
    private String resultDigest;
    /** acting 段耗时（毫秒） */
    private Long durationMs;
    /** 何时 */
    private java.time.LocalDateTime occurredAt;

}
