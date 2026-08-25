package com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Skill 版本资源 DO（贫血模型，不可变：只插不改）。一行 = 版本内一个资源文件，
 * 内容为文本或 "base64:" 前缀二进制（对齐 agentscope 生态约定）。
 */
@TableName("ai_skill_resources")
@KeySequence("ai_skill_resources_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillResourceDO extends TenantBaseDO {

    /**
     * 资源行编号
     */
    @TableId
    private Long id;
    /**
     * 所属版本编号（ai_skill_version.id，软关联）
     */
    private Long versionId;
    /**
     * 资源相对路径（版本内唯一）
     */
    private String resourcePath;
    /**
     * 资源内容（文本或 base64: 前缀二进制）
     */
    private String resourceContent;

}
