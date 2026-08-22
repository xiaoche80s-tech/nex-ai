package com.gkht.ai.nexai.module.ai.skill.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gkht.ai.nexai.module.ai.skill.domain.exception.SkillVersionImmutableException;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillVersion;
import com.gkht.ai.nexai.module.ai.skill.domain.repository.SkillRepository;
import com.gkht.ai.nexai.module.ai.skill.domain.valueobject.SkillContent;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.converter.SkillConverter;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillVersionDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 技能 Repository 实现：DO ↔ 领域模型适配，聚合重建经 reconstitute。
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
        if (dataObject.getId() == null) {
            skillMapper.insert(dataObject);
        } else {
            // updateById 默认忽略 null 字段，而发布会把草稿写回 null，故显式全量 set；
            // 首参传空 DO 承接 updater/update_time 自动填充
            skillMapper.update(new SkillDO(), new LambdaUpdateWrapper<SkillDO>()
                    .eq(SkillDO::getId, dataObject.getId())
                    .set(SkillDO::getName, dataObject.getName())
                    .set(SkillDO::getDescription, dataObject.getDescription())
                    .set(SkillDO::getLatestVersionNo, dataObject.getLatestVersionNo())
                    .set(SkillDO::getCurrentVersionNo, dataObject.getCurrentVersionNo())
                    .set(SkillDO::getDraftSkillMd, dataObject.getDraftSkillMd())
                    .set(SkillDO::getDraftResources, dataObject.getDraftResources()));
        }
        return dataObject.getId();
    }

    @Override
    public Skill findById(Long id) {
        SkillDO dataObject = skillMapper.selectById(id);
        return dataObject == null ? null : reconstitute(dataObject);
    }

    @Override
    public Skill findByName(String name) {
        SkillDO dataObject = skillMapper.selectByName(name);
        return dataObject == null ? null : reconstitute(dataObject);
    }

    @Override
    public void deleteByIdCascade(Long id) {
        skillMapper.deleteById(id);
        skillVersionMapper.deleteBySkillId(id);
    }

    @Override
    public void createVersion(SkillVersion version) {
        // 端口契约：版本只插入（insert-only）；带编号的版本是已落库的存量记录，更新即违反不可变不变量
        if (version.getId() != null) {
            throw new SkillVersionImmutableException(version.getSkillId(), version.getVersionNo());
        }
        SkillVersionDO dataObject = skillConverter.toVersionDataObject(version);
        skillVersionMapper.insert(dataObject);
    }

    @Override
    public List<SkillVersion> findVersionsBySkillId(Long skillId) {
        return skillVersionMapper.selectListBySkillId(skillId).stream()
                .map(this::reconstituteVersion).toList();
    }

    @Override
    public SkillVersion findVersion(Long skillId, Integer versionNo) {
        SkillVersionDO dataObject = skillVersionMapper.selectBySkillIdAndVersionNo(skillId, versionNo);
        return dataObject == null ? null : reconstituteVersion(dataObject);
    }

    private Skill reconstitute(SkillDO dataObject) {
        return Skill.reconstitute(dataObject.getId(), dataObject.getName(),
                dataObject.getDescription(), dataObject.getLatestVersionNo(),
                dataObject.getCurrentVersionNo(),
                skillConverter.jsonToDraft(dataObject.getDraftSkillMd(), dataObject.getDraftResources()),
                dataObject.getCreateTime());
    }

    private SkillVersion reconstituteVersion(SkillVersionDO dataObject) {
        return SkillVersion.reconstitute(dataObject.getId(), dataObject.getSkillId(),
                dataObject.getVersionNo(),
                SkillContent.of(dataObject.getSkillMd(),
                        skillConverter.jsonToResources(dataObject.getResources())),
                dataObject.getRemark(), dataObject.getCreateTime());
    }

}
