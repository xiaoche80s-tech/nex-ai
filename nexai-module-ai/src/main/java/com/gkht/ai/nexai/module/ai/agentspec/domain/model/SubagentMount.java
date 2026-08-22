package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * 子智能体挂载值对象（不可变，按值判等）：M2 预留。
 *
 * <p>对齐 agentscope 的子智能体声明语义（front matter / SubagentDeclaration）：
 * 挂载 = 引用另一个规格 + 可选的工具白名单（空 = 继承父智能体的全部工具；
 * 非空 = 白名单过滤）。子智能体在运行时经 agent_spawn 动态实例化而非静态挂载。
 * 「挂载自己」由聚合根在保存草稿时拒绝（防自引用循环 spawn）。</p>
 */
public final class SubagentMount {

    /** 被挂载的规格编号（外部聚合引用） */
    private final Long specId;
    /** 工具白名单（工具名），null / 空 = 继承全部；精确语义由 M2 权限工单定义 */
    private final List<String> tools;

    private SubagentMount(Long specId, List<String> tools) {
        this.specId = specId;
        this.tools = tools;
    }

    /**
     * 构建子智能体挂载
     *
     * @param specId 被挂载规格编号，不能为 null
     * @param tools  工具白名单，可空 = 继承父智能体全部工具
     */
    public static SubagentMount of(Long specId, List<String> tools) {
        if (specId == null) {
            throw new IllegalArgumentException("子智能体挂载必须指定规格");
        }
        return new SubagentMount(specId, tools == null ? List.of() : List.copyOf(tools));
    }

    public Long getSpecId() {
        return specId;
    }

    public List<String> getTools() {
        return tools;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SubagentMount other)) {
            return false;
        }
        return Objects.equals(specId, other.specId) && Objects.equals(tools, other.tools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(specId, tools);
    }

}
