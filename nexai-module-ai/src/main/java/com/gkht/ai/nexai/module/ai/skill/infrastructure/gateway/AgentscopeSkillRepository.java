package com.gkht.ai.nexai.module.ai.skill.infrastructure.gateway;

import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.repository.SkillRepository;
import com.gkht.ai.nexai.module.ai.skill.domain.valueobject.SkillContent;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillVersionDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillVersionMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.repository.SkillContentAssembler;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import io.agentscope.core.skill.util.MarkdownSkillParser;
import io.agentscope.core.skill.util.SkillUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 官方 {@link AgentSkillRepository} 的平台实现（ADR-0003，2026-08-22 修订：内容独立成表 +
 * 资源行级子表）：自建 {@code ai_skill} 表族承载多租户与版本化，不使用官方
 * {@code agentscope_skills} 表与自动建表。供运行时装配链注入（如 DynamicSkillMiddleware
 * 的仓储列表），实现工单 11 挂载生效的读侧。
 *
 * <p><b>可见性语义</b>：只读「已发布」技能（默认版本指针指向的版本 → 内容行 → 资源行
 * 三段组配）——与智能体规格「运行时只见已发布版本」的纪律一致，草稿不进运行时。读侧经
 * MyBatis 租户拦截器按 {@link TenantContextHolder} 当前租户过滤，调用方（运行时链路）
 * 必须先建立租户上下文。</p>
 *
 * <p><b>写侧语义</b>（运行时写回技能，如自学习闭环）：{@link #save} 把传入 AgentSkill
 * 固化为该技能的<b>草稿</b>（不存在则创建技能），经聚合 Repository 落新内容行——
 * 不绕过「发布才进读侧」的不变量；{@link #setWriteable} 只读化运行时写通道，
 * 不影响管理面应用服务。</p>
 */
@Component
public class AgentscopeSkillRepository implements AgentSkillRepository {

    private static final Logger log = LoggerFactory.getLogger(AgentscopeSkillRepository.class);

    /** 仓储类型标识（getSource 前缀与 AgentSkill.getSkillId 的 source 段） */
    static final String REPOSITORY_TYPE = "nexai";
    /** 仓储定位（表名） */
    static final String REPOSITORY_LOCATION = "ai_skill";

    private final SkillMapper skillMapper;
    private final SkillVersionMapper skillVersionMapper;
    private final SkillContentAssembler contentAssembler;
    private final SkillRepository skillRepository;
    /** 运行时写通道旗标（读侧不受限），volatile 供 setWriteable 并发切换 */
    private volatile boolean writeable = true;

    public AgentscopeSkillRepository(SkillMapper skillMapper,
                                     SkillVersionMapper skillVersionMapper,
                                     SkillContentAssembler contentAssembler,
                                     SkillRepository skillRepository) {
        this.skillMapper = skillMapper;
        this.skillVersionMapper = skillVersionMapper;
        this.contentAssembler = contentAssembler;
        this.skillRepository = skillRepository;
    }

    @Override
    public AgentSkill getSkill(String name) {
        requireTenant();
        SkillDO skill = skillMapper.selectByName(name);
        SkillVersionDO version = currentVersionOf(skill);
        if (version == null) {
            // 对齐官方语义：不存在（含未发布）按名抛出，调用方以此感知缺技能
            throw new IllegalArgumentException("Skill not found: " + name);
        }
        SkillContent content = contentAssembler.assembleOne(version.getContentId());
        if (content == null) {
            throw new IllegalStateException(
                    "Skill content row missing: " + name + " (content " + version.getContentId() + ")");
        }
        return rebuild(content);
    }

    /**
     * 返回技能名列表（按名升序）。
     * 接口 javadoc 称返回 id（name_version_source），但官方 PG/MySQL 实现均返回纯 name，
     * 且运行时装配链（DynamicSkillMiddleware#reloadSkills）只消费 {@link #getAllSkills()}
     * 并按 name 组装——此处对齐官方实现的事实行为。
     */
    @Override
    public List<String> getAllSkillNames() {
        requireTenant();
        return skillMapper.selectListPublished().stream()
                .map(SkillDO::getName)
                .toList();
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        requireTenant();
        List<SkillDO> published = skillMapper.selectListPublished();
        if (published.isEmpty()) {
            return List.of();
        }
        // 三段组配：版本行（按 skill_id + version_no 定位默认版本）→ 内容行 → 资源行
        Map<String, SkillVersionDO> versionBySkillId = skillVersionMapper
                .selectListBySkillIds(published.stream().map(SkillDO::getId).toList()).stream()
                .collect(Collectors.toMap(
                        version -> version.getSkillId() + ":" + version.getVersionNo(),
                        version -> version,
                        (a, b) -> a));
        Map<Long, SkillContent> contentById = contentAssembler.assemble(versionBySkillId.values().stream()
                .map(SkillVersionDO::getContentId).filter(Objects::nonNull).toList());
        List<AgentSkill> skills = new ArrayList<>(published.size());
        for (SkillDO skill : published) {
            SkillVersionDO version = versionBySkillId.get(skill.getId() + ":" + skill.getCurrentVersionNo());
            if (version == null) {
                log.warn("[getAllSkills][技能 {} 指向的默认版本 {} 缺失，跳过]", skill.getId(), skill.getCurrentVersionNo());
                continue;
            }
            SkillContent content = contentById.get(version.getContentId());
            if (content == null) {
                log.warn("[getAllSkills][技能 {} 默认版本 {} 的内容行 {} 缺失，跳过]",
                        skill.getId(), skill.getCurrentVersionNo(), version.getContentId());
                continue;
            }
            try {
                skills.add(rebuild(content));
            } catch (IllegalArgumentException ex) {
                // 对齐官方容忍语义：单个技能构建失败不拖垮整批（warn 后跳过）
                log.warn("[getAllSkills][技能 {} 重建失败：{}]", skill.getId(), ex.getMessage());
            }
        }
        return skills;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(List<AgentSkill> skills, boolean force) {
        requireTenant();
        if (skills == null || skills.isEmpty()) {
            return false;
        }
        if (!writeable) {
            log.warn("[save][运行时写通道已只读，拒绝写回技能]");
            return false;
        }
        for (AgentSkill agentSkill : skills) {
            Skill existing = skillRepository.findByName(agentSkill.getName());
            if (existing != null && !force) {
                // 对齐官方语义：非 force 覆盖已存在技能时显式报冲突，交由调用方决定
                throw new IllegalStateException("Cannot save skill: '" + agentSkill.getName()
                        + "' already exists and force=false. Use force=true to overwrite.");
            }
            // AgentSkill → SKILL.md 原文（front matter 由 metadata 重新生成，正文原样）；
            // 经聚合 Repository 落新内容行 + 主表指针，内容行生命周期封装在其内
            String skillMd = MarkdownSkillParser.generate(agentSkill.getMetadata(), agentSkill.getSkillContent());
            SkillContent content = SkillContent.of(skillMd, agentSkill.getResources());
            if (existing == null) {
                skillRepository.save(Skill.create(agentSkill.getName(), agentSkill.getDescription(), content));
            } else {
                existing.editDraft(agentSkill.getName(), agentSkill.getDescription(), content);
                skillRepository.save(existing);
            }
            log.info("[save][技能 {} 已写入草稿（force={}），发布后进入运行时读侧]", agentSkill.getName(), force);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String skillName) {
        requireTenant();
        if (!writeable) {
            log.warn("[delete][运行时写通道已只读，拒绝删除技能 {}]", skillName);
            return false;
        }
        Skill existing = skillRepository.findByName(skillName);
        if (existing == null) {
            return false;
        }
        skillRepository.deleteByIdCascade(existing.getId());
        return true;
    }

    @Override
    public boolean skillExists(String skillName) {
        requireTenant();
        SkillDO skill = skillMapper.selectByName(skillName);
        return skill != null && skill.getCurrentVersionNo() != null;
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return new AgentSkillRepositoryInfo(REPOSITORY_TYPE, REPOSITORY_LOCATION, writeable);
    }

    @Override
    public String getSource() {
        return REPOSITORY_TYPE + "_" + REPOSITORY_LOCATION;
    }

    @Override
    public void setWriteable(boolean writeable) {
        this.writeable = writeable;
    }

    @Override
    public boolean isWriteable() {
        return writeable;
    }

    /**
     * 版本快照 → AgentSkill：经官方解析器从 SKILL.md 原文重建（与解析网关同源），
     * source 取本仓储标识（getSkillId = name_source）
     */
    private AgentSkill rebuild(SkillContent content) {
        return SkillUtil.createFrom(content.getSkillMd(), content.getResources(), REPOSITORY_TYPE);
    }

    /** 技能的当前默认版本行；技能不存在或未发布返回 null */
    private SkillVersionDO currentVersionOf(SkillDO skill) {
        if (skill == null || skill.getCurrentVersionNo() == null) {
            return null;
        }
        return skillVersionMapper.selectBySkillIdAndVersionNo(skill.getId(), skill.getCurrentVersionNo());
    }

    /** 强断言租户上下文存在：读侧按当前租户过滤，无租户即无确定的技能集 */
    private void requireTenant() {
        TenantContextHolder.getRequiredTenantId();
    }

}
