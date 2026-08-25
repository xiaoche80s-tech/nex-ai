package com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Skill 版本 DO（贫血模型，不可变：只插不改不删）。skill_markdown 列存 SKILL.md 全文
 * （YAML frontmatter + 正文），资源文件在 ai_skill_resources（工单 26 拆表），
 * 版本号按 skill 内严格递增（唯一索引兜底并发）。
 */
@TableName("ai_skill_version")
@KeySequence("ai_skill_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillVersionDO extends TenantBaseDO {

    /**
     * 版本编号
     */
    @TableId
    private Long id;
    /**
     * 所属 skill 编号
     */
    private Long skillId;
    /**
     * 版本号（skill 内严格递增，1 起）
     */
    private Integer versionNo;
    /**
     * SKILL.md 全文（YAML frontmatter + 正文）；资源文件在 ai_skill_resources
     */
    private String skillMarkdown;
    /**
     * 版本备注
     */
    private String note;

}
