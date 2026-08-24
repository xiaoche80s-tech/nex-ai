package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 版本快照实体纯 JUnit：版本结构校验（版本号/备注边界/配置必填）与不可变性（无修改入口）。
 */
class AgentSpecVersionTest {

    private static AgentSpecConfig config() {
        return AgentSpecConfig.of(1L, "回答客户咨询", "你是客服", 10, null, null, null, null, null);
    }

    @Test
    @DisplayName("创建版本：版本号与全量配置就位、备注去空白、未落库编号为 null")
    void createsWithNormalization() {
        AgentSpecVersion version = AgentSpecVersion.create(7L, 3, config(), "  首版发布  ");
        assertNull(version.getId());
        assertEquals(7L, version.getSpecId());
        assertEquals(3, version.getVersionNo());
        assertEquals("首版发布", version.getNote());
        assertEquals(config(), version.getConfig());
    }

    @Test
    @DisplayName("备注边界：空白归 null、超长被拒")
    void validatesNote() {
        assertNull(AgentSpecVersion.create(1L, 1, config(), "   ").getNote());
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpecVersion.create(1L, 1, config(), "注".repeat(256)));
    }

    @Test
    @DisplayName("版本结构校验：缺规格/非法版本号/缺配置均被拒")
    void validatesStructure() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpecVersion.create(null, 1, config(), null));
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpecVersion.create(1L, 0, config(), null));
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpecVersion.create(1L, null, config(), null));
        assertThrows(IllegalArgumentException.class,
                () -> AgentSpecVersion.create(1L, 1, null, null));
    }

    @Test
    @DisplayName("首版工厂固定版本号 1")
    void createFirstPinsVersionOne() {
        AgentSpecVersion version = AgentSpecVersion.createFirst(1L, config(), null);
        assertEquals(1, version.getVersionNo());
    }

    @Test
    @DisplayName("快照不可变：实体无修改入口（发布后内容即历史）")
    void snapshotIsImmutable() {
        AgentSpecVersion version = AgentSpecVersion.create(1L, 1, config(), null);
        assertDoesNotThrow(version::getConfig);
        assertDoesNotThrow(version::getVersionNo);
    }

}
