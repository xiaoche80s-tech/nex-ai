package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 执行环境值对象纯 JUnit：校验链不变量与合法形态。
 */
class ExecutionEnvConfigTest {

    @Test
    @DisplayName("校验链一：未启用 workspace 时开启沙箱被拒绝")
    void rejectsSandboxWithoutWorkspace() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ExecutionEnvConfig.of(false, true, null));
        assertEquals("未启用 workspace 时不能开启沙箱或执行能力（纯对话智能体）", ex.getMessage());
    }

    @Test
    @DisplayName("校验链一：未启用 workspace 时声明能力被拒绝")
    void rejectsCapabilitiesWithoutWorkspace() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ExecutionEnvConfig.of(false, false, List.of(ExecutionCapability.SHELL)));
        assertEquals("未启用 workspace 时不能开启沙箱或执行能力（纯对话智能体）", ex.getMessage());
    }

    @Test
    @DisplayName("校验链二：未启用沙箱时声明能力被拒绝（本地模式 shell 无隔离）")
    void rejectsCapabilitiesWithoutSandbox() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ExecutionEnvConfig.of(true, false, List.of(ExecutionCapability.PYTHON)));
        assertEquals("执行能力仅沙箱模式可开放（本地模式 shell 无隔离）", ex.getMessage());
    }

    @Test
    @DisplayName("合法形态：workspace+沙箱+能力组合通过；disabled 全关通过；能力列表被冻结")
    void acceptsValidForms() {
        ExecutionEnvConfig full = assertDoesNotThrow(() -> ExecutionEnvConfig.of(true, true,
                List.of(ExecutionCapability.SHELL, ExecutionCapability.PYTHON, ExecutionCapability.NODE)));
        assertEquals(3, full.getCapabilities().size());

        ExecutionEnvConfig workspaceOnly = assertDoesNotThrow(() -> ExecutionEnvConfig.of(true, false, null));
        assertEquals(List.of(), workspaceOnly.getCapabilities());

        ExecutionEnvConfig disabled = ExecutionEnvConfig.disabled();
        assertEquals(false, disabled.isWorkspaceEnabled());
        assertEquals(false, disabled.isSandboxEnabled());
        assertEquals(List.of(), disabled.getCapabilities());
    }

    @Test
    @DisplayName("能力列表：重复项被拒绝（数量上限 = 3 种能力去重后不可能超限，重复即形态非法）")
    void rejectsDuplicateCapabilities() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ExecutionEnvConfig.of(true, true,
                        List.of(ExecutionCapability.SHELL, ExecutionCapability.SHELL)));
        assertEquals("执行能力不能重复且最多 3 项", ex.getMessage());
    }

    @Test
    @DisplayName("按值判等：同开关与能力组合的两个实例相等")
    void equalsByValue() {
        ExecutionEnvConfig one = ExecutionEnvConfig.of(true, true, List.of(ExecutionCapability.NODE));
        ExecutionEnvConfig other = ExecutionEnvConfig.of(true, true, List.of(ExecutionCapability.NODE));
        assertEquals(one, other);
        assertEquals(one.hashCode(), other.hashCode());
    }

}
