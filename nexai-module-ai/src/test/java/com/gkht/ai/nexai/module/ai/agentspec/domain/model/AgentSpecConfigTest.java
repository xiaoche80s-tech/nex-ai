package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 规格配置值对象纯 JUnit：草稿态可空性（模型引用/自描述）与文本边界。
 */
class AgentSpecConfigTest {

    private static AgentSpecConfig configOf(String description, String systemPrompt, Integer maxIters) {
        return AgentSpecConfig.of(null, description, systemPrompt, maxIters, null, null, null, null);
    }

    @Test
    @DisplayName("草稿态：模型引用与自描述均可空（发布时校验补齐）")
    void allowsNullModelAndDescriptionInDraft() {
        AgentSpecConfig config = assertDoesNotThrow(() -> configOf(null, null, null));
        assertNull(config.getModelId());
        assertNull(config.getDescription());
    }

    @Test
    @DisplayName("文本规范化：空白描述与提示归 null，有内容去首尾空白")
    void normalizesBlankTexts() {
        AgentSpecConfig config = configOf("  客服助手  ", "  你是客服  ", null);
        assertEquals("客服助手", config.getDescription());
        assertEquals("你是客服", config.getSystemPrompt());

        AgentSpecConfig blank = configOf("   ", "   ", null);
        assertNull(blank.getDescription());
        assertNull(blank.getSystemPrompt());
    }

    @Test
    @DisplayName("自描述超过 1024 字符被拒绝；系统提示超过 16384 字符被拒绝")
    void rejectsOverlongTexts() {
        IllegalArgumentException description = assertThrows(IllegalArgumentException.class,
                () -> configOf("长".repeat(1025), null, null));
        assertEquals("规格自描述不能超过 1024 个字符", description.getMessage());

        IllegalArgumentException prompt = assertThrows(IllegalArgumentException.class,
                () -> configOf(null, "长".repeat(16_385), null));
        assertEquals("系统提示不能超过 16384 个字符", prompt.getMessage());
    }

    @Test
    @DisplayName("maxIters 小于 1 被拒绝；边界值 1 通过")
    void validatesMaxIters() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> configOf(null, null, 0));
        assertEquals("最大迭代轮数不能小于 1", ex.getMessage());
        assertEquals(1, configOf(null, null, 1).getMaxIters());
    }

    @Test
    @DisplayName("挂载层：skillIds/mcpServers null 归空列表；MCP 挂载缺服务编号被拒绝")
    void normalizesMountsAndValidatesMcpMount() {
        AgentSpecConfig config = configOf(null, null, null);
        assertEquals(List.of(), config.getSkillIds());
        assertEquals(List.of(), config.getMcpServers());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> McpServerMount.of(null, List.of("search")));
        assertEquals("MCP 挂载必须携带服务编号", ex.getMessage());
    }

    @Test
    @DisplayName("挂载层：同一 MCP 服务重复挂载被拒绝（白名单合并语义有歧义）")
    void rejectsDuplicateMcpServerMount() {
        McpServerMount first = McpServerMount.of(1L, List.of("search"));
        McpServerMount duplicated = McpServerMount.of(1L, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AgentSpecConfig.of(null, null, null, null, null, null,
                        List.of(first, duplicated), null));
        assertEquals("同一 MCP 服务不能重复挂载", ex.getMessage());
    }

}
