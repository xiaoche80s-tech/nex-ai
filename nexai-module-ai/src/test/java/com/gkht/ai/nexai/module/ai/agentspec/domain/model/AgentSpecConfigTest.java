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
 * AgentSpecConfig 值对象 S3 纯 JUnit 测试：三层结构（agent 层 / 模型调用层 / 挂载层）校验与按值判等。
 */
class AgentSpecConfigTest {

    @Test
    @DisplayName("构建：模型引用与自描述必填，系统提示去空白、空白归 null")
    void buildValidatesAgentLayer() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpecConfig.of(null, "描述", "提示", 10, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpecConfig.of(1L, "  ", "提示", 10, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpecConfig.of(1L, null, "提示", 10, null, null, null, null, null));

        AgentSpecConfig config = AgentSpecConfig.of(1L, " 企业智能客服 ", "  你是客服  ",
                10, null, null, null, null, null);
        assertEquals("企业智能客服", config.getDescription());
        assertEquals("你是客服", config.getSystemPrompt());

        assertNull(AgentSpecConfig.of(1L, "描述", "   ", 10, null, null, null, null, null).getSystemPrompt());
    }

    @Test
    @DisplayName("构建：自描述超长被拒绝（上限 1024 字符）")
    void buildRejectsOverlongDescription() {
        assertThrows(IllegalArgumentException.class, () -> AgentSpecConfig.of(1L, "描".repeat(1025),
                null, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("推理参数：maxIters >= 1，可空")
    void validatesMaxIters() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpecConfig.of(1L, "描述", null, 0, null, null, null, null, null));
        assertNull(AgentSpecConfig.of(1L, "描述", null, null, null, null, null, null, null).getMaxIters());
    }

    @Test
    @DisplayName("调用参数组：作为嵌套值对象整体携带，校验由 GenerateOptions 自治")
    void carriesGenerateOptions() {
        GenerateOptions options = GenerateOptions.of(0.7d, 0.9d, 4096);
        AgentSpecConfig config = AgentSpecConfig.of(1L, "描述", null, 10, options,
                null, null, null, null);

        assertEquals(options, config.getGenerateOptions());
        // 组内校验在值对象构造时触发
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpecConfig.of(1L, "描述", null, 10, GenerateOptions.of(2.1d, null, null),
                        null, null, null, null));
        // 组可空 = 全默认
        assertNull(AgentSpecConfig.of(1L, "描述", null, null, null, null, null, null, null)
                .getGenerateOptions());
    }

    @Test
    @DisplayName("挂载层：MCP 与子智能体为结构化挂载（引用 + 白名单），引用必填、列表可空规范化")
    void normalizesMounts() {
        McpServerMount mcp = McpServerMount.of(3L, List.of("search"));
        SubagentMount subagent = SubagentMount.of(2L, List.of("calc"));
        AgentSpecConfig config = AgentSpecConfig.of(1L, "描述", null, null, null,
                List.of(9L), null, List.of(mcp), List.of(subagent));

        assertEquals(List.of(9L), config.getSkillIds());
        assertTrue(config.getKnowledgeBaseIds().isEmpty());
        assertEquals(3L, config.getMcpServers().get(0).getServerId());
        assertEquals(List.of("search"), config.getMcpServers().get(0).getAllowedTools());
        assertEquals(2L, config.getSubagents().get(0).getSpecId());
        assertEquals(List.of("calc"), config.getSubagents().get(0).getTools());

        assertThrows(IllegalArgumentException.class, () -> McpServerMount.of(null, null));
        assertThrows(IllegalArgumentException.class, () -> SubagentMount.of(null, null));
        // 白名单可空 = 全部/继承
        assertTrue(McpServerMount.of(3L, null).getAllowedTools().isEmpty());
        assertTrue(SubagentMount.of(2L, null).getTools().isEmpty());
    }

    @Test
    @DisplayName("按值判等：任一层字段不同即不等（含嵌套调用参数组与挂载列表）")
    void equalsByValueAcrossLayers() {
        AgentSpecConfig base = AgentSpecConfig.of(1L, "描述", "提示", 10,
                GenerateOptions.of(0.7d, null, null), List.of(1L), List.of(2L),
                List.of(McpServerMount.of(3L, null)), List.of(SubagentMount.of(4L, null)));

        assertEquals(base, AgentSpecConfig.of(1L, "描述", "提示", 10,
                GenerateOptions.of(0.7d, null, null), List.of(1L), List.of(2L),
                List.of(McpServerMount.of(3L, null)), List.of(SubagentMount.of(4L, null))));
        assertEquals(base.hashCode(), base.hashCode());

        assertNotEquals(base, AgentSpecConfig.of(1L, "别的描述", "提示", 10,
                GenerateOptions.of(0.7d, null, null), List.of(1L), List.of(2L),
                List.of(McpServerMount.of(3L, null)), List.of(SubagentMount.of(4L, null))));
        assertNotEquals(base, AgentSpecConfig.of(1L, "描述", "提示", 10,
                GenerateOptions.of(0.5d, null, null), List.of(1L), List.of(2L),
                List.of(McpServerMount.of(3L, null)), List.of(SubagentMount.of(4L, null))));
        assertNotEquals(base, AgentSpecConfig.of(1L, "描述", "提示", 10,
                GenerateOptions.of(0.7d, null, null), List.of(1L), List.of(2L),
                List.of(McpServerMount.of(3L, null)), List.of(SubagentMount.of(5L, null))));
    }

}
