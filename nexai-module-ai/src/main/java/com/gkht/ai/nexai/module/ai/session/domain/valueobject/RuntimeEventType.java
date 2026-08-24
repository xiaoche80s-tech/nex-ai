package com.gkht.ai.nexai.module.ai.session.domain.valueobject;

/**
 * 运行时事件信封（不可变）：网关出参的统一载体，隔离 agentscope 事件类型——
 * 应用层/控制器只消费「类型判别 + JSON 载荷」两段。
 *
 * <p>类型全集（与 agentscope AgentEventType 一一对应）：
 * AGENT_START / AGENT_END / AGENT_RESULT / MODEL_CALL_START / MODEL_CALL_END /
 * TEXT_BLOCK_START / TEXT_BLOCK_DELTA / TEXT_BLOCK_END / THINKING_BLOCK_START /
 * THINKING_BLOCK_DELTA / THINKING_BLOCK_END / TOOL_CALL_START / TOOL_CALL_DELTA /
 * TOOL_CALL_END / TOOL_RESULT_START / TOOL_RESULT_TEXT_DELTA / TOOL_RESULT_END /
 * REQUIRE_USER_CONFIRM / USER_CONFIRM_RESULT / EXCEED_MAX_ITERS / REQUEST_STOP /
 * CUSTOM；平台侧错误统一为 SESSION_ERROR（非 agentscope 类型，收尾语义）。</p>
 */
public enum RuntimeEventType {
    AGENT_START,
    AGENT_END,
    AGENT_RESULT,
    MODEL_CALL_START,
    MODEL_CALL_END,
    TEXT_BLOCK_START,
    TEXT_BLOCK_DELTA,
    TEXT_BLOCK_END,
    THINKING_BLOCK_START,
    THINKING_BLOCK_DELTA,
    THINKING_BLOCK_END,
    DATA_BLOCK_START,
    DATA_BLOCK_DELTA,
    DATA_BLOCK_END,
    TOOL_CALL_START,
    TOOL_CALL_DELTA,
    TOOL_CALL_END,
    TOOL_RESULT_START,
    TOOL_RESULT_TEXT_DELTA,
    TOOL_RESULT_DATA_DELTA,
    TOOL_RESULT_END,
    REQUIRE_USER_CONFIRM,
    USER_CONFIRM_RESULT,
    EXCEED_MAX_ITERS,
    REQUEST_STOP,
    ALL_TOOLS_DENIED,
    CUSTOM,
    /** 平台侧收尾错误事件（非 agentscope 类型） */
    SESSION_ERROR,
    /** OpenAI 兼容出口的流式 chunk（payload = ChatCompletionsChunk JSON，工单 16；非 agentscope 类型） */
    OPENAI_CHUNK
}
