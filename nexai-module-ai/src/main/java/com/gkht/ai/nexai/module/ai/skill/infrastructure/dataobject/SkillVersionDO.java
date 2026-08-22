package com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能版本 DO（贫血模型）：发布时固化的不可变指针行，只插入不更新（ADR-0003 修订）。
 * 内容本体在 ai_skill_content——发布 = 把草稿内容行引用转正到本表 content_id（零复制）。
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
     * 内容行编号（ai_skill_content.id，发布时从主表草稿指针转正）
     */
    private Long contentId;
    /**
     * 发布说明
     */
    private String remark;

}
