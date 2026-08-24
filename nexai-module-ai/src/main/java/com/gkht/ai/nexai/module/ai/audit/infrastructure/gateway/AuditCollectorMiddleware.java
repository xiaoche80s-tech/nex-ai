package com.gkht.ai.nexai.module.ai.audit.infrastructure.gateway;

import com.gkht.ai.nexai.framework.common.util.json.JsonUtils;
import com.gkht.ai.nexai.module.ai.audit.domain.model.AuditEvent;
import com.gkht.ai.nexai.module.ai.audit.domain.model.SensitiveMasker;
import com.gkht.ai.nexai.module.ai.audit.domain.model.ToolOutcome;
import com.gkht.ai.nexai.module.ai.audit.domain.repository.AuditEventRepository;
import com.gkht.ai.nexai.module.ai.framework.config.AiObservabilityProperties;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeContextKeys;
import com.gkht.ai.nexai.module.ai.shared.util.TenantExecution;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.message.ToolUseBlock;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 审计采集中间件（工单 14，ADR-0001 宪法内自建）：onActing 拦截点采集
 * 「谁-何时-哪个会话-调了什么工具-结果摘要」——acting 流终止时逐工具调用落 PG，
 * 入参与结果经 {@link SensitiveMasker} 脱敏摘要。<b>采集失败仅告警不阻断会话流。</b>
 *
 * <p>维度来源 {@link RuntimeContext}：sessionId（= 会话业务键）、userId、
 * extra.tenantId / agentId / specId / versionNo（装配时由运行时网关注入）。
 * 落库线程可能缺失租户上下文（reactor 调度），从 extra 恢复后写入、finally 清理。</p>
 */
@Component
@ConditionalOnProperty(prefix = "nexai.ai.observability.audit", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AuditCollectorMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(AuditCollectorMiddleware.class);

    @Resource
    private AuditEventRepository auditEventRepository;

    @Resource
    private AiObservabilityProperties observabilityProperties;

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext ctx, ActingInput input,
                                     Function<ActingInput, Flux<AgentEvent>> next) {
        long startedAt = System.currentTimeMillis();
        // 工具结果按 toolCallId 收口（一个 acting 轮可能并发多个工具调用）
        Map<String, ToolResultEndEvent> resultsByCallId = new ConcurrentHashMap<>();
        return next.apply(input)
                .doOnNext(event -> {
                    if (event instanceof ToolResultEndEvent resultEnd) {
                        resultsByCallId.put(resultEnd.getToolCallId(), resultEnd);
                    }
                })
                .doFinally(signal -> record(input, ctx, startedAt, resultsByCallId));
    }

    /** acting 流终止：逐工具调用组装审计事件并落库（旁路，失败不冒泡；
     *  落库线程为 boundedElastic（chat 流的调度线程），同步写库与会话层 onRuntimeEvent 同模式） */
    private void record(ActingInput input, RuntimeContext ctx, long startedAt,
                        Map<String, ToolResultEndEvent> resultsByCallId) {
        try {
            Long tenantId = ctx.get(RuntimeContextKeys.TENANT_ID, Long.class);
            if (tenantId == null) {
                log.debug("审计维度缺失租户（sessionKey={}），跳过采集", ctx.getSessionId());
                return;
            }
            int maxPayload = observabilityProperties.getAudit().getMaxPayloadLength();
            java.util.List<String> extraKeywords =
                    observabilityProperties.getAudit().getSensitiveKeyKeywords();
            LocalDateTime occurredAt = LocalDateTime.now();
            List<AuditEvent> events = new ArrayList<>();
            for (ToolUseBlock toolCall : input.toolCalls()) {
                ToolResultEndEvent resultEnd = resultsByCallId.get(toolCall.getId());
                events.add(AuditEvent.record(
                        tenantId, ctx.getSessionId(),
                        ctx.get(RuntimeContextKeys.SPEC_ID, Long.class),
                        ctx.get(RuntimeContextKeys.VERSION_NO, Integer.class),
                        ctx.get(RuntimeContextKeys.AGENT_ID, String.class), ctx.getUserId(),
                        toolCall.getId(), toolCall.getName(),
                        ToolOutcome.fromStateName(resultEnd == null ? null
                                : resultEnd.getState() == null ? null : resultEnd.getState().name()),
                        SensitiveMasker.mask(JsonUtils.toJsonString(
                                toolCall.getInput() == null ? Map.of() : toolCall.getInput()),
                                maxPayload, extraKeywords),
                        resultDigest(resultEnd, maxPayload, extraKeywords),
                        System.currentTimeMillis() - startedAt, occurredAt));
            }
            if (events.isEmpty()) {
                return;
            }
            TenantExecution.withTenant(tenantId, () -> auditEventRepository.appendAll(events));
        } catch (Exception ex) {
            log.warn("审计事件落库失败（sessionKey={}）：{}", ctx.getSessionId(), ex.getMessage(), ex);
        }
    }

    /** 结果摘要：结果状态 + 框架 metadata（若有）脱敏截断 */
    private static String resultDigest(ToolResultEndEvent resultEnd, int maxPayload,
                                       java.util.List<String> extraKeywords) {
        if (resultEnd == null) {
            return null;
        }
        Object metadata = resultEnd.getMetadata();
        String base = "state=" + resultEnd.getState();
        if (metadata == null || metadata.toString().isBlank()) {
            return base;
        }
        return SensitiveMasker.mask(base + " " + metadata, maxPayload, extraKeywords);
    }

}
