package com.gkht.ai.nexai.module.ai.session.infrastructure.runtime;

import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEventType;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.util.JsonUtils;

/**
 * 事件流翻译（纯函数）：agentscope {@code AgentEvent} → 平台 {@link RuntimeEvent} 信封。
 * 类型一一对应改名（29 常量 + 平台暂不消费的框架事件统一透传为 CUSTOM），
 * 载荷为 agentscope 事件序列化 JSON（含 type 判别字段，前端按类型化事件流渲染）。
 */
public final class EventTranslator {

    private EventTranslator() {
    }

    /** agentscope 事件 → 平台事件信封（type 改名 + 事件 JSON 载荷） */
    public static RuntimeEvent toRuntimeEvent(AgentEvent event) {
        RuntimeEventType type = toRuntimeType(event.getType());
        String payload = JsonUtils.getJsonCodec().toJson(event);
        return RuntimeEvent.of(type, payload);
    }

    /** 事件类型一一对应改名；平台暂不消费的框架事件类型：外部执行/子代理暴露/提示块统一透传为 CUSTOM */
    static RuntimeEventType toRuntimeType(AgentEventType type) {
        return switch (type) {
            case AGENT_START -> RuntimeEventType.AGENT_START;
            case AGENT_END -> RuntimeEventType.AGENT_END;
            case AGENT_RESULT -> RuntimeEventType.AGENT_RESULT;
            case MODEL_CALL_START -> RuntimeEventType.MODEL_CALL_START;
            case MODEL_CALL_END -> RuntimeEventType.MODEL_CALL_END;
            case TEXT_BLOCK_START -> RuntimeEventType.TEXT_BLOCK_START;
            case TEXT_BLOCK_DELTA -> RuntimeEventType.TEXT_BLOCK_DELTA;
            case TEXT_BLOCK_END -> RuntimeEventType.TEXT_BLOCK_END;
            case THINKING_BLOCK_START -> RuntimeEventType.THINKING_BLOCK_START;
            case THINKING_BLOCK_DELTA -> RuntimeEventType.THINKING_BLOCK_DELTA;
            case THINKING_BLOCK_END -> RuntimeEventType.THINKING_BLOCK_END;
            case DATA_BLOCK_START -> RuntimeEventType.DATA_BLOCK_START;
            case DATA_BLOCK_DELTA -> RuntimeEventType.DATA_BLOCK_DELTA;
            case DATA_BLOCK_END -> RuntimeEventType.DATA_BLOCK_END;
            case TOOL_CALL_START -> RuntimeEventType.TOOL_CALL_START;
            case TOOL_CALL_DELTA -> RuntimeEventType.TOOL_CALL_DELTA;
            case TOOL_CALL_END -> RuntimeEventType.TOOL_CALL_END;
            case TOOL_RESULT_START -> RuntimeEventType.TOOL_RESULT_START;
            case TOOL_RESULT_TEXT_DELTA -> RuntimeEventType.TOOL_RESULT_TEXT_DELTA;
            case TOOL_RESULT_DATA_DELTA -> RuntimeEventType.TOOL_RESULT_DATA_DELTA;
            case TOOL_RESULT_END -> RuntimeEventType.TOOL_RESULT_END;
            case REQUIRE_USER_CONFIRM -> RuntimeEventType.REQUIRE_USER_CONFIRM;
            case USER_CONFIRM_RESULT -> RuntimeEventType.USER_CONFIRM_RESULT;
            case EXCEED_MAX_ITERS -> RuntimeEventType.EXCEED_MAX_ITERS;
            case REQUEST_STOP -> RuntimeEventType.REQUEST_STOP;
            case ALL_TOOLS_DENIED -> RuntimeEventType.ALL_TOOLS_DENIED;
            case CUSTOM -> RuntimeEventType.CUSTOM;
            // 平台暂不消费的框架事件类型：外部执行/子代理暴露/提示块统一透传为 CUSTOM
            case REQUIRE_EXTERNAL_EXECUTION, EXTERNAL_EXECUTION_RESULT,
                    SUBAGENT_EXPOSED, HINT_BLOCK -> RuntimeEventType.CUSTOM;
        };
    }
}
