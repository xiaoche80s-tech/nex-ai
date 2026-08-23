package com.gkht.ai.nexai.module.ai.support;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolBase;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 敏感工具测试替身（extends ToolBase）：{@code checkPermissions} 恒返回 ASK——
 * 模拟「挂载时标记 sensitiveTools 的敏感工具」，调用前挂起进 ASKING 等人工审批（HITL）。
 * 经工具挂载的 sensitiveTools 名单翻译为 PermissionRule(name, ASK) 后，本工具触发
 * RequireUserConfirmEvent；审批（ConfirmResult）后恢复执行。
 */
public class ConfirmableTool extends ToolBase {

    private final String toolName;

    public ConfirmableTool(String toolName) {
        super(ToolBase.builder()
                .name(toolName)
                .description("敏感工具（HITL 测试）：调用需人工审批")
                .inputSchema(Map.of("type", "object", "properties", Map.of())));
        this.toolName = toolName;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.just(ToolResultBlock.text("敏感工具执行成功：" + toolName));
    }

    @Override
    public Mono<PermissionDecision> checkPermissions(
            Map<String, Object> toolInput, PermissionContextState context) {
        return Mono.just(PermissionDecision.ask("该工具调用需要人工审批（敏感工具）"));
    }
}
