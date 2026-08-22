package com.gkht.ai.nexai.module.ai.skill.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillCreateCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillPublishCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillSwitchVersionCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillUpdateCommand;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDetailDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillVersionDTO;
import com.gkht.ai.nexai.module.ai.skill.application.query.SkillPageQuery;

import java.util.List;

/**
 * 技能应用服务：技能 CRUD 与版本链编排（草稿 → 发布 → 再编辑）。
 */
public interface SkillService {

    /** 创建技能（携带首个草稿），返回技能编号 */
    Long createSkill(SkillCreateCommand command);

    /** 编辑技能（覆盖草稿；SKILL.md 的 name/description 随草稿更新） */
    void updateSkill(SkillUpdateCommand command);

    /** 发布当前草稿为不可变新版本，默认版本指针前移，返回新版本号 */
    Integer publishSkill(SkillPublishCommand command);

    /** 切换当前默认版本（回滚/迭代入口） */
    void switchDefaultVersion(SkillSwitchVersionCommand command);

    /** 删除技能及其全部版本 */
    void deleteSkill(Long id);

    /** 技能分页 */
    PageResult<SkillDTO> getSkillPage(SkillPageQuery query);

    /** 技能详情（含草稿与当前默认版本快照） */
    SkillDetailDTO getSkill(Long id);

    /** 版本历史（按版本号倒序，含各版本内容快照） */
    List<SkillVersionDTO> getVersionList(Long skillId);

}
