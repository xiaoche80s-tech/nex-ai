package com.gkht.ai.nexai.module.ai.session.infrastructure.gateway;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionCapability;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionEnvConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.GenerateOptions;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.gateway.ChatModelFactory;
import com.gkht.ai.nexai.module.ai.framework.config.AiRuntimeProperties;
import com.gkht.ai.nexai.module.ai.session.domain.gateway.AgentRuntimeGateway;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEventType;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.ToolCallDecision;
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
import io.agentscope.core.util.JsonUtils;
import io.agentscope.extensions.postgresql.PostgresDistributedStore;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * 版本戳（渠道/模型更新时间 + 规格当前版本号）变化时失效重建（工单 08），旧实例按引用计数善后
 * close。同 (userId, sessionId) 的并发调用由框架槽位门排队（FIFO），不同会话并行。</p>
 *
 * <p><b>模型注册</b>：{@code ModelRegistry} 为 JVM 级静态表，实例构建时按「租户:渠道:模型」注册
 * 一次；模型/密钥变更经版本戳失效重建时重新注册覆盖（进程内全局生效，重建即刷新）。</p>
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

    @Resource
    private AiRuntimeProperties runtimeProperties;

    @Resource
    private DataSource dataSource;

    @Resource
    private ChatModelFactory chatModelFactory;

    /** 运行时工具贡献者（测试注入敏感工具/回显工具；生产挂载工单 12/13 经此接线） */
    @Resource
    private List<RuntimeToolContributor> runtimeToolContributors = List.of();

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
     * 执行环境层（workspace/沙箱/能力→镜像）、挂载层（敏感名单→permission ASK）。
     * 模型经 ModelRegistry 按名注册解析（注册一次，重建刷新）。
     */
    private HarnessAgent assemble(AgentRuntimeConfig config) {
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
        // 运行时工具贡献者：装配时注册一次（敏感工具经 permission ASK 规则挂起，HITL 触发源）
        for (RuntimeToolContributor contributor : runtimeToolContributors) {
            contributor.contribute(agent.getToolkit());
        }
        return agent;
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
        return RuntimeContext.builder()
                .sessionId(config.getSessionKey())
                .userId(config.getUserId())
                .put("tenantId", config.getTenantId())
                .put("sessionKey", config.getSessionKey())
                .put("agentId", config.getAgentId())
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
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : current.getClass().getSimpleName() + ": " + message;
    }

    // ------------------------------------------------------------------
    //  常驻实例注册表（工单 05 基座 + 工单 08 版本戳失效）
    // ------------------------------------------------------------------

    /** agentscope 槽位状态键（与框架约定一致） */
    private interface AgentStateKeys {
        String CONTEXT_KEY = "agent_state";
    }

    /**
     * 常驻实例条目：agent 实例 + 引用计数 + 版本戳。acquire 递增计数（活动流持有），release
     * 递减；引用归零且已失效（版本戳变化被替换）时善后 close。close 释放后台资源
     * （转录镜像排空/TaskRepository/workspace 索引，HarnessAgent#close）。
     */
    private static final class InstanceEntry {
        private final HarnessAgent agent;
        private final String key;
        private final AtomicInteger refs = new AtomicInteger();
        private volatile String stamp;
        private volatile boolean stale;

        InstanceEntry(HarnessAgent agent, String key, String stamp) {
            this.agent = agent;
            this.key = key;
            this.stamp = stamp;
        }

        HarnessAgent agent() {
            return agent;
        }
    }

    /**
     * 常驻实例管理器：按「规格 + 版本号」（specReference）缓存，版本戳变化时失效重建。
     *
     * <p>快路径/回退分流（工单 08）：MVP 调试会话一律按「默认版本 + 无覆盖」构造装配指令，
     * 命中 {@code specId:versionNo} 缓存即快路径复用常驻实例；装配参数覆盖（非默认版本/
     * 执行环境不符）由应用层构造不同 specReference，自然回落 per-spec 装配——本管理器
     * 不感知分流细节，只按 specReference 缓存并做版本戳失效。</p>
     *
     * <p>版本戳 = 渠道更新时间 + 模型更新时间 + 规格当前版本号（三源任一变化即失效）；
     * 装配指令携带的版本戳与缓存条目比对，不一致时重建新实例（新会话即用新配置），
     * 旧实例引用归零后 close。</p>
     */
    private final class AgentInstanceManager {

        private final Map<String, InstanceEntry> instances = new ConcurrentHashMap<>();

        /** 装配指令 → 版本戳（渠道更新时间 + 模型更新时间 + 规格引用），三源任一变化即失效 */
        private String versionStamp(AgentRuntimeConfig config) {
            long channelStamp = config.getChannel().getUpdateTime() == null
                    ? 0L : config.getChannel().getUpdateTime().hashCode();
            long modelStamp = config.getModel().getUpdateTime() == null
                    ? 0L : config.getModel().getUpdateTime().hashCode();
            return config.specReference() + ":" + channelStamp + ":" + modelStamp;
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
    }

    private DistributedStore distributedStore() {
        return PostgresDistributedStore.create(dataSource);
    }
}
