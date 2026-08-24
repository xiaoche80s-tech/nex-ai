package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

/**
 * 挂载（CONTEXT.md 术语）：AgentSpec 声明的外部能力引用的 sealed 家族骨架。
 * 各挂载为薄 record（判等/不可变由 record 语义覆盖），校验规则集中在各自 {@code of()}
 * 工厂；规范构造器即信任构造（快照读路径不重跑校验——历史快照按固化时规则成立）。
 * 新增挂载通道（如 SkillMount）实现本接口即可纳入家族。
 */
public sealed interface Mount permits ToolMount, FolderMount {
}
