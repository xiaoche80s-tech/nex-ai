package com.gkht.ai.nexai.module.ai.usage.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型用量 DO（append-only）：单次模型调用的 token（输入/输出/缓存命中）与耗时，
 * 维度含租户（tenant_id 由租户插件注入）/会话/规格。先采集不计价（第三波计价数据源）。
 */
@TableName("ai_model_usage")
@KeySequence("ai_model_usage_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增
@Data
@EqualsAndHashCode(callSuper = true)
public class ModelUsageDO extends TenantBaseDO {

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
    /** 槽位用户 */
    private String userId;
    /** 模型名（agentscope Model.getModelName） */
    private String modelName;
    /** 本次调用消息数 */
    private Integer messageCount;
    /** 输入 token */
    private Integer inputTokens;
    /** 输出 token */
    private Integer outputTokens;
    /** 缓存命中 token（inputTokens 子集，未报告为 0） */
    private Integer cachedTokens;
    /** 总 token（派生：输入 + 输出，落列便于聚合） */
    private Integer totalTokens;
    /** 模型调用耗时（秒） */
    private Double durationSeconds;
    /** 何时 */
    private java.time.LocalDateTime occurredAt;

}
