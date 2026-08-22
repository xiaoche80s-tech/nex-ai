package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * MCP 服务挂载值对象（不可变，按值判等）：M2 预留。
 *
 * <p>挂载单位是服务而非工具名列表：工具清单来自 server 的只读发现（US23）且随 server 端动态增减，
 * 「服务 + 白名单」语义下清单变化时白名单外的新增工具默认不可用（与 US25 权限引擎默认拒绝的精神一致），
 * 而工具名列表会随清单一变即悬空。allowedTools 空 = 该服务全部工具；非空 = 白名单子集。
 * 白名单的精确执行语义（与权限引擎的关系）由 M2 工单 14/15/16 定义。</p>
 */
public final class McpServerMount {

    /** 被挂载的 MCP 服务编号（外部聚合引用，ai_mcp_server） */
    private final Long serverId;
    /** 工具白名单（工具名），null / 空 = 该服务全部工具 */
    private final List<String> allowedTools;

    private McpServerMount(Long serverId, List<String> allowedTools) {
        this.serverId = serverId;
        this.allowedTools = allowedTools;
    }

    /**
     * 构建 MCP 服务挂载
     *
     * @param serverId     被挂载服务编号，不能为 null
     * @param allowedTools 工具白名单，可空 = 全部工具
     */
    public static McpServerMount of(Long serverId, List<String> allowedTools) {
        if (serverId == null) {
            throw new IllegalArgumentException("MCP 服务挂载必须指定服务");
        }
        return new McpServerMount(serverId, allowedTools == null ? List.of() : List.copyOf(allowedTools));
    }

    public Long getServerId() {
        return serverId;
    }

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
        return Objects.equals(serverId, other.serverId) && Objects.equals(allowedTools, other.allowedTools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverId, allowedTools);
    }

}
