package com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 智能体规格版本快照 DO（贫血模型，不可变：只插不改不删）。
 * config 列存一次发布固化的全量四层配置 JSON（与 ai_agent_spec.draft 同构），
 * 是智能体运行的唯一权威源——本地盘仅是它的物化缓存。
 * 版本号在同一规格内唯一（uk_ai_agent_spec_version 兜底并发）；快照含租户列
 * 随规格级联（版本按规格聚合，租户归属继承自规格）。
 */
@TableName("ai_agent_spec_version")
@KeySequence("ai_agent_spec_version_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentSpecVersionDO extends TenantBaseDO {

    /**
     * 版本快照编号
     */
    @TableId
    private Long id;
    /**
     * 所属规格编号（聚合根引用）
     */
    private Long specId;
    /**
     * 版本号（规格内严格递增，1 起），发布后不可变
     */
    private Integer versionNo;
    /**
     * 全量四层配置快照 JSON（与草稿同构：agent 层/模型调用层/挂载层/执行环境层）
     */
    private String config;
    /**
     * 发布备注
     */
    private String note;

}
