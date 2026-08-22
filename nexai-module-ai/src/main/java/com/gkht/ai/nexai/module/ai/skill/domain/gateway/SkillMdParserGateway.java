package com.gkht.ai.nexai.module.ai.skill.domain.gateway;

import com.gkht.ai.nexai.module.ai.skill.domain.exception.SkillMdInvalidException;
import com.gkht.ai.nexai.module.ai.skill.domain.valueobject.SkillProfile;

/**
 * SKILL.md 解析端口：按官方技能包规范（YAML front matter 的 name/description 必填、
 * 正文非空）解析技能标识，规则与运行时重建 AgentSkill 的解析器完全同源。
 * 官方解析器属外部依赖，经此端口隔离在 domain 之外。
 */
public interface SkillMdParserGateway {

    /**
     * 解析 SKILL.md 全文，提取 front matter 的 name 与 description
     *
     * @param skillMd SKILL.md 全文
     * @return 技能标识
     * @throws SkillMdInvalidException front matter 缺 name/description、无 front matter 或正文为空
     */
    SkillProfile parse(String skillMd);

}
