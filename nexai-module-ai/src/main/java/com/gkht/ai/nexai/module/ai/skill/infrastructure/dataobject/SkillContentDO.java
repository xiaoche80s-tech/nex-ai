package com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能内容 DO（贫血模型）：一行 = 一份完整 SKILL.md（ADR-0003 修订）。
 * 内容行不可变：编辑草稿 = 写新行；发布 = 版本行引用转正（零复制）。
 * skill_id 冗余归属列，供聚合删除时级联清理（含不再被引用的历史草稿行）。
 */
@TableName("ai_skill_content")
@KeySequence("ai_skill_content_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillContentDO extends TenantBaseDO {

    /**
     * 内容行编号
     */
    @TableId
    private Long id;
    /**
     * 归属技能编号（ai_skill.id）
     */
    private Long skillId;
    /**
     * SKILL.md 全文（front matter 完整保留）
     */
    private String skillMd;

}
