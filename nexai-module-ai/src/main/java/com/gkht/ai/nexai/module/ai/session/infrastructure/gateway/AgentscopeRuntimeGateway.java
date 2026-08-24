package com.gkht.ai.nexai.module.ai.session.infrastructure.gateway;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionCapability;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionEnvConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.FolderFile;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.FolderMount;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.GenerateOptions;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolMount;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolSource;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.gateway.ChatModelFactory;
import com.gkht.ai.nexai.module.ai.framework.config.AiRuntimeProperties;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.model.McpServer;
import com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.gateway.AgentscopeMcpServerGateway;
import com.gkht.ai.nexai.module.infra.api.file.FileApi;
import com.gkht.ai.nexai.module.ai.session.domain.gateway.AgentRuntimeGateway;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEventType;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeContextKeys;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.SkillMountDirectory;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.ToolCallDecision;
import com.gkht.ai.nexai.module.ai.shared.tool.PlatformToolEntry;
import com.gkht.ai.nexai.module.ai.shared.tool.PlatformToolRegistry;
import com.gkht.ai.nexai.module.ai.shared.util.Hashes;
import com.gkht.ai.nexai.module.ai.shared.util.RootCauses;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.skill.SkillFilter;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.extensions.postgresql.PostgresDistributedStore;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * agentscope 运行时网关实现（ADR-0001 直用，零子类翻译）：把 {@link HarnessAgent} 的调用面
 * 翻译为领域端口 {@link AgentRuntimeGateway} 的语义——AgentSpec 四层配置 → Builder 装配、
 * agentscope 事件流 → {@link RuntimeEvent} 信封、HITL 三态 → {@code ConfirmResult} 恢复、
 * (userId, sessionId) 槽位寻址。domain 层零 agentscope 依赖，本类是本聚合唯一的框架接触面。
 *
 * <p><b>常驻实例（ADR-0001 沿用）</b>：任何链路不 per-请求新建实例——本类持
 * {@link AgentInstanceManager} 按「规格 + 版本号」缓存 HarnessAgent 实例，装配一次、常驻复用；
 * 版本戳（渠道/模型/MCP Server 更新时间 + 技能挂载指纹 + 规格当前版本号）变化时失效重建
 * （工单 08/12/13），旧实例按引用计数善后 close（MCP client 一并关闭——框架不级联，平台自管）。
 * 同 (userId, sessionId) 的并发调用由框架槽位门排队（FIFO），不同会话并行。</p>
 *
 * <p><b>模型注册</b>：{@code ModelRegistry} 为 JVM 级静态表，实例构建时按「租户:渠道:模型」注册
 * 一次；模型/密钥变更经版本戳失效重建时重新注册覆盖（进程内全局生效，重建即刷新）。</p>
 *
 * <p><b>挂载翻译（工单 12/13 语义定案）</b>：技能挂载目录 → {@code FileSystemSkillRepository}
 * + {@code SkillFilter.only}（仅挂载技能对模型可见）；MCP 挂载 → {@code McpClientBuilder}
 * 三传输注册 + {@code enableTools} 白名单收敛（挂载白名单优先，空则 server 级白名单）；
 * 平台工具库挂载 → 注册表寻条目注册 + 白名单外工具移除。<b>MCP 不可达/超时为运行性缺失：
 * 降级跳过该挂载（warn 日志 + {@code MCP_MOUNT_UNAVAILABLE} 语义），智能体照常装配不阻断会话
 * ——工具缺位由调试台探测与事件流暴露；配置性缺失（server 不存在/停用）已由应用层显式报错。</b></p>
 */
@Component
public class AgentscopeRuntimeGateway implements AgentRuntimeGateway {

    private static final Logger log = LoggerFactory.getLogger(AgentscopeRuntimeGateway.class);

    /** 事件 JSON 载荷的错误类型（非 agentscope 类型，平台侧收尾语义） */
    private static final String ERROR_EVENT_TYPE = "SESSION_ERROR";
    /** 沙箱容器内 workspace 根（框架约定） */
    private static final String SANDBOX_WORKSPACE_ROOT = "/workspace";
    /** 平台内置工具移除清单（web 工具对租户智能体无配置即无意义，且 key 泄漏面大） */
    private static final List<String> REMOVED_BUILTIN_TOOLS = List.of("web_fetch", "web_search");
    /** 沙箱缺省镜像（能力映射未配置时的兜底） */
    private static final String DEFAULT_SANDBOX_IMAGE = "ubuntu:22.04";
    /** 事件载荷错误消息截断长度（调用方错误消息可能携带长响应体） */
    private static final int ROOT_MESSAGE_MAX_LENGTH = 500;
    /** MCP 挂载连接超时上限（运行性缺失及时止损，降级跳过不拖死装配） */
    private static final Duration MCP_CONNECT_TIMEOUT_CAP = Duration.ofSeconds(20);

    @Resource
    private AiRuntimeProperties runtimeProperties;

    @Resource
    private DataSource dataSource;

    @Resource
    private ChatModelFactory chatModelFactory;

    /** 运行时工具贡献者（测试注入敏感工具/回显工具的接缝；生产挂载走装配指令翻译） */
    @Resource
    private List<RuntimeToolContributor> runtimeToolContributors = List.of();

    /** 平台工具库注册表（source=PLATFORM 挂载寻址；测试上下文未装配时为 null，挂载降级跳过） */
    @Autowired(required = false)
    private PlatformToolRegistry platformToolRegistry;

    /** 文件实体存取（infra FileApi 跨模块 api，工单 18 文件夹物化下载；测试上下文未装配时为 null） */
    @Autowired(required = false)
    private FileApi fileApi;

    /** 运行时采集中间件（工单 14：审计 onActing / 用量 onModelCall，按配置条件注册；测试上下文可为空） */
    @Autowired(required = false)
    private List<io.agentscope.core.middleware.MiddlewareBase> runtimeCollectorMiddlewares = List.of();

    /** 常驻实例注册表（线程安全；装配一次、常驻复用，版本戳失效重建） */
    private final AgentInstanceManager instanceManager = new AgentInstanceManager();

    @Override
    public Flux<RuntimeEvent> chat(AgentRuntimeConfig config, String content) {
        if (content == null || content.isBlank()) {
            return Flux.just(errorEvent("消息内容不能为空"));
        }
        return Flux.defer(() -> {
            try {
                InstanceEntry entry = instanceManager.acquire(config);
                RuntimeContext context = runtimeContext(config);
                Msg userMsg = new UserMessage(content);
                return entry.agent().streamEvents(List.of(userMsg), context)
                        .map(this::toRuntimeEvent)
                        .onErrorResume(ex -> Flux.just(errorEvent(rootMessage(ex))))
                        .doFinally(signal -> instanceManager.release(entry));
            } catch (Exception ex) {
                // 装配期错误（规格未发布/模型禁用/沙箱不可用等）以事件收尾，不抛异常
                return Flux.just(errorEvent(rootMessage(ex)));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** OpenAI 兼容出口转换器（ADR-0001 直用 agentscope ChatCompletionsStreamingAdapter） */
    private final io.agentscope.core.chat.completions.streaming.ChatCompletionsStreamingAdapter
            openAiAdapter = new io.agentscope.core.chat.completions.streaming.ChatCompletionsStreamingAdapter();

    @Override
    public Flux<RuntimeEvent> chatOpenAi(AgentRuntimeConfig config,
                                         List<com.gkht.ai.nexai.module.ai.session.domain.valueobject.ChatMessageInput> messages,
                                         String requestId) {
        return Flux.defer(() -> {
            try {
                InstanceEntry entry = instanceManager.acquire(config);
                RuntimeContext context = runtimeContext(config);
                List<Msg> msgs = messages.stream().map(AgentscopeRuntimeGateway::toMsg).toList();
                // 无状态出口：adapter 收客户端全量历史；model 名回填路由的规格编码
                return openAiAdapter.stream(entry.agent().getDelegate(), msgs, requestId,
                                config.getAgentId())
                        .map(chunk -> RuntimeEvent.of(RuntimeEventType.OPENAI_CHUNK,
                                JsonUtils.getJsonCodec().toJson(chunk)))
                        .onErrorResume(ex -> Flux.just(errorEvent(rootMessage(ex))))
                        .doFinally(signal -> instanceManager.release(entry));
            } catch (Exception ex) {
                return Flux.just(errorEvent(rootMessage(ex)));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 出口消息 → agentscope Msg（system/user/assistant 扁平口径；其余角色归 user） */
    private static Msg toMsg(
            com.gkht.ai.nexai.module.ai.session.domain.valueobject.ChatMessageInput input) {
        return switch (input.getRole()) {
            case "system" -> new io.agentscope.core.message.SystemMessage(input.getContent());
            case "assistant" -> new io.agentscope.core.message.AssistantMessage(input.getContent());
            default -> new UserMessage(input.getContent());
        };
    }

    @Override
    public Flux<RuntimeEvent> confirmToolCalls(AgentRuntimeConfig config,
                                               List<ToolCallDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return Flux.just(errorEvent("审批决定不能为空"));
        }
        return Flux.defer(() -> {
            try {
                InstanceEntry entry = instanceManager.acquire(config);
                RuntimeContext context = runtimeContext(config);
                List<ConfirmResult> results = decisions.stream()
                        .map(AgentscopeRuntimeGateway::toConfirmResult)
                        .toList();
                Msg confirmMsg = UserMessage.builder()
                        .textContent("[工具确认回应]")
                        .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, results))
                        .build();
                return entry.agent().streamEvents(List.of(confirmMsg), context)
                        .map(this::toRuntimeEvent)
                        .onErrorResume(ex -> Flux.just(errorEvent(rootMessage(ex))))
                        .doFinally(signal -> instanceManager.release(entry));
            } catch (Exception ex) {
                return Flux.just(errorEvent(rootMessage(ex)));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public boolean interrupt(AgentRuntimeConfig config) {
        InstanceEntry entry = instanceManager.peek(config);
        if (entry == null) {
            return false;
        }
        // HarnessAgent 未透传 interrupt(RuntimeContext)，经 delegate（内层 ReActAgent）触发槽位旗标；
        // 流在下一个检查点停止推理并正常收尾（INTERRUPTED 恢复消息 + AGENT_END）
        entry.agent().getDelegate().interrupt(runtimeContext(config));
        return true;
    }

    @Override
    public List<String> listWorkspaceFiles(AgentRuntimeConfig config, String relativePath) {
        InstanceEntry entry = instanceManager.acquire(config);
        try {
            WorkspaceManager ws = entry.agent().workspaceFor(config.getUserId(), config.getSessionKey());
            Path root = ws.getWorkspace();
            Path target = resolveWithin(root, relativePath);
            if (target == null || !Files.isDirectory(target)) {
                return List.of();
            }
            try (var stream = Files.list(target)) {
                return stream.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .map(p -> Files.isDirectory(p)
                                ? p.getFileName() + "/" : p.getFileName().toString())
                        .toList();
            } catch (IOException ex) {
                log.warn("列出 workspace 文件失败：{}", target, ex);
                return List.of();
            }
        } finally {
            instanceManager.release(entry);
        }
    }

    @Override
    public String readWorkspaceFile(AgentRuntimeConfig config, String relativePath) {
        InstanceEntry entry = instanceManager.acquire(config);
        try {
            WorkspaceManager ws = entry.agent().workspaceFor(config.getUserId(), config.getSessionKey());
            Path root = ws.getWorkspace();
            Path target = resolveWithin(root, relativePath);
            if (target == null || !Files.isRegularFile(target)) {
                return null;
            }
            try {
                return Files.readString(target);
            } catch (IOException ex) {
                log.warn("读取 workspace 文件失败：{}", target, ex);
                return null;
            }
        } finally {
            instanceManager.release(entry);
        }
    }

    @Override
    public List<Map<String, Object>> loadSessionMessages(AgentRuntimeConfig config) {
        InstanceEntry entry = instanceManager.acquire(config);
        try {
            var store = entry.agent().getStateStore();
            if (store == null) {
                return List.of();
            }
            // 恢复链路读槽位持久化的 AgentState（对话上下文）：消息历史经 getContext() 取
            // Msg 列表，逐个转 JSON Map 快照返回（应用层/前端渲染历史消息）
            return store.get(config.getUserId(), config.getSessionKey(),
                    AgentStateKeys.CONTEXT_KEY, io.agentscope.core.state.AgentState.class)
                    .map(state -> state.getContext().stream()
                            .map(msg -> msgToMap(msg))
                            .toList())
                    .orElseGet(List::of);
        } catch (Exception ex) {
            log.warn("加载会话上下文失败：{}/{}", config.getUserId(), config.getSessionKey(), ex);
            return List.of();
        } finally {
            instanceManager.release(entry);
        }
    }

    @Override
    @PreDestroy
    public void closeAll() {
        instanceManager.closeAll();
    }

    // ------------------------------------------------------------------
    //  装配翻译（零子类）：AgentSpec 四层配置 → HarnessAgent.Builder
    // ------------------------------------------------------------------

    /**
     * 按装配指令构建 HarnessAgent（实例管理器回调）。
     * 四层配置逐层翻译：agent 层（模型/系统提示/迭代上限）、模型调用层（GenerateOptions）、
     * 执行环境层（workspace/沙箱/能力→镜像）、挂载层（技能目录→文件仓库、敏感名单→permission ASK、
     * 工具挂载→Toolkit 注册）。模型经 ModelRegistry 按名注册解析（注册一次，重建刷新）。
     *
     * @return 装配结果（agent 实例 + 需随实例善后关闭的 MCP client 清单——框架不级联，平台自管）
     */
    private AssembledAgent assemble(AgentRuntimeConfig config) {
        String registryName = registerModel(config);
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(config.getAgentName())
                .agentId(config.getAgentId())
                .model(registryName)
                .defaultSessionId(config.getSessionKey())
                .stateStore(distributedStore().agentStateStore());

        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            builder.sysPrompt(config.getSystemPrompt());
        }
        if (config.getMaxIters() != null) {
            builder.maxIters(config.getMaxIters());
        }
        if (config.getGenerateOptions() != null) {
            builder.generateOptions(toAgentscopeOptions(config.getGenerateOptions()));
        }

        // 可观测预埋（工单 14，ADR-0001 直用框架）：OTel 三段 span（agent 调用/模型调用/工具执行）
        // 恒挂——无全局 OTel SDK 时全部 no-op 近零开销，SDK 导出器与采样经 otel.* 标准属性配置；
        // 平台自建采集（审计 onActing / 用量 onModelCall，宪法内自建）经注入列表按配置装配
        builder.middleware(new io.agentscope.core.tracing.OtelTracingMiddleware());
        runtimeCollectorMiddlewares.forEach(builder::middleware);

        // 技能挂载目录 → 文件仓库 + 名单收敛（工单 12）：仓库按物化基目录读取，
        // SkillFilter.only 使仅挂载技能对模型可见（目录下其余技能与 workspace/skills 层一并滤除）
        if (!config.getSkillMounts().isEmpty()) {
            List<io.agentscope.core.skill.repository.AgentSkillRepository> repositories =
                    config.getSkillMounts().stream()
                            .<io.agentscope.core.skill.repository.AgentSkillRepository>map(
                                    mount -> new FileSystemSkillRepository(
                                            Path.of(mount.getBaseDir()), false))
                            .toList();
            builder.skillRepositories(repositories);
            List<String> mountedNames = config.getSkillMounts().stream()
                    .flatMap(mount -> mount.getSkillNames().stream()).distinct().toList();
            builder.skillFilter(SkillFilter.only(mountedNames.toArray(String[]::new)));
        }

        // 敏感工具 → permission ASK（HITL 触发源，工单 09）：名单内工具调用前挂起等人工审批
        List<PermissionRule> askRules = sensitiveRules(config);
        if (!askRules.isEmpty()) {
            PermissionContextState.Builder permissionBuilder = PermissionContextState.builder()
                    .mode(PermissionMode.DEFAULT);
            askRules.forEach(rule -> permissionBuilder.addAskRule(rule.toolName(), rule));
            builder.permissionContext(permissionBuilder.build());
        }

        ExecutionEnvConfig env = config.getExecutionEnv();
        boolean workspaceEnabled = env != null && env.isWorkspaceEnabled();
        if (workspaceEnabled) {
            Path workspace = ensureWorkspace(config);
            materializeAgentsMd(workspace, config.getSystemPrompt());
            materializeFolders(workspace, config);
            builder.workspace(workspace);
            if (env.isSandboxEnabled()) {
                DockerFilesystemSpec sandboxSpec = new DockerFilesystemSpec()
                        .image(resolveSandboxImage(env.getCapabilities()))
                        .workspaceRoot(SANDBOX_WORKSPACE_ROOT);
                sandboxSpec.isolationScope(isolationScope(config.getOwnerLevel()));
                builder.filesystem(sandboxSpec);
            } else {
                // 本地模式：workspace 可读写、项目目录只读；无沙箱禁执行能力（ExecutionEnvConfig 已校验）
                LocalFilesystemSpec localSpec = new LocalFilesystemSpec()
                        .project(workspace)
                        .isolationScope(isolationScope(config.getOwnerLevel()));
                builder.filesystem(localSpec).disableShellTool();
            }
        } else {
            // 纯对话智能体：零落盘，无文件与执行工具（ExecutionEnvConfig 校验链保证）
            builder.disableFilesystemTools().disableShellTool();
        }

        HarnessAgent agent = builder.build();
        // 平台内置工具收敛：web 工具（web_fetch/web_search）租户智能体默认不开放
        for (String tool : REMOVED_BUILTIN_TOOLS) {
            try {
                agent.getToolkit().removeTool(tool);
            } catch (Exception ex) {
                log.debug("移除内置工具 {} 失败（可能未注册）：{}", tool, ex.getMessage());
            }
        }
        // 挂载层工具注册（工单 13）：MCP 三传输（白名单收敛）+ 平台工具库条目
        List<McpClientWrapper> mcpClients = registerMountedTools(agent.getToolkit(), config);
        // 运行时工具贡献者：装配时注册一次（测试接缝——敏感工具经 permission ASK 规则挂起）
        for (RuntimeToolContributor contributor : runtimeToolContributors) {
            contributor.contribute(agent.getToolkit());
        }
        return new AssembledAgent(agent, mcpClients);
    }

    /**
     * 工具挂载 → Toolkit 注册（工单 13）：
     * MCP 挂载经 {@code McpClientBuilder} 三传输建连 + {@code enableTools} 白名单收敛
     * （挂载白名单优先，空则 server 级白名单，再空 = 全部）；平台工具库挂载经注册表寻条目注册 +
     * 白名单外工具移除。<b>运行性缺失（MCP 不可达/超时）降级跳过该挂载（warn 日志），装配不阻断；
     * 返回的 MCP client 清单由实例条目持有，随实例善后关闭。</b>
     */
    private List<McpClientWrapper> registerMountedTools(Toolkit toolkit, AgentRuntimeConfig config) {
        List<McpClientWrapper> mcpClients = new ArrayList<>();
        for (ToolMount mount : config.getTools()) {
            if (mount.getSource() == ToolSource.MCP) {
                registerMcpMount(toolkit, config, mount, mcpClients);
            } else if (mount.getSource() == ToolSource.PLATFORM) {
                registerPlatformMount(toolkit, mount);
            }
        }
        return mcpClients;
    }

    /** MCP 挂载注册：建连失败/超时（运行性缺失）降级跳过 */
    private void registerMcpMount(Toolkit toolkit, AgentRuntimeConfig config, ToolMount mount,
                                  List<McpClientWrapper> mcpClients) {
        McpServer server = config.getMcpServers().stream()
                .filter(candidate -> candidate.getId().equals(mount.getSourceId()))
                .findFirst().orElse(null);
        if (server == null) {
            // 应用层已做配置性校验，此处防御性跳过（不阻断装配）
            log.warn("MCP 挂载 {} 未在装配指令中解析出 Server，跳过", mount.getSourceId());
            return;
        }
        McpClientWrapper client = null;
        try {
            Duration timeout = connectTimeout(server);
            client = AgentscopeMcpServerGateway.buildClient(server, timeout).buildSync();
            // 白名单收敛：挂载白名单优先；空则 server 级白名单；再空（= null）= 该 server 全部工具
            List<String> enableTools = !mount.getAllowedTools().isEmpty()
                    ? mount.getAllowedTools()
                    : !server.getAllowedTools().isEmpty() ? server.getAllowedTools() : null;
            // registration().apply() 内部完成 initialize + listTools + 过滤注册（阻塞到完成）
            toolkit.registration().mcpClient(client).enableTools(enableTools).apply();
            mcpClients.add(client);
        } catch (Exception ex) {
            log.warn("MCP 工具挂载不可用，降级跳过（server={}, sourceId={}）：{}",
                    server.getName(), mount.getSourceId(), rootMessage(ex), ex);
            closeMcpQuietly(client);
        }
    }

    /**
     * 平台工具库挂载注册：注册表寻条目 → @Tool 注册 → 白名单外工具移除；条目缺失降级跳过。
     *
     * <p><b>已知边界</b>：白名单收敛按全局工具名 removeTool——若其他挂载（MCP/另一平台条目）
     * 提供同名工具且恰在本条目白名单外，会被一并移除。MVP 以 snake_case 工具名的命名空间
     * 实践规避；彻底隔离（每挂载独立工具组）随 ToolGroupManager 组化收敛后置。</p>
     */
    private void registerPlatformMount(Toolkit toolkit, ToolMount mount) {
        if (platformToolRegistry == null) {
            log.warn("平台工具库注册表未装配（测试上下文？），挂载 {} 跳过", mount.getSourceId());
            return;
        }
        PlatformToolEntry entry = platformToolRegistry.findById(mount.getSourceId());
        if (entry == null) {
            log.warn("平台工具库条目 {} 不存在（部署版本间条目增删？），挂载跳过", mount.getSourceId());
            return;
        }
        toolkit.registerTool(entry.getToolInstance());
        if (!mount.getAllowedTools().isEmpty()) {
            for (String toolName : entry.getToolNames()) {
                if (!mount.getAllowedTools().contains(toolName)) {
                    try {
                        toolkit.removeTool(toolName);
                    } catch (Exception ex) {
                        log.debug("移除白名单外工具 {} 失败（可能未注册）：{}", toolName, ex.getMessage());
                    }
                }
            }
        }
    }

    /** MCP 连接超时：server 自配超时与上限取小（运行性缺失及时止损） */
    private static Duration connectTimeout(McpServer server) {
        return RootCauses.minTimeout(server.getTimeoutSeconds(), MCP_CONNECT_TIMEOUT_CAP);
    }

    private static void closeMcpQuietly(McpClientWrapper client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ex) {
                log.debug("关闭 MCP client 失败（忽略）：{}", ex.getMessage());
            }
        }
    }

    /** 渠道/模型 → ModelRegistry 命名注册（租户:渠道:模型），返回注册名 */
    private String registerModel(AgentRuntimeConfig config) {
        String registryName = String.format("nexai:t%s:m%s",
                config.getChannel().getId(), config.getModel().getId());
        ModelRegistry.register(registryName,
                chatModelFactory.create(config.getChannel(), config.getModel().getModelId()));
        return registryName;
    }

    /** 模型调用参数翻译：领域 GenerateOptions → agentscope GenerateOptions */
    private static io.agentscope.core.model.GenerateOptions toAgentscopeOptions(
            GenerateOptions options) {
        io.agentscope.core.model.GenerateOptions.Builder builder =
                io.agentscope.core.model.GenerateOptions.builder();
        if (options.getTemperature() != null) {
            builder.temperature(options.getTemperature());
        }
        if (options.getTopP() != null) {
            builder.topP(options.getTopP());
        }
        if (options.getMaxTokens() != null) {
            builder.maxTokens(options.getMaxTokens());
        }
        return builder.build();
    }

    /** 敏感工具名单 → PermissionRule(name, ASK) 列表（HITL 触发源，工单 09）：
     *  工具挂载的 sensitiveTools 名单内工具调用前挂起等人工审批；
     *  白名单非空时名单为其子集（ToolMount 已校验）。 */
    private static List<PermissionRule> sensitiveRules(AgentRuntimeConfig config) {
        return config.getTools().stream()
                .flatMap(mount -> mount.getSensitiveTools().stream())
                .distinct()
                .map(tool -> new PermissionRule(tool, null, PermissionBehavior.ASK, "nexai"))
                .toList();
    }

    /** 沙箱能力 → 镜像查表（服务级 yaml 配置）；未配置的能力缺省 SHELL 通用执行镜像 */
    private String resolveSandboxImage(List<ExecutionCapability> capabilities) {
        if (capabilities != null) {
            for (ExecutionCapability capability : capabilities) {
                String image = runtimeProperties.getSandboxImages().get(capability.name());
                if (image != null && !image.isBlank()) {
                    return image;
                }
            }
        }
        return runtimeProperties.getSandboxImages()
                .getOrDefault("SHELL", DEFAULT_SANDBOX_IMAGE);
    }

    /** workspace 归属层级布局：t{tenantId}/u{userId} 下挂 {specCode}（spec 调研结论，根路径层级） */
    private Path ensureWorkspace(AgentRuntimeConfig config) {
        Path root = runtimeProperties.getWorkspace().resolvedRoot();
        long tenantId = config.getTenantId();
        Path path = switch (config.getOwnerLevel()) {
            case PLATFORM -> root.resolve("platform").resolve(config.getAgentId());
            case TENANT -> root.resolve("t" + tenantId).resolve(config.getAgentId());
            case USER -> root.resolve("t" + tenantId)
                    .resolve("u" + config.getOwnerUserId()).resolve(config.getAgentId());
        };
        try {
            Files.createDirectories(path);
            return path;
        } catch (IOException ex) {
            throw new IllegalStateException("创建 workspace 目录失败：" + path, ex);
        }
    }

    /** AGENTS.md 物化 = DB 快照唯一权威源 + 本地物化缓存 + 装配时内容比对（工单 07） */
    private void materializeAgentsMd(Path workspace, String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return;
        }
        Path agentsMd = workspace.resolve("AGENTS.md");
        try {
            if (Files.exists(agentsMd) && systemPrompt.equals(Files.readString(agentsMd))) {
                return; // 内容相同跳过覆写（物化缓存命中）
            }
            Files.writeString(agentsMd, systemPrompt); // 不同则覆写（DB 快照为唯一权威源）
        } catch (IOException ex) {
            throw new IllegalStateException("AGENTS.md 物化失败：" + agentsMd, ex);
        }
    }

    /**
     * 规格私有文件夹物化（工单 18）：ASSET → workspace {@code knowledge/<name>/}（agentscope
     * 原生预留位）、TOOLSET → {@code toolsets/<name>/}（自定义目录，不占框架硬编码路径）。
     * 版本快照清单为唯一权威源，装配期按 contentHash 内容比对：磁盘内容一致跳过覆写
     * （物化缓存命中）；存储对象与清单哈希不符（FileApi 后端对象被覆盖）或对象缺失时
     * <b>显式报错</b>，不静默沿用旧物化。文件可被智能体经文件工具直读
     * （TOOLSET 脚本执行受既有沙箱能力链约束，挂载本身不强制开沙箱）。
     */
    private void materializeFolders(Path workspace, AgentRuntimeConfig config) {
        for (FolderMount folder : config.getFolders()) {
            Path dir = workspace.resolve(folder.targetSegment()).resolve(folder.getName());
            for (FolderFile file : folder.getFiles()) {
                Path target = resolveWithin(dir, file.path());
                if (target == null) {
                    throw new IllegalStateException(
                            "文件夹挂载路径逃逸：" + folder.getName() + "/" + file.path());
                }
                try {
                    if (Files.exists(target)
                            && file.contentHash().equals(sha256Hex(Files.readAllBytes(target)))) {
                        continue; // 内容一致跳过覆写（物化缓存命中）
                    }
                    if (fileApi == null) {
                        throw new IllegalStateException(
                                "文件存储 API 未装配，无法物化文件夹：" + folder.getName());
                    }
                    byte[] content = fileApi.getFileContent(file.url());
                    if (content == null) {
                        throw new IllegalStateException(
                                "文件夹挂载的存储对象缺失：" + file.url());
                    }
                    if (!file.contentHash().equals(sha256Hex(content))) {
                        throw new IllegalStateException("文件夹挂载内容哈希不匹配（存储对象已被覆盖？）："
                                + folder.getName() + "/" + file.path());
                    }
                    Files.createDirectories(target.getParent());
                    Files.write(target, content);
                } catch (IOException ex) {
                    throw new IllegalStateException("文件夹物化失败：" + target, ex);
                }
            }
        }
    }

    private static String sha256Hex(byte[] content) {
        return Hashes.sha256Hex(content);
    }

    private IsolationScope isolationScope(OwnerLevel ownerLevel) {
        // 归属级隔离：用户级规格按 AGENT（同一规格的会话共享 workspace 根，无额外 uid 前缀层——
        // 平台路径已含 u{userId} 段）；租户/平台级按 USER（会话间按用户命名空间隔离）
        return ownerLevel == OwnerLevel.USER ? IsolationScope.AGENT : IsolationScope.USER;
    }

    // ------------------------------------------------------------------
    //  HITL 三态翻译
    // ------------------------------------------------------------------

    private static ConfirmResult toConfirmResult(ToolCallDecision decision) {
        ToolUseBlock block = ToolUseBlock.builder()
                .id(decision.toolCallId())
                .name(decision.toolName())
                .input(parseArguments(decision.argumentsJson()))
                .build();
        return new ConfirmResult(decision.approved(), block);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = JsonUtils.getJsonCodec().fromJson(argumentsJson, Map.class);
            return parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /** Msg → JSON Map 快照（恢复历史用；agentscope codec 序列化后按 Map 读取） */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> msgToMap(Msg msg) {
        Object parsed = JsonUtils.getJsonCodec().fromJson(
                JsonUtils.getJsonCodec().toJson(msg), Map.class);
        return parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    // ------------------------------------------------------------------
    //  事件流翻译
    // ------------------------------------------------------------------

    private RuntimeEvent toRuntimeEvent(AgentEvent event) {
        RuntimeEventType type = toRuntimeType(event.getType());
        String payload = JsonUtils.getJsonCodec().toJson(event);
        return RuntimeEvent.of(type, payload);
    }

    private static RuntimeEventType toRuntimeType(AgentEventType type) {
        return switch (type) {
            case AGENT_START -> RuntimeEventType.AGENT_START;
            case AGENT_END -> RuntimeEventType.AGENT_END;
            case AGENT_RESULT -> RuntimeEventType.AGENT_RESULT;
            case MODEL_CALL_START -> RuntimeEventType.MODEL_CALL_START;
            case MODEL_CALL_END -> RuntimeEventType.MODEL_CALL_END;
            case TEXT_BLOCK_START -> RuntimeEventType.TEXT_BLOCK_START;
            case TEXT_BLOCK_DELTA -> RuntimeEventType.TEXT_BLOCK_DELTA;
            case TEXT_BLOCK_END -> RuntimeEventType.TEXT_BLOCK_END;
            case THINKING_BLOCK_START -> RuntimeEventType.THINKING_BLOCK_START;
            case THINKING_BLOCK_DELTA -> RuntimeEventType.THINKING_BLOCK_DELTA;
            case THINKING_BLOCK_END -> RuntimeEventType.THINKING_BLOCK_END;
            case DATA_BLOCK_START -> RuntimeEventType.DATA_BLOCK_START;
            case DATA_BLOCK_DELTA -> RuntimeEventType.DATA_BLOCK_DELTA;
            case DATA_BLOCK_END -> RuntimeEventType.DATA_BLOCK_END;
            case TOOL_CALL_START -> RuntimeEventType.TOOL_CALL_START;
            case TOOL_CALL_DELTA -> RuntimeEventType.TOOL_CALL_DELTA;
            case TOOL_CALL_END -> RuntimeEventType.TOOL_CALL_END;
            case TOOL_RESULT_START -> RuntimeEventType.TOOL_RESULT_START;
            case TOOL_RESULT_TEXT_DELTA -> RuntimeEventType.TOOL_RESULT_TEXT_DELTA;
            case TOOL_RESULT_DATA_DELTA -> RuntimeEventType.TOOL_RESULT_DATA_DELTA;
            case TOOL_RESULT_END -> RuntimeEventType.TOOL_RESULT_END;
            case REQUIRE_USER_CONFIRM -> RuntimeEventType.REQUIRE_USER_CONFIRM;
            case USER_CONFIRM_RESULT -> RuntimeEventType.USER_CONFIRM_RESULT;
            case EXCEED_MAX_ITERS -> RuntimeEventType.EXCEED_MAX_ITERS;
            case REQUEST_STOP -> RuntimeEventType.REQUEST_STOP;
            case ALL_TOOLS_DENIED -> RuntimeEventType.ALL_TOOLS_DENIED;
            case CUSTOM -> RuntimeEventType.CUSTOM;
            // 平台暂不消费的框架事件类型：外部执行/子代理暴露/提示块统一透传为 CUSTOM
            case REQUIRE_EXTERNAL_EXECUTION, EXTERNAL_EXECUTION_RESULT,
                    SUBAGENT_EXPOSED, HINT_BLOCK -> RuntimeEventType.CUSTOM;
        };
    }

    private static RuntimeEvent errorEvent(String message) {
        String payload = JsonUtils.getJsonCodec().toJson(Map.of(
                "type", ERROR_EVENT_TYPE, "message", message));
        return RuntimeEvent.of(RuntimeEventType.SESSION_ERROR, payload);
    }

    // ------------------------------------------------------------------
    //  RuntimeContext 组装（spec：tenantId/sessionKey/agentId 走 extra，userId 独立字段）
    // ------------------------------------------------------------------

    private RuntimeContext runtimeContext(AgentRuntimeConfig config) {
        // specId/versionNo 供审计与用量 middleware 取规格维度（工单 14；维度键常量两端共用）
        return RuntimeContext.builder()
                .sessionId(config.getSessionKey())
                .userId(config.getUserId())
                .put(RuntimeContextKeys.TENANT_ID, config.getTenantId())
                .put(RuntimeContextKeys.SESSION_KEY, config.getSessionKey())
                .put(RuntimeContextKeys.AGENT_ID, config.getAgentId())
                .put(RuntimeContextKeys.SPEC_ID, config.getSpecId())
                .put(RuntimeContextKeys.VERSION_NO, config.getVersionNo())
                .build();
    }

    /** 路径越界防护：相对路径解析到 workspace 根内，逃逸返回 null */
    private static Path resolveWithin(Path root, String relativePath) {
        Path resolved = (relativePath == null || relativePath.isBlank())
                ? root
                : root.resolve(relativePath).normalize();
        return resolved.startsWith(root) ? resolved : null;
    }

    private static String rootMessage(Throwable ex) {
        return RootCauses.rootMessage(ex, ROOT_MESSAGE_MAX_LENGTH);
    }

    // ------------------------------------------------------------------
    //  常驻实例注册表（工单 05 基座 + 工单 08 版本戳失效）
    // ------------------------------------------------------------------

    /** agentscope 槽位状态键（与框架约定一致） */
    private interface AgentStateKeys {
        String CONTEXT_KEY = "agent_state";
    }

    /**
     * 常驻实例条目：agent 实例 + 挂载建连的 MCP client + 引用计数 + 版本戳。acquire 递增计数
     * （活动流持有），release 递减；引用归零且已失效（版本戳变化被替换）时善后 close。
     * close 释放后台资源（转录镜像排空/TaskRepository/workspace 索引，HarnessAgent#close），
     * MCP client 框架不级联关闭，由本条目一并善后（工单 13）。
     */
    private static final class InstanceEntry {
        private final HarnessAgent agent;
        private final List<McpClientWrapper> mcpClients;
        private final String key;
        private final AtomicInteger refs = new AtomicInteger();
        private volatile String stamp;
        private volatile boolean stale;

        InstanceEntry(AssembledAgent assembled, String key, String stamp) {
            this.agent = assembled.agent();
            this.mcpClients = assembled.mcpClients();
            this.key = key;
            this.stamp = stamp;
        }

        HarnessAgent agent() {
            return agent;
        }
    }

    /** 装配结果：agent 实例 + 需随实例善后关闭的 MCP client 清单 */
    private record AssembledAgent(HarnessAgent agent, List<McpClientWrapper> mcpClients) {
    }

    /**
     * 常驻实例管理器：按「规格 + 版本号」（specReference）缓存，版本戳变化时失效重建。
     *
     * <p>快路径/回退分流（工单 08）：MVP 调试会话一律按「默认版本 + 无覆盖」构造装配指令，
     * 命中 {@code specId:versionNo} 缓存即快路径复用常驻实例；装配参数覆盖（非默认版本/
     * 执行环境不符）由应用层构造不同 specReference，自然回落 per-spec 装配——本管理器
     * 不感知分流细节，只按 specReference 缓存并做版本戳失效。</p>
     *
     * <p>版本戳 = 渠道/模型/MCP Server 更新时间 + 技能挂载指纹（skillId@versionNo）+
     * 规格引用（任一变化即失效）：渠道/模型配置热更、MCP Server 配置变更、技能推新版本、
     * 规格发布/切版本，都使下一次调用重建实例（新会话即用新配置，工单 08/12/13）。</p>
     */
    private final class AgentInstanceManager {

        private final Map<String, InstanceEntry> instances = new ConcurrentHashMap<>();

        /** 装配指令 → 版本戳（渠道/模型/MCP 更新时间 + 技能/文件夹指纹 + 规格引用），任一变化即失效 */
        private String versionStamp(AgentRuntimeConfig config) {
            long channelStamp = config.getChannel().getUpdateTime() == null
                    ? 0L : config.getChannel().getUpdateTime().hashCode();
            long modelStamp = config.getModel().getUpdateTime() == null
                    ? 0L : config.getModel().getUpdateTime().hashCode();
            long mcpStamp = config.getMcpServers().stream()
                    .mapToLong(server -> server.getUpdateTime() == null
                            ? 0L : server.getUpdateTime().hashCode()).sum();
            String skillStamp = config.getSkillMounts().stream()
                    .map(SkillMountDirectory::getFingerprint)
                    .reduce("", String::concat);
            // 文件夹挂载经版本发布自然纳入失效（清单固化进版本快照），指纹兜底同版本号下的清单比对
            String folderStamp = config.getFolders().stream()
                    .map(FolderMount::fingerprint)
                    .reduce("", String::concat);
            return config.specReference() + ":" + channelStamp + ":" + modelStamp
                    + ":" + mcpStamp + ":" + skillStamp.hashCode() + ":" + folderStamp.hashCode();
        }

        /** 获取（或构建）实例并持有引用；调用方必须配对 release */
        InstanceEntry acquire(AgentRuntimeConfig config) {
            String key = config.specReference();
            String stamp = versionStamp(config);
            InstanceEntry existing = instances.get(key);
            if (existing != null && !existing.stale && Objects.equals(existing.stamp, stamp)) {
                existing.refs.incrementAndGet();
                return existing;
            }
            // 无实例、已失效（stale）或版本戳不一致：构建新实例（版本戳失效重建），
            // put 无条件替换——旧条目标记失效，引用归零后善后 close
            InstanceEntry fresh = new InstanceEntry(assemble(config), key, stamp);
            InstanceEntry replaced = instances.put(key, fresh);
            fresh.refs.incrementAndGet();
            if (replaced != null) {
                replaced.stale = true;
                closeIfIdle(replaced);
            }
            return fresh;
        }

        /** 仅查看（不持有引用）：中断定位用，实例不存在返回 null */
        InstanceEntry peek(AgentRuntimeConfig config) {
            return instances.get(config.specReference());
        }

        /** 释放引用；已失效（版本戳变化被替换）且引用归零时善后 close */
        void release(InstanceEntry entry) {
            if (entry.refs.decrementAndGet() == 0 && entry.stale) {
                instances.remove(entry.key, entry);
                closeQuietly(entry);
            }
        }

        /** 被替换的旧实例若已无引用，立即善后 close（幂等：release 路径也会处理） */
        private void closeIfIdle(InstanceEntry entry) {
            if (entry.refs.get() == 0) {
                instances.remove(entry.key, entry);
                closeQuietly(entry);
            }
        }

        void closeAll() {
            instances.values().forEach(AgentscopeRuntimeGateway.this::closeQuietly);
            instances.clear();
        }
    }

    private void closeQuietly(InstanceEntry entry) {
        try {
            entry.agent.close();
        } catch (Exception ex) {
            log.warn("关闭常驻实例失败：{}", entry.agent.getName(), ex);
        }
        // MCP client 不随 agent.close 级联关闭（框架边界），平台自管善后
        entry.mcpClients.forEach(AgentscopeRuntimeGateway::closeMcpQuietly);
    }

    private DistributedStore distributedStore() {
        return PostgresDistributedStore.create(dataSource);
    }
}
