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
 * 规格聚合根的版本状态机纯 JUnit（ADR 0004 显式状态机）：发布（快照固化 +
 * 当前版本推进 + 清空草稿 + 发布校验）、编辑保存重建草稿且不动快照、切换当前版本不动草稿。
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
    @DisplayName("发布：草稿固化为快照、当前版本推进、草稿清空（发布即回到稳定态）")
    void publishFreezesDraftAndAdvancesPointer() {
        AgentSpec spec = specWithDraft(1L);
        assertFalse(spec.hasPublishedVersion());
        assertNull(spec.getCurrentVersionNo());
        AgentSpecConfig draftBeforePublish = spec.getDraft();

        AgentSpecVersion v1 = spec.publish(1, "首版");
        assertEquals(1, v1.getVersionNo());
        assertNotNull(v1.getConfig());
        assertEquals("草稿提示", v1.getConfig().getSystemPrompt());
        assertTrue(spec.hasPublishedVersion());
        assertEquals(1, spec.getCurrentVersionNo());

        // 快照与草稿内容同源：发布即固化发布时的草稿
        assertEquals(draftBeforePublish, v1.getConfig());
        // 发布清空草稿（ADR 0004）：规格回到稳定态，hasDraft=false
        assertFalse(spec.hasDraft());
        assertNull(spec.getDraft());
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
    @DisplayName("发布后编辑草稿：既有快照不受影响；全链路 发布→编辑重建→可再发布 v3")
    void editingAfterPublishDoesNotMutateSnapshot() {
        AgentSpec spec = specWithDraft(1L);
        AgentSpecVersion v1 = spec.publish(1, null);
        String frozenPrompt = v1.getConfig().getSystemPrompt();
        assertFalse(spec.hasDraft(), "发布后草稿已清空");

        // 编辑保存重建草稿（整体替换），不触碰已发布快照
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
        assertFalse(spec.hasDraft(), "发布 v2 后草稿再次清空");

        // 全链路（工单 22）：发布清空 → 编辑保存重建 → 可再发布 v3
        spec.replaceDraft(AgentSpecConfig.of(1L, "v3 描述", "v3 提示", 10, null, null, null, null, null));
        AgentSpecVersion v3 = spec.publish(3, "三版");
        assertEquals(3, v3.getVersionNo());
        assertEquals(3, spec.getCurrentVersionNo());
        assertEquals("v3 提示", v3.getConfig().getSystemPrompt());
    }

    @Test
    @DisplayName("无草稿时再发布被拒（发布门禁：仅草稿态可发布）")
    void publishRejectsWhenDraftCleared() {
        AgentSpec spec = specWithDraft(1L);
        spec.publish(1, null);
        assertFalse(spec.hasDraft());
        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> spec.publish(2, null));
        assertEquals("没有可发布的草稿", rejected.getMessage());
        assertEquals(1, spec.getCurrentVersionNo(), "被拒发布不得推进当前版本指针");
    }

    @Test
    @DisplayName("切换当前版本：目标版本归属本规格则回退指针；切换不动草稿；缺失或不归属则拒绝且指针不变")
    void switchVersionChecksExistence() {
        AgentSpec spec = specWithDraft(1L);
        AgentSpecVersion v1 = spec.publish(1, null);
        spec.replaceDraft(AgentSpecConfig.of(1L, null, null, null, null, null, null, null, null));
        spec.publish(2, null);
        assertEquals(2, spec.getCurrentVersionNo());

        // 编辑保存重建草稿后切换当前版本：只动指针，不动草稿（两条独立工作线，ADR 0004）
        spec.replaceDraft(AgentSpecConfig.of(1L, "编辑中", null, null, null, null, null, null, null));
        spec.switchToVersion(v1);
        assertEquals(1, spec.getCurrentVersionNo(), "回退到已发布版本应只移动当前版本指针");
        assertTrue(spec.hasDraft(), "切换当前版本不得清空草稿");
        assertEquals("编辑中", spec.getDraft().getDescription(), "切换当前版本不得修改草稿内容");

        // 归属其他规格的版本对象 = 悬空目标，拒绝且指针不动
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> spec.switchToVersion(AgentSpecVersion.reconstitute(99L, 8L, 99, null, null, null)));
        assertEquals("版本 99 不存在", missing.getMessage());
        assertEquals(1, spec.getCurrentVersionNo(), "切换失败不得改变当前版本指针");
        assertEquals("编辑中", spec.getDraft().getDescription(), "切换失败同样不得动草稿");
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
