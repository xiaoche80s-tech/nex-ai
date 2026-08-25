package com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Skill 资产 DO（贫血模型）。主体列只留管理元数据（name/description/owner_level/owner_user_id），
 * 能力包内容（Markdown + 资源）随版本链存 {@link SkillVersionDO}（不可变快照）。
 */
@TableName("ai_skill")
@KeySequence("ai_skill_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillDO extends TenantBaseDO {

    /**
     * skill 编号
     */
    @TableId
    private Long id;
    /**
     * 技能名称（agent 引用标识，创建后不可变；同一归属下唯一）
     */
    private String name;
    /**
     * 描述
     */
    private String description;
    /**
     * 归属层级（TENANT/USER）
     */
    private String ownerLevel;
    /**
     * 归属用户编号（用户级 = 创建者），非用户级为 null
     */
    private Long ownerUserId;
    /**
     * 当前生效版本号（运行时物化采用），null = 尚无版本
     */
    private Integer currentVersionNo;
    /**
     * 是否上架（0=下架 1=上架，终端技能目录曝光位）
     */
    private Integer published;
    /**
     * Git 同步源编号（软关联 ai_skill_git_source，工单 29 建），自建为 null
     */
    private Long gitSourceId;

}
