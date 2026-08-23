package com.gkht.ai.nexai.module.ai.session.domain.valueobject;

/**
 * 挂起工具调用的上下文（不可变）：{@code RequireUserConfirmEvent} 推送的审批卡片数据——
 * 工具名、调用参数摘要与权限上下文。前端据此展示审批卡片，回传的
 * {@link ToolCallDecision} 与之按 toolCallId 一一对应。
 */
public record ToolCallContext(String toolCallId, String toolName, String argumentsJson) {

    public static ToolCallContext of(String toolCallId, String toolName, String argumentsJson) {
        return new ToolCallContext(toolCallId, toolName, argumentsJson);
    }
}
