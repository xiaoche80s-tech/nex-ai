package com.gkht.ai.nexai.module.ai.skill.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Skill 能力包内容值对象（不可变，按值判等）：Markdown 正文（含 YAML frontmatter 的
 * SKILL.md 契约——name/description 必填）+ 资源文件 Map（相对路径 → 内容，支持 base64 二进制）。
 * 是 DB 版本链的载荷，运行时物化为文件目录（agentscope 文件仓库源可读）。
 */
public final class SkillContent {

    /** Markdown 长度上限（字符） */
    static final int MARKDOWN_MAX_LENGTH = 65_536;
    /** 资源文件数量上限 */
    static final int RESOURCES_MAX_SIZE = 128;

    /** SKILL.md Markdown（含 YAML frontmatter：name/description 必填） */
    private final String markdown;
    /** 资源文件 Map（相对路径 → 内容；内容以 base64: 前缀表达二进制） */
    private final Map<String, String> resources;

    private SkillContent(String markdown, Map<String, String> resources) {
        this.markdown = markdown;
        this.resources = resources;
    }

    /**
     * 构建能力包内容
     *
     * @param markdown  SKILL.md Markdown，不能为空白；须含 name/description 的 YAML frontmatter
     *                  （物化时由 agentscope 校验）
     * @param resources 资源文件 Map，可空；相对路径不能含 .. 逃逸
     */
    public static SkillContent of(String markdown, Map<String, String> resources) {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("能力包 Markdown 不能为空");
        }
        if (markdown.length() > MARKDOWN_MAX_LENGTH) {
            throw new IllegalArgumentException("能力包 Markdown 不能超过 " + MARKDOWN_MAX_LENGTH + " 个字符");
        }
        Map<String, String> normalized = resources == null ? Map.of() : Map.copyOf(resources);
        if (normalized.size() > RESOURCES_MAX_SIZE) {
            throw new IllegalArgumentException("资源文件不能超过 " + RESOURCES_MAX_SIZE + " 个");
        }
        normalized.keySet().forEach(SkillContent::validateResourcePath);
        return new SkillContent(markdown, normalized);
    }

    /** 资源相对路径校验：非空、不以 / 开头、不含 .. 逃逸 */
    private static void validateResourcePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("资源文件路径不能为空");
        }
        if (path.startsWith("/") || path.split("/").length > 0
                && List.of(path.split("/")).contains("..")) {
            throw new IllegalArgumentException("资源文件路径不能是绝对路径或包含 .. 逃逸");
        }
    }

    public String getMarkdown() {
        return markdown;
    }

    /** 资源文件 Map（相对路径 → 内容），不可变 */
    public Map<String, String> getResources() {
        return resources;
    }

    /** 资源文件 Map 的可变副本（物化适配用） */
    public Map<String, String> resourcesCopy() {
        return new LinkedHashMap<>(resources);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkillContent other)) {
            return false;
        }
        return markdown.equals(other.markdown) && resources.equals(other.resources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(markdown, resources);
    }

}
