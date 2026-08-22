package com.gkht.ai.nexai.module.ai.session.infrastructure.gateway;

import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionEnvConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.model.infrastructure.gateway.ChatModelFactory;
import com.gkht.ai.nexai.module.ai.session.domain.exception.SessionRunningException;
import com.gkht.ai.nexai.module.ai.session.domain.exception.SessionSandboxUnavailableException;
import com.gkht.ai.nexai.module.ai.session.domain.gateway.AgentRuntimeGateway;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.ToolCallDecision;
import com.gkht.ai.nexai.module.ai.session.framework.config.AiRuntimeProperties;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 运行时网关（agentscope-harness 适配器，ADR-0007）：管理面表数据 → HarnessAgent 运行时的装配链。
 *
 * <p>装配方向（spec 总体架构决策）：表数据 → 每请求经 {@link ChatModelFactory} 构造 ChatModel
 * → 编程式命名注册进 {@link ModelRegistry}（不走 SPI）→ HarnessAgent 按名字解析装配。
 * 注册名带租户前缀防跨租户碰撞；每次装配覆盖注册——渠道密钥等配置变更即时生效。</p>
 *
 * <p><b>规格驱动装配矩阵（ExecutionEnvConfig → 内置行为）</b>：
 * 纯对话（无 workspace）零落盘——文件/执行/记忆/transcript 全禁、系统提示直传；
 * workspace 启用——per-spec 常驻目录（归属层级布局）+ 文件六件套 + subagents + memory 四件套 +
 * transcript 持久化，人格经 AGENTS.md 物化注入（快照 systemPrompt 是唯一事实源，装配时内容比对覆写）；
 * 沙箱启用——文件与执行进 Docker 容器（能力→镜像查表），非沙箱本地模式禁执行能力
 * （宿主 shell 无隔离）。web_fetch/web_search 框架无条件注册，装配后 toolkit 显式移除。</p>
 *
 * <p>线程模型与运行注册（interrupt 寻址、同会话并发拒绝）沿用工单 06/08 的机制：
 * 装配在调用线程同步完成，事件流生产切弹性线程；{@link #runningAgents} 注册表持有运行中流的
 * agent 实例 + RuntimeContext，中断经原实例的 delegate（HarnessAgent 未透传
 * {@code interrupt(RuntimeContext)}）触发真实旗标。</p>
 */
@Component
public class AgentscopeRuntimeGateway implements AgentRuntimeGateway {

    private static final Logger log = LoggerFactory.getLogger(AgentscopeRuntimeGateway.class);

    /** 事件流出错时降级输出的自定义事件类型（SSE 端点保证流以可读错误收尾而非中断连接） */
    static final String ERROR_EVENT_TYPE = "SESSION_ERROR";

    /** HarnessAgent 会话状态的存储 key（对话上下文、工具调用状态、确认元数据都在其中） */
    private static final String AGENT_STATE_KEY = "agent_state";

    /** 确认回应时挂在消息上的说明文本：HITL 恢复分支不把消息本体写入上下文，仅作构建合法性占位 */
    private static final String CONFIRM_MESSAGE_TEXT = "[工具确认回应]";

    /** workspace 人格物化文件（WorkspaceContextMiddleware 注入系统提示，快照 systemPrompt 的投影） */
    static final String AGENTS_MD = "AGENTS.md";

    /** web 工具名（框架无条件注册，toolkit 层显式移除） */
    private static final String WEB_FETCH_TOOL = "web_fetch";
    private static final String WEB_SEARCH_TOOL = "web_search";

    /** 沙箱容器内 workspace 挂载根 */
    private static final String SANDBOX_WORKSPACE_ROOT = "/workspace";

    private final ChatModelFactory chatModelFactory;
    private final AgentStateStoreProvider agentStateStoreProvider;
    private final AiRuntimeProperties runtimeProperties;
    private final SandboxImageResolver sandboxImageResolver;
    private final DockerAvailabilityProbe dockerAvailabilityProbe;
    /** 无实现者时为空列表（ObjectProvider 惰性收集，避免空集合注入失败） */
    private final List<RuntimeToolContributor> runtimeToolContributors;

    /** 运行中事件流注册表：sessionKey → 该流的 agent 实例与寻址上下文（中断与并发防护的依据） */
    private final ConcurrentHashMap<String, RunningAgent> runningAgents = new ConcurrentHashMap<>();

    /** 一次事件流运行的寻址快照（agent 实例 + RuntimeContext） */
    private record RunningAgent(HarnessAgent agent, RuntimeContext context) {
    }

    public AgentscopeRuntimeGateway(ChatModelFactory chatModelFactory,
                                    AgentStateStoreProvider agentStateStoreProvider,
                                    AiRuntimeProperties runtimeProperties,
                                    SandboxImageResolver sandboxImageResolver,
                                    DockerAvailabilityProbe dockerAvailabilityProbe,
                                    ObjectProvider<RuntimeToolContributor> runtimeToolContributors) {
        this.chatModelFactory = chatModelFactory;
        this.agentStateStoreProvider = agentStateStoreProvider;
        this.runtimeProperties = runtimeProperties;
        this.sandboxImageResolver = sandboxImageResolver;
        this.dockerAvailabilityProbe = dockerAvailabilityProbe;
        this.runtimeToolContributors = runtimeToolContributors.orderedStream().toList();
    }

    @Override
    public Flux<String> chat(AgentRuntimeConfig config, String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        return streamEvents(config, () -> {
            HarnessAgent agent = assemble(config);
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
            HarnessAgent agent = assemble(config);
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
        // 经运行中的 agent 实例触发其真实 (userId, sessionId) 槽位旗标——HarnessAgent 未透传
        // interrupt(RuntimeContext)，经 delegate（内层 ReActAgent）触发；
        // 流在下一个检查点停止推理并以正常事件序列收尾（INTERRUPTED 恢复消息 + AGENT_END）
        delegateOf(running.agent()).interrupt(running.context());
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
                    // HarnessAutoCloseable：排空异步镜像写、关闭子代理任务池与 workspace 句柄（不删文件）
                    assembled.agent().close();
                });
    }

    /** 一次装配的产物：agent 实例 + 寻址上下文 + 冷事件流 */
    private record AssembledStream(HarnessAgent agent, RuntimeContext context, Flux<AgentEvent> events) {
    }

    private ReActAgent delegateOf(HarnessAgent agent) {
        return agent.getDelegate();
    }

    /**
     * 装配链（规格驱动矩阵）：模型命名注册 → agent 构建（执行环境 → workspace/沙箱/内置行为开关）
     * → toolkit 组装 → web 工具移除。包内可见供规格驱动矩阵测试直接断言装配产物。
     */
    HarnessAgent assemble(AgentRuntimeConfig config) {
        ExecutionEnvConfig env = config.getExecutionEnv();
        boolean workspaceEnabled = env != null && env.isWorkspaceEnabled();

        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(config.getAgentName())
                .model(registerModel(config))
                .stateStore(agentStateStoreProvider.get())
                .defaultSessionId(config.getSessionKey());
        if (config.getMaxIters() != null) {
            builder.maxIters(config.getMaxIters());
        }
        // 调用参数整组透传（temperature/topP/maxTokens 非空项生效）
        com.gkht.ai.nexai.module.ai.agentspec.domain.model.GenerateOptions options = config.getGenerateOptions();
        if (options != null) {
            GenerateOptions.Builder optionsBuilder = new GenerateOptions.Builder();
            if (options.getTemperature() != null) {
                optionsBuilder.temperature(options.getTemperature());
            }
            if (options.getTopP() != null) {
                optionsBuilder.topP(options.getTopP());
            }
            if (options.getMaxTokens() != null) {
                optionsBuilder.maxTokens(options.getMaxTokens());
            }
            builder.generateOptions(optionsBuilder.build());
        }

        if (workspaceEnabled) {
            // workspace 模式：人格经 AGENTS.md 物化注入（快照 systemPrompt 唯一事实源），
            // builder.sysPrompt 不传以免双重注入
            Path workspace = ensureWorkspace(config);
            materializeAgentsMd(workspace, config.getSystemPrompt());
            builder.workspace(workspace);
            if (env.isSandboxEnabled()) {
                requireDockerAvailable(config);
                DockerFilesystemSpec sandboxSpec = new DockerFilesystemSpec();
                sandboxSpec.image(sandboxImageResolver.resolveImage(env.getCapabilities()));
                sandboxSpec.workspaceRoot(SANDBOX_WORKSPACE_ROOT);
                // isolationScope 声明于父类 SandboxFilesystemSpec（返回父类型），拆开链式调用
                sandboxSpec.isolationScope(isolationScope(config.getOwnerLevel()));
                builder.filesystem(sandboxSpec);
            } else {
                // 本地模式：project 显式指向 workspace 自身（无外部只读层——不显式设置时框架
                // 默认把服务器工作目录作为只读下层暴露）；本地 shell 无隔离，禁执行能力
                LocalFilesystemSpec localSpec = new LocalFilesystemSpec()
                        .project(workspace)
                        .isolationScope(isolationScope(config.getOwnerLevel()));
                builder.filesystem(localSpec).disableShellTool();
            }
        } else {
            // 纯对话：零落盘——不设 workspace/filesystem（框架会回落默认本地 overlay，
            // 但相关工具与落盘中间件全禁后无代码路径触达），系统提示直传
            builder.disableFilesystemTools().disableShellTool().disableTranscript()
                    .disableMemoryHooks().disableMemoryTools();
            if (config.getSystemPrompt() != null) {
                builder.sysPrompt(config.getSystemPrompt());
            }
        }

        Toolkit toolkit = new Toolkit();
        for (RuntimeToolContributor contributor : runtimeToolContributors) {
            contributor.contribute(toolkit);
        }
        builder.toolkit(toolkit);

        HarnessAgent agent = builder.build();
        // web 工具框架层无条件注册且无 builder 开关，装配后显式移除（ADR-0007 决策 2）
        agent.getToolkit().removeTool(WEB_FETCH_TOOL);
        agent.getToolkit().removeTool(WEB_SEARCH_TOOL);
        return agent;
    }

    /**
     * workspace 目录（归属层级布局，ADR-0007 决策 3）：平台级 {root}/platform/{specCode}/、
     * 租户级 {root}/t{tenantId}/{specCode}/、用户级 {root}/t{tenantId}/u{userId}/{specCode}/。
     * 目录常驻不删除（膨胀由框架 retention 自治，目录治理为 M2/M3 债务）。包内可见供矩阵测试断言。
     */
    Path workspacePath(AgentRuntimeConfig config) {
        Path root = runtimeProperties.getWorkspace().getRoot();
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return switch (config.getOwnerLevel()) {
            case PLATFORM -> root.resolve("platform").resolve(config.getSpecCode());
            case TENANT -> root.resolve("t" + tenantId).resolve(config.getSpecCode());
            case USER -> root.resolve("t" + tenantId)
                    .resolve("u" + config.getOwnerUserId()).resolve(config.getSpecCode());
        };
    }

    /**
     * IsolationScope（ADR-0007 决策 5）：租户级/平台级 USER（种子共享一份零复制、
     * 记忆/用户文件 per-user namespace）；用户级 AGENT（workspace 已在该用户目录下，物理目录即隔离）
     */
    private IsolationScope isolationScope(OwnerLevel ownerLevel) {
        return ownerLevel == OwnerLevel.USER ? IsolationScope.AGENT : IsolationScope.USER;
    }

    /** 建目录（幂等）。装配线程同步执行，workspace 属首次使用时创建 */
    private Path ensureWorkspace(AgentRuntimeConfig config) {
        Path workspace = workspacePath(config);
        try {
            Files.createDirectories(workspace);
        } catch (IOException ex) {
            throw new IllegalStateException("workspace 目录创建失败：" + workspace, ex);
        }
        return workspace;
    }

    /**
     * AGENTS.md 物化（ADR-0007 决策 4）：每次装配从 DB 版本快照（config.systemPrompt）比对
     * 本地 {workspace}/AGENTS.md，相同跳过、不同覆写——本地盘只是物化缓存，一致性靠使用前校验。
     * systemPrompt 为空不物化（框架对缺失 AGENTS.md 只告警）。
     */
    private void materializeAgentsMd(Path workspace, String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return;
        }
        Path agentsMd = workspace.resolve(AGENTS_MD);
        try {
            if (Files.exists(agentsMd) && systemPrompt.equals(Files.readString(agentsMd))) {
                return;
            }
            Files.writeString(agentsMd, systemPrompt);
        } catch (IOException ex) {
            throw new IllegalStateException("AGENTS.md 物化失败：" + agentsMd, ex);
        }
    }

    /**
     * 沙箱前置检查：环境无 docker 而规格要求沙箱 → 显式报错（不静默降级）
     */
    private void requireDockerAvailable(AgentRuntimeConfig config) {
        if (!dockerAvailabilityProbe.isAvailable()) {
            throw new SessionSandboxUnavailableException(config.getAgentName());
        }
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
