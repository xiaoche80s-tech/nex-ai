package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规格聚合根的版本状态机纯 JUnit：发布（快照固化 + 当前版本推进 + 发布校验）、
 * 编辑不动快照（发布后编辑不影响既有快照）、切换当前版本。
 */
class AgentSpecVersioningTest {

    private static AgentSpec specWithDraft(Long modelId) {
        AgentSpecConfig config = AgentSpecConfig.of(modelId, "草稿描述", "草稿提示", 10,
                null, null, null, null, null);
        // 经 reconstitute 携带编号（模拟已落库规格；publish 需要非空规格编号）
        return AgentSpec.reconstitute(7L, "客服助手", "customer-service", null,
                OwnerLevel.TENANT, null, config, null, null);
    }

    @Test
    @DisplayName("发布：草稿固化为快照、当前版本推进、快照内容即草稿内容")
    void publishFreezesDraftAndAdvancesPointer() {
        AgentSpec spec = specWithDraft(1L);
        assertFalse(spec.hasPublishedVersion());
        assertNull(spec.getCurrentVersionNo());

        AgentSpecVersion v1 = spec.publish(1, "首版");
        assertEquals(1, v1.getVersionNo());
        assertNotNull(v1.getConfig());
        assertEquals("草稿提示", v1.getConfig().getSystemPrompt());
        assertTrue(spec.hasPublishedVersion());
        assertEquals(1, spec.getCurrentVersionNo());

        // 快照与草稿内容同源：发布即固化当前草稿
        assertEquals(spec.getDraft(), v1.getConfig());
    }

    @Test
    @DisplayName("发布校验：无草稿 / 缺模型引用被拒绝，快照不产生")
    void publishValidates() {
        AgentSpec emptyDraft = AgentSpec.reconstitute(1L, "客服助手", "customer-service", null,
                OwnerLevel.TENANT, null, null, null, null);
        IllegalStateException missingDraft = assertThrows(IllegalStateException.class,
                () -> emptyDraft.publish(1, null));
        assertEquals("没有可发布的草稿", missingDraft.getMessage());

        IllegalStateException missingModel = assertThrows(IllegalStateException.class,
                () -> specWithDraft(null).publish(1, null));
        assertEquals("发布前必须为规格配置模型", missingModel.getMessage());

        assertNull(specWithDraft(1L).getCurrentVersionNo(), "发布校验失败不得推进当前版本指针");
    }

    @Test
    @DisplayName("发布后编辑草稿：既有快照不受影响，发布新版本得到新快照")
    void editingAfterPublishDoesNotMutateSnapshot() {
        AgentSpec spec = specWithDraft(1L);
        AgentSpecVersion v1 = spec.publish(1, null);
        String frozenPrompt = v1.getConfig().getSystemPrompt();

        // 再编辑进入新草稿（整体替换），不触碰已发布快照
        AgentSpecConfig edited = AgentSpecConfig.of(1L, "新描述", "新提示", 10, null, null, null, null, null);
        spec.replaceDraft(edited);
        assertEquals(1, spec.getCurrentVersionNo(), "编辑草稿不得改变当前版本指针");
        assertEquals("新提示", spec.getDraft().getSystemPrompt());
        assertEquals(frozenPrompt, v1.getConfig().getSystemPrompt(), "发布后的快照内容不得被后续编辑影响");

        AgentSpecVersion v2 = spec.publish(2, null);
        assertEquals(2, v2.getVersionNo());
        assertEquals("新提示", v2.getConfig().getSystemPrompt());
        assertEquals("草稿描述", v1.getConfig().getDescription(), "v1 快照仍是发布时的内容");
        assertEquals(2, spec.getCurrentVersionNo());
    }

    @Test
    @DisplayName("切换当前版本：目标版本存在则回退指针，不存在则拒绝且指针不变")
    void switchVersionChecksExistence() {
        AgentSpec spec = specWithDraft(1L);
        spec.publish(1, null);
        spec.replaceDraft(AgentSpecConfig.of(1L, null, null, null, null, null, null, null, null));
        spec.publish(2, null);
        assertEquals(2, spec.getCurrentVersionNo());

        spec.switchToVersion(1, true);
        assertEquals(1, spec.getCurrentVersionNo(), "回退到已发布版本应只移动当前版本指针");

        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> spec.switchToVersion(99, false));
        assertEquals("版本 99 不存在", missing.getMessage());
        assertEquals(1, spec.getCurrentVersionNo(), "切换失败不得改变当前版本指针");
    }

    @Test
    @DisplayName("reconstitute 恢复当前版本指针（持久化重建后状态保持）")
    void reconstituteRestoresPointer() {
        AgentSpec rebuilt = AgentSpec.reconstitute(5L, "客服助手", "customer-service", null,
                OwnerLevel.TENANT, null, specWithDraft(1L).getDraft(), 3, null);
        assertTrue(rebuilt.hasPublishedVersion());
        assertEquals(3, rebuilt.getCurrentVersionNo());
    }

    @Test
    @DisplayName("replaceDraft 拒绝 null 草稿")
    void replaceDraftRejectsNull() {
        AgentSpec spec = specWithDraft(1L);
        assertThrows(IllegalArgumentException.class, () -> spec.replaceDraft(null));
    }

}
