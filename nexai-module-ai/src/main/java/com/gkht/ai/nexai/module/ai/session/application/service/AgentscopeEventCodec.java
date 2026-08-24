package com.gkht.ai.nexai.module.ai.session.application.service;

import com.gkht.ai.nexai.framework.common.util.json.JsonUtils;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * agentscope 事件 JSON 的读侧解析（后端唯一 schema 知识点，工单 21 候选 5）：
 * 事件载荷 {@link RuntimeEvent#payload()} 的出帧方向直出 agentscope 序列化 JSON
 * （前端契约，ADR-0001 不自建）；本类只收口后端读侧——各处不再各自摸
 * {@code toolCalls[].id/name/input} 字段结构（原 Session 聚合注释、应用层
 * ToolCallJSON 镜像类、测试探针三处散布的 schema 知识收敛于此）。
 *
 * <p><b>兼容读</b>：解析失败或结构不符返回空结果 + warn 日志（存量会话数据不迁移，
 * 挂起上下文为会话态临时数据，容忍丢失）。</p>
 */
public final class AgentscopeEventCodec {

    private static final Logger log = LoggerFactory.getLogger(AgentscopeEventCodec.class);

    private AgentscopeEventCodec() {
    }

    /**
     * 挂起工具调用（与 agentscope RequireUserConfirmEvent.toolCalls 数组元素
     * （ToolUseBlock 序列化）同构的读侧类型）。
     */
    public record PendingToolCall(String toolCallId, String toolName, Map<String, Object> arguments) {
    }

    /**
     * 解析 RequireUserConfirmEvent JSON（Session.pendingConfirmations 持久化原文 /
     * 事件流载荷）中的挂起工具调用列表。
     */
    public static List<PendingToolCall> parsePendingToolCalls(String requireUserConfirmEventJson) {
        if (requireUserConfirmEventJson == null || requireUserConfirmEventJson.isBlank()) {
            return List.of();
        }
        try {
            var toolCalls = JsonUtils.parseTree(requireUserConfirmEventJson).path("toolCalls");
            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                return List.of();
            }
            List<PendingToolCall> result = new ArrayList<>(toolCalls.size());
            for (var toolCall : toolCalls) {
                String id = toolCall.path("id").asText(null);
                if (id == null || id.isBlank()) {
                    continue;
                }
                var inputNode = toolCall.path("input");
                Map<String, Object> input = inputNode.isObject()
                        ? parseInputMap(inputNode.toString()) : Map.of();
                result.add(new PendingToolCall(id,
                        toolCall.path("name").asText(null), input));
            }
            return result;
        } catch (Exception ex) {
            log.warn("解析挂起审批上下文失败（兼容读，按无挂起处理）：{}", ex.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseInputMap(String inputJson) {
        try {
            return JsonUtils.parseObject(inputJson, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /** 首 个挂起工具调用编号（事件断言探针用）；无挂起返回 null */
    public static String firstToolCallId(String requireUserConfirmEventJson) {
        List<PendingToolCall> calls = parsePendingToolCalls(requireUserConfirmEventJson);
        return calls.isEmpty() ? null : calls.get(0).toolCallId();
    }
}
