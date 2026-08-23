package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 模型调用参数值对象纯 JUnit：取值范围校验。
 */
class GenerateOptionsTest {

    @Test
    @DisplayName("合法边界：0/2 温度与 0/1 topP 闭区间通过，maxTokens=1 通过")
    void acceptsBoundaryValues() {
        assertDoesNotThrow(() -> GenerateOptions.of(0d, 0d, 1));
        assertDoesNotThrow(() -> GenerateOptions.of(2d, 1d, 8192));
        assertDoesNotThrow(() -> GenerateOptions.of(null, null, null));
    }

    @Test
    @DisplayName("温度越界（负数 / 大于 2）被拒绝")
    void rejectsOutOfRangeTemperature() {
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> GenerateOptions.of(-0.1d, null, null));
        assertEquals("温度必须在 0 ~ 2.0 之间", negative.getMessage());

        IllegalArgumentException tooHigh = assertThrows(IllegalArgumentException.class,
                () -> GenerateOptions.of(2.1d, null, null));
        assertEquals("温度必须在 0 ~ 2.0 之间", tooHigh.getMessage());
    }

    @Test
    @DisplayName("topP 越界被拒绝；maxTokens 小于 1 被拒绝")
    void rejectsInvalidTopPAndMaxTokens() {
        IllegalArgumentException topP = assertThrows(IllegalArgumentException.class,
                () -> GenerateOptions.of(null, 1.1d, null));
        assertEquals("topP 必须在 0 ~ 1.0 之间", topP.getMessage());

        IllegalArgumentException maxTokens = assertThrows(IllegalArgumentException.class,
                () -> GenerateOptions.of(null, null, 0));
        assertEquals("最大 tokens 不能小于 1", maxTokens.getMessage());
    }

}
