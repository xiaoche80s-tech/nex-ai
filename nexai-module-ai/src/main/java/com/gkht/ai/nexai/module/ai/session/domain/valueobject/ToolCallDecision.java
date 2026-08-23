package com.gkht.ai.nexai.module.ai.session.domain.valueobject;

/**
 * 敏感工具调用的人工审批决定（不可变）：HITL 三态——
 * 确认（approved=true，参数不变）/ 拒绝（approved=false）/ 改参数（approved=true + 修改后参数）。
 *
 * <p>与 {@link ToolCallContext} 按 toolCallId 一一对应；基础设施层翻译为 agentscope
 * {@code ConfirmResult(confirmed, toolCall)}——改参数即用新参数构造同 id/name 的 ToolUseBlock
 * 传入，被替换执行；拒绝即 confirmed=false 走 denied 分支（Permission denied by user）。</p>
 */
public record ToolCallDecision(String toolCallId, String toolName, boolean approved,
                               String argumentsJson) {

    /**
     * 构建审批决定
     *
     * @param toolCallId     挂起工具调用 ID，不能为空
     * @param toolName       工具名，不能为空
     * @param approved       true = 确认（含改参数），false = 拒绝
     * @param argumentsJson  工具参数 JSON；确认且未改参数时为原参数，拒绝时可为 null
     */
    public static ToolCallDecision of(String toolCallId, String toolName, boolean approved,
                                      String argumentsJson) {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("审批决定必须携带工具调用 ID");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("审批决定必须携带工具名");
        }
        return new ToolCallDecision(toolCallId, toolName, approved, argumentsJson);
    }
}
