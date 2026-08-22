package com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能版本 DO（贫血模型）：发布时固化的不可变全量快照，只插入不更新。
 * skill_md 存 SKILL.md 原文，resources 存资源文件集 JSON 字符串。
 */
@TableName("ai_skill_version")
@KeySequence("ai_skill_version_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillVersionDO extends TenantBaseDO {

    /**
     * 版本记录编号
     */
    @TableId
    private Long id;
    /**
     * 所属技能编号（ai_skill.id）
     */
    private Long skillId;
    /**
     * 版本号，技能内从 1 递增
     */
    private Integer versionNo;
    /**
     * SKILL.md 原文快照（不可变）
     */
    private String skillMd;
    /**
     * 资源文件集 JSON 字符串（path → content，不可变）
     */
    private String resources;
    /**
     * 发布说明
     */
    private String remark;

}
