package com.gkht.ai.nexai.module.ai.session.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * agentscope 事件读侧 codec 纯 JUnit（工单 21 候选 5）：RequireUserConfirmEvent JSON
 * 解析出挂起工具调用；兼容读——null/空白/损坏 JSON/结构不符一律空列表不抛异常
 * （存量会话数据不迁移，挂起上下文容忍丢失）。
 */
class AgentscopeEventCodecTest {

    private static final String CONFIRM_EVENT = """
            {"type":"REQUIRE_USER_CONFIRM","toolCalls":[
              {"id":"call-1","name":"search_web","input":{"query":"天气","limit":3}},
              {"id":"call-2","name":"delete_user","input":{}}
            ]}""";

    @Test
    @DisplayName("正常解析：toolCalls 数组 → 挂起调用列表（id/name/参数）")
    void parsesPendingToolCalls() {
        var calls = AgentscopeEventCodec.parsePendingToolCalls(CONFIRM_EVENT);
        assertEquals(2, calls.size());
        assertEquals("call-1", calls.get(0).toolCallId());
        assertEquals("search_web", calls.get(0).toolName());
        assertEquals(Map.of("query", "天气", "limit", 3), calls.get(0).arguments());
        assertEquals("call-2", calls.get(1).toolCallId());
    }

    @Test
    @DisplayName("兼容读：null/空白/损坏 JSON/无 toolCalls 数组/缺 id 元素 → 空列表不抛异常")
    void tolerantReadOnMalformedPayload() {
        assertTrue(AgentscopeEventCodec.parsePendingToolCalls(null).isEmpty());
        assertTrue(AgentscopeEventCodec.parsePendingToolCalls("  ").isEmpty());
        assertTrue(AgentscopeEventCodec.parsePendingToolCalls("not-json{").isEmpty());
        assertTrue(AgentscopeEventCodec.parsePendingToolCalls("{\"type\":\"OTHER\"}").isEmpty());
        // 缺 id 的数组元素跳过而非整体失败
        assertTrue(AgentscopeEventCodec
                .parsePendingToolCalls("{\"toolCalls\":[{\"name\":\"no-id\"}]}").isEmpty());
    }

    @Test
    @DisplayName("firstToolCallId：首个挂起调用编号；无挂起返回 null")
    void extractsFirstToolCallId() {
        assertEquals("call-1", AgentscopeEventCodec.firstToolCallId(CONFIRM_EVENT));
        assertNull(AgentscopeEventCodec.firstToolCallId("{\"toolCalls\":[]}"));
        assertNull(AgentscopeEventCodec.firstToolCallId(null));
    }
}
