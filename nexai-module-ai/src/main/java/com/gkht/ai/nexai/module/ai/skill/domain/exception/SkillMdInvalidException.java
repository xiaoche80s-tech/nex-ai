package com.gkht.ai.nexai.module.ai.skill.domain.exception;

/**
 * SKILL.md 不符合技能包规范：YAML front matter 缺 name/description、无 front matter 或正文为空。
 */
public class SkillMdInvalidException extends RuntimeException {

    public SkillMdInvalidException(String detail) {
        super(detail);
    }

}
