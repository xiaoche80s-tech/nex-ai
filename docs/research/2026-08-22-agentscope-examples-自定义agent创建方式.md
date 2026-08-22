# agentscope-examples 示例调研：用户自定义 Agent 的创建方式

> 调研日期：2026-08-22
> 调研对象：本机 `~/agent-project/agentscope-java/agentscope-examples`（GitHub agentscope-ai/agentscope-java，main 分支，主干 2.0.3-SNAPSHOT），必要时对照 `agentscope-core`、`agentscope-harness` 源码。
> 调研纪律：**只采信 v2 主干代码**；`docs/v1/`、README 历史段落未作为论据。示例源码注释中的 "Migration notes" 是 v2 主干代码的一部分（描述 v1→v2 迁移），仅取其 v2 侧结论。
> 引用约定：`~/agent-project/agentscope-java/` 简写为 `<AS>`；行号以当前工作区文件为准。

---

## TL;DR：示例里"创建一个自定义 agent"共有 6 种方式（由轻到重）

| # | 方式 | 代表示例/工程 | 一句话 |
|---|---|---|---|
| 1 | **Builder 最小编程创建** | `BasicChatExample` | `ReActAgent.builder().name().sysPrompt().model("dashscope:qwen-plus").build()`，5 行得到一个能对话的 agent |
| 2 | **组件级定制**（不写子类） | documentation 各子包 | 在 Builder 上叠加 sysPrompt / model / toolkit（@Tool、ToolGroup、MCP）/ skillRepository / middleware（5 个切点）/ permissionContext / stateStore / maxIters 等开关，全部通过组合完成定制 |
| 3 | **换增强基类 HarnessAgent** | `UserIsolatedMultiTurnsExample`、codingagent、paw、dataagent | `HarnessAgent.builder()` 在 ReActAgent 之上加 workspace、记忆压缩、subagent、沙箱文件系统，仍是纯 Builder 组合 |
| 4 | **声明式配置文件** | dataagent / codingagent / paw 的 `agentscope.json` | `Bootstrap` 读 JSON → `applyFileEntry()` 逐字段灌进 `HarnessAgent.Builder`，改配置不改代码即可增删 agent |
| 5 | **运行时用户自定义（平台级）** | dataagent 的 catalog 链路 | 用户 HTTP POST 一份 `AgentDefinition` → JPA 存储 → 首次对话时懒加载 `buildAndRegisterUca()` 动态构建 HarnessAgent 并注册进 gateway（`uca-{userId}-{agentId}`） |
| 6 | **注册表工厂（每请求新建）** | agui / copilotkit 的 `AguiAgentRegistryCustomizer` | 向 AG-UI registry 注册 `agentId → () -> Agent` 工厂，按 URL/Header 选择 agent，每次请求新鲜实例 |

核心结论：**v2 没有任何示例通过"继承 ReActAgent/写子类"来自定义 agent**。所有定制都通过 Builder 组合 + Middleware 洋葱链 + 声明式配置实现；dataagent 展示了把这套 Builder 变成"终端用户可操作的多租户 agent 平台"的完整参考实现。

---

## 1. examples 模块总览

`<AS>/agentscope-examples/pom.xml` 聚合 5 个子工程：

| 子工程 | 性质 | 演示内容 |
|---|---|---|
| `documentation/`（包 `io.agentscope.examples.documentation2`） | 52 个可独立 `mvn exec:java` 运行的单类示例 | 按 quickstart / tool / middleware / skill / mcp / streaming / hitl / state / model / structuredoutput / multimodal / context / harness 十三个子包逐主题演示 core+extensions+harness 的用法 |
| `agents/agentscope-dataagent` | Spring Boot Web 完整应用（JWT 登录 + JPA + Docker 沙箱 + 前端） | **多租户"用户自定义 agent"平台**：agent 目录/克隆/分享/模板/AI 草稿、marketplace、webhook 通道、会话管理——本次调研重点 |
| `agents/agentscope-codingagent` | GitHub PR 审查机器人 | 双 agent（coding + reviewer）工厂装配、模型回退、预算中间件、SQLite 状态存储 |
| `agents/agentscope-paw` | dataagent 的姊妹版（个人版） | 与 dataagent 同构的 catalog 链路（无 `uca-` 命名空间前缀），多了 marketplace/Aistio 注册 |
| `agui`、`agentscope-copilotkit` | AG-UI 协议 Web 示例 | 多 agent 注册表、自定义事件转换、HITL 中断在 AG-UI/CopilotKit 前端的呈现 |

documentation 各子包一览（均为 `src/main/java/io/agentscope/examples/documentation2/` 下）：

- `quickstart/`：`BasicChatExample`（最简流式聊天）、`UserIsolatedMultiTurnsExample`（HarnessAgent + 按 userId 隔离的多轮会话）
- `tool/`：`ToolCallingExample`（@Tool 注册）、`ToolGroupExample`（工具分组 + meta-tool）、`ToolCallingWithConverterExample`（参数转换器）、`ToolExecutionContextExample`（RuntimeContext 传 POJO 给工具）、`PermissionContextExample`（权限引擎）、`RoutingByToolCallsExample`（按工具调用路由）
- `middleware/`：`SystemPromptMiddlewareExample`（动态系统提示词）、`CustomizedMiddlewareExample`（全生命周期监控）、`ModelCallMiddlewareExample`（拦截模型 API 调用）
- `skill/`：`AgentSkillExample`（FileSystem 仓库）、`GitSkillRepositoryExample`（Git 仓库）、`SkillWithToolGroupExample`（skill 激活工具组）
- `mcp/`：`McpStdioExample` / `McpSseExample` / `McpStreamableHttpExample`（三种传输挂 MCP 工具）
- `hitl/`：`InterruptionExample`（打断）、`PermissionHITLExample`（权限引擎确认）、`HookStopAgentExample`（middleware 主动暂停）
- `state/`、`streaming/`、`structuredoutput/`、`multimodal/`、`context/`、`model/`：状态持久化、事件流、结构化输出、视觉/多模态、RuntimeContext、模型注册表
- `harness/`：subagent（声明/流式/直发）、async（异步子 agent/工具）、channel（Gateway 多 agent）、memory（压缩）、planmode、workspace（本地/沙箱/共享存储）、skill 组合

---

## 2. 基础创建方式：ReActAgent.builder()

### 2.1 最简形态

`BasicChatExample` 是官方最简 agent——5 个 builder 方法 + streamEvents 即可交互（`<AS>/agentscope-examples/documentation/src/main/java/io/agentscope/examples/documentation2/quickstart/BasicChatExample.java:61-67`）：

```java
ReActAgent agent =
        ReActAgent.builder()
                .name("Assistant")
                .sysPrompt("You are a helpful AI assistant. Be friendly and concise.")
                .model("dashscope:qwen-plus")
                .toolkit(new Toolkit())
                .build();
```

要点：
- **model 用字符串**：`"dashscope:qwen-plus"` 走 `ModelRegistry` 自动解析 provider 并从环境变量读 API key（`BasicChatExample.java:31-33` 注释）。`ModelRegistryExample` 专门演示这一点：`ReActAgent.Builder.model(String)` 内部调 `ModelRegistry.resolve()`，换 provider 只改字符串（`.../model/ModelRegistryExample.java:99-107`）。
- **没有 memory / formatter**：v2 中 `.memory(new InMemoryMemory())` 已删除（各示例 Migration notes 一致注明 "Removed `.memory(new InMemoryMemory())` — not required in 2.0"，如 `.../tool/ToolCallingExample.java:36-39`）；会话上下文由 AgentState + stateStore 承担。

### 2.2 必填件与默认值（对照 core 源码）

`ReActAgent.Builder` 的字段与默认值见 `<AS>/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:4404-4482`：

```java
public static class Builder {
    String name;
    String description;
    String sysPrompt;
    Model model;
    Toolkit toolkit = new Toolkit();   // 默认空工具箱
    int maxIters = 10;                 // 默认 10 轮 ReAct
    ...
    private final List<MiddlewareBase> middlewares = new ArrayList<>();
    private boolean enableMetaTool = false;
    private AgentStateStore stateStore;
    private PermissionContextState permissionContext;
```

- **无强制必填校验**：Builder 不做 `Objects.requireNonNull(model)` 之类的检查，缺 model 时首次调用才会失败；示例们总是显式给 name/sysPrompt/model。
- **toolkit 有默认值**，但示例惯例是显式传入（便于先 `registerTool`）。
- **formatter 装在 model 上而不是 agent 上**：所有 DashScope 示例都是 `DashScopeChatModel.builder().formatter(new DashScopeChatFormatter())`（如 `.../middleware/SystemPromptMiddlewareExample.java:77-83`）。
- **maxIters** 可在 builder 上覆盖：`InterruptionExample.java:97` 的 `.maxIters(10)`。

### 2.3 build() 背后的机制（core）

`ReActAgent.Builder.build()`（`ReActAgent.java:5174-5225`）做四件对"自定义 agent"有直接影响的事：

```java
public ReActAgent build() {
    // Deep copy toolkit to avoid state interference between agents
    Toolkit agentToolkit = this.toolkit.copy();
    ...
    if (enableMetaTool) {
        agentToolkit.registerMetaTool();
    }
    ...
    if (!skillRepositories.isEmpty() && dynamicSkillsEnabled) {
        middlewares.add(
                new DynamicSkillMiddleware(
                        List.copyOf(skillRepositories),
                        agentToolkit,
                        skillFilter != null ? skillFilter : SkillFilter.all(),
                        skillCodeExecutionEnabled,
                        skillWorkDir));
    }
    // List.sort is stable: middlewares with equal order retain their registration order.
    middlewares.sort(Comparator.comparingInt(MiddlewareBase::order).reversed());
    ReActAgent agent = new ReActAgent(this, agentToolkit);
```

1. **深拷贝 toolkit**——同一个 Toolkit 对象可安全喂给多个 agent，互不串状态（`ReActAgent.java:5175-5176`）。
2. `enableMetaTool(true)` 时注入 `reset_equipped_tools` 元工具（`ReActAgent.java:5188-5190`）。
3. **`.skillRepository(...)` 不是简单挂仓库，而是自动安装一条 `DynamicSkillMiddleware`**，每次 call 重建 skill 提示并联动 SkillToolGroup（`ReActAgent.java:5208-5216`）。
4. middleware 按 `order()` 降序排序成洋葱链（`ReActAgent.java:5218-5219`）。

Builder 全量可选项还包括 `stateStore` / `defaultSessionId` / `generateOptions` / `permissionContext` / `fallbackModel` / `enableTaskList()` / `enablePendingToolRecovery` 等（方法清单见 `ReActAgent.java:4492-5025` 的 Builder 方法区），第 3 章按主题展开。

### 2.4 增强基类：HarnessAgent.builder()

`UserIsolatedMultiTurnsExample` 演示 v2 另一条装配线——`HarnessAgent`（`.../quickstart/UserIsolatedMultiTurnsExample.java:49-64`）：

```java
HarnessAgent agent =
        HarnessAgent.builder()
                .name("quickstart-agent")
                .sysPrompt("You are a note-taking assistant.")
                .model(model)
                .workspace(workspace)          // AGENTS.md / MEMORY.md / subagents/ 注入
                .stateStore(stateStore)
                .compaction(
                        CompactionConfig.builder()
                                .triggerMessages(30)
                                .keepMessages(10)
                                .flushBeforeCompact(true)
                                .build())
                .build();
```

`HarnessAgent.Builder` 在 ReActAgent.Builder 之上追加 workspace 上下文加载（AGENTS.md/MEMORY.md/KNOWLEDGE.md，`<AS>/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:149`）、subagent 声明、沙箱文件系统（`filesystem(SandboxFilesystemSpec)`）、记忆压缩、工具结果驱逐、消息总线等（方法清单见 `HarnessAgent.java:1443-1904`）。所有完整应用示例（dataagent/codingagent/paw）都用 HarnessAgent 作为 agent 载体；agent 间多轮隔离靠 `RuntimeContext.builder().sessionId().userId()`（`UserIsolatedMultiTurnsExample.java:68-69`）。

---

## 3. 定制行为的方式（不写子类）

本章按"定制什么"归组，每组给最小骨架。**所有方式都是组合式的：写一个类实现某接口/加注解，然后塞给 Builder**。

### 3.1 系统提示词：静态 sysPrompt + 动态 onSystemPrompt

静态值直接 `.sysPrompt(...)`。需要每次调用注入动态内容（时间、用户身份、环境）时用 `MiddlewareBase.onSystemPrompt`——管道式，多个 middleware 顺序接力（`.../middleware/SystemPromptMiddlewareExample.java:128-142`）：

```java
public static class TimestampMiddleware implements MiddlewareBase {
    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        String timestamp = Instant.now().toString();
        String appended = currentPrompt + "\n\n[Context] Current UTC time: " + timestamp;
        return Mono.just(appended);
    }
}
```

挂载处：`.middleware(new TimestampMiddleware()).middleware(new EnvironmentMiddleware("demo", "user-42"))`（同文件 :84-85）。HarnessAgent 路线还有第三层：workspace 下 `AGENTS.md` 自动注入且优先级高于 sysPrompt（`agentscope-dataagent/docs/agent-definition.md:82` 字段表注明"与 workspace AGENTS.md 叠加，后者优先"）。

### 3.2 middleware：4 个洋葱切点 + 1 个管道

`MiddlewareBase` 接口共 5 个可覆写方法（`<AS>/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java:59-159`）：`onAgent`（整个调用）、`onReasoning`（LLM 推理阶段）、`onActing`（工具执行阶段）、`onModelCall`（裸模型 API 调用）、`onSystemPrompt`。前四个是 `next.apply(input)` 洋葱链，`order()` 越大越靠外（默认 1，`MiddlewareBase.java:62-73`）。

`CustomizedMiddlewareExample` 一条 middleware 同时监控三个阶段（`.../middleware/CustomizedMiddlewareExample.java:135-187`，节选）：

```java
static class MonitoringMiddleware implements MiddlewareBase {
    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent, RuntimeContext ctx, AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        System.out.println("\n[MIDDLEWARE] onAgent START — agent: " + agent.getName());
        return next.apply(input)
                .doOnComplete(() -> System.out.println("[MIDDLEWARE] onAgent END"));
    }
    @Override
    public Flux<AgentEvent> onActing(
            Agent agent, RuntimeContext ctx, ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {
        // input.toolCalls() 可拿到本轮全部 ToolUseBlock
        return next.apply(input).doOnNext(event -> { /* tap 工具进度 delta */ });
    }
}
```

`ModelCallMiddlewareExample` 演示最深的切点 `onModelCall`：可读 `input.messages()/tools()/options()/model()`，wrap 返回的 Flux 做审计与计时；"必须调用 next 否则调用被丢弃；返回不同 Flux 可整体替换模型响应"（`.../middleware/ModelCallMiddlewareExample.java:154-186`）。

### 3.3 工具：@Tool 注解 → ToolGroup → meta-tool → MCP

**注册普通工具**：任意 POJO 方法加 `@Tool`/`@ToolParam`，整对象 `toolkit.registerTool(...)`（`.../tool/ToolCallingExample.java:60-61` + :127-136）：

```java
Toolkit toolkit = new Toolkit();
toolkit.registerTool(new SimpleTools());   // 类内三个 @Tool 方法一次全注册

@Tool(name = "get_current_time", description = "Get the current time in a specific timezone")
public String getCurrentTime(
        @ToolParam(name = "timezone", description = "Timezone name, e.g. 'Asia/Tokyo'")
        String timezone) { ... }
```

工具方法还可声明 `ToolEmitter emitter` 参数流式吐进度块（`CustomizedMiddlewareExample.java:200-213`）。

**工具分组 + 元工具**（按需激活，省 token）：`ToolGroupExample.java:112-123` 创建三个默认非激活的组，builder 上 `.enableMetaTool(true)`（:80）让 agent 自己通过 `reset_equipped_tools` 换组：

```java
toolkit.createToolGroup("math_ops", "Mathematical calculations", false);
toolkit.registration().tool(new MathTools()).group("math_ops").apply();
```

**MCP 挂载**：三种传输一致地"建 client → registerMcpClient"（`.../mcp/McpStdioExample.java:76-85`）：

```java
McpClientWrapper mcpClient =
        McpClientBuilder.create("filesystem")
                .stdioTransport("npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp")
                .buildAsync()
                .block();
Toolkit toolkit = new Toolkit();
toolkit.registerMcpClient(mcpClient).block();
```

SSE / StreamableHTTP 只是换 `sseTransport(url)` / `streamableHttpTransport(url)`（`McpSseExample.java:78-80`、`McpStreamableHttpExample.java:77-80`）。

### 3.4 skill：三种仓库 + skill 绑定工具组

**FileSystem 仓库**（`.../skill/AgentSkillExample.java:91-112`）：

```java
FileSystemSkillRepository skillRepo = new FileSystemSkillRepository(skillsDir, false);
ReActAgent agent = ReActAgent.builder()
        ...
        .toolkit(toolkit)
        .skillRepository(skillRepo)   // build() 时自动安装 DynamicSkillMiddleware
        .build();
```

**Git 仓库**：`GitSkillRepository(SKILLS_REPO_URL)` 克隆远端到本地临时目录（`GitSkillRepositoryExample.java:35,68`）。

**skill 激活工具组**——skill 与工具联动的高阶玩法（`SkillWithToolGroupExample.java:120-133`）：

```java
toolkit.createSkillToolGroup(
        TOOL_GROUP,                          // "data-analysis-tools"
        "Tools exposed when the 'data-analysis' skill is active",
        false,                               // 初始不激活
        ACTIVATING_SKILL);                   // activateOnSkill = "data-analysis"
toolkit.registration().tool(new DataTools()).group(TOOL_GROUP).apply();
```

当 `DynamicSkillMiddleware` 加载名为 `data-analysis` 的 SKILL.md 时，该工具组自动对模型可见（同文件 :44-49 的 Pattern 注释）。

### 3.5 权限与 HITL：permissionContext + ASK/确认恢复

**权限引擎路线**（`.../hitl/PermissionHITLExample.java:95-124`）：builder 挂 `PermissionContextState`，工具按 ALLOW/ASK/DENY 规则三态放行：

```java
PermissionContextState permCtx =
        PermissionContextState.builder()
                .mode(mode)                                   // DEFAULT 或 DONT_ASK（headless 时 ASK 降级为 DENY）
                .addAllowRule("safe_read",
                        new PermissionRule("safe_read", null, PermissionBehavior.ALLOW, "policy"))
                .addAskRule("dangerous_delete",
                        new PermissionRule("dangerous_delete", null, PermissionBehavior.ASK, "policy"))
                .build();

ReActAgent agent = ReActAgent.builder()
        ...
        .permissionContext(permCtx)
        .build();
```

命中 ASK 时 agent 提前返回 `getGenerateReason() == GenerateReason.PERMISSION_ASKING`；调用方从返回 Msg 中筛出 `ToolCallState.ASKING` 的 ToolUseBlock，问用户后把 `ConfirmResult` 列表塞进恢复消息的 metadata 再次 `agent.call(...)`（同文件 :159-193）：

```java
List<ConfirmResult> confirmResults =
        askingTools.stream().map(t -> new ConfirmResult(approved, t)).toList();
Msg resumeMsg = Msg.builder().name("user").role(MsgRole.USER)
        .textContent(approved ? "approved" : "denied")
        .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, confirmResults))
        .build();
```

**middleware 手工暂停路线**（`HookStopAgentExample.java:210-235`）：`onActing` 检测到危险工具时把调用标记为 ASKING，并发出 `RequireUserConfirmEvent` + `RequestStopEvent` 直接终止本轮——不依赖权限引擎的等价手工实现：

```java
if (!dangerousToolCalls.isEmpty()) {
    markToolCallsAsking(ctx, dangerousToolCalls);
    return Flux.just(
            new RequireUserConfirmEvent(null, dangerousToolCalls),
            new RequestStopEvent("Dangerous tool requires user confirmation",
                    GenerateReason.PERMISSION_ASKING));
}
return next.apply(input);
```

**打断**：`agent.interrupt(Msg)` 仍是公开 API，长任务运行中注入一条用户消息即可（`InterruptionExample.java:132-135`）。

### 3.6 状态、结构化输出与多模态

- **持久会话**：`.stateStore(new JsonFileAgentStateStore(path)).defaultSessionId(id)`，首次 call 自动加载历史、每次交互后自动保存（`.../state/StateExample.java:76-85`）。
- **结构化输出**：agent 不用改配置，调用侧 `agent.call(userMsg, ProductRequirements.class)` + `msg.getStructuredData(...)`（`.../structuredoutput/StructuredOutputExample.java:92-97`）；另有 Fallback 变体示例。
- **多模态**：模型 `defaultOptions(GenerateOptions...)` 与图片块消息（`.../multimodal/VisionExample.java:73`）。
- **每调用上下文**：`RuntimeContext.builder().sessionId().userId()` 传给 `agent.call(msg, ctx)`，实现同 agent 多用户隔离（`.../context/RuntimeContextExample.java:59-71`、`UserIsolatedMultiTurnsExample.java:68-84`）。

### 3.7 HarnessAgent 独有：子 agent 声明式定义

`harness/subagent/SubagentStreamingExample.java:70-88` 展示"一个 agent 内嵌子 agent"——仍是 builder 组合，无需类继承：

```java
HarnessAgent agent =
        HarnessAgent.builder()
                .name("orchestrator")
                .sysPrompt("You are an orchestrator. ... spawn the researcher subagent ...")
                .model("dashscope:qwen-plus")
                .subagent(
                        SubagentDeclaration.builder()
                                .name("researcher")
                                .description("Research specialist. ...")
                                .inlineAgentsBody("You are a research assistant. ...")
                                .persistSession(true)
                                .build())
                .stateStore(stateStore)
                .build();
```

同族示例还有 `SubagentSendDirectlyExample`、`AsyncSubagentExample`、`GatewayMultiAgentExample`（多 agent 经 Gateway 路由）、`ChannelSendExample`。

---

## 4. dataagent：运行时"用户自定义 agent"完整链路（重点）

dataagent 是 examples 中唯一的"终端用户（非程序员）自定义 agent"平台。它把第 2/3 章的 Builder 组合封装成了 HTTP + JPA + gateway 的运行时链路。

### 4.1 两种 scope、三种定义方式

`<AS>/agentscope-examples/agents/agentscope-dataagent/src/main/java/io/agentscope/dataagent/web/catalog/AgentDefinition.java:26-33`：

- **global**——定义在项目 `agentscope.json`，启动时注册进 `HarnessGateway`，所有人可见可用但不可改删；
- **user**——某用户通过 API 创建，仅本人（及被分享者）可见，可增删改、克隆、分享。

配套文档 `<AS>/agentscope-examples/agents/agentscope-dataagent/docs/agent-definition.md:13-17` 给出三种定义方式：① `agentscope.json` 文件（生产/多 agent/版本管理，零代码）；② `application.yml` 兜底（文件不存在时自动生成单 agent 配置）；③ Java 代码注入（自定义工具/动态构建）。

### 4.2 定义载体：AgentDefinition / AgentCreateRequest 的字段

API 视图 `AgentDefinition` 是 26 字段的 record（`AgentDefinition.java:71-97`），核心字段：

| 字段组 | 字段 | 说明 |
|---|---|---|
| 身份 | `id/name/description/sysPrompt` | id 在 scope 内唯一；创建时 sanitize 为 `[a-z0-9_-]`（`AgentCatalogService.java:785-787`） |
| 模型 | `model`（可选覆盖）、`maxIters` | model 为空回落 bootstrap 级模型 |
| 工具 | `toolsAllow/toolsDeny` | 显式 allow/deny 列表 |
| skill | `skillsAllow/skillsDeny`、（StoredEntry 另有 `skillRepositories`） | skill 白/黑名单 + 分层 skill 仓库 |
| 身份显示 | `identityName/identityEmoji` | 聊天 UI 展示名与 emoji |
| 群聊 | `groupChatMentionPatterns/groupChatRequireMention` | 群聊 @ 触发规则 |
| 隔离 | `workspacePath`（仅创建时）、`sandboxMode/sandboxScope` | 数据根目录（相对路径解析到 `~/.agentscope/`）与沙箱模式（SESSION/USER/AGENT/GLOBAL） |
| 治理 | `scope/ownerId/shares/runAs/forkOf/tierForCurrentUser` | 归属、分享授权（CLONE/RUN/EDIT 三档）、执行身份（INVOKER/OWNER）、克隆溯源、当前用户有效权限档 |

HTTP 创建请求 `AgentCatalogService.AgentCreateRequest`（`AgentCatalogService.java:794-815`）在此之上多三个一次性字段：`templateId`（模板实例化）、`aiDraft`（AI 草稿）、`workspacePath`。

### 4.3 存储：UserAgentDefinitionStore → JPA

接口 `UserAgentDefinitionStore`（`UserAgentDefinitionStore.java:42-57`）只有 4 个方法：`list(userId) / findById / save / delete`，外加存储模型 `StoredEntry` record（:69-92）——与 API 的 `AgentDefinition` 字段基本同构，但**不含 tools/skills 的运行时解析结果**，且带 `skillRepositories`。

关键转换函数 `toConfigEntry()`（:125-160）把用户定义折算成运行时 `AgentConfigEntry`（tools allow/deny → `ToolsConfig`、identity → `IdentityConfig`、groupChat → `GroupChatConfig`、skills → `SkillsConfig`），说明**用户存储模型与 JSON 配置模型共用同一套运行时入口**。

唯一内置实现 `JpaUserAgentDefinitionStore`（`web/persistence/jpa/JpaUserAgentDefinitionStore.java:43-126`）：Spring Data JPA 存 `AgentEntity`（`@Column` 见 `AgentEntity.java:75-110`：owner_id + agent_id 联合定位，字段含 sys_prompt/model/max_iters/workspace_path...），默认 H2（`~/.agentscope-builder/db`），开 `jdbc` profile 或设 `BUILDER_DB_URL` 可换 MySQL/PostgreSQL（接口 javadoc :34-38）。

### 4.4 HTTP 入口

`AgentCatalogController`（`web/catalog/AgentCatalogController.java:51-47`）暴露 5 个端点，全部走 Spring Security `Authentication` 取 userId：

- `GET /api/agents`（:74-75）——列出 global + 自己的 + 被分享的 user agent
- `GET /api/agents/{id}`（:85-86）
- `POST /api/agents`（:113-115）——创建，见下
- `PUT /api/agents/{id}`（:134-135）、`DELETE /api/agents/{id}`（:161-163）

创建的服务端动作（`AgentCatalogService.createUserAgent`，`AgentCatalogService.java:184-263`）：校验 name 必填 → 生成/消毒 id → 查重（同用户 + global 命名空间，:193-200）→ 构造 `StoredEntry` 存库（:232）→ **workspace 脚手架**三选一（:239-250）：模板 `templateRegistry.instantiate` / AI 草稿 `writeDraftFiles`（生成 AGENTS.md、tools.json、skills/、subagents/，:313-380）/ 默认 `WorkspaceScaffolder.scaffold`。失败不回滚——workspace 可随时从目录重建（:237 注释）。

### 4.5 动态装配：resolveGatewayAgentId → buildAndRegisterUca

**用户 agent 是懒加载的**：创建只落库不构建；第一次对话才构建。路由键解析在 `resolveGatewayAgentId`（`AgentCatalogService.java:616-631`）：

```java
public String resolveGatewayAgentId(String userId, String agentId) {
    if (isGlobal(agentId)) {
        return agentId;                       // global：启动时已注册，直接用
    }
    UserAgentDefinitionStore.StoredEntry entry =
            store.findById(userId, agentId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Agent not found or not accessible: " + agentId));

    String cacheKey = ucaCacheKey(userId, agentId);
    return registeredUcaIds.computeIfAbsent(cacheKey, k -> buildAndRegisterUca(userId, entry));
}
```

`ConcurrentHashMap.computeIfAbsent` 保证并发下只构建一次；缓存值是 gateway 内的注册 id。

真正的装配函数 `buildAndRegisterUca`（`AgentCatalogService.java:707-770`）——**这就是"用户自定义 agent"翻译成 HarnessAgent 的完整对照表**：

```java
private String buildAndRegisterUca(String userId, UserAgentDefinitionStore.StoredEntry entry) {
    String gatewayAgentId = UCA_PREFIX + userId + "-" + entry.id();   // "uca-{userId}-{agentId}"

    Path workspace = userWorkspacePath(userId, entry);

    HarnessAgent.Builder b = HarnessAgent.builder();

    String name = entry.name() != null ? entry.name() : entry.id();
    b.name(name);
    if (entry.description() != null) b.description(entry.description());
    if (entry.sysPrompt() != null)   b.sysPrompt(entry.sysPrompt());
    if (entry.maxIters() != null)    b.maxIters(entry.maxIters());
    // Model: prefer per-agent override, fall back to bootstrap-level model.
    if (entry.model() != null && !entry.model().isBlank()) {
        b.model(entry.model());
    } else if (model != null) {
        b.model(model);
    }
    b.workspace(workspace);

    // 分层 skill 仓库：workspace 覆盖层隐式；显式条目按序追加，先者胜出
    if (entry.skillRepositories() != null && !entry.skillRepositories().isEmpty()) {
        var repos = SkillRepositorySupport.createAll(workspace, entry.skillRepositories());
        if (!repos.isEmpty()) b.skillRepositories(repos);
    }

    // 预置 outbound-send 工具 + 工具事件总线中间件
    Toolkit ucaToolkit = new Toolkit();
    ucaToolkit.registerTool(new OutboundTool(builderBootstrap.channelManager()));
    b.toolkit(ucaToolkit);
    b.middleware(new ToolNotificationMiddleware(toolEventBus));

    HarnessAgent agent = b.build();
    builderBootstrap.gateway().registerAgent(gatewayAgentId, agent);   // 注册进运行时网关
    return gatewayAgentId;
}
```

逐行对应关系：

| StoredEntry 字段 | HarnessAgent.Builder 调用 | 行号 |
|---|---|---|
| `name`/`description`/`sysPrompt`/`maxIters` | 同名方法 | :714-725 |
| `model`（或 bootstrap 级 model bean） | `b.model(...)` | :727-731 |
| `workspacePath` → `workspaceManagerFactory.resolveAgentDataPath` | `b.workspace(path)` | :710,732 |
| `skillRepositories` → `SkillRepositorySupport.createAll` | `b.skillRepositories(repos)` | :736-743 |
| （平台注入）OutboundTool | `ucaToolkit.registerTool` + `b.toolkit` | :748-752 |
| （平台注入）工具事件流 | `b.middleware(new ToolNotificationMiddleware(...))` | :755-756 |
| — | `b.build()` + `gateway.registerAgent("uca-...")` | :758-761 |

**更新即失效**：`updateUserAgent` 落库后 `registeredUcaIds.remove(ucaCacheKey(...))`（:453-454），下次对话重新构建——定义变更即时生效；`invalidateUca`（:577-580）供改 tools.json/skills 等运行时资源的控制器调用。

### 4.6 对话路由：ChatController → ChatUiChannel → gateway

用户发消息到 `POST /api/agents/{agentId}/chat/send`（或 `/stream` SSE），核心分发在 `ChatController.executeChat`（`web/api/ChatController.java:541-563`）：

```java
private Mono<Msg> executeChat(String userId, String agentId, String message, String conversationId) {
    List<Msg> msgs = shapeInboundMessages(userBindings.list(userId), ChatUiChannel.CHANNEL_ID, message);

    InboundMessage inbound;
    if (agentId == null || agentId.isBlank()) {
        inbound = InboundMessage.dm(ChatUiChannel.CHANNEL_ID, userId, List.copyOf(msgs));  // 绑定驱动路由
    } else {
        String gatewayAgentId = catalogService.resolveGatewayAgentId(userId, agentId);     // ← 懒构建发生点
        inbound = InboundMessage.builder(ChatUiChannel.CHANNEL_ID, Peer.direct(userId), List.copyOf(msgs))
                .preferredAgentId(gatewayAgentId)
                .accountId(conversationId)
                .build();
    }
    Mono<Msg> call = chatUiChannel.dispatch(inbound);
    ...
}
```

`preferredAgentId` 指定网关路由目标（用户显式选择该 agent 时短路绑定档位评估，:530-536 注释）；`accountId(conversationId)` 划分会话。会话键预览用 `chatUiChannel.previewRoute(probe).context().canonicalKey()`（:368-374）。

### 4.7 全局 agent：agentscope.json → DataAgentBootstrap

global agent 的装配在启动期完成。`DataAgentBootstrap.Builder.build()`（`runtime/DataAgentBootstrap.java:501-647`）三阶段：

1. **读配置**：`loadConfigFile` 解析 `~/.agentscope/dataagent/agentscope.json`（:502-508）；`AgentscopeConfig`（`runtime/config/AgentscopeConfig.java:34-65`）= `main`（默认入口 agent id）+ `agents`（id → `AgentConfigEntry` map）+ `channels` + `session`。
2. **合并来源**：agent id 集合 = 文件 agents ∪ `builder.agent(id, agent)` 预构建 ∪ `configureAgent(id, customizer)` 定制器（:513-516）；都为空则报错提示两条路径（:518-523）。
3. **逐 agent 构建**：`HarnessAgent.builder()` → `applyFileEntry(cwd, id, entry, b)` 灌字段 → `configureAllAgents` 全局定制器（注入中间件/状态存储/沙箱）→ `b.build()` → `gateway.registerAgent`（:579-626）。

`applyFileEntry`（`DataAgentBootstrap.java:365-406`）是 JSON 字段→Builder 的翻译表：name/description/sysPrompt/workspace（相对 cwd 解析，缺省 `DEFAULT_WORKSPACE_ROOT`）/maxIters/environmentMemory/model/skillRepositories（`SkillRepositorySupport.createAll`）/identity.name 覆盖显示名。

`AgentConfigEntry`（`runtime/config/AgentConfigEntry.java:44-114`）字段与用户 StoredEntry 高度同构（tools allow/deny、identity、groupChat、skills allow/deny、skillRepositories 分层），javadoc 注明"Fields mirror OpenClaw's agent definition schema"（:32）——**同一份 schema 同时服务文件定义与用户定义**，这正是 4.3 节 `toConfigEntry()` 存在的原因。

Spring 侧装配在 `web/config/DataAgentConfig.java:186-234`：`builderBootstrap` Bean 里 `DataAgentBootstrap.builder().cwd(cwd)`，注入全局模型与 `configureAllAgents(b -> { b.middleware(new ToolNotificationMiddleware(...)); b.stateStore(stateStore); b.filesystem(new DockerFilesystemSpec().client(sandboxClient).isolationScope(IsolationScope.USER)); })`（:224-232）——**每个 agent 都套上工具事件流 + 分布式状态存储 + 用户级 Docker 沙箱**，最后 `bootstrap.start(webChannel)`（:256）。

### 4.8 会话与子 agent spawn：SessionAgentManager

`SessionAgentManager`（`runtime/session/SessionAgentManager.java:68`）管理 agent 的会话生命周期，与 UCA 注册并列（UCA 解决"有哪些 agent"，SessionAgentManager 解决"会话怎么开"）：

- `registerSession(agentId, label, parentSessionKey, parentSpawnDepth, userId)`（:259-353）：校验 agent 存在（:266）与 spawn 深度上限 `MAX_SPAWN_DEPTH`（:271-280）、label 查重（:281-293），生成 `runId`/`sessionId`/`sessionKey`（形如 `agent:{agentId}:subagent-{uuid}`，:295-297），写 `SessionEntry`（含 sessionFilePath、spawnedBy、depth、userId，:307-321），登记父子关系与 `SessionStore` 持久化，最后返回 `SpawnResult(runId, sessionKey, sessionId, sessionFilePath, agentId, "ok", null)`（:347-348）。
- `registerMainSession(...)`（:371-405）：主会话版本，sessionKey 形如 `agent:{agentId}:main:{sessionId}`，可带 `gateKey`（gateway 路由持久化）。
- `execute(sessionKeyOrLabel, prompt, timeoutMs)`（:415 起）执行一轮会话；另有 `resetSession`/`evictAgent`/`runMaintenance` 等运维方法。

dataagent 启动时由 `DataAgentBootstrap` 用主 agent 的 subagent 声明构建 `DefaultAgentManager`，再包一层 `SessionAgentManager`（`DataAgentBootstrap.java:556-567`），并制成 `SessionsTool` 注入每个 agent（:572）——agent 因此能通过工具调用 spawn/使用子会话。

### 4.9 链路总览（文字版调用链）

```
【创建】
POST /api/agents (AgentCatalogController.java:113)
  └─ AgentCatalogService.createUserAgent (:184)
       ├─ UserAgentDefinitionStore.save → JpaUserAgentDefinitionStore → AgentEntity (JPA)
       └─ workspace 脚手架（模板 / AI 草稿 / 默认）

【对话（首次触发了构建）】
POST /api/agents/{agentId}/chat/send (ChatController.java:271)
  └─ executeChat (:541)
       └─ AgentCatalogService.resolveGatewayAgentId (:616)
            ├─ global? → 直接返回 agentId（启动期已由 DataAgentBootstrap 注册）
            └─ user?   → registeredUcaIds.computeIfAbsent → buildAndRegisterUca (:707)
                            ├─ HarnessAgent.builder() 逐字段装配（StoredEntry → Builder）
                            ├─ b.build()
                            └─ HarnessGateway.registerAgent("uca-{userId}-{agentId}", agent)
       └─ chatUiChannel.dispatch(InboundMessage(preferredAgentId=gatewayAgentId))
            └─ ChannelRouter → gateway 查 agent → HarnessAgent.call / streamEvents
                 └─ 内部 SessionAgentManager 维护会话（registerMainSession / execute）

【更新/失效】
PUT /api/agents/{id} → store.save + registeredUcaIds.remove → 下次对话重建
```

补充两点：
- 姊妹工程 paw 的同构实现注册时**无命名空间前缀**（`agentscope-paw/.../web/catalog/AgentCatalogService.java:58` "no namespace prefix"、:376 `gateway.registerAgent(entry.id(), agent)`），dataagent 演进为 `uca-{userId}-{agentId}` 解决不同用户同名 agent 冲突；dataagent 自带文档 `docs/agent-definition.md:26` 写的 `uda-` 前缀已过时，**以代码 `UCA_PREFIX = "uca-"`（`AgentCatalogService.java:72`）为准**。
- 克隆（`prepareClone`，:474-540）：复制 StoredEntry + `forkOf = 源id` + 独立 workspacePath，shares/会话清空。

---

## 5. 其它工程的组装方式（简述）

### 5.1 codingagent：静态工厂 + 双 agent + 环境变量

入口 `CodingAgentFactory.create(workspace, toolkit, linearCtx)`（`agents/agentscope-codingagent/.../agent/CodingAgentFactory.java:59-90`）：

```java
HarnessAgent.Builder builder =
        HarnessAgent.builder()
                .name("agentscope-coding-agent")
                .model(model)                       // buildModel()：CODING_MODEL_ID 前缀路由 dashscope/openai/anthropic
                .sysPrompt(CodingSystemPrompt.build(workingDir, linearCtx))
                .workspace(workspace)
                .toolkit(toolkit != null ? toolkit : new Toolkit())
                .maxIters(resolveMaxIters())         // CODING_MAX_ITERS，默认 50
                .compaction(CompactionConfig.builder()...build());

if ("docker".equalsIgnoreCase(sandboxType)) {        // SANDBOX_TYPE=docker 时换沙箱文件系统
    DockerFilesystemSpec sandboxSpec = new DockerFilesystemSpec();
    sandboxSpec.image(image).workspaceRoot(workingDir).isolationScope(IsolationScope.SESSION);
    builder.filesystem(sandboxSpec);
}
return builder.build();
```

特色：模型选择/回退（`FallbackModel` 包装主备模型，:121-128）、`ReviewerAgentFactory` 第二个审查 agent、`CodingBootstrap` 用 `configureAgent` 分别注入两个 agent 并挂 `ModelCallLimitMiddleware`/`ThreadBudgetMiddleware` 等预算中间件（`CodingBootstrap.java:449-488`）。**这里的"自定义"全部发生在编译期/部署期**（环境变量驱动），没有终端用户运行时定义。

### 5.2 paw：dataagent 的姊妹版

`ClawBootstrap` 与 `DataAgentBootstrap` 同构（读 `${clawHome}/agentscope.json`、prebuilt/configureAgent/configureAllAgents 三来源合并，`agents/agentscope-paw/.../runtime/ClawBootstrap.java:462-514`）；catalog 链路同 4.5，差异是注册 id 无前缀（见 4.9 补充）、多 marketplace 与 Aistio 注册。

### 5.3 agui：注册表工厂，每请求新鲜实例

`agui/.../config/AgentConfiguration.java:58-81`——一个 `AguiAgentRegistryCustomizer` Bean 注册多个 agent 工厂：

```java
@Bean
public AguiAgentRegistryCustomizer aguiAgentRegistryCustomizer() {
    AguiAgentRegistryCustomizer aguiAgentRegistryCustomizer =
            registry -> {
                // Using a factory ensures each request gets a fresh agent instance
                registry.registerFactory("default", this::createDefaultAgent);
                registry.registerFactory("chat", this::createChatAgent);
                registry.registerFactory("calculator", this::createCalculatorAgent);
            };
    return aguiAgentRegistryCustomizer;
}
```

客户端经 URL 路径 `POST /agui/run/{agentId}`、Header `X-Agent-Id` 或 body `forwardedProps.agentId` 选 agent（:48-53）。三个工厂内部仍是标准 `ReActAgent.builder()`（如 `createDefaultAgent`，:128-154）。同一文件还演示 `AgentEventConverter`（自定义事件转 AG-UI 帧）与 `AguiEventEnricher`（事件富化）。

### 5.4 copilotkit：agui 模式 + 全能力展示 agent

`agentscope-copilotkit/.../config/AgentConfiguration.java:83-107` 同样用 registry（多注册一个 `workbench` showcase agent）。`createWorkbenchAgent` 是**单个 agent 上叠满定制开关的范本**（:188-210 附近）：

```java
return ReActAgent.builder()
        .name("AgentScope_Workbench")
        .sysPrompt(WORKBENCH_SYS_PROMPT)
        .model(DashScopeChatModel.builder()...enableThinking(true)...build())
        .toolkit(toolkit)
        .enableTaskList()                       // 内置 todo_write 工具 + 每轮任务提醒
        .permissionContext(workbenchPermissionContext())   // ALLOW/ASK/DENY 三态规则
        .middleware(new WorkbenchEventMiddleware(workbenchStateRegistry))  // 任务镜像到共享状态
        .maxIters(16)
        .build();
```

`workbenchPermissionContext()`（:212-240）给出三态规则的完整样例：查询/状态类工具 ALLOW、`deploy_release` ASK、`purge_production_data` DENY（deny 不可被任何 mode 抬升）。

### 5.5 组装方式对比

| 工程 | 定义时机 | 载体 | 是否终端用户可定义 | agent 生命周期 |
|---|---|---|---|---|
| documentation | 编码期 | ReActAgent/HarnessAgent Builder | 否（main 方法演示） | 进程内单例 |
| codingagent | 部署期（env）+ 编码期 | 静态工厂 → HarnessAgent | 否 | 进程内单例×2 |
| dataagent | 编码期（json）+ **运行时（HTTP）** | Bootstrap + AgentCatalogService | **是** | global 单例；user 懒构建+缓存，更新即失效 |
| paw | 同 dataagent | ClawBootstrap + catalog | 是（无命名空间隔离） | 同上 |
| agui / copilotkit | 编码期 | registry factory | 否（但按请求选择） | 每请求新建 |

---

## 6. 对 nex-ai 平台的启示（面向 AgentSpec 运行时装配）

1. **"Builder 组合 + 零子类"作为 AgentSpec 的执行模型**：dataagent 证明了 `HarnessAgent.Builder` 的字段面（name/sysPrompt/model/maxIters/workspace/skillRepositories/toolkit/middleware/stateStore/permissionContext/filesystem）足以覆盖一个多租户 agent 平台的全部用户可见配置项，nex-ai 的 AgentSpec 可直接对齐这组字段做"Spec → Builder 翻译函数"（参照 `buildAndRegisterUca` 的逐行映射，`AgentCatalogService.java:707-770`），无需为不同 spec 生成不同的 Java 类。
2. **懒构建 + 缓存 + 更新失效的生命周期模式**：`registeredUcaIds.computeIfAbsent` 懒构建、更新/删除时 `remove` 失效、下次对话自动重建（`AgentCatalogService.java:616-631, 453-454, 567`）——比"启动全量构建"省资源且让定义变更即时生效，适合 nex-ai 的 AgentSpec 热更新。
3. **一份 schema 三个入口**：`AgentConfigEntry` 同时服务 agentscope.json 文件定义、用户 API 定义（`StoredEntry.toConfigEntry()`）、运行时装配（`applyFileEntry`）——nex-ai 的 AgentSpec 若也保持"文件/DB/API 同构"，可以复用同一套校验与装配代码，且天然支持从配置文件引导出平台内置 agent。
4. **命名空间隔离与用户数据根**：`uca-{userId}-{agentId}` 注册键 + 每 agent 独立 workspacePath（创建时定死、路径消毒拒绝 `..` 穿越，`AgentCatalogService.java:279-305`）+ `IsolationScope.USER` 沙箱——这是多租户平台最低成本的隔离组合，nex-ai 已有租户体系，可映射 tenantId 到该命名空间方案。
5. **平台能力经 configureAllAgents + middleware 注入而非侵入 agent**：工具事件流（ToolNotificationMiddleware→SSE）、分布式状态存储、Docker 沙箱都是在 `DataAgentConfig.java:224-232` 对所有 agent 统一追加的横切配置；nex-ai 的审计/计费/租户过滤等平台关切照此模式实现，可保持 AgentSpec 纯净（只描述业务，不含平台细节）。
