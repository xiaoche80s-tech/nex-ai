package com.gkht.ai.nexai.module.ai.skill.domain.repository;

import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillOwnerLevel;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillVersion;

import java.util.List;

/**
 * Skill 聚合仓储端口（按聚合不按表）：skill 主体（描述/当前版本指针）与不可变版本链
 * 同属一个聚合边界。列表查询走轻量读写分离（应用服务经 Mapper 直查转 DTO）。
 */
public interface SkillRepository {

    /**
     * 保存 skill：无编号时插入（回填编号），有编号时更新主体
     *
     * @return skill 编号
     */
    Long save(Skill skill);

    /**
     * 按编号读取 skill，不存在返回 null
     */
    Skill findById(Long id);

    /**
     * 删除 skill 及其版本链（聚合级联）
     */
    void deleteByIdCascade(Long id);

    /**
     * 保存版本（不可变，仅插入；skillId 未落库时先回填）
     */
    Long saveVersion(SkillVersion version);

    /**
     * 读取 skill 下全部版本（按版本号升序），无版本返回空列表
     */
    List<SkillVersion> listVersions(Long skillId);

    /**
     * 读取 skill 下的最大版本号，无版本返回 null（登记新版本时计算）
     */
    Integer findMaxVersionNo(Long skillId);

    /**
     * 精确读取某版本（含 markdown 与资源），不存在返回 null
     */
    SkillVersion findVersion(Long skillId, Integer versionNo);

    /**
     * 更新 skill 主体（描述/当前版本指针）
     */
    void update(Skill skill);

}
