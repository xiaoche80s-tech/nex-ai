package com.gkht.ai.nexai.module.ai.skill.infrastructure.repository;

import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillContent;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillOwnerLevel;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillVersion;
import com.gkht.ai.nexai.module.ai.skill.domain.repository.SkillRepository;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.converter.SkillConverter;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillVersionDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Skill Repository 实现：skill 聚合根与其不可变版本链的 DO ↔ 领域模型适配。
 * 版本内容（markdown + resources）经 converter 的 ContentJSON 桥接序列化。
 */
@Repository
public class SkillRepositoryImpl implements SkillRepository {

    @Resource
    private SkillMapper skillMapper;

    @Resource
    private SkillVersionMapper skillVersionMapper;

    @Resource
    private SkillConverter skillConverter;

    @Override
    public Long save(Skill skill) {
        SkillDO dataObject = skillConverter.toDataObject(skill);
        skillMapper.insert(dataObject);
        // 回填 DB 生成的主键（后续 update 推进版本指针需要聚合根 id）
        skill.assignId(dataObject.getId());
        return dataObject.getId();
    }

    @Override
    public Skill findById(Long id) {
        SkillDO dataObject = skillMapper.selectById(id);
        return dataObject == null ? null : reconstitute(dataObject);
    }

    @Override
    public Skill findByNameAndOwner(SkillOwnerLevel ownerLevel, Long ownerUserId, String name) {
        SkillDO dataObject = skillMapper.selectOne(
                new com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX<SkillDO>()
                        .eq(SkillDO::getOwnerLevel, ownerLevel.name())
                        .eq(ownerUserId != null, SkillDO::getOwnerUserId, ownerUserId)
                        .isNull(ownerUserId == null, SkillDO::getOwnerUserId)
                        .eq(SkillDO::getName, name));
        return dataObject == null ? null : reconstitute(dataObject);
    }

    @Override
    public void deleteByIdCascade(Long id) {
        skillMapper.deleteById(id);
        skillVersionMapper.delete(new com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX<SkillVersionDO>()
                .eq(SkillVersionDO::getSkillId, id));
    }

    @Override
    public Long saveVersion(SkillVersion version) {
        SkillVersionDO dataObject = skillConverter.toVersionDataObject(version);
        skillVersionMapper.insert(dataObject);
        return dataObject.getId();
    }

    @Override
    public List<SkillVersion> listVersions(Long skillId) {
        return skillVersionMapper.selectListBySkillId(skillId).stream()
                .map(this::reconstituteVersion).toList();
    }

    @Override
    public Integer findMaxVersionNo(Long skillId) {
        return skillVersionMapper.selectMaxVersionNo(skillId);
    }

    @Override
    public void update(Skill skill) {
        skillMapper.updateById(skillConverter.toDataObject(skill));
    }

    private Skill reconstitute(SkillDO dataObject) {
        return Skill.reconstitute(dataObject.getId(), dataObject.getName(),
                dataObject.getDescription(), SkillOwnerLevel.valueOf(dataObject.getOwnerLevel()),
                dataObject.getOwnerUserId(), dataObject.getCurrentVersionNo(),
                dataObject.getCreateTime());
    }

    private SkillVersion reconstituteVersion(SkillVersionDO dataObject) {
        return SkillVersion.reconstitute(dataObject.getId(), dataObject.getSkillId(),
                dataObject.getVersionNo(), skillConverter.jsonToContent(dataObject.getContent()),
                dataObject.getNote(), dataObject.getCreateTime());
    }

}
