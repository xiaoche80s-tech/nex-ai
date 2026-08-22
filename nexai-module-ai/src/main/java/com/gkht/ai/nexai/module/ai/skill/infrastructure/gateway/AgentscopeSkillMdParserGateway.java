package com.gkht.ai.nexai.module.ai.skill.infrastructure.gateway;

import com.gkht.ai.nexai.module.ai.skill.domain.exception.SkillMdInvalidException;
import com.gkht.ai.nexai.module.ai.skill.domain.gateway.SkillMdParserGateway;
import com.gkht.ai.nexai.module.ai.skill.domain.valueobject.SkillProfile;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.util.SkillUtil;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SKILL.md 解析网关（agentscope 适配器）：经官方 {@link SkillUtil} 解析，
 * 与运行时重建 AgentSkill 的解析器同源——管理面校验通过的 SKILL.md 运行时必然可装载。
 */
@Component
public class AgentscopeSkillMdParserGateway implements SkillMdParserGateway {

    @Override
    public SkillProfile parse(String skillMd) {
        try {
            // resources 不参与标识解析，传空集；front matter 与正文非空校验由官方解析器完成
            AgentSkill parsed = SkillUtil.createFrom(skillMd, Map.of());
            return new SkillProfile(parsed.getName(), parsed.getDescription());
        } catch (IllegalArgumentException ex) {
            throw new SkillMdInvalidException(ex.getMessage());
        }
    }

}
