package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * 执行环境配置值对象（不可变，按值判等）：智能体规格的执行能力声明，
 * 随版本快照固化（影响运行时装配形态：workspace 落盘 / 沙箱容器 / 文件与执行工具矩阵）。
 *
 * <p>校验不变量（ADR-0006 决策 6）：
 * {@code !workspaceEnabled → sandboxEnabled=false ∧ capabilities=∅}（纯对话智能体零落盘）；
 * {@code !sandboxEnabled → capabilities=∅}（本地模式 shell 在宿主机裸奔，非沙箱禁执行能力）。</p>
 */
public final class ExecutionEnvConfig {

    /** 执行能力数量上限（SHELL/PYTHON/NODE 三选多） */
    static final int CAPABILITIES_MAX_SIZE = 3;

    private final boolean workspaceEnabled;
    private final boolean sandboxEnabled;
    private final List<ExecutionCapability> capabilities;

    private ExecutionEnvConfig(boolean workspaceEnabled, boolean sandboxEnabled,
                               List<ExecutionCapability> capabilities) {
        this.workspaceEnabled = workspaceEnabled;
        this.sandboxEnabled = sandboxEnabled;
        this.capabilities = capabilities;
    }

    /**
     * 构建执行环境配置
     *
     * @param workspaceEnabled 是否启用 per-spec 常驻 workspace（文件工具与落盘的总开关）
     * @param sandboxEnabled   是否启用 Docker 沙箱（仅 workspaceEnabled=true 时有意义）
     * @param capabilities     沙箱内开放的执行能力，可空，仅 sandboxEnabled=true 可非空
     */
    public static ExecutionEnvConfig of(boolean workspaceEnabled, boolean sandboxEnabled,
                                        List<ExecutionCapability> capabilities) {
        if (!workspaceEnabled && (sandboxEnabled || capabilities != null && !capabilities.isEmpty())) {
            throw new IllegalArgumentException("未启用 workspace 时不能开启沙箱或执行能力（纯对话智能体）");
        }
        if (!sandboxEnabled && capabilities != null && !capabilities.isEmpty()) {
            throw new IllegalArgumentException("执行能力仅沙箱模式可开放（本地模式 shell 无隔离）");
        }
        if (capabilities != null && (capabilities.size() > CAPABILITIES_MAX_SIZE
                || capabilities.stream().distinct().count() != capabilities.size())) {
            throw new IllegalArgumentException("执行能力不能重复且最多 " + CAPABILITIES_MAX_SIZE + " 项");
        }
        return new ExecutionEnvConfig(workspaceEnabled, sandboxEnabled,
                capabilities == null ? List.of() : List.copyOf(capabilities));
    }

    /** 全关形态（纯对话智能体）：零落盘、无文件与执行工具 */
    public static ExecutionEnvConfig disabled() {
        return new ExecutionEnvConfig(false, false, List.of());
    }

    public boolean isWorkspaceEnabled() {
        return workspaceEnabled;
    }

    public boolean isSandboxEnabled() {
        return sandboxEnabled;
    }

    public List<ExecutionCapability> getCapabilities() {
        return capabilities;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExecutionEnvConfig other)) {
            return false;
        }
        return workspaceEnabled == other.workspaceEnabled
                && sandboxEnabled == other.sandboxEnabled
                && capabilities.equals(other.capabilities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceEnabled, sandboxEnabled, capabilities);
    }

}
