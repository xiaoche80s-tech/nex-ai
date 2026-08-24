package com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 智能体规格 DO（贫血模型）。draft 列存草稿配置 JSON 字符串（四层结构：
 * agent 层/模型调用层/挂载层/执行环境层），NULL 表示无草稿。
 * 行为性配置全在 draft JSON 内随版本快照固化，主体列只留管理元数据
 * （name/spec_code/owner_level/owner_user_id/icon；spec_code 与归属创建后不可变）。
 */
@TableName("ai_agent_spec")
@KeySequence("ai_agent_spec_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentSpecDO extends TenantBaseDO {

    /**
     * 规格编号
     */
    @TableId
    private Long id;
    /**
     * 规格名称
     */
    private String name;
    /**
     * 业务编码（slug），创建后不可变；唯一性按归属层级（生产库部分唯一索引 uk_ai_agent_spec_code）
     */
    private String specCode;
    /**
     * 图标标识
     */
    private String icon;
    /**
     * 归属层级（PLATFORM/TENANT/USER），创建后不可变
     */
    private String ownerLevel;
    /**
     * 归属用户编号（用户级 = 创建者），非用户级为 null
     */
    private Long ownerUserId;
    /**
     * 草稿配置 JSON 字符串（模型引用/自描述/系统提示/推理参数/调用参数/挂载列表/执行环境）。
     * 更新策略 ALWAYS：发布清空草稿（ADR 0004）时须把 NULL 写回库，
     * 默认的 NOT_NULL 策略会跳过 null 字段导致草稿残留。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String draft;
    /**
     * 当前生效版本号（当前版本指针，运行寻址），NULL 表示从未发布
     */
    private Integer currentVersionNo;

}
