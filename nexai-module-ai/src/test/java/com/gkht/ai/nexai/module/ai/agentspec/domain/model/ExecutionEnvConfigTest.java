package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * ExecutionEnvConfig 值对象 S3 纯 JUnit 测试：执行环境不变量
 * （ADR-0006 决策 6——非沙箱禁执行能力、纯对话零能力）与按值判等。
 */
class ExecutionEnvConfigTest {

    @Test
    @DisplayName("纯对话：workspace 关闭时沙箱与执行能力必须全关")
    void rejectsSandboxWithoutWorkspace() {
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionEnvConfig.of(false, true, null));
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionEnvConfig.of(false, false, List.of(ExecutionCapability.SHELL)));
    }

    @Test
    @DisplayName("非沙箱禁执行能力：本地模式 shell 在宿主机裸奔")
    void rejectsCapabilitiesWithoutSandbox() {
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionEnvConfig.of(true, false, List.of(ExecutionCapability.PYTHON)));
    }

    @Test
    @DisplayName("合法组合：纯对话 / 仅 workspace / workspace + 沙箱 + 能力")
    void acceptsValidCombinations() {
        assertEquals(ExecutionEnvConfig.disabled(), ExecutionEnvConfig.of(false, false, null));

        ExecutionEnvConfig workspaceOnly = ExecutionEnvConfig.of(true, false, null);
        assertTrue(workspaceOnly.isWorkspaceEnabled());
        assertFalse(workspaceOnly.isSandboxEnabled());
        assertTrue(workspaceOnly.getCapabilities().isEmpty());

        ExecutionEnvConfig sandboxed = ExecutionEnvConfig.of(true, true,
                List.of(ExecutionCapability.SHELL, ExecutionCapability.PYTHON));
        assertTrue(sandboxed.isSandboxEnabled());
        assertEquals(2, sandboxed.getCapabilities().size());
    }

    @Test
    @DisplayName("能力列表不可重复（枚举组合无序语义）")
    void rejectsDuplicateCapabilities() {
        assertThrows(IllegalArgumentException.class, () -> ExecutionEnvConfig.of(true, true,
                List.of(ExecutionCapability.SHELL, ExecutionCapability.SHELL)));
    }

    @Test
    @DisplayName("按值判等：三开关与能力组合共同参与判等")
    void equalsByValue() {
        assertEquals(ExecutionEnvConfig.of(true, true, List.of(ExecutionCapability.NODE)),
                ExecutionEnvConfig.of(true, true, List.of(ExecutionCapability.NODE)));
        assertFalse(ExecutionEnvConfig.of(true, true, List.of(ExecutionCapability.NODE))
                .equals(ExecutionEnvConfig.of(true, true, List.of(ExecutionCapability.PYTHON))));
        assertFalse(ExecutionEnvConfig.of(true, false, null)
                .equals(ExecutionEnvConfig.of(false, false, null)));
    }

}
