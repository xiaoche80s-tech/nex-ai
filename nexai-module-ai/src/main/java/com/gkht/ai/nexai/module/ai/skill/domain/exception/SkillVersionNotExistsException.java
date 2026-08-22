package com.gkht.ai.nexai.module.ai.skill.domain.exception;

/**
 * 技能版本不存在：切换默认版本指向了未发布过的版本号。
 */
public class SkillVersionNotExistsException extends RuntimeException {

    public SkillVersionNotExistsException(Long skillId, int versionNo) {
        super("技能 " + skillId + " 不存在版本 " + versionNo);
    }

}
