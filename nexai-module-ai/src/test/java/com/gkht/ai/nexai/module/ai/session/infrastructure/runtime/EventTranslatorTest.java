package com.gkht.ai.nexai.module.ai.session.infrastructure.runtime;

import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEventType;
import io.agentscope.core.event.AgentEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 事件类型翻译直测（穷尽映射）：一一对应改名 + 平台暂不消费的框架事件统一透传 CUSTOM。
 * 载荷 JSON 序列化由网关 e2e 测试（AgentscopeRuntimeGatewayTest）覆盖，此处不重复。
 */
class EventTranslatorTest {

    @Test
    @DisplayName("类型一一对应改名：抽验各事件族的关键映射")
    void renamesTypesOneToOne() {
        assertEquals(RuntimeEventType.AGENT_START, EventTranslator.toRuntimeType(AgentEventType.AGENT_START));
        assertEquals(RuntimeEventType.AGENT_END, EventTranslator.toRuntimeType(AgentEventType.AGENT_END));
        assertEquals(RuntimeEventType.MODEL_CALL_END, EventTranslator.toRuntimeType(AgentEventType.MODEL_CALL_END));
        assertEquals(RuntimeEventType.TEXT_BLOCK_DELTA, EventTranslator.toRuntimeType(AgentEventType.TEXT_BLOCK_DELTA));
        assertEquals(RuntimeEventType.THINKING_BLOCK_DELTA, EventTranslator.toRuntimeType(AgentEventType.THINKING_BLOCK_DELTA));
        assertEquals(RuntimeEventType.TOOL_CALL_START, EventTranslator.toRuntimeType(AgentEventType.TOOL_CALL_START));
        assertEquals(RuntimeEventType.TOOL_RESULT_END, EventTranslator.toRuntimeType(AgentEventType.TOOL_RESULT_END));
        assertEquals(RuntimeEventType.REQUIRE_USER_CONFIRM, EventTranslator.toRuntimeType(AgentEventType.REQUIRE_USER_CONFIRM));
        assertEquals(RuntimeEventType.USER_CONFIRM_RESULT, EventTranslator.toRuntimeType(AgentEventType.USER_CONFIRM_RESULT));
        assertEquals(RuntimeEventType.EXCEED_MAX_ITERS, EventTranslator.toRuntimeType(AgentEventType.EXCEED_MAX_ITERS));
        assertEquals(RuntimeEventType.ALL_TOOLS_DENIED, EventTranslator.toRuntimeType(AgentEventType.ALL_TOOLS_DENIED));
        assertEquals(RuntimeEventType.CUSTOM, EventTranslator.toRuntimeType(AgentEventType.CUSTOM));
    }

    @Test
    @DisplayName("平台暂不消费的框架事件统一透传为 CUSTOM")
    void unconsumedFrameworkEventsPassThroughAsCustom() {
        assertEquals(RuntimeEventType.CUSTOM,
                EventTranslator.toRuntimeType(AgentEventType.REQUIRE_EXTERNAL_EXECUTION));
        assertEquals(RuntimeEventType.CUSTOM,
                EventTranslator.toRuntimeType(AgentEventType.EXTERNAL_EXECUTION_RESULT));
        assertEquals(RuntimeEventType.CUSTOM,
                EventTranslator.toRuntimeType(AgentEventType.SUBAGENT_EXPOSED));
        assertEquals(RuntimeEventType.CUSTOM,
                EventTranslator.toRuntimeType(AgentEventType.HINT_BLOCK));
    }
}
