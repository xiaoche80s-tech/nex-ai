package com.gkht.ai.nexai.module.ai.skill.domain.exception;

/**
 * 无草稿可发布：技能当前没有草稿（刚发布过或从未创建），发布语义要求草稿 → 发布 → 再编辑生成新草稿。
 */
public class SkillPublishWithoutDraftException extends RuntimeException {

    public SkillPublishWithoutDraftException(Long skillId) {
        super("技能 " + skillId + " 当前没有可发布的草稿，请先编辑生成新草稿");
    }

}
