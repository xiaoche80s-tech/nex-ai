package com.gkht.ai.nexai.module.ai.skill.domain.gateway;

import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillContent;

/**
 * Skill 运行时物化端口（六边形出端口）：把 DB 版本链的当前版本内容物化为文件目录，
 * 供 agentscope 文件仓库源读取（ADR-0003 TENANT/USER 轨）。物化位置按归属隔离：
 * 租户级 {root}/t{tenantId}/skills/{name}/，用户级 {root}/t{tenantId}/u{userId}/skills/{name}/。
 * 实现侧直用 agentscope SkillFileSystemHelper.saveSkills 契约（SKILL.md + 资源文件目录）。
 */
public interface SkillMaterializationGateway {

    /**
     * 物化 skill 的指定版本到文件目录（覆盖写：内容比对后仅变化时落盘）。
     *
     * @param skill    skill 聚合根（名称/归属决定目录）
     * @param tenantId 租户编号
     * @param content  版本能力包内容（Markdown + 资源）
     * @return 物化后的目录绝对路径
     */
    String materialize(Skill skill, Long tenantId, SkillContent content);
}
