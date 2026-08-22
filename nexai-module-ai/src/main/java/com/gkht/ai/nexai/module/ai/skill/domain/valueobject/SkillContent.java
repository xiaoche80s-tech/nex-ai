package com.gkht.ai.nexai.module.ai.skill.domain.valueobject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 技能内容值对象（不可变，按值判等）：SKILL.md 全文 + 附属资源文件集。
 *
 * <p>SKILL.md 原文保真存储（front matter 的 name/description 及扩展 metadata 不拆散），
 * 运行时经官方解析器从原文重建 AgentSkill。资源路径按运行时物化安全规则校验
 * （禁 {@code ..} 段与反斜杠，防路径逃逸）。</p>
 */
public final class SkillContent {

    /** SKILL.md 全文长度上限（字符） */
    static final int SKILL_MD_MAX_LENGTH = 131_072;
    /** 单个资源文件内容长度上限（字符） */
    static final int RESOURCE_MAX_LENGTH = 262_144;
    /** 资源路径长度上限（字符），对齐官方 AgentSkillRepository 惯例 */
    static final int RESOURCE_PATH_MAX_LENGTH = 500;

    private final String skillMd;
    private final Map<String, String> resources;

    private SkillContent(String skillMd, Map<String, String> resources) {
        this.skillMd = skillMd;
        this.resources = resources;
    }

    /**
     * 构造技能内容
     *
     * @param skillMd   SKILL.md 全文（含 YAML front matter），不能为空白
     * @param resources 附属资源文件集（path → content），可空；front matter 的
     *                  name/description 校验不在本值对象（解析规则属外部格式，经 gateway 端口解析）
     */
    public static SkillContent of(String skillMd, Map<String, String> resources) {
        if (skillMd == null || skillMd.isBlank()) {
            throw new IllegalArgumentException("SKILL.md 内容不能为空");
        }
        if (skillMd.length() > SKILL_MD_MAX_LENGTH) {
            throw new IllegalArgumentException("SKILL.md 内容不能超过 " + SKILL_MD_MAX_LENGTH + " 个字符");
        }
        Map<String, String> immutableResources = Collections.emptyMap();
        if (resources != null && !resources.isEmpty()) {
            Map<String, String> ordered = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : resources.entrySet()) {
                validateResourcePath(entry.getKey());
                if (entry.getValue() == null) {
                    throw new IllegalArgumentException("资源文件内容不能为空：" + entry.getKey());
                }
                if (entry.getValue().length() > RESOURCE_MAX_LENGTH) {
                    throw new IllegalArgumentException(
                            "资源文件内容不能超过 " + RESOURCE_MAX_LENGTH + " 个字符：" + entry.getKey());
                }
                ordered.put(entry.getKey(), entry.getValue());
            }
            immutableResources = Collections.unmodifiableMap(ordered);
        }
        return new SkillContent(skillMd, immutableResources);
    }

    /** 资源路径校验：非空、长度受限、禁 {@code ..} 段与反斜杠（允许 {@code /} 分隔子目录） */
    private static void validateResourcePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("资源文件路径不能为空");
        }
        String normalized = path.strip();
        if (normalized.length() > RESOURCE_PATH_MAX_LENGTH) {
            throw new IllegalArgumentException("资源文件路径不能超过 " + RESOURCE_PATH_MAX_LENGTH + " 个字符");
        }
        if (normalized.contains("\\")) {
            throw new IllegalArgumentException("资源文件路径不能包含反斜杠：" + normalized);
        }
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("资源文件路径不能包含 .. 段：" + normalized);
            }
        }
    }

    public String getSkillMd() {
        return skillMd;
    }

    /** 附属资源文件集（不可变，保持插入序） */
    public Map<String, String> getResources() {
        return resources;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkillContent other)) {
            return false;
        }
        return skillMd.equals(other.skillMd) && resources.equals(other.resources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(skillMd, resources);
    }

}
