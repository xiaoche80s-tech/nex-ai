package com.gkht.ai.nexai.module.ai.session.domain.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台错误帧工厂纯 JUnit：JSON 转义（引号/反斜杠/控制字符）与 null 兜底——
 * 全入口（网关/服务/两 controller）的错误帧构造收口于此，转义正确性单点验证。
 */
class RuntimeEventTest {

    @Test
    @DisplayName("sessionError：转义引号/反斜杠/换行，帧结构固定")
    void sessionErrorEscapesPayload() {
        RuntimeEvent event = RuntimeEvent.sessionError("含\"引号\"与\\反斜杠\n换行");
        assertEquals(RuntimeEventType.SESSION_ERROR, event.type());
        assertTrue(event.payload().startsWith("{\"type\":\"SESSION_ERROR\",\"message\":\""),
                "帧结构固定，实际：" + event.payload());
        assertTrue(event.payload().contains("\\\"引号\\\""), "引号须转义");
        assertTrue(event.payload().contains("\\\\反斜杠"), "反斜杠须转义");
        assertTrue(event.payload().contains("\\n换行"), "换行须转义");
        assertTrue(event.payload().endsWith("\"}"));
    }

    @Test
    @DisplayName("sessionError：null/空白消息兜底为未知错误")
    void sessionErrorFallsBackOnNull() {
        assertEquals("{\"type\":\"SESSION_ERROR\",\"message\":\"未知错误\"}",
                RuntimeEvent.sessionError(null).payload());
        assertEquals("{\"type\":\"SESSION_ERROR\",\"message\":\"未知错误\"}",
                RuntimeEvent.sessionError("  ").payload());
    }
}
