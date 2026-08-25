package com.gkht.ai.nexai.module.ai.skill.infrastructure.repository;

import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillOwnerLevel;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillVersion;
import com.gkht.ai.nexai.module.ai.skill.domain.repository.SkillRepository;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.converter.SkillConverter;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillResourceDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillVersionDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillResourceMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Skill Repository 实现：skill 聚合根与其不可变版本链的 DO ↔ 领域模型适配。
 * 版本内容已拆表（工单 26）：markdown 走 ai_skill_version.skill_markdown、
 * 资源走 ai_skill_resources 行（只插不改）。
 */
@Repository
public class SkillRepositoryImpl implements SkillRepository {

    @Resource
    private SkillMapper skillMapper;

    @Resource
    private SkillVersionMapper skillVersionMapper;

    @Resource
    private SkillResourceMapper skillResourceMapper;

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
    public void deleteByIdCascade(Long id) {
        // 资源行先于版本行清理（软删语义下，selectList 自动滤已删行）
        skillVersionMapper.selectListBySkillId(id).forEach(versionDO ->
                skillResourceMapper.delete(new LambdaQueryWrapperX<SkillResourceDO>()
                        .eq(SkillResourceDO::getVersionId, versionDO.getId())));
        skillVersionMapper.delete(new LambdaQueryWrapperX<SkillVersionDO>()
                .eq(SkillVersionDO::getSkillId, id));
        skillMapper.deleteById(id);
    }

    @Override
    public Long saveVersion(SkillVersion version) {
        SkillVersionDO dataObject = skillConverter.toVersionDataObject(version);
        skillVersionMapper.insert(dataObject);
        // 版本行落库拿到主键后展开资源行（只插不改：版本不可变的物理体现）。
        // 逐行插入而非 insertBatch：Db.saveBatch 按类型静态查找 Mapper，测试上下文
        // 的 @Import 注册与自动扫描会双 bean 歧义；资源行 ≤128，循环插入无性能差异
        for (SkillResourceDO row : skillConverter.contentToResourceDOs(dataObject.getId(),
                version.getContent())) {
            skillResourceMapper.insert(row);
        }
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
    public SkillVersion findVersion(Long skillId, Integer versionNo) {
        SkillVersionDO dataObject = skillVersionMapper.selectOne(new LambdaQueryWrapperX<SkillVersionDO>()
                .eq(SkillVersionDO::getSkillId, skillId)
                .eq(SkillVersionDO::getVersionNo, versionNo));
        return dataObject == null ? null : reconstituteVersion(dataObject);
    }

    @Override
    public void update(Skill skill) {
        skillMapper.updateById(skillConverter.toDataObject(skill));
    }

    private Skill reconstitute(SkillDO dataObject) {
        return Skill.reconstitute(dataObject.getId(), dataObject.getName(),
                dataObject.getDescription(), SkillOwnerLevel.valueOf(dataObject.getOwnerLevel()),
                dataObject.getOwnerUserId(), dataObject.getCurrentVersionNo(),
                dataObject.getPublished() != null && dataObject.getPublished() == 1,
                dataObject.getGitSourceId(), dataObject.getCreateTime());
    }

    private SkillVersion reconstituteVersion(SkillVersionDO dataObject) {
        return SkillVersion.reconstitute(dataObject.getId(), dataObject.getSkillId(),
                dataObject.getVersionNo(),
                skillConverter.toContent(dataObject.getSkillMarkdown(),
                        skillResourceMapper.selectListByVersionId(dataObject.getId())),
                dataObject.getNote(), dataObject.getCreateTime());
    }

}
