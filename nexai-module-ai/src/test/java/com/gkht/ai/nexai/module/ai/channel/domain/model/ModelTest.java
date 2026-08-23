package com.gkht.ai.nexai.module.ai.channel.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型实体纯 JUnit：挂接与元数据校验、计价非负、更新与启停。
 */
class ModelTest {

    private static Model createDefault() {
        return Model.create(1L, "gpt-4o", "GPT-4o 主力", 128_000,
                new BigDecimal("0.5"), new BigDecimal("2.5"));
    }

    @Test
    @DisplayName("登记成功：文本去空白、初始启用、计价与窗口可空")
    void createsWithDefaults() {
        Model model = Model.create(1L, "  qwen-plus  ", "  通义千问  ", null, null, null);
        assertEquals("qwen-plus", model.getModelId());
        assertEquals("通义千问", model.getName());
        assertTrue(model.isEnabled());
        assertEquals(null, model.getContextWindow());
        assertEquals(null, model.getInputPrice());
    }

    @Test
    @DisplayName("必填校验：缺渠道/标识/显示名被拒绝；窗口非正、单价为负被拒绝")
    void validatesMetadata() {
        assertThrows(IllegalArgumentException.class, () -> Model.create(null, "gpt-4o", "名", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Model.create(1L, " ", "名", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Model.create(1L, "gpt-4o", " ", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> Model.create(1L, "gpt-4o", "名", 0, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> Model.create(1L, "gpt-4o", "名", null, new BigDecimal("-0.1"), null));
        assertThrows(IllegalArgumentException.class,
                () -> Model.create(1L, "gpt-4o", "名", null, null, new BigDecimal("-1")));
        // 边界合法值
        assertDoesNotThrow(() -> Model.create(1L, "gpt-4o", "名", 1,
                BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("更新：全量替换且可改挂渠道")
    void updatesAllFields() {
        Model model = createDefault();
        model.update(2L, "claude-sonnet", "Claude 主力", 200_000,
                new BigDecimal("3"), new BigDecimal("15"));
        assertEquals(2L, model.getChannelId());
        assertEquals("claude-sonnet", model.getModelId());
        assertEquals(200_000, model.getContextWindow());
    }

    @Test
    @DisplayName("长度上限：标识 128/显示名 64 超限被拒绝")
    void validatesLengths() {
        assertDoesNotThrow(() -> Model.create(1L, "m".repeat(128), "名", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> Model.create(1L, "m".repeat(129), "名", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> Model.create(1L, "gpt-4o", "长".repeat(65), null, null, null));
    }

    @Test
    @DisplayName("启停切换：停用保留数据")
    void enableDisable() {
        Model model = createDefault();
        model.disable();
        assertEquals(false, model.isEnabled());
        assertEquals("gpt-4o", model.getModelId());
        model.enable();
        assertTrue(model.isEnabled());
    }

}
