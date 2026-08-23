package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * 工具挂载值对象（不可变，按值判等）：挂载层的统一模式「工具来源 + 引用 + 放行面 + 敏感面」，
 * 对齐 agentscope Toolkit 注册面与 permission 引擎语义——放行面（allowedTools）为空表示
 * 放行该来源全部工具；敏感面（sensitiveTools）内的工具调用前挂起等人工审批（HITL）。
 *
 * <p>工具来源不只 MCP（{@link ToolSource}）：MCP Server 与平台工具库（@Tool 业务工具）
 * 共用本挂载结构；内置工具随执行环境层启用，不走挂载。</p>
 *
 * <p>运行时翻译：敏感工具 → agentscope {@code PermissionRule(name, ASK)}，
 * 挂起事件 RequireUserConfirmEvent 经会话层推送，人工确认（ConfirmResult）后恢复。</p>
 *
 * <p>MVP 仅建模不暴露编辑面（挂载接线在后置工单）。</p>
 */
public final class ToolMount {

    /** 工具白名单数量上限（防误粘贴长列表撑爆快照） */
    static final int ALLOWED_TOOLS_MAX_SIZE = 128;
    /** 敏感工具名单数量上限（同上） */
    static final int SENSITIVE_TOOLS_MAX_SIZE = 128;

    /** 工具来源 */
    private final ToolSource source;
    /** 来源条目编号（MCP = ai_mcp_server.id；PLATFORM = 平台工具库条目编号），外部聚合引用 */
    private final Long sourceId;
    /** 工具白名单（放行面，工具名列表），空 = 该来源全部工具 */
    private final List<String> allowedTools;
    /** 敏感工具名单（审批面，工具名列表）：名单内工具调用前挂起等人工审批（HITL） */
    private final List<String> sensitiveTools;

    private ToolMount(ToolSource source, Long sourceId, List<String> allowedTools,
                      List<String> sensitiveTools) {
        this.source = source;
        this.sourceId = sourceId;
        this.allowedTools = allowedTools;
        this.sensitiveTools = sensitiveTools;
    }

    /**
     * 构建工具挂载
     *
     * @param source         工具来源，不能为 null
     * @param sourceId       来源条目编号，不能为 null（草稿态不校验存在性，发布时校验补齐）
     * @param allowedTools   工具白名单，可空（= 全部工具），不能含空白项且最多 128 项
     * @param sensitiveTools 敏感工具名单，可空，不能含空白/重复项且最多 128 项；
     *                       白名单非空时名单必须是白名单的子集（标了审批的工具必须先在放行面内）
     */
    public static ToolMount of(ToolSource source, Long sourceId, List<String> allowedTools,
                               List<String> sensitiveTools) {
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
        if (sensitiveTools != null && (sensitiveTools.size() > SENSITIVE_TOOLS_MAX_SIZE
                || sensitiveTools.stream().anyMatch(tool -> tool == null || tool.isBlank())
                || sensitiveTools.stream().distinct().count() != sensitiveTools.size())) {
            throw new IllegalArgumentException("敏感工具名单不能含空白或重复项且最多 "
                    + SENSITIVE_TOOLS_MAX_SIZE + " 项");
        }
        List<String> allowed = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        List<String> sensitive = sensitiveTools == null ? List.of() : List.copyOf(sensitiveTools);
        // 白名单为空 = 放行全部（全集运行时才知道，无法静态校验子集）
        if (!allowed.isEmpty() && !allowed.containsAll(sensitive)) {
            throw new IllegalArgumentException("敏感工具必须在白名单内（标了审批的工具须先在放行面内）");
        }
        return new ToolMount(source, sourceId, allowed, sensitive);
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

    /** 敏感工具名单，空列表 = 无需审批；名单内工具调用前挂起等人工审批（HITL） */
    public List<String> getSensitiveTools() {
        return sensitiveTools;
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
                && allowedTools.equals(other.allowedTools)
                && sensitiveTools.equals(other.sensitiveTools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, sourceId, allowedTools, sensitiveTools);
    }

}
