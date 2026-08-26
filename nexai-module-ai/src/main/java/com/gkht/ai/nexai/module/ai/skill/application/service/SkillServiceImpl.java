package com.gkht.ai.nexai.module.ai.skill.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillCreateCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillVersionCommand;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillVersionContentDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillVersionDTO;
import com.gkht.ai.nexai.module.ai.skill.application.query.SkillPageQuery;
import com.gkht.ai.nexai.module.ai.skill.domain.gateway.SkillMaterializationGateway;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillContent;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillOwnerLevel;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillVersion;
import com.gkht.ai.nexai.module.ai.skill.domain.repository.SkillRepository;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.converter.SkillConverter;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_NAME_DUPLICATE;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_OWNER_LEVEL_UNSUPPORTED;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_CONFIG_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_TENANT_CONTEXT_MISSING;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_VERSION_NOT_EXISTS;

/**
 * Skill 资产应用服务实现。写走聚合（Repository 端口）+ 物化网关（agentscope 直用），
 * 读按轻量读写分离经 Mapper 直查转 DTO。归属 MVP 开放 TENANT/USER。
 */
@Service
@Validated
public class SkillServiceImpl implements SkillService {

    @Resource
    private SkillRepository skillRepository;

    @Resource
    private SkillMapper skillMapper;

    @Resource
    private SkillVersionMapper skillVersionMapper;

    @Resource
    private SkillConverter skillConverter;

    @Resource
    private SkillMaterializationGateway materializationGateway;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSkill(SkillCreateCommand command, Long userId) {
        SkillOwnerLevel ownerLevel = parseOwnerLevel(command.getOwnerLevel());
        Long ownerUserId = ownerLevel == SkillOwnerLevel.USER ? userId : null;
        if (ownerLevel == SkillOwnerLevel.USER && ownerUserId == null) {
            throw exception(SKILL_OWNER_LEVEL_UNSUPPORTED);
        }
        if (skillMapper.existsByNameAndOwner(ownerLevel, ownerUserId, command.getName())) {
            throw exception(SKILL_NAME_DUPLICATE, command.getName());
        }
        Skill skill;
        SkillContent content;
        try {
            skill = Skill.create(command.getName(), command.getDescription(), ownerLevel, ownerUserId);
            content = SkillContent.of(command.getMarkdown(), command.getResources());
        } catch (IllegalArgumentException ex) {
            throw exception(SKILL_CONFIG_INVALID, ex.getMessage());
        }
        try {
            Long skillId = skillRepository.save(skill);
            // 登记首个版本（不可变版本链；版本内容只构造一次）并推进当前版本指针
            SkillVersion version = SkillVersion.create(skillId, 1, content, command.getNote());
            skillRepository.saveVersion(version);
            skill.advanceCurrentVersion(1);
            skillRepository.update(skill);
            // 物化（事务内落盘；失败回滚 DB，物化缓存下次装配时比对覆写）
            materializationGateway.materialize(skill, requireTenantId(), version.getContent());
            return skillId;
        } catch (DuplicateKeyException ex) {
            throw exception(SKILL_NAME_DUPLICATE, command.getName());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer addSkillVersion(SkillVersionCommand command, Long userId) {
        Skill skill = requireSkill(command.getSkillId());
        Integer maxVersionNo = skillRepository.findMaxVersionNo(skill.getId());
        int nextVersionNo = maxVersionNo == null ? 1 : maxVersionNo + 1;
        SkillVersion version;
        try {
            version = skill.addVersion(nextVersionNo,
                    SkillContent.of(command.getMarkdown(), command.getResources()),
                    command.getNote());
        } catch (IllegalArgumentException ex) {
            throw exception(SKILL_CONFIG_INVALID, ex.getMessage());
        }
        try {
            skillRepository.saveVersion(version);
            skillRepository.update(skill);
            materializationGateway.materialize(skill, requireTenantId(), version.getContent());
        } catch (DuplicateKeyException ex) {
            throw exception(SKILL_NAME_DUPLICATE, "版本号冲突，请重试");
        }
        return version.getVersionNo();
    }

    @Override
    public PageResult<SkillDTO> getSkillPage(SkillPageQuery query, Long userId) {
        return skillConverter.toDTOPage(skillMapper.selectPage(query, query.getName(), userId,
                query.getPublished(), query.getSourceType()));
    }

    @Override
    public List<SkillVersionDTO> listSkillVersions(Long skillId, Long userId) {
        requireSkill(skillId);
        List<SkillVersionDTO> versions = skillVersionMapper.selectListBySkillId(skillId).stream()
                .map(skillConverter::toVersionDTO).toList();
        Skill skill = skillRepository.findById(skillId);
        Integer currentVersionNo = skill == null ? null : skill.getCurrentVersionNo();
        versions.forEach(v ->
                v.setCurrent(currentVersionNo != null && currentVersionNo.equals(v.getVersionNo())));
        return versions;
    }

    @Override
    public SkillVersionContentDTO getVersionContent(Long skillId, Integer versionNo) {
        requireSkill(skillId);
        SkillVersion version = skillRepository.findVersion(skillId, versionNo);
        if (version == null) {
            throw exception(SKILL_VERSION_NOT_EXISTS);
        }
        SkillVersionContentDTO dto = new SkillVersionContentDTO();
        dto.setSkillId(skillId);
        dto.setVersionNo(version.getVersionNo());
        dto.setNote(version.getNote());
        dto.setMarkdown(version.getContent().getMarkdown());
        dto.setResourcePaths(List.copyOf(version.getContent().getResources().keySet()));
        dto.setCreateTime(version.getCreateTime());
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSkill(Long id, Long userId) {
        requireSkill(id);
        skillRepository.deleteByIdCascade(id);
    }

    @Override
    public void publishSkill(Long id, Long userId) {
        Skill skill = requireSkill(id);
        skill.publish();
        skillRepository.update(skill);
    }

    @Override
    public void unpublishSkill(Long id, Long userId) {
        Skill skill = requireSkill(id);
        skill.unpublish();
        skillRepository.update(skill);
    }

    private Skill requireSkill(Long id) {
        Skill skill = skillRepository.findById(id);
        if (skill == null) {
            throw exception(SKILL_NOT_EXISTS);
        }
        return skill;
    }

    /** 归属层级解析：null → TENANT（默认） */
    private SkillOwnerLevel parseOwnerLevel(String code) {
        if (code == null || code.isBlank()) {
            return SkillOwnerLevel.TENANT;
        }
        try {
            return SkillOwnerLevel.valueOf(code);
        } catch (IllegalArgumentException ex) {
            throw exception(SKILL_OWNER_LEVEL_UNSUPPORTED);
        }
    }

    private Long requireTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception(SKILL_TENANT_CONTEXT_MISSING);
        }
        return tenantId;
    }

}
