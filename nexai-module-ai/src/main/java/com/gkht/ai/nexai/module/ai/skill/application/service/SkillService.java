package com.gkht.ai.nexai.module.ai.skill.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillCreateCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillVersionCommand;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillVersionContentDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillVersionDTO;
import com.gkht.ai.nexai.module.ai.skill.application.query.SkillPageQuery;

import java.util.List;

/**
 * Skill 资产应用服务：创建/版本链/物化/列表/删除。归属（TENANT/USER）由登录态推导，
 * 物化经 domain/gateway 端口（agentscope 直用）。
 */
public interface SkillService {

    /**
     * 创建 skill 并登记首个版本，物化到文件目录
     *
     * @return skill 编号
     */
    Long createSkill(SkillCreateCommand command, Long userId);

    /**
     * 登记新版本（编辑产生），物化到文件目录
     *
     * @return 新版本号
     */
    Integer addSkillVersion(SkillVersionCommand command, Long userId);

    /**
     * Skill 分页列表（归属可见性：租户级租户内全见 + 用户级仅归属用户）
     */
    PageResult<SkillDTO> getSkillPage(SkillPageQuery query, Long userId);

    /**
     * Skill 版本列表（当前版本标识 + 物化路径）
     */
    List<SkillVersionDTO> listSkillVersions(Long skillId, Long userId);

    /**
     * 读取指定版本全量内容（markdown + 资源路径清单，预览用）
     */
    SkillVersionContentDTO getVersionContent(Long skillId, Integer versionNo);

    /**
     * 删除 skill 及其版本链（级联）
     */
    void deleteSkill(Long id, Long userId);

    /**
     * 上架（进入终端技能目录）
     */
    void publishSkill(Long id, Long userId);

    /**
     * 下架（移出终端技能目录）
     */
    void unpublishSkill(Long id, Long userId);

}
