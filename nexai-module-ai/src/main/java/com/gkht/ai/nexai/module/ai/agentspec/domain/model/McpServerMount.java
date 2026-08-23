package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * MCP 服务挂载值对象（不可变，按值判等）：挂载层的统一模式「引用 + 可选工具白名单过滤」，
 * 对齐 agentscope Toolkit 的白名单语义——白名单为空表示放行该服务全部工具。
 *
 * <p>MVP 仅建模不暴露编辑面（MCP Server 注册与挂载接线在后置工单）。</p>
 */
public final class McpServerMount {

    /** 工具白名单数量上限（防误粘贴长列表撑爆快照） */
    static final int ALLOWED_TOOLS_MAX_SIZE = 128;

    /** 挂载的 MCP Server 编号（ai_mcp_server.id，外部聚合引用） */
    private final Long serverId;
    /** 工具白名单（工具名列表），空 = 该服务全部工具 */
    private final List<String> allowedTools;

    private McpServerMount(Long serverId, List<String> allowedTools) {
        this.serverId = serverId;
        this.allowedTools = allowedTools;
    }

    /**
     * 构建挂载
     *
     * @param serverId     MCP Server 编号，不能为 null
     * @param allowedTools 工具白名单，可空（= 全部工具），不能含空白项且最多 128 项
     */
    public static McpServerMount of(Long serverId, List<String> allowedTools) {
        if (serverId == null) {
            throw new IllegalArgumentException("MCP 挂载必须携带服务编号");
        }
        if (allowedTools != null && (allowedTools.size() > ALLOWED_TOOLS_MAX_SIZE
                || allowedTools.stream().anyMatch(tool -> tool == null || tool.isBlank()))) {
            throw new IllegalArgumentException("工具白名单不能含空白项且最多 " + ALLOWED_TOOLS_MAX_SIZE + " 项");
        }
        return new McpServerMount(serverId,
                allowedTools == null ? List.of() : List.copyOf(allowedTools));
    }

    public Long getServerId() {
        return serverId;
    }

    /** 工具白名单，空列表 = 该服务全部工具 */
    public List<String> getAllowedTools() {
        return allowedTools;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof McpServerMount other)) {
            return false;
        }
        return serverId.equals(other.serverId) && allowedTools.equals(other.allowedTools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverId, allowedTools);
    }

}
