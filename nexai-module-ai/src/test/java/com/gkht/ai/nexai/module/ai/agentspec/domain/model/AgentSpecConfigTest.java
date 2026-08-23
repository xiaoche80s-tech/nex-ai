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
    @DisplayName("挂载层：skillIds/tools null 归空列表；工具挂载缺来源或编号被拒绝")
    void normalizesMountsAndValidatesToolMount() {
        AgentSpecConfig config = configOf(null, null, null);
        assertEquals(List.of(), config.getSkillIds());
        assertEquals(List.of(), config.getTools());

        IllegalArgumentException noSource = assertThrows(IllegalArgumentException.class,
                () -> ToolMount.of(null, 1L, List.of("search"), null));
        assertEquals("工具挂载必须声明来源", noSource.getMessage());

        IllegalArgumentException noRef = assertThrows(IllegalArgumentException.class,
                () -> ToolMount.of(ToolSource.MCP, null, List.of("search"), null));
        assertEquals("工具挂载必须携带来源条目编号", noRef.getMessage());
    }

    @Test
    @DisplayName("挂载层：敏感工具名单空白/重复被拒绝；白名单非空时敏感名单须为其子集")
    void validatesSensitiveTools() {
        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> ToolMount.of(ToolSource.MCP, 1L, null, List.of("del", "del")));
        assertEquals("敏感工具名单不能含空白或重复项且最多 128 项", duplicate.getMessage());

        IllegalArgumentException notSubset = assertThrows(IllegalArgumentException.class,
                () -> ToolMount.of(ToolSource.MCP, 1L, List.of("search"), List.of("delete_user")));
        assertEquals("敏感工具必须在白名单内（标了审批的工具须先在放行面内）", notSubset.getMessage());

        // 白名单为空 = 放行全部，敏感名单允许列任意工具（全集运行时才知道，无法静态校验子集）
        assertDoesNotThrow(() -> ToolMount.of(ToolSource.MCP, 1L, null, List.of("delete_user")));
        // 敏感工具在放行面内：合法
        assertDoesNotThrow(() -> ToolMount.of(ToolSource.PLATFORM, 2L,
                List.of("search", "delete_user"), List.of("delete_user")));
    }

    @Test
    @DisplayName("挂载层：同一工具来源条目重复挂载被拒绝；跨来源同编号可共存")
    void rejectsDuplicateToolMount() {
        ToolMount mcpOne = ToolMount.of(ToolSource.MCP, 1L, List.of("search"), null);
        ToolMount mcpDuplicated = ToolMount.of(ToolSource.MCP, 1L, null, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AgentSpecConfig.of(null, null, null, null, null, null,
                        List.of(mcpOne, mcpDuplicated), null));
        assertEquals("同一工具来源条目不能重复挂载", ex.getMessage());

        // MCP 与平台工具库是不同来源：同编号不冲突
        ToolMount platform = ToolMount.of(ToolSource.PLATFORM, 1L, null, null);
        assertDoesNotThrow(() -> AgentSpecConfig.of(null, null, null, null, null, null,
                List.of(mcpOne, platform), null));
    }

}
