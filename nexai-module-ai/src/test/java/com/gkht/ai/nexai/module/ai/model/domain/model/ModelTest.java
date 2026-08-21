package com.gkht.ai.nexai.module.ai.model.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Model 聚合根纯 JUnit 测试（S3 接缝）：不继承任何基类，直接构造实体断言业务规则。
 */
public class ModelTest {

    private static final Long CHANNEL_ID = 100L;

    @Test
    @DisplayName("创建模型：字段去空白，能力标签规范化（去空/去重/转小写/保序），默认启用")
    public void createNormalizesFields() {
        Model model = Model.create(CHANNEL_ID, "  gpt-4o  ", "  GPT-4o 主力  ",
                128_000, new BigDecimal("0.5"), new BigDecimal("2.5"),
                List.of(" Chat ", "", "chat", "Vision"));

        assertNull(model.getId());
        assertEquals(CHANNEL_ID, model.getChannelId());
        assertEquals("gpt-4o", model.getModelId());
        assertEquals("GPT-4o 主力", model.getName());
        assertEquals(128_000, model.getContextWindow());
        assertEquals(new BigDecimal("0.5"), model.getInputPrice());
        assertEquals(new BigDecimal("2.5"), model.getOutputPrice());
        assertEquals(List.of("chat", "vision"), model.getCapabilities());
        assertTrue(model.isEnabled());
        assertNull(model.getCreateTime());
    }

    @Test
    @DisplayName("创建模型：上下文窗口与单价可空（未知则不填）")
    public void createAllowsOptionalFields() {
        Model model = Model.create(CHANNEL_ID, "qwen-plus", "通义千问", null, null, null, List.of());

        assertNull(model.getContextWindow());
        assertNull(model.getInputPrice());
        assertNull(model.getOutputPrice());
        assertEquals(List.of(), model.getCapabilities());
    }

    @Test
    @DisplayName("创建模型校验：渠道/模型标识/显示名为空、上下文窗口或单价为负均拒绝")
    public void createValidatesRequiredFields() {
        assertThrows(IllegalArgumentException.class,
                () -> Model.create(null, "gpt-4o", "名", null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> Model.create(CHANNEL_ID, " ", "名", null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> Model.create(CHANNEL_ID, "gpt-4o", "", null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> Model.create(CHANNEL_ID, "gpt-4o", "名", -1, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> Model.create(CHANNEL_ID, "gpt-4o", "名", null, new BigDecimal("-0.1"), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> Model.create(CHANNEL_ID, "gpt-4o", "名", null, null, new BigDecimal("-1"), null));
    }

    @Test
    @DisplayName("能力标签：单个超长（>32 字符）或总数超限（>16）拒绝")
    public void capabilityConstraints() {
        String tooLong = "a".repeat(33);
        assertThrows(IllegalArgumentException.class,
                () -> Model.create(CHANNEL_ID, "gpt-4o", "名", null, null, null, List.of(tooLong)));

        List<String> tooMany = java.util.stream.IntStream.rangeClosed(1, 17)
                .mapToObj(i -> "tag" + i).toList();
        assertThrows(IllegalArgumentException.class,
                () -> Model.create(CHANNEL_ID, "gpt-4o", "名", null, null, null, tooMany));
    }

    @Test
    @DisplayName("能力标签列表对外不可变：修改返回的列表抛 UnsupportedOperationException")
    public void capabilitiesAreImmutable() {
        Model model = Model.create(CHANNEL_ID, "gpt-4o", "名", null, null, null, List.of("chat"));

        assertThrows(UnsupportedOperationException.class, () -> model.getCapabilities().add("hack"));
    }

    @Test
    @DisplayName("更新模型：字段与标签全量替换，可改挂渠道")
    public void updateReplacesFields() {
        Model model = Model.create(CHANNEL_ID, "gpt-4o", "旧名", null, null, null, List.of("chat"));

        model.update(200L, "gpt-4o-mini", "新名", 8_000, new BigDecimal("0.1"),
                new BigDecimal("0.4"), List.of("chat", "tools"));

        assertEquals(200L, model.getChannelId());
        assertEquals("gpt-4o-mini", model.getModelId());
        assertEquals("新名", model.getName());
        assertEquals(8_000, model.getContextWindow());
        assertEquals(List.of("chat", "tools"), model.getCapabilities());
    }

    @Test
    @DisplayName("启停模型：disable 后 isEnabled 为 false，enable 恢复")
    public void enableDisable() {
        Model model = Model.create(CHANNEL_ID, "gpt-4o", "名", null, null, null, null);

        assertTrue(model.isEnabled());
        model.disable();
        assertFalse(model.isEnabled());
        model.enable();
        assertTrue(model.isEnabled());
    }

    @Test
    @DisplayName("reconstitute 重建：字段原样恢复不做规范化（持久化数据已规范化）")
    public void reconstituteKeepsRawValues() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 22, 12, 0);
        Model model = Model.reconstitute(1L, CHANNEL_ID, "gpt-4o", "GPT-4o",
                128_000, new BigDecimal("0.5"), new BigDecimal("2.5"),
                List.of("chat"), false, now);

        assertEquals(1L, model.getId());
        assertFalse(model.isEnabled());
        assertEquals(now, model.getCreateTime());
        assertEquals(List.of("chat"), model.getCapabilities());
    }

    @Test
    @DisplayName("聚合根按身份判等：未落库只与自身相等")
    public void equalityByIdentity() {
        Model persisted = Model.reconstitute(1L, CHANNEL_ID, "gpt-4o", "名", null, null, null,
                null, true, null);
        Model sameId = Model.reconstitute(1L, CHANNEL_ID, "other", "他", null, null, null,
                null, true, null);
        Model fresh = Model.create(CHANNEL_ID, "gpt-4o", "名", null, null, null, null);

        assertEquals(persisted, sameId);
        assertEquals(persisted.hashCode(), sameId.hashCode());
        // 未落库聚合只与自身相等（id 为 null）
        assertEquals(fresh, fresh);
        assertFalse(fresh.equals(Model.create(CHANNEL_ID, "gpt-4o", "名", null, null, null, null)));
    }

}
