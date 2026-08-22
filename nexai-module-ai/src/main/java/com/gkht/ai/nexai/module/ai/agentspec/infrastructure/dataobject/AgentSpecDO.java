package com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 智能体规格 DO（贫血模型）。draft 列存草稿配置 JSON 字符串（与版本快照同构），NULL 表示无草稿；
 * 默认版本以版本号表达（而非版本记录编号），发布在聚合内即可完成指针前移。
 * 给 LLM 的自描述在 draft JSON 内（随版本快照固化），主体列只留管理元数据（name/icon）。
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
     * 图标标识
     */
    private String icon;
    /**
     * 已发布的最新版本号，从未发布为 0
     */
    private Integer latestVersionNo;
    /**
     * 当前默认版本号，从未发布为 null
     */
    private Integer currentVersionNo;
    /**
     * 草稿配置 JSON 字符串（模型引用/自描述/系统提示/推理参数/调用参数/挂载列表）
     */
    private String draft;

}
