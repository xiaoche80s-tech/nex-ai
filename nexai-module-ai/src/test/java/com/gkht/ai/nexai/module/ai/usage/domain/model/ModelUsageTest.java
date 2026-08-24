package com.gkht.ai.nexai.module.ai.usage.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 模型用量领域纯 JUnit（工单 14）：维度必填、数量非负、总 token 派生。
 */
class ModelUsageTest {

    private static ModelUsage record(String modelName, int messageCount, int input,
                                     int output, int cached) {
        return ModelUsage.record(1L, "dbg-1", 7L, 2, "cs-agent", "t1-user",
                modelName, messageCount, input, output, cached, 0.25, LocalDateTime.now());
    }

    @Test
    @DisplayName("合法记录：维度含租户/会话/规格，token 与耗时齐备；总 token 派生")
    void acceptsValidUsage() {
        ModelUsage usage = record("fake-chat-model", 5, 10, 20, 3);
        assertEquals(1L, usage.getTenantId());
        assertEquals("dbg-1", usage.getSessionKey());
        assertEquals(7L, usage.getSpecId());
        assertEquals(2, usage.getVersionNo());
        assertEquals("fake-chat-model", usage.getModelName());
        assertEquals(5, usage.getMessageCount());
        assertEquals(30, usage.getTotalTokens());
        assertEquals(3, usage.getCachedTokens());
        assertEquals(0.25, usage.getDurationSeconds());
    }

    @Test
    @DisplayName("必填维度缺失被拒绝：租户/会话/模型名/时间")
    void rejectsMissingDimensions() {
        LocalDateTime now = LocalDateTime.now();
        assertEquals("模型用量必须携带租户维度", assertThrows(IllegalArgumentException.class,
                () -> ModelUsage.record(null, "dbg", null, null, null, null, "m",
                        1, 1, 1, 0, 0, now)).getMessage());
        assertEquals("模型用量必须携带会话维度", assertThrows(IllegalArgumentException.class,
                () -> ModelUsage.record(1L, null, null, null, null, null, "m",
                        1, 1, 1, 0, 0, now)).getMessage());
        assertEquals("模型用量必须携带模型名（最多 256 字符）", assertThrows(IllegalArgumentException.class,
                () -> ModelUsage.record(1L, "dbg", null, null, null, null, " ",
                        1, 1, 1, 0, 0, now)).getMessage());
        assertEquals("模型用量必须携带发生时间", assertThrows(IllegalArgumentException.class,
                () -> ModelUsage.record(1L, "dbg", null, null, null, null, "m",
                        1, 1, 1, 0, 0, null)).getMessage());
    }

    @Test
    @DisplayName("数量字段为负被拒绝（token/消息数/耗时）")
    void rejectsNegativeNumbers() {
        assertEquals("模型用量的数量字段不能为负", assertThrows(IllegalArgumentException.class,
                () -> record("m", -1, 1, 1, 0)).getMessage());
        assertEquals("模型用量的数量字段不能为负", assertThrows(IllegalArgumentException.class,
                () -> record("m", 1, -1, 1, 0)).getMessage());
        assertEquals("模型用量的数量字段不能为负", assertThrows(IllegalArgumentException.class,
                () -> record("m", 1, 1, -1, 0)).getMessage());
        assertEquals("模型用量的数量字段不能为负", assertThrows(IllegalArgumentException.class,
                () -> ModelUsage.record(1L, "dbg", null, null, null, null, "m",
                        1, 1, 1, 0, -0.1, LocalDateTime.now())).getMessage());
    }

}
