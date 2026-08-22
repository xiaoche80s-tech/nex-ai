package com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能资源文件 DO（贫血模型）：一行 = 一个附属资源文件，行级化对齐官方
 * agentscope_skill_resources 惯例（ADR-0003 修订）。(content_id, path) 业务唯一，
 * 逻辑删除与租户全套列随 TenantBaseDO。
 */
@TableName("ai_skill_resource")
@KeySequence("ai_skill_resource_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillResourceDO extends TenantBaseDO {

    /**
     * 资源行编号
     */
    @TableId
    private Long id;
    /**
     * 所属内容行编号（ai_skill_content.id）
     */
    private Long contentId;
    /**
     * 资源文件相对路径（如 scripts/run.py）
     */
    private String path;
    /**
     * 资源文件内容
     */
    private String content;

}
