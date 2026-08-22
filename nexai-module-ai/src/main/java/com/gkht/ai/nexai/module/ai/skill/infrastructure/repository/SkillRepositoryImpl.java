package com.gkht.ai.nexai.module.ai.skill.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gkht.ai.nexai.module.ai.skill.domain.exception.SkillVersionImmutableException;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillVersion;
import com.gkht.ai.nexai.module.ai.skill.domain.repository.SkillRepository;
import com.gkht.ai.nexai.module.ai.skill.domain.valueobject.SkillContent;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.converter.SkillConverter;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillContentDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillResourceDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillVersionDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillContentMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillResourceMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 技能 Repository 实现（ADR-0003 修订：内容独立成表 + 资源行级子表）。
 *
 * <p><b>内容行生命周期</b>（内容行不可变）：编辑草稿 = 插入新内容行 + 资源行，主表指针前移
 * （内容未变则复用原行，幂等保存），无版本引用的旧内容行连同资源行清理；
 * 发布 = 版本行引用转正（content_id 取主表现存草稿指针，零复制），随后主表指针置空——
 * 故 {@link #createVersion} 必须先于 {@link #save} 执行（应用服务编排该顺序），本实现带防御断言。</p>
 *
 * <p>方法上的 @Transactional 是兜底而非事务边界（边界在应用服务）：AgentscopeSkillRepository
 * 等非应用层调用方直调时保证多表原子性；传播 REQUIRED，经应用服务进入时加入同一事务。</p>
 */
@Repository
public class SkillRepositoryImpl implements SkillRepository {

    @Resource
    private SkillMapper skillMapper;

    @Resource
    private SkillVersionMapper skillVersionMapper;

    @Resource
    private SkillContentMapper skillContentMapper;

    @Resource
    private SkillResourceMapper skillResourceMapper;

    @Resource
    private SkillContentAssembler contentAssembler;

    @Resource
    private SkillConverter skillConverter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(Skill skill) {
        if (skill.getId() == null) {
            return insert(skill);
        }
        return update(skill);
    }

    private Long insert(Skill skill) {
        // 领域不变量：新建必带草稿（Skill.create 强制），存储层显式断言防未来调用方绕过
        if (!skill.hasDraft()) {
            throw new IllegalStateException("新技能必须携带初始草稿，内容行无法落库");
        }
        SkillDO main = skillConverter.toDataObject(skill);
        // 新技能：主表先落库（内容行需归属 skill_id），内容行随后插入并回填指针
        skillMapper.insert(main);
        Long contentId = insertContent(main.getId(), skill.getDraft());
        skillMapper.update(new SkillDO(), new LambdaUpdateWrapper<SkillDO>()
                .eq(SkillDO::getId, main.getId())
                .set(SkillDO::getDraftContentId, contentId));
        return main.getId();
    }

    private Long update(Skill skill) {
        SkillDO existing = skillMapper.selectById(skill.getId());
        if (existing == null) {
            // 并发删除的竞态兜底：调用方（应用服务 requireSkill / gateway findByName）校验后、
            // 本事务落库前技能可能已被删除，明确报错而非 NPE
            throw new IllegalStateException("技能 " + skill.getId() + " 已不存在，保存失败（可能已被并发删除）");
        }
        Long oldDraftContentId = existing.getDraftContentId();
        Long newContentId = draftContentIdFor(skill, oldDraftContentId);

        SkillDO dataObject = skillConverter.toDataObject(skill);
        // updateById 默认忽略 null 字段，而发布会把草稿指针写回 null，故显式全量 set；
        // 首参传空 DO 承接 updater/update_time 自动填充
        skillMapper.update(new SkillDO(), new LambdaUpdateWrapper<SkillDO>()
                .eq(SkillDO::getId, dataObject.getId())
                .set(SkillDO::getName, dataObject.getName())
                .set(SkillDO::getDescription, dataObject.getDescription())
                .set(SkillDO::getLatestVersionNo, dataObject.getLatestVersionNo())
                .set(SkillDO::getCurrentVersionNo, dataObject.getCurrentVersionNo())
                .set(SkillDO::getDraftContentId, newContentId));

        // 旧草稿内容行清理：指针已被版本引用（发布引用转正）的保留，纯草稿迭代的连同资源行清理
        if (oldDraftContentId != null && !oldDraftContentId.equals(newContentId)) {
            deleteContentIfUnreferenced(oldDraftContentId);
        }
        return dataObject.getId();
    }

    /**
     * 聚合草稿 → 应指向的内容行编号：草稿内容与现存内容行相同（值判等）则复用原行
     * （幂等保存，切换默认版本等不碰草稿的 save 不产生内容行 churn）；否则写新行
     */
    private Long draftContentIdFor(Skill skill, Long oldDraftContentId) {
        if (!skill.hasDraft()) {
            return null;
        }
        if (oldDraftContentId != null) {
            SkillContent existingDraft = contentAssembler.assembleOne(oldDraftContentId);
            if (existingDraft != null && existingDraft.equals(skill.getDraft())) {
                return oldDraftContentId;
            }
        }
        return insertContent(skill.getId(), skill.getDraft());
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
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIdCascade(Long id) {
        // 资源行的清理键是内容行编号：先于内容行逻辑删取出集合
        List<Long> contentIds = skillContentMapper.selectListBySkillId(id).stream()
                .map(SkillContentDO::getId).toList();
        skillResourceMapper.deleteByContentIds(contentIds);
        skillContentMapper.deleteBySkillId(id);
        skillVersionMapper.deleteBySkillId(id);
        skillMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVersion(SkillVersion version) {
        // 端口契约：版本只插入（insert-only）；带编号的版本是已落库的存量记录，更新即违反不可变不变量
        if (version.getId() != null) {
            throw new SkillVersionImmutableException(version.getSkillId(), version.getVersionNo());
        }
        // 引用转正：版本行指向主表当前草稿内容行（调用方须在 save 清空指针前调用）
        SkillDO main = skillMapper.selectById(version.getSkillId());
        if (main == null || main.getDraftContentId() == null) {
            throw new IllegalStateException(
                    "技能 " + version.getSkillId() + " 无草稿内容行可转正，发布顺序异常（createVersion 须先于 save）");
        }
        SkillVersionDO dataObject = skillConverter.toVersionDataObject(version);
        dataObject.setContentId(main.getDraftContentId());
        skillVersionMapper.insert(dataObject);
    }

    @Override
    public List<SkillVersion> findVersionsBySkillId(Long skillId) {
        return reconstituteVersions(skillVersionMapper.selectListBySkillId(skillId));
    }

    @Override
    public SkillVersion findVersion(Long skillId, Integer versionNo) {
        SkillVersionDO dataObject = skillVersionMapper.selectBySkillIdAndVersionNo(skillId, versionNo);
        return dataObject == null ? null : reconstituteVersions(List.of(dataObject)).get(0);
    }

    // ==================== 跨表装配 ====================

    private Skill reconstitute(SkillDO dataObject) {
        return Skill.reconstitute(dataObject.getId(), dataObject.getName(),
                dataObject.getDescription(), dataObject.getLatestVersionNo(),
                dataObject.getCurrentVersionNo(), draftOf(dataObject), dataObject.getCreateTime());
    }

    /** 主表草稿指针 → 领域草稿值对象；无指针为 null */
    private SkillContent draftOf(SkillDO dataObject) {
        return contentAssembler.assembleOne(dataObject.getDraftContentId());
    }

    private List<SkillVersion> reconstituteVersions(List<SkillVersionDO> versionRows) {
        if (versionRows.isEmpty()) {
            return List.of();
        }
        Map<Long, SkillContent> contentById = contentAssembler.assemble(
                versionRows.stream().map(SkillVersionDO::getContentId).filter(Objects::nonNull).toList());
        List<SkillVersion> versions = new ArrayList<>(versionRows.size());
        for (SkillVersionDO row : versionRows) {
            SkillContent content = contentById.get(row.getContentId());
            if (content == null) {
                throw new IllegalStateException("技能版本 " + row.getId() + " 指向的内容行 " + row.getContentId() + " 缺失");
            }
            versions.add(SkillVersion.reconstitute(row.getId(), row.getSkillId(), row.getVersionNo(),
                    content, row.getRemark(), row.getCreateTime()));
        }
        return versions;
    }

    /** 插入一份完整内容（内容行 + 资源行），返回内容行编号 */
    private Long insertContent(Long skillId, SkillContent content) {
        SkillContentDO contentRow = new SkillContentDO();
        contentRow.setSkillId(skillId);
        contentRow.setSkillMd(content.getSkillMd());
        skillContentMapper.insert(contentRow);
        List<SkillResourceDO> resourceRows = content.getResources().entrySet().stream()
                .map(entry -> resourceOf(contentRow.getId(), entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        if (!resourceRows.isEmpty()) {
            skillResourceMapper.insertBatch(resourceRows);
        }
        return contentRow.getId();
    }

    private SkillResourceDO resourceOf(Long contentId, String path, String content) {
        SkillResourceDO row = new SkillResourceDO();
        row.setContentId(contentId);
        row.setPath(path);
        row.setContent(content);
        return row;
    }

    /** 内容行无版本引用（纯草稿迭代残留）时连同资源行逻辑删；被版本引用的历史内容保留 */
    private void deleteContentIfUnreferenced(Long contentId) {
        boolean referencedByVersion = skillVersionMapper.selectCount(
                SkillVersionDO::getContentId, contentId) > 0;
        if (!referencedByVersion) {
            skillResourceMapper.deleteByContentIds(List.of(contentId));
            skillContentMapper.deleteById(contentId);
        }
    }

}
