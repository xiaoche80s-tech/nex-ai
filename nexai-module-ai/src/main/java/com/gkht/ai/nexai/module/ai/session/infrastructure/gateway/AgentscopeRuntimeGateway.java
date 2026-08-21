package com.gkht.ai.nexai.module.ai.session.infrastructure.gateway;

import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.module.ai.model.infrastructure.gateway.ChatModelFactory;
import com.gkht.ai.nexai.module.ai.session.domain.gateway.AgentRuntimeGateway;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 运行时网关（agentscope 适配器，工单 06 核心）：管理面表数据 → agentscope 运行时的装配链。
 *
 * <p>装配方向（spec 总体架构决策）：表数据 → 每请求经 {@link ChatModelFactory} 构造 ChatModel
 * → 编程式命名注册进 {@link ModelRegistry}（不走 SPI）→ ReActAgent 按名字解析装配。
 * 注册名带租户前缀防跨租户碰撞；每次装配覆盖注册——渠道密钥等配置变更即时生效，
 * Model 实例不可变，覆盖不影响正在使用旧实例的会话。</p>
 *
 * <p>线程模型：本方法在调用线程（Servlet）同步完成注册与装配（无 DB 访问），
 * 事件流的生产经 {@code subscribeOn} 整体切换到弹性线程——不在 Reactor 序列中做阻塞调用，
 * 也不占用容器线程等待 LLM 响应。流终结后关闭 agent（状态存储为共享单例，不在此关闭）。</p>
 */
@Component
public class AgentscopeRuntimeGateway implements AgentRuntimeGateway {

    private static final Logger log = LoggerFactory.getLogger(AgentscopeRuntimeGateway.class);

    /** 事件流出错时降级输出的自定义事件类型（SSE 端点保证流以可读错误收尾而非中断连接） */
    static final String ERROR_EVENT_TYPE = "SESSION_ERROR";

    private final ChatModelFactory chatModelFactory;
    private final AgentStateStoreProvider agentStateStoreProvider;
    /** 无实现者时为空列表（ObjectProvider 惰性收集，避免空集合注入失败） */
    private final List<RuntimeToolContributor> runtimeToolContributors;

    public AgentscopeRuntimeGateway(ChatModelFactory chatModelFactory,
                                    AgentStateStoreProvider agentStateStoreProvider,
                                    ObjectProvider<RuntimeToolContributor> runtimeToolContributors) {
        this.chatModelFactory = chatModelFactory;
        this.agentStateStoreProvider = agentStateStoreProvider;
        this.runtimeToolContributors = runtimeToolContributors.orderedStream().toList();
    }

    @Override
    public Flux<String> chat(AgentRuntimeConfig config, String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        ReActAgent agent = assemble(config);
        return agent.streamEvents(message, runtimeContext(config))
                .subscribeOn(Schedulers.boundedElastic())
                // 保真优先：逐事件经 agentscope 原生 codec（Jackson 2）转 JSON，不让 Spring 栈序列化事件对象（ADR-0005）
                .map(event -> JsonUtils.getJsonCodec().toJson(event))
                .onErrorResume(ex -> {
                    log.warn("[chat][会话 {} 事件流出错：{}]", config.getSessionKey(), ex.getMessage());
                    return Flux.just(JsonUtils.getJsonCodec().toJson(
                            Map.of("type", ERROR_EVENT_TYPE, "message", rootMessage(ex))));
                })
                .doFinally(signal -> agent.close());
    }

    /**
     * 装配链：模型命名注册 → agent 构建（系统提示 / 推理参数 / 状态存储 / 工具）
     */
    private ReActAgent assemble(AgentRuntimeConfig config) {
        ReActAgent.Builder builder = ReActAgent.builder()
                .name(config.getAgentName())
                .model(registerModel(config))
                .stateStore(agentStateStoreProvider.get())
                .defaultSessionId(config.getSessionKey());
        if (config.getSystemPrompt() != null) {
            builder.sysPrompt(config.getSystemPrompt());
        }
        if (config.getMaxIters() != null) {
            builder.maxIters(config.getMaxIters());
        }
        if (config.getTemperature() != null) {
            builder.generateOptions(new GenerateOptions.Builder()
                    .temperature(config.getTemperature()).build());
        }
        Toolkit toolkit = new Toolkit();
        for (RuntimeToolContributor contributor : runtimeToolContributors) {
            contributor.contribute(toolkit);
        }
        builder.toolkit(toolkit);
        return builder.build();
    }

    /**
     * 表数据 → ModelRegistry 编程注册。注册名形如 {@code nexai:t1:m5}——租户隔离 + 模型编号定位。
     * 每次装配覆盖注册（ChatModel 构造只是轻量配置对象）：渠道密钥/端点变更即时生效；
     * Model 实例不可变，覆盖不影响正在使用旧实例的会话。
     */
    private String registerModel(AgentRuntimeConfig config) {
        String registryName = String.format("nexai:t%s:m%s",
                TenantContextHolder.getRequiredTenantId(), config.getModel().getId());
        ModelRegistry.register(registryName,
                chatModelFactory.create(config.getChannel(), config.getModel().getModelId()));
        return registryName;
    }

    private RuntimeContext runtimeContext(AgentRuntimeConfig config) {
        return RuntimeContext.builder()
                .sessionId(config.getSessionKey())
                .userId(config.getUserId())
                .build();
    }

    /**
     * 逐层解包取根因消息（Reactor 会用 RuntimeException 包装底层异常），截断防长响应体刷屏
     */
    private String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        } else {
            message = current.getClass().getSimpleName() + ": " + message;
        }
        return message.length() > 500 ? message.substring(0, 500) + "…" : message;
    }

}
