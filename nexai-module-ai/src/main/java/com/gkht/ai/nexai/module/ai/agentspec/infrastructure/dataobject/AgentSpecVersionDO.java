package com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 智能体规格版本 DO（贫血模型）：发布时固化的不可变全量快照，只插入不更新。
 * snapshot 列存配置 JSON 字符串。
 */
@TableName("ai_agent_spec_version")
@KeySequence("ai_agent_spec_version_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentSpecVersionDO extends TenantBaseDO {

    /**
     * 版本记录编号
     */
    @TableId
    private Long id;
    /**
     * 所属规格编号（ai_agent_spec.id）
     */
    private Long specId;
    /**
     * 版本号，规格内从 1 递增
     */
    private Integer versionNo;
    /**
     * 全量配置快照 JSON 字符串（不可变）
     */
    private String snapshot;
    /**
     * 发布说明
     */
    private String remark;

}
