package com.gkht.ai.nexai.module.ai.support;

import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.message.ToolResultBlock;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * S1 测试用「需人工确认」工具（工单 08 HITL 接缝）：{@code checkPermissions} 恒返回 ASK，
 * 使 agent 在执行前发出 REQUIRE_USER_CONFIRM 事件挂起等待三态回应；
 * 获批准后真实执行（回显文本），拒绝则不执行（由运行时写入 DENIED 结果）。
 *
 * <p>M2 的权限引擎接入（工单 16）复用同一确认通道——本工具即该通道的最小驱动器。</p>
 */
public class ConfirmableEchoTool extends ToolBase {

    public ConfirmableEchoTool() {
        super(ToolBase.builder()
                .name("confirmable_echo")
                .description("需要人工确认后执行的回显工具，用于验证 HITL 三态闭环")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("text", Map.of("type", "string", "description", "要回显的文本")),
                        "required", List.of("text")))
                .readOnly(true)
                .concurrencySafe(true));
    }

    @Override
    public Mono<PermissionDecision> checkPermissions(Map<String, Object> toolInput,
                                                     PermissionContextState context) {
        return Mono.just(PermissionDecision.ask("该工具调用需要人工确认"));
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        Object text = param.getInput().get("text");
        return Mono.just(ToolResultBlock.text("confirmed-echo:" + text));
    }

}
