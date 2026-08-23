package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规格聚合根纯 JUnit：spec_code 规则、身份校验与创建行为。
 */
class AgentSpecTest {

    private static AgentSpecConfig draft() {
        return AgentSpecConfig.of(null, "回答客户咨询", null, null, null, null, null, null);
    }

    @Test
    @DisplayName("创建成功：文本去空白、草稿就位、未落库编号为 null")
    void createsWithNormalization() {
        AgentSpec spec = AgentSpec.create("  客服助手  ", "customer-service", " ep:service ",
                OwnerLevel.TENANT, null, draft());
        assertEquals("客服助手", spec.getName());
        assertEquals("customer-service", spec.getSpecCode());
        assertEquals("ep:service", spec.getIcon());
        assertEquals(OwnerLevel.TENANT, spec.getOwnerLevel());
        assertNull(spec.getOwnerUserId());
        assertTrue(spec.hasDraft());
        assertNull(spec.getId());
    }

    @Test
    @DisplayName("spec_code 规则：小写字母开头的小写字母/数字/连字符（2~64 位）合法")
    void acceptsValidSpecCodes() {
        assertDoesNotThrow(() -> AgentSpec.create("规格", "ab", null, OwnerLevel.TENANT, null, draft()));
        assertDoesNotThrow(() -> AgentSpec.create("规格", "a" + "-9".repeat(31) + "x", null,
                OwnerLevel.TENANT, null, draft()));
    }

    @Test
    @DisplayName("spec_code 规则：大写/数字开头/下划线/空格/超长/空值均被拒绝")
    void rejectsInvalidSpecCodes() {
        String expectedMessage = "业务编码必须为小写字母开头的小写字母/数字/连字符组合（2~64 位）";
        for (String invalid : new String[]{null, "", "Abc", "1abc", "ab_c", "ab c", "a", "-ab",
                "a" + "b".repeat(64)}) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> AgentSpec.create("规格", invalid, null, OwnerLevel.TENANT, null, draft()),
                    "应拒绝非法编码：" + invalid);
            assertEquals(expectedMessage, ex.getMessage());
        }
    }

    @Test
    @DisplayName("身份校验：用户级缺归属用户、非用户级携带归属用户均被拒绝")
    void validatesOwnerConsistency() {
        IllegalArgumentException missingUser = assertThrows(IllegalArgumentException.class,
                () -> AgentSpec.create("规格", "abc", null, OwnerLevel.USER, null, draft()));
        assertEquals("用户级规格必须携带归属用户", missingUser.getMessage());

        IllegalArgumentException extraUser = assertThrows(IllegalArgumentException.class,
                () -> AgentSpec.create("规格", "abc", null, OwnerLevel.TENANT, 1L, draft()));
        assertEquals("非用户级规格不携带归属用户", extraUser.getMessage());

        IllegalArgumentException noLevel = assertThrows(IllegalArgumentException.class,
                () -> AgentSpec.create("规格", "abc", null, null, null, draft()));
        assertEquals("规格必须声明归属层级", noLevel.getMessage());
    }

    @Test
    @DisplayName("主体校验：空白名称、超长名称与超长图标被拒绝")
    void validatesProfile() {
        IllegalArgumentException blankName = assertThrows(IllegalArgumentException.class,
                () -> AgentSpec.create("  ", "abc", null, OwnerLevel.TENANT, null, draft()));
        assertEquals("规格名称不能为空", blankName.getMessage());

        IllegalArgumentException longName = assertThrows(IllegalArgumentException.class,
                () -> AgentSpec.create("长".repeat(65), "abc", null, OwnerLevel.TENANT, null, draft()));
        assertEquals("规格名称不能超过 64 个字符", longName.getMessage());

        IllegalArgumentException longIcon = assertThrows(IllegalArgumentException.class,
                () -> AgentSpec.create("规格", "abc", "i".repeat(129), OwnerLevel.TENANT, null, draft()));
        assertEquals("图标标识不能超过 128 个字符", longIcon.getMessage());
    }

    @Test
    @DisplayName("创建必须携带初始草稿")
    void rejectsCreationWithoutDraft() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AgentSpec.create("规格", "abc", null, OwnerLevel.TENANT, null, null));
        assertEquals("新规格必须携带初始草稿", ex.getMessage());
    }

}
