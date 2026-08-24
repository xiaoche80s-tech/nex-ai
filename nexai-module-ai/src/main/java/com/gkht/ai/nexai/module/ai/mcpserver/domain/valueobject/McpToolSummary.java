package com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject;

import java.util.Objects;

/**
 * MCP 工具摘要值对象（不可变，按值判等）：探测拉取的工具清单条目（名称 + 描述）。
 * 参数 schema 不入领域（仅管理页展示用，网关实现侧直接随 DTO 透传）。
 */
public final class McpToolSummary {

    private final String name;
    private final String description;

    private McpToolSummary(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static McpToolSummary of(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工具名不能为空");
        }
        return new McpToolSummary(name.strip(), description);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof McpToolSummary other)) {
            return false;
        }
        return name.equals(other.name) && Objects.equals(description, other.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description);
    }

}
