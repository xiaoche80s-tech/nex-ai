package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion;

/**
 * 生效快照结果记录（CONTEXT.md：生效快照）：规格聚合 + 其当前版本指针指向的
 * 不可变版本快照，一次解析同时给出——运行侧各入口（调试会话/OpenAI 兼容出口/
 * 终端页面）统一经 {@code AgentSpecService.resolveCurrentVersion*} 消费，
 * 不自行筛选版本列表。
 */
public record EffectiveSpecSnapshot(AgentSpec spec, AgentSpecVersion version) {
}
