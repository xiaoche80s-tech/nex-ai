package com.gkht.ai.nexai.module.ai.session.domain.valueobject;

import java.util.Objects;

/**
 * HITL 工具确认决定值对象（不可变）：对一条 REQUIRE_USER_CONFIRM 事件中工具调用的三态回应。
 *
 * <p>三态语义（spec User Story 15）：{@code approved=true + 原参数} 批准执行；
 * {@code approved=true + 修改后参数 JSON} 修改参数后批准（运行时以本对象携带的参数替换
 * 待确认调用）；{@code approved=false} 拒绝（运行时向上下文写入拒绝结果，工具不执行）。
 * 参数以 JSON 字符串传递（与 AgentEvent 中工具调用的 content 形态一致），
 * 端口实现负责翻译为运行时原语，domain 不 import agentscope 类型（ADR-0001）。</p>
 *
 * @param toolCallId    待确认工具调用的标识（须与确认请求事件中的 id 一致）
 * @param toolName      工具名（用于重建调用块）
 * @param argumentsJson 工具调用参数 JSON；修改参数后批准时携带改后的完整参数
 * @param approved      是否批准执行
 */
public record ToolCallDecision(String toolCallId, String toolName, String argumentsJson,
                               boolean approved) {

    public ToolCallDecision {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("确认决定必须携带工具调用标识");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("确认决定必须携带工具名");
        }
        if (argumentsJson == null) {
            argumentsJson = "{}";
        }
        argumentsJson = argumentsJson.strip();
    }

    /** 便捷工厂：按原参数批准 */
    public static ToolCallDecision approve(String toolCallId, String toolName, String argumentsJson) {
        return new ToolCallDecision(toolCallId, toolName, argumentsJson, true);
    }

    /** 便捷工厂：拒绝 */
    public static ToolCallDecision deny(String toolCallId, String toolName, String argumentsJson) {
        return new ToolCallDecision(toolCallId, toolName, argumentsJson, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ToolCallDecision other)) {
            return false;
        }
        return approved == other.approved
                && Objects.equals(toolCallId, other.toolCallId)
                && Objects.equals(toolName, other.toolName)
                && Objects.equals(argumentsJson, other.argumentsJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolCallId, toolName, argumentsJson, approved);
    }

}
