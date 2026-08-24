package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import java.util.List;

/**
 * 工具挂载值对象（record，按值判等，实现 {@link Mount} 家族）：挂载层的统一模式
 * 「工具来源 + 引用 + 放行面 + 敏感面」，对齐 agentscope Toolkit 注册面与 permission
 * 引擎语义——放行面（allowedTools）为空表示放行该来源全部工具；敏感面
 * （sensitiveTools）内的工具调用前挂起等人工审批（HITL）。
 *
 * <p>工具来源不只 MCP（{@link ToolSource}）：MCP Server 与平台工具库（@Tool 业务工具）
 * 共用本挂载结构；内置工具随执行环境层启用，不走挂载。</p>
 *
 * <p>运行时翻译：敏感工具 → agentscope {@code PermissionRule(name, ASK)}，
 * 挂起事件 RequireUserConfirmEvent 经会话层推送，人工确认（ConfirmResult）后恢复。</p>
 *
 * <p>规范构造器为信任构造（快照读路径，仅做不可变规范化）；写路径校验走 {@link #of}。</p>
 *
 * @param source         工具来源
 * @param sourceId       来源条目编号（MCP = ai_mcp_server.id；PLATFORM = 平台工具库条目编号），外部聚合引用
 * @param allowedTools   工具白名单（放行面，工具名列表），空 = 该来源全部工具
 * @param sensitiveTools 敏感工具名单（审批面，工具名列表）：名单内工具调用前挂起等人工审批（HITL）
 */
public record ToolMount(ToolSource source, Long sourceId,
                        List<String> allowedTools, List<String> sensitiveTools) implements Mount {

    /** 工具来源字面量（Command 校验注解与 domain 校验共用，一条规则一处真相） */
    public static final String SOURCE_REGEX = "MCP|PLATFORM";
    /** 工具白名单数量上限（防误粘贴长列表撑爆快照；Command 校验注解共用） */
    public static final int ALLOWED_TOOLS_MAX_SIZE = 128;
    /** 敏感工具名单数量上限（同上） */
    public static final int SENSITIVE_TOOLS_MAX_SIZE = 128;

    /** 信任构造规范化：null 清单归空表并拷贝为不可变（不跑业务校验） */
    public ToolMount {
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        sensitiveTools = sensitiveTools == null ? List.of() : List.copyOf(sensitiveTools);
    }

    /**
     * 构建工具挂载（写路径校验）
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
        // 白名单为空 = 放行全部（全集运行时才知道，无法静态校验子集）
        if (allowedTools != null && !allowedTools.isEmpty()
                && !allowedTools.containsAll(sensitiveTools == null ? List.of() : sensitiveTools)) {
            throw new IllegalArgumentException("敏感工具必须在白名单内（标了审批的工具须先在放行面内）");
        }
        return new ToolMount(source, sourceId, allowedTools, sensitiveTools);
    }

}
