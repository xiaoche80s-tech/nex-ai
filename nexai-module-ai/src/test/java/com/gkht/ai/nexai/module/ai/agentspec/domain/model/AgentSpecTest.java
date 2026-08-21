package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import com.gkht.ai.nexai.module.ai.agentspec.domain.exception.AgentSpecPublishWithoutDraftException;
import com.gkht.ai.nexai.module.ai.agentspec.domain.exception.AgentSpecVersionImmutableException;
import com.gkht.ai.nexai.module.ai.agentspec.domain.exception.AgentSpecVersionNotExistsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentSpec 聚合 S3 纯 JUnit 测试：草稿 → 发布 → 新草稿状态机、版本不可变守护、默认版本切换。
 * 不继承任何基类（AGENTS.md 约定的 domain 层测试例外）。
 *
 * <p>发布只发生在已落库的聚合上（版本需归属规格编号），测试用 reconstitute 模拟持久化重建。</p>
 */
class AgentSpecTest {

    private static AgentSpecConfig config(String systemPrompt) {
        return AgentSpecConfig.of(1L, systemPrompt, 10, 0.7d, null, null, null, null);
    }

    /** 模拟已落库、携带草稿、尚未发布过的规格 */
    private static AgentSpec persistedDraftSpec() {
        return AgentSpec.reconstitute(1L, "客服助手", "回答客户咨询", "ep:service", 0, null,
                config("你是客服"), null);
    }

    /** 模拟已落库且已发布 v1（草稿已清空）的规格 */
    private static AgentSpec publishedOnceSpec() {
        AgentSpec spec = persistedDraftSpec();
        spec.publish(null);
        return spec;
    }

    @Test
    @DisplayName("创建规格：携带首个草稿，未发布过（最新版本号 0、无默认版本）")
    void createCarriesInitialDraft() {
        AgentSpec spec = AgentSpec.create("客服助手", "回答客户咨询", "ep:service", config("你是客服"));

        assertNull(spec.getId());
        assertEquals("客服助手", spec.getName());
        assertEquals("回答客户咨询", spec.getDescription());
        assertTrue(spec.hasDraft());
        assertEquals(config("你是客服"), spec.getDraft());
        assertEquals(0, spec.getLatestVersionNo());
        assertNull(spec.getCurrentVersionNo());
    }

    @Test
    @DisplayName("创建规格：名称空白或草稿缺失被拒绝")
    void createValidatesInput() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpec.create("  ", null, null, config("提示")));
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpec.create("客服助手", null, null, null));
    }

    @Test
    @DisplayName("编辑草稿：主体信息与草稿内容全量更新")
    void editDraftUpdatesProfileAndConfig() {
        AgentSpec spec = persistedDraftSpec();

        spec.editDraft("售前助手", "回答售前咨询", "ep:sale", config("你是售前顾问"));

        assertEquals("售前助手", spec.getName());
        assertEquals("回答售前咨询", spec.getDescription());
        assertEquals("ep:sale", spec.getIcon());
        assertEquals("你是售前顾问", spec.getDraft().getSystemPrompt());
    }

    @Test
    @DisplayName("发布：草稿固化为 v1 不可变快照，默认版本指针前移，草稿清空")
    void publishLocksFirstVersion() {
        AgentSpec spec = persistedDraftSpec();

        AgentSpecVersion version = spec.publish("首个版本");

        assertEquals(1, version.getVersionNo());
        assertNull(version.getId());
        assertEquals(1L, version.getSpecId());
        assertEquals(config("你是客服"), version.getConfig());
        assertEquals("首个版本", version.getRemark());
        assertEquals(1, spec.getLatestVersionNo());
        assertEquals(1, spec.getCurrentVersionNo());
        assertFalse(spec.hasDraft());
    }

    @Test
    @DisplayName("发布语义：再编辑生成新草稿，再发布得 v2 且默认指针前移")
    void publishThenEditThenPublishAgain() {
        AgentSpec spec = publishedOnceSpec();

        spec.editDraft("客服助手", null, null, config("v2 提示"));

        assertTrue(spec.hasDraft());
        AgentSpecVersion second = spec.publish("迭代提示词");
        assertEquals(2, second.getVersionNo());
        assertEquals(config("v2 提示"), second.getConfig());
        assertEquals(2, spec.getLatestVersionNo());
        assertEquals(2, spec.getCurrentVersionNo());
    }

    @Test
    @DisplayName("发布：无草稿（刚发布过）再发布被拒绝")
    void publishWithoutDraftRejected() {
        AgentSpec spec = publishedOnceSpec();

        assertThrows(AgentSpecPublishWithoutDraftException.class, () -> spec.publish(null));
    }

    @Test
    @DisplayName("已发布版本不可变：modify 尝试被拒绝并携带规格与版本号")
    void publishedVersionModificationRejected() {
        AgentSpec spec = publishedOnceSpec();
        spec.editDraft("客服助手", null, null, config("v2 提示"));
        AgentSpecVersion latest = spec.publish(null);

        AgentSpecVersionImmutableException ex = assertThrows(AgentSpecVersionImmutableException.class,
                () -> latest.modify(config("篡改快照")));
        assertTrue(ex.getMessage().contains("v2"));
        // 早前版本同样拒绝（重建自持久化数据的 v1）
        AgentSpecVersion first = AgentSpecVersion.reconstitute(99L, 1L, 1, config("你是客服"), null, null);
        assertThrows(AgentSpecVersionImmutableException.class, () -> first.modify(config("篡改 v1")));
    }

    @Test
    @DisplayName("版本实体按（规格 + 版本号）判等：落库前后同一业务键相等，不同版本不等")
    void versionEqualityByIdentityKey() {
        AgentSpec spec = publishedOnceSpec();
        spec.editDraft("客服助手", null, null, config("v2 提示"));
        AgentSpecVersion second = spec.publish(null);
        // 未落库的新版本与重建的持久化版本按业务键（specId + versionNo）判等
        AgentSpecVersion rebuilt = AgentSpecVersion.reconstitute(100L, second.getSpecId(),
                second.getVersionNo(), second.getConfig(), null, null);

        assertEquals(second, rebuilt);
        assertEquals(second.hashCode(), rebuilt.hashCode());
        AgentSpecVersion first = AgentSpecVersion.reconstitute(99L, 1L, 1, config("你是客服"), null, null);
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("切换默认版本：可切回任意已发布版本（回滚），越界被拒绝")
    void switchDefaultVersionWithinRange() {
        AgentSpec spec = publishedOnceSpec();
        spec.editDraft("客服助手", null, null, config("v2 提示"));
        spec.publish(null);
        assertEquals(2, spec.getCurrentVersionNo());

        spec.switchDefaultVersion(1);
        assertEquals(1, spec.getCurrentVersionNo());
        assertEquals(2, spec.getLatestVersionNo());

        assertThrows(AgentSpecVersionNotExistsException.class, () -> spec.switchDefaultVersion(0));
        assertThrows(AgentSpecVersionNotExistsException.class, () -> spec.switchDefaultVersion(3));
    }

    @Test
    @DisplayName("切换默认版本：从未发布的规格没有任何可选版本")
    void switchDefaultVersionBeforePublishRejected() {
        AgentSpec spec = persistedDraftSpec();

        assertThrows(AgentSpecVersionNotExistsException.class, () -> spec.switchDefaultVersion(1));
    }

    @Test
    @DisplayName("发布后切换默认版本不影响迭代：再编辑发布仍得递增新版本")
    void switchDefaultThenEditStartsFreshDraft() {
        AgentSpec spec = publishedOnceSpec();
        spec.switchDefaultVersion(1);

        spec.editDraft("客服助手", null, null, config("v2 提示"));

        assertEquals(2, spec.publish(null).getVersionNo());
    }

}
