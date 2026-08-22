package com.gkht.ai.nexai.module.ai.skill.domain.repository;

import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillVersion;

import java.util.List;

/**
 * 技能聚合 Repository 端口：按聚合（技能 + 其全部版本）而非按表。
 */
public interface SkillRepository {

    /**
     * 保存聚合根：无编号插入，有编号显式全量更新（发布会把草稿写回 null）
     *
     * @return 技能编号
     */
    Long save(Skill skill);

    Skill findById(Long id);

    /** 按技能名查找（租户内），供重名校验与运行时按名寻址 */
    Skill findByName(String name);

    /** 删除技能及其全部版本（逻辑删除） */
    void deleteByIdCascade(Long id);

    /**
     * 插入版本快照（insert-only；带编号的版本是已落库的存量记录，更新即违反不可变不变量）
     */
    void createVersion(SkillVersion version);

    List<SkillVersion> findVersionsBySkillId(Long skillId);

    SkillVersion findVersion(Long skillId, Integer versionNo);

}
