package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * 工具挂载值对象（不可变，按值判等）：挂载层的统一模式「工具来源 + 引用 + 可选白名单过滤」，
 * 对齐 agentscope Toolkit 的注册面与白名单语义——白名单为空表示放行该来源全部工具。
 *
 * <p>工具来源不只 MCP（{@link ToolSource}）：MCP Server 与平台工具库（@Tool 业务工具）
 * 共用本挂载结构；内置工具随执行环境层启用，不走挂载。</p>
 *
 * <p>MVP 仅建模不暴露编辑面（挂载接线在后置工单）。</p>
 */
public final class ToolMount {

    /** 工具白名单数量上限（防误粘贴长列表撑爆快照） */
    static final int ALLOWED_TOOLS_MAX_SIZE = 128;

    /** 工具来源 */
    private final ToolSource source;
    /** 来源条目编号（MCP = ai_mcp_server.id；PLATFORM = 平台工具库条目编号），外部聚合引用 */
    private final Long sourceId;
    /** 工具白名单（工具名列表），空 = 该来源全部工具 */
    private final List<String> allowedTools;

    private ToolMount(ToolSource source, Long sourceId, List<String> allowedTools) {
        this.source = source;
        this.sourceId = sourceId;
        this.allowedTools = allowedTools;
    }

    /**
     * 构建工具挂载
     *
     * @param source       工具来源，不能为 null
     * @param sourceId     来源条目编号，不能为 null（草稿态不校验存在性，发布时校验补齐）
     * @param allowedTools 工具白名单，可空（= 全部工具），不能含空白项且最多 128 项
     */
    public static ToolMount of(ToolSource source, Long sourceId, List<String> allowedTools) {
        if (source == null) {
            throw new IllegalArgumentException("工具挂载必须声明来源");
        }
        if (sourceId == null) {
            throw new IllegalArgumentException("工具挂载必须携带来源条目编号");
        }
        if (allowedTools != null && (allowedTools.size() > ALLOWED_TOOLS_MAX_SIZE
                || allowedTools.stream().anyMatch(tool -> tool == null || tool.isBlank()))) {
            throw new IllegalArgumentException("工具白名单不能含空白项且最多 " + ALLOWED_TOOLS_MAX_SIZE + " 项");
        }
        return new ToolMount(source, sourceId,
                allowedTools == null ? List.of() : List.copyOf(allowedTools));
    }

    public ToolSource getSource() {
        return source;
    }

    public Long getSourceId() {
        return sourceId;
    }

    /** 工具白名单，空列表 = 该来源全部工具 */
    public List<String> getAllowedTools() {
        return allowedTools;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ToolMount other)) {
            return false;
        }
        return source == other.source && sourceId.equals(other.sourceId)
                && allowedTools.equals(other.allowedTools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, sourceId, allowedTools);
    }

}
