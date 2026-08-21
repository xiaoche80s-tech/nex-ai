package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentSpecConfig 值对象 S3 纯 JUnit 测试：校验规则、可空引用列表、按值判等。
 */
class AgentSpecConfigTest {

    @Test
    @DisplayName("构建：模型引用必填，系统提示去空白、空白归 null")
    void buildNormalizesPrompt() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpecConfig.of(null, "提示", 10, 0.7d, null, null, null, null));

        AgentSpecConfig config = AgentSpecConfig.of(1L, "  你是客服  ", 10, 0.7d, null, null, null, null);
        assertEquals(1L, config.getModelId());
        assertEquals("你是客服", config.getSystemPrompt());

        assertNull(AgentSpecConfig.of(1L, "   ", 10, 0.7d, null, null, null, null).getSystemPrompt());
    }

    @Test
    @DisplayName("推理参数：maxIters >= 1，温度在 [0, 2] 内，边界值合法")
    void validatesReasoningParams() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpecConfig.of(1L, null, 0, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpecConfig.of(1L, null, null, -0.1d, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpecConfig.of(1L, null, null, 2.1d, null, null, null, null));

        AgentSpecConfig bounded = AgentSpecConfig.of(1L, null, 1, 2.0d, null, null, null, null);
        assertEquals(1, bounded.getMaxIters());
        assertEquals(2.0d, bounded.getTemperature());
    }

    @Test
    @DisplayName("M2 预留引用列表：可空传入一律规范化为空列表（不可变）")
    void nullReferenceListsBecomeEmptyImmutable() {
        AgentSpecConfig config = AgentSpecConfig.of(1L, null, null, null, null, null, null, null);

        assertTrue(config.getSkillIds().isEmpty());
        assertTrue(config.getKnowledgeBaseIds().isEmpty());
        assertTrue(config.getMcpServerIds().isEmpty());
        assertTrue(config.getSubagentSpecIds().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> config.getSkillIds().add(9L));
    }

    @Test
    @DisplayName("按值判等：全部字段相同相等，任一引用列表不同即不等")
    void equalsByValue() {
        AgentSpecConfig base = AgentSpecConfig.of(1L, "提示", 10, 0.7d,
                List.of(1L), List.of(2L), List.of(3L), List.of(4L));
        AgentSpecConfig same = AgentSpecConfig.of(1L, "提示", 10, 0.7d,
                List.of(1L), List.of(2L), List.of(3L), List.of(4L));

        assertEquals(base, same);
        assertEquals(base.hashCode(), same.hashCode());

        AgentSpecConfig differentSkill = AgentSpecConfig.of(1L, "提示", 10, 0.7d,
                List.of(1L, 5L), List.of(2L), List.of(3L), List.of(4L));
        assertNotEquals(base, differentSkill);

        AgentSpecConfig differentPrompt = AgentSpecConfig.of(1L, "别的提示", 10, 0.7d,
                List.of(1L), List.of(2L), List.of(3L), List.of(4L));
        assertNotEquals(base, differentPrompt);
    }

}
