package com.gkht.ai.nexai.module.ai.usage.infrastructure.gateway;

import com.gkht.ai.nexai.module.ai.framework.config.AiObservabilityProperties;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeContextKeys;
import com.gkht.ai.nexai.module.ai.shared.util.TenantExecution;
import com.gkht.ai.nexai.module.ai.usage.domain.model.ModelUsage;
import com.gkht.ai.nexai.module.ai.usage.domain.repository.ModelUsageRepository;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.ChatUsage;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.function.Function;

/**
 * 模型用量采集中间件（工单 14，ADR-0001 宪法内自建）：onModelCall 拦截点从
 * {@code ModelCallEndEvent.getUsage()}（ChatUsage：token/缓存命中/耗时）采集单次模型调用用量，
 * 维度含租户/会话/规格，落 PG——先采集不计价，第三波计价时不可回补的数据已就位。
 * <b>采集失败仅告警不阻断会话流。</b>
 */
@Component
@ConditionalOnProperty(prefix = "nexai.ai.observability.usage", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class UsageCollectorMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(UsageCollectorMiddleware.class);

    @Resource
    private ModelUsageRepository modelUsageRepository;

    @Resource
    private AiObservabilityProperties observabilityProperties;

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext ctx, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        return next.apply(input)
                .doOnNext(event -> {
                    if (event instanceof ModelCallEndEvent callEnd) {
                        ChatUsage usage = callEnd.getUsage();
                        if (usage != null) {
                            record(ctx, input, usage);
                        }
                    }
                });
    }

    /** 单次模型调用用量落库（旁路，失败不冒泡；耗时取 ChatUsage.time 框架口径；
     *  落库线程为 boundedElastic，同步写库与会话层 onRuntimeEvent 同模式） */
    private void record(RuntimeContext ctx, ModelCallInput input, ChatUsage usage) {
        try {
            Long tenantId = ctx.get(RuntimeContextKeys.TENANT_ID, Long.class);
            if (tenantId == null) {
                log.debug("用量维度缺失租户（sessionKey={}），跳过采集", ctx.getSessionId());
                return;
            }
            ModelUsage record = ModelUsage.record(tenantId, ctx.getSessionId(),
                    ctx.get(RuntimeContextKeys.SPEC_ID, Long.class),
                    ctx.get(RuntimeContextKeys.VERSION_NO, Integer.class),
                    ctx.get(RuntimeContextKeys.AGENT_ID, String.class), ctx.getUserId(),
                    input.model().getModelName(),
                    input.messages() == null ? 0 : input.messages().size(),
                    usage.getInputTokens(), usage.getOutputTokens(), usage.getCachedTokens(),
                    usage.getTime(), LocalDateTime.now());
            TenantExecution.withTenant(tenantId, () -> modelUsageRepository.append(record));
        } catch (Exception ex) {
            log.warn("模型用量落库失败（sessionKey={}）：{}", ctx.getSessionId(), ex.getMessage(), ex);
        }
    }

}
