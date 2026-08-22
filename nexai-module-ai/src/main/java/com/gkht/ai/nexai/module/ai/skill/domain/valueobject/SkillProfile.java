package com.gkht.ai.nexai.module.ai.skill.domain.valueobject;

/**
 * SKILL.md 解析出的技能标识（front matter 的 name 与 description），由解析端口产出。
 * 附属资源与正文不拆出——存储保真 SKILL.md 原文，运行时按原文重建。
 */
public record SkillProfile(String name, String description) {
}
