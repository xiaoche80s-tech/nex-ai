package com.gkht.ai.nexai.module.ai.session.infrastructure.gateway;

import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.module.ai.model.infrastructure.gateway.ChatModelFactory;
import com.gkht.ai.nexai.module.ai.session.domain.exception.SessionRunningException;
import com.gkht.ai.nexai.module.ai.session.domain.gateway.AgentRuntimeGateway;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.ToolCallDecision;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 运行时网关（agentscope 适配器，工单 06 核心，工单 08 扩中断/HITL/克隆）：
 * 管理面表数据 → agentscope 运行时的装配链。
 *
 * <p>装配方向（spec 总体架构决策）：表数据 → 每请求经 {@link ChatModelFactory} 构造 ChatModel
 * → 编程式命名注册进 {@link ModelRegistry}（不走 SPI）→ ReActAgent 按名字解析装配。
 * 注册名带租户前缀防跨租户碰撞；每次装配覆盖注册——渠道密钥等配置变更即时生效，
 * Model 实例不可变，覆盖不影响正在使用旧实例的会话。</p>
 *
 * <p>线程模型：装配（含运行注册）在调用线程（Servlet）同步完成（无 DB 访问），
 * 事件流的生产经 {@code subscribeOn} 整体切换到弹性线程——不在 Reactor 序列中做阻塞调用，
 * 也不占用容器线程等待 LLM 响应。流终结后关闭 agent（状态存储为共享单例，不在此关闭）。</p>
 *
 * <p><b>运行注册与中断（工单 08）</b>：agentscope 的中断旗标挂在 per-(userId, sessionId) 的
 * {@code AgentState} 上，而 stateCache 是 agent 实例内的——本网关每请求新建 agent 实例，
 * 跨实例 {@code interrupt(userId, sessionId)} 只会触发新实例加载到的另一个旗标副本，对运行中的流无效。
 * 故以 {@link #runningAgents} 注册表持有「运行中流的 agent 实例 + RuntimeContext」，
 * 中断经原实例触发其真实旗标；注册与注销在流订阅生命周期内完成，同一会话并发第二条流
 * 在注册时即被拒绝（{@link SessionRunningException}）。</p>
 */
@Component
public class AgentscopeRuntimeGateway implements AgentRuntimeGateway {

    private static final Logger log = LoggerFactory.getLogger(AgentscopeRuntimeGateway.class);

    /** 事件流出错时降级输出的自定义事件类型（SSE 端点保证流以可读错误收尾而非中断连接） */
    static final String ERROR_EVENT_TYPE = "SESSION_ERROR";

    /** ReActAgent 会话状态的存储 key（对话上下文、工具调用状态、确认元数据都在其中） */
    private static final String AGENT_STATE_KEY = "agent_state";

    /** 确认回应时挂在消息上的说明文本：HITL 恢复分支不把消息本体写入上下文，仅作构建合法性占位 */
    private static final String CONFIRM_MESSAGE_TEXT = "[工具确认回应]";

    private final ChatModelFactory chatModelFactory;
    private final AgentStateStoreProvider agentStateStoreProvider;
    /** 无实现者时为空列表（ObjectProvider 惰性收集，避免空集合注入失败） */
    private final List<RuntimeToolContributor> runtimeToolContributors;

    /** 运行中事件流注册表：sessionKey → 该流的 agent 实例与寻址上下文（中断与并发防护的依据） */
    private final ConcurrentHashMap<String, RunningAgent> runningAgents = new ConcurrentHashMap<>();

    /** 一次事件流运行的寻址快照（agent 实例 + RuntimeContext） */
    private record RunningAgent(ReActAgent agent, RuntimeContext context) {
    }

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
        return streamEvents(config, () -> {
            ReActAgent agent = assemble(config);
            RuntimeContext context = runtimeContext(config);
            return new AssembledStream(agent, context, agent.streamEvents(message, context));
        });
    }

    @Override
    public Flux<String> confirmToolCalls(AgentRuntimeConfig config, List<ToolCallDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            throw new IllegalArgumentException("确认决定列表不能为空");
        }
        return streamEvents(config, () -> {
            ReActAgent agent = assemble(config);
            RuntimeContext context = runtimeContext(config);
            // 三态决定 → agentscope ConfirmResult：改参数后批准 = confirmed + 携带修改后 toolCall；
            // 拒绝 = 原样 toolCall + confirmed=false。执行时参数取自 input（结构化 Map）而非
            // content，故这里把参数 JSON 双写 input 与 content（后者与事件流中工具调用形态一致）
            List<ConfirmResult> results = decisions.stream()
                    .map(decision -> new ConfirmResult(decision.approved(), ToolUseBlock.builder()
                            .id(decision.toolCallId())
                            .name(decision.toolName())
                            .input(parseArguments(decision.argumentsJson()))
                            .content(decision.argumentsJson())
                            .build()))
                    .toList();
            Msg confirmMsg = UserMessage.builder()
                    .textContent(CONFIRM_MESSAGE_TEXT)
                    .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, results))
                    .build();
            return new AssembledStream(agent, context, agent.streamEvents(confirmMsg, context));
        });
    }

    @Override
    public boolean interrupt(String sessionKey) {
        RunningAgent running = runningAgents.get(sessionKey);
        if (running == null) {
            return false;
        }
        // 经运行中的 agent 实例触发其真实 (userId, sessionId) 槽位旗标，
        // 流在下一个检查点停止推理并以正常事件序列收尾（INTERRUPTED 恢复消息 + AGENT_END）
        running.agent().interrupt(running.context());
        return true;
    }

    @Override
    public boolean copySessionState(String fromUserId, String fromSessionKey,
                                    String toUserId, String toSessionKey) {
        AgentStateStore store = agentStateStoreProvider.get();
        AgentState state = store.get(fromUserId, fromSessionKey, AGENT_STATE_KEY, AgentState.class).orElse(null);
        if (state == null) {
            // 源会话从未发过消息（无对话历史可复制），克隆保持空白
            return false;
        }
        store.save(toUserId, toSessionKey, AGENT_STATE_KEY, state);
        return true;
    }

    /**
     * 事件流的共用管道：装配（同步）→ 注册运行 → 订阅切弹性线程 → 逐事件原生 codec 转 JSON →
     * 错误降级 SESSION_ERROR → 流终结注销运行并关闭 agent。
     */
    private Flux<String> streamEvents(AgentRuntimeConfig config, Supplier<AssembledStream> assembler) {
        // 装配与流构建保持同步（让非法参数在调用线程暴露，而非藏进冷流）
        AssembledStream assembled = assembler.get();
        // 注册运行：同一会话已有流在跑则拒绝（putIfAbsent 原子判重）
        RunningAgent running = new RunningAgent(assembled.agent(), assembled.context());
        if (runningAgents.putIfAbsent(config.getSessionKey(), running) != null) {
            throw new SessionRunningException(config.getSessionKey());
        }
        return assembled.events()
                .subscribeOn(Schedulers.boundedElastic())
                // 保真优先：逐事件经 agentscope 原生 codec（Jackson 2）转 JSON，不让 Spring 栈序列化事件对象（ADR-0005）
                .map(event -> JsonUtils.getJsonCodec().toJson(event))
                .onErrorResume(ex -> {
                    log.warn("[streamEvents][会话 {} 事件流出错：{}]", config.getSessionKey(), ex.getMessage());
                    return Flux.just(JsonUtils.getJsonCodec().toJson(
                            Map.of("type", ERROR_EVENT_TYPE, "message", rootMessage(ex))));
                })
                .doFinally(signal -> {
                    // 条件注销：只移除自己注册的条目，防止误删紧随其后新流的注册
                    runningAgents.remove(config.getSessionKey(), running);
                    assembled.agent().close();
                });
    }

    /** 一次装配的产物：agent 实例 + 寻址上下文 + 冷事件流 */
    private record AssembledStream(ReActAgent agent, RuntimeContext context, Flux<AgentEvent> events) {
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
     * 工具参数 JSON → 结构化入参（执行链从 input 取参）；非对象 JSON 或解析失败按空参处理，
     * 让工具侧的参数校验去报具体的错
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String argumentsJson) {
        try {
            Map<String, Object> parsed = JsonUtils.getJsonCodec().fromJson(argumentsJson, Map.class);
            return parsed != null ? parsed : Map.of();
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /**
     * 逐层解包取根因消息（Reactor 会用 RuntimeException 包装底层异常），截断防长响应体刷屏
     */
    private String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message;
        if (current.getMessage() == null || current.getMessage().isBlank()) {
            message = current.getClass().getSimpleName();
        } else {
            message = current.getClass().getSimpleName() + ": " + current.getMessage();
        }
        return message.length() > 500 ? message.substring(0, 500) + "…" : message;
    }

}
