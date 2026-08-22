package com.gkht.ai.nexai.module.ai.skill.domain.exception;

/**
 * 已发布技能版本不可变：迭代请走「编辑草稿 → 发布新版本」。
 */
public class SkillVersionImmutableException extends RuntimeException {

    public SkillVersionImmutableException(Long skillId, int versionNo) {
        super("技能 " + skillId + " 的版本 " + versionNo + " 已发布，不可修改");
    }

}
