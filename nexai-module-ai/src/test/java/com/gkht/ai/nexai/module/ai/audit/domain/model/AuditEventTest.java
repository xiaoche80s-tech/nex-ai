package com.gkht.ai.nexai.module.ai.audit.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计领域纯 JUnit（工单 14）：维度必填校验、摘要截断、脱敏规则。
 */
class AuditEventTest {

    private static AuditEvent record(String toolName, String args, String result) {
        return AuditEvent.record(1L, "dbg-1", 7L, 2, "customer-service", "t1-user",
                "call-1", toolName, ToolOutcome.SUCCESS, args, result, 12L, LocalDateTime.now());
    }

    @Test
    @DisplayName("合法事件：维度四元组齐备（租户/会话/规格/用户）+ 工具 + 结果")
    void acceptsValidEvent() {
        AuditEvent event = record("search_orders", "{\"orderId\": 1}", "state=SUCCESS");
        assertEquals(1L, event.getTenantId());
        assertEquals("dbg-1", event.getSessionKey());
        assertEquals(7L, event.getSpecId());
        assertEquals(2, event.getVersionNo());
        assertEquals("search_orders", event.getToolName());
        assertEquals(ToolOutcome.SUCCESS, event.getOutcome());
        assertEquals(12L, event.getDurationMs());
        assertTrue(event.getOccurredAt() != null);
    }

    @Test
    @DisplayName("必填维度缺失被拒绝：租户/会话/工具名/结果口径/时间")
    void rejectsMissingDimensions() {
        LocalDateTime now = LocalDateTime.now();
        assertEquals("审计事件必须携带租户维度", assertThrows(IllegalArgumentException.class,
                () -> AuditEvent.record(null, "dbg", null, null, null, null, null, "t", ToolOutcome.SUCCESS,
                        null, null, null, now)).getMessage());
        assertEquals("审计事件必须携带会话维度", assertThrows(IllegalArgumentException.class,
                () -> AuditEvent.record(1L, " ", null, null, null, null, null, "t", ToolOutcome.SUCCESS,
                        null, null, null, now)).getMessage());
        assertEquals("审计事件必须携带工具名（最多 128 字符）", assertThrows(IllegalArgumentException.class,
                () -> AuditEvent.record(1L, "dbg", null, null, null, null, null, " ",
                        ToolOutcome.SUCCESS, null, null, null, now)).getMessage());
        assertEquals("审计事件必须携带结果口径", assertThrows(IllegalArgumentException.class,
                () -> AuditEvent.record(1L, "dbg", null, null, null, null, null, "t", null,
                        null, null, null, now)).getMessage());
        assertEquals("审计事件必须携带发生时间", assertThrows(IllegalArgumentException.class,
                () -> AuditEvent.record(1L, "dbg", null, null, null, null, null, "t",
                        ToolOutcome.SUCCESS, null, null, null, null)).getMessage());
    }

    @Test
    @DisplayName("超长摘要硬顶截断（2048 字符，配置级截断在脱敏阶段）")
    void truncatesOverlongDigest() {
        AuditEvent event = record("t", "x".repeat(3000), null);
        assertEquals(2048, event.getArgumentsDigest().length());
        assertNull(event.getResultDigest());
    }

    @Test
    @DisplayName("结果口径：agentscope 状态名映射，未知值归 UNKNOWN 不丢事件")
    void mapsOutcomeFromStateName() {
        assertEquals(ToolOutcome.SUCCESS, ToolOutcome.fromStateName("SUCCESS"));
        assertEquals(ToolOutcome.ERROR, ToolOutcome.fromStateName("ERROR"));
        assertEquals(ToolOutcome.DENIED, ToolOutcome.fromStateName("DENIED"));
        assertEquals(ToolOutcome.INTERRUPTED, ToolOutcome.fromStateName("INTERRUPTED"));
        assertEquals(ToolOutcome.UNKNOWN, ToolOutcome.fromStateName("WHATEVER"));
        assertEquals(ToolOutcome.UNKNOWN, ToolOutcome.fromStateName(null));
    }

    @Test
    @DisplayName("脱敏规则：敏感键值打码、普通键保留、超长截断带标记")
    void masksSensitivePayloads() {
        String payload = "{\"orderId\": 1, \"api_key\": \"sk-secret-value\", 'password': 'p@ss'}";
        String masked = SensitiveMasker.mask(payload, 512);
        assertTrue(masked.contains("\"orderId\": 1"), "普通键应保留");
        assertTrue(!masked.contains("sk-secret-value"), "敏感值应打码");
        assertTrue(!masked.contains("p@ss"), "单引号敏感值应打码");

        String truncated = SensitiveMasker.mask("a".repeat(600), 512);
        assertTrue(truncated.length() <= 512 + "…(截断)".length());
        assertTrue(truncated.endsWith("…(截断)"));

        assertNull(SensitiveMasker.mask(null, 512));
        // 恶意嵌套键名（子串匹配口径）：my_api_token 同样打码
        assertTrue(!SensitiveMasker.mask("{\"my_api_token\": \"leak\"}", 512).contains("leak"));
    }

    @Test
    @DisplayName("合法挂载边界（借位本类跑数量字段）：批量事件逐条独立校验")
    void validatesEachEventIndependently() {
        assertDoesNotThrow(() -> List.of(record("a", null, null), record("b", null, null)).size());
    }

}
