package com.gkht.ai.nexai.module.ai.skill.infrastructure.gateway;

import com.gkht.ai.nexai.module.ai.framework.config.AiRuntimeProperties;
import com.gkht.ai.nexai.module.ai.skill.domain.gateway.SkillMaterializationGateway;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillContent;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.util.SkillFileSystemHelper;
import io.agentscope.core.skill.util.SkillUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Skill 物化适配器（agentscope 直用，ADR-0001）：把 DB 版本链内容物化为文件目录，
 * 供 agentscope 文件仓库源读取。直用 {@link SkillFileSystemHelper#saveSkills} 契约——
 * 每个 skill 一个目录，内含 SKILL.md（Markdown + YAML frontmatter）与资源文件。
 *
 * <p>物化位置按归属隔离：租户级 {root}/t{tenantId}/skills/{name}/，
 * 用户级 {root}/t{tenantId}/u{userId}/skills/{name}/——与 workspace 布局同构。
 * 覆盖写语义（force=true）：物化缓存与 DB 权威源不一致时重写（DB 为唯一权威源）。</p>
 */
@Component
public class AgentscopeSkillMaterializationGateway implements SkillMaterializationGateway {

    private static final Logger log = LoggerFactory.getLogger(AgentscopeSkillMaterializationGateway.class);

    private final AiRuntimeProperties runtimeProperties;

    public AgentscopeSkillMaterializationGateway(AiRuntimeProperties runtimeProperties) {
        this.runtimeProperties = runtimeProperties;
    }

    @Override
    public String materialize(Skill skill, Long tenantId, SkillContent content) {
        Path root = runtimeProperties.getSkills().resolvedRoot();
        Path dir = switch (skill.getOwnerLevel()) {
            case TENANT -> root.resolve("t" + tenantId).resolve("skills").resolve(skill.getName());
            case USER -> root.resolve("t" + tenantId).resolve("u" + skill.getOwnerUserId())
                    .resolve("skills").resolve(skill.getName());
        };
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new IllegalStateException("创建 skill 物化目录失败：" + dir, ex);
        }
        // 内容比对：SKILL.md 与全部资源文件均一致才跳过覆写（物化缓存命中）。
        // 只比 SKILL.md 会漏掉「仅改资源文件」的版本（DB 快照唯一权威源，工单 10 版本链语义）。
        Path skillMd = dir.resolve("SKILL.md");
        try {
            if (Files.exists(skillMd) && content.getMarkdown().equals(Files.readString(skillMd))
                    && resourcesMatch(dir, content)) {
                log.debug("skill 物化缓存命中：{}（内容与资源一致，跳过覆写）", dir);
                return dir.toString();
            }
        } catch (IOException ex) {
            log.warn("skill 物化内容比对失败，强制覆写：{}", dir, ex);
        }
        // DB 快照唯一权威源 → 物化（force=true 覆盖旧缓存）
        AgentSkill agentSkill = SkillUtil.createFrom(content.getMarkdown(), content.resourcesCopy(),
                "nexai:" + skill.getOwnerLevel().name().toLowerCase());
        SkillFileSystemHelper.saveSkills(dir.getParent(), List.of(agentSkill), true);
        log.info("skill 已物化：{} → {}", skill.getName(), dir);
        return dir.toString();
    }

    /** 资源文件集合比对：DB 版本声明的资源在磁盘上逐一存在且内容一致 */
    private boolean resourcesMatch(Path skillDir, SkillContent content) throws IOException {
        for (Map.Entry<String, String> entry : content.resourcesCopy().entrySet()) {
            Path resource = skillDir.resolve(entry.getKey()).normalize();
            if (!resource.startsWith(skillDir) || !Files.isRegularFile(resource)) {
                return false;
            }
            String diskContent = entry.getValue() != null && entry.getValue().startsWith("base64:")
                    ? new String(java.util.Base64.getDecoder().decode(
                            entry.getValue().substring("base64:".length())))
                    : Files.readString(resource);
            if (!entry.getValue().equals(diskContent)) {
                return false;
            }
        }
        return true;
    }

}
