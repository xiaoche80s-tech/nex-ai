package com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能 DO（贫血模型）：主体与指针，零内容列（ADR-0003 修订）。
 * 草稿经 draft_content_id 指向内容表行（NULL 表示无草稿）；
 * 默认版本以版本号表达，发布在聚合内即可完成指针前移。
 */
@TableName("ai_skill")
@KeySequence("ai_skill_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillDO extends TenantBaseDO {

    /**
     * 技能编号
     */
    @TableId
    private Long id;
    /**
     * 技能名（SKILL.md front matter 的 name，运行时挂载寻址键）
     */
    private String name;
    /**
     * 技能描述（SKILL.md front matter 的 description，冗余列供列表展示）
     */
    private String description;
    /**
     * 已发布的最新版本号，从未发布为 0
     */
    private Integer latestVersionNo;
    /**
     * 当前默认版本号（运行时读取的版本），从未发布为 null
     */
    private Integer currentVersionNo;
    /**
     * 草稿内容行编号（ai_skill_content.id），NULL 表示无草稿
     */
    private Long draftContentId;

}
