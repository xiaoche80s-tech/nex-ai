package com.gkht.ai.nexai.module.ai.skill.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillCreateCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillDraftCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillPublishCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillSwitchVersionCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillUpdateCommand;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDetailDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillVersionDTO;
import com.gkht.ai.nexai.module.ai.skill.application.query.SkillPageQuery;
import com.gkht.ai.nexai.module.ai.skill.domain.exception.SkillMdInvalidException;
import com.gkht.ai.nexai.module.ai.skill.domain.exception.SkillPublishWithoutDraftException;
import com.gkht.ai.nexai.module.ai.skill.domain.exception.SkillVersionNotExistsException;
import com.gkht.ai.nexai.module.ai.skill.domain.gateway.SkillMdParserGateway;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillVersion;
import com.gkht.ai.nexai.module.ai.skill.domain.repository.SkillRepository;
import com.gkht.ai.nexai.module.ai.skill.domain.valueobject.SkillContent;
import com.gkht.ai.nexai.module.ai.skill.domain.valueobject.SkillProfile;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.converter.SkillConverter;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_MD_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_NAME_DUPLICATE;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_PUBLISH_WITHOUT_DRAFT;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_VERSION_NOT_EXISTS;

/**
 * 技能应用服务实现。写走聚合（Repository 端口），读按轻量读写分离经 Mapper 直查。
 *
 * <p>SKILL.md 是唯一事实来源：技能名/描述由解析端口从 front matter 提取后同步进聚合
 * （解析与运行时重建 AgentSkill 同源，校验失败以明确错误拒绝）；草稿经发布固化为不可变
 * 版本快照后，运行时仓储（官方 AgentSkillRepository 实现）才对其可见。</p>
 */
@Service
@Validated
public class SkillServiceImpl implements SkillService {

    @Resource
    private SkillRepository skillRepository;

    @Resource
    private SkillMapper skillMapper;

    @Resource
    private SkillConverter skillConverter;

    @Resource
    private SkillMdParserGateway skillMdParserGateway;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSkill(SkillCreateCommand command) {
        SkillProfile profile = parseProfile(command);
        requireNameAvailable(profile.name(), null);
        Skill skill = Skill.create(profile.name(), profile.description(), toContent(command));
        return skillRepository.save(skill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSkill(SkillUpdateCommand command) {
        Skill skill = requireSkill(command.getId());
        SkillProfile profile = parseProfile(command);
        requireNameAvailable(profile.name(), skill.getId());
        skill.editDraft(profile.name(), profile.description(), toContent(command));
        skillRepository.save(skill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer publishSkill(SkillPublishCommand command) {
        Skill skill = requireSkill(command.getId());
        SkillVersion version;
        try {
            version = skill.publish(command.getRemark());
        } catch (SkillPublishWithoutDraftException ex) {
            throw exception(SKILL_PUBLISH_WITHOUT_DRAFT);
        }
        // 聚合状态（指针前移、草稿清空）与版本快照同一事务落库
        skillRepository.save(skill);
        skillRepository.createVersion(version);
        return version.getVersionNo();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void switchDefaultVersion(SkillSwitchVersionCommand command) {
        Skill skill = requireSkill(command.getId());
        try {
            skill.switchDefaultVersion(command.getVersionNo());
        } catch (SkillVersionNotExistsException ex) {
            throw exception(SKILL_VERSION_NOT_EXISTS);
        }
        skillRepository.save(skill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSkill(Long id) {
        requireSkill(id);
        skillRepository.deleteByIdCascade(id);
    }

    @Override
    public PageResult<SkillDTO> getSkillPage(SkillPageQuery query) {
        PageResult<SkillDO> page = skillMapper.selectPage(query, query.getName());
        return skillConverter.toDTOPage(page);
    }

    @Override
    public SkillDetailDTO getSkill(Long id) {
        Skill skill = requireSkill(id);
        SkillDetailDTO detail = skillConverter.toDetailDTO(skill);
        detail.setHasDraft(skill.hasDraft());
        detail.setDraft(skill.hasDraft() ? skillConverter.toContentDTO(skill.getDraft()) : null);
        detail.setCurrentVersion(findCurrentVersion(skill.getId(), skill.getCurrentVersionNo()));
        return detail;
    }

    @Override
    public List<SkillVersionDTO> getVersionList(Long skillId) {
        requireSkill(skillId);
        return skillRepository.findVersionsBySkillId(skillId).stream()
                .map(skillConverter::toVersionDTO)
                .toList();
    }

    /**
     * 查询技能当前默认版本并转出参，从未发布为 null
     */
    private SkillVersionDTO findCurrentVersion(Long skillId, Integer currentVersionNo) {
        if (currentVersionNo == null) {
            return null;
        }
        SkillVersion version = skillRepository.findVersion(skillId, currentVersionNo);
        return version == null ? null : skillConverter.toVersionDTO(version);
    }

    /**
     * SKILL.md 权威解析：front matter 的 name/description 必填、正文非空，
     * 校验失败转明确错误码（携带官方解析器的具体原因）
     */
    private SkillProfile parseProfile(SkillDraftCommand command) {
        try {
            return skillMdParserGateway.parse(command.getSkillMd());
        } catch (SkillMdInvalidException ex) {
            throw exception(SKILL_MD_INVALID, ex.getMessage());
        }
    }

    private SkillContent toContent(SkillDraftCommand command) {
        return SkillContent.of(command.getSkillMd(), command.getResources());
    }

    /**
     * 技能名是运行时挂载寻址键，租户内不可重名（软删后的名字可复用；
     * excludeId 用于编辑自身时跳过）
     */
    private void requireNameAvailable(String name, Long excludeId) {
        Skill existing = skillRepository.findByName(name);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw exception(SKILL_NAME_DUPLICATE, name);
        }
    }

    private Skill requireSkill(Long id) {
        Skill skill = skillRepository.findById(id);
        if (skill == null) {
            throw exception(SKILL_NOT_EXISTS);
        }
        return skill;
    }

}
