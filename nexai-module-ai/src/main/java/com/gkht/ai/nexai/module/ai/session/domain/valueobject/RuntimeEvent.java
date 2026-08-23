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
}
