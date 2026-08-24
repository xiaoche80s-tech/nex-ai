package com.gkht.ai.nexai.module.ai.session.domain.valueobject;

/**
 * 运行时事件信封（不可变）：{@link RuntimeEventType} 判别 + 事件 JSON 载荷。
 * 载荷为 agentscope 事件序列化后的完整 JSON（含 type 判别字段），
 * 前端按类型化事件流逐条渲染。
 */
public record RuntimeEvent(RuntimeEventType type, String payload) {

    public static RuntimeEvent of(RuntimeEventType type, String payload) {
        return new RuntimeEvent(type, payload);
    }

    /**
     * 平台错误事件：全入口统一错误帧（{@code {"type":"SESSION_ERROR","message":…}}）。
     * message 为 null 时兜底「未知错误」；JSON 转义在此单点处理（含引号/反斜杠/控制字符），
     * 各入口不再手拼字符串。
     */
    public static RuntimeEvent sessionError(String message) {
        String safe = message == null || message.isBlank() ? "未知错误" : message;
        return new RuntimeEvent(RuntimeEventType.SESSION_ERROR,
                "{\"type\":\"SESSION_ERROR\",\"message\":\"" + escapeJson(safe) + "\"}");
    }

    /** 最小 JSON 字符串转义（domain 零框架依赖，手写而非引 JSON 库） */
    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
