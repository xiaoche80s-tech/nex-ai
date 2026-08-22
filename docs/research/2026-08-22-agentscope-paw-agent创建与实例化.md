# agentscope-paw 调研：Agent 的创建与实例化全链路

> 调研日期：2026-08-22
> 调研对象：本机 `~/agent-project/agentscope-java/agentscope-examples/agents/agentscope-paw`（包 `io.agentscope.claw2`，共 82 个 Java 文件，含测试），必要时对照 `agentscope-core`、`agentscope-harness` 及姊妹工程 `agentscope-dataagent` 源码。
> 调研纪律：**只采信 v2 主干代码**；`docs/v1/`、README 历史段落不作为论据（README 与代码存在少量命名漂移，下文均已注明并以代码为准）。
> 引用约定：`~/agent-project/agentscope-java/` 简写为 `<AS>`；行号以当前工作区文件为准。
> 姊妹篇：`2026-08-22-agentscope-examples-自定义agent创建方式.md` 第 5.2 节仅简述过 paw（"dataagent 姊妹版、注册无命名空间前缀"），本文是 paw 的完整深挖。

---

## TL;DR：paw 如何创建 agent、如何实例化

paw 里 **"agent 定义"与 "agent 实例" 彻底分离**，创建分三条路径，实例化分两个时机：

| 创建路径 | 定义落在哪里 | 何时构建 `HarnessAgent` 实例 | 注册键 |
|---|---|---|---|
| ① 启动期内置 agent | `${claw.home}/agentscope.json`（不存在则自动生成 `default`） | Spring 启动时（`ClawBootstrap.Builder#build`，JVM 生命周期单例） | 裸 `agentId` |
| ② 运行时 UI/HTTP 自定义 agent | `${claw.home}/agents.json`（JSON 文件，原子写） | **首次对话时懒构建**（`AgentCatalogService#resolveGatewayAgentId` 的 `computeIfAbsent`），更新/删除时缓存失效、下次对话重建 | 裸 `agentId`，**无命名空间前缀** |
| ③ 子 agent（subagent） | 主 agent workspace 的 `subagents/*.md` 文件 | **每个子会话首次执行时**经 `SessionAgentManager.agentCache.computeIfAbsent` 创建并按 sessionKey 缓存 | 不进 gateway 注册表，按 sessionKey 走 `DefaultAgentManager` 工厂 |

一句话：**创建 agent = 写一份 JSON 定义（+ 可选的 workspace 脚手架/模板/AI 草稿落盘）；实例化 agent = 首次被路由到时用 `HarnessAgent.builder()` 现场构建、注册进 `HarnessGateway` 并常驻内存**。与 dataagent 的唯一结构性差异是：paw 单用户无租户，注册键不加 `uca-{userId}-` 前缀、定义存 JSON 文件而非 JPA、剥离了分享/克隆/沙箱/多租户整套面。

---

## 1. 工程总览：paw 是什么

### 1.1 定位

paw 是 [QwenPaw](https://github.com/agentscope-ai/openpaw) 的 Java 版——**装在自己电脑上的单用户个人助手**。它以用户身份直接操作本机文件系统与 Shell，自进化产物（技能、子 agent、记忆、`AGENTS.md`）全部是 agent 自己写在自己 workspace 里的文件；同时内置钉钉/企微/飞书/GitHub/GitLab 通道适配，让 agent 出现在用户已有的 IM 里（`<AS>/agentscope-examples/agents/agentscope-paw/README_zh.md:7-11`）。

三个 harness 示例工程的定位差异：

| | paw | dataagent | codingagent |
|---|---|---|---|
| 场景 | 个人本机助手 | 多租户"用户自定义 agent"平台 | GitHub PR 审查机器人 |
| 用户数 | 1（你自己） | 多用户（JWT 登录 + JPA） | 单一机器人身份 |
| 隔离 | 无——直接本机 FS + Shell | userId 命名空间 + 可选 Docker 沙箱 | 无特殊隔离 |
| 存储 | 纯 JSON 文件 | Spring Data JPA | SQLite 状态存储 |
| 前端 | React SPA（`frontend/`，打包进 `static/`） | 有 | 无 |

README 明说：paw **故意不做**登录、多租户隔离、Docker sandbox、横向扩展，需要这些请去姊妹项目 agentscope-service / dataagent（`README_zh.md:11`）。剥离记录见 `<AS>/.../agentscope-paw/builder.md` 的 "What was removed" 一节：JWT 认证、JPA 层、按用户 workspace 命名空间（`WorkspaceManagerFactory`）、Docker 沙箱接线、agent 分享/克隆（`AgentAclService`/`WorkspaceCopier`）、活动流、用量统计、身份链接全部移除。

### 1.2 技术栈与模块布局

- **Web 框架**：Spring Boot **WebFlux**（reactive HTTP + SSE），非 Servlet 栈；Spring Security 仅用于 permit-all 过滤链 + CORS（`<AS>/.../agentscope-paw/pom.xml:118-128`、`web/config/SecurityConfig.java`）。
- **AI 接入**：`agentscope-harness`（`HarnessAgent`）+ `agentscope-extensions-model-dashscope`（`DashScopeChatModel`，`qwen-max` 默认）；若用户自备 `Model` Bean 则优先用户 bean（`web/config/BuilderConfig.java:105-115` 的 `@ConditionalOnMissingBean(Model.class)`）。
- **持久化**：**零数据库**——agent 定义、会话索引、transcript 全部是本地 JSON/JSONL 文件，根目录 `claw.home`（默认 `~/.agentscope/claw`，`application.yml:27-28`）。SQLite 仅作为 harness 记忆索引的传递依赖（`pom.xml:111-116`）。
- **前端**：React + Vite（`frontend/`），frontend-maven-plugin 在 `generate-resources` 阶段构建并塞进 `src/main/resources/static/`（`pom.xml:181-220`）。
- **命名漂移提示**：README 品牌叙事用 `paw.*` / `PAW_HOME`，但代码与 `application.yml` 实际是 **`claw.*` / `CLAW_HOME`**、包名 `io.agentscope.claw2`、Spring 应用名 `agentscope-claw`（`application.yml:5-7,27-28`）。本文一律以代码为准。

代码布局（`src/main/java/io/agentscope/claw2/` 下，共 60 个主源码文件 + 9 个测试文件，合计 82 与任务描述一致——`find ... -name "*.java" | wc -l` = 82）：

```
claw2/
├── Claw2App.java                  # @SpringBootApplication 入口
├── runtime/                       # 运行时装配层（不依赖 Spring Web）
│   ├── ClawBootstrap.java         # ★ 装配/启动单一门面（Builder 模式）
│   ├── config/                    # agentscope.json 的 POJO 映射 + 通道工厂注册表
│   ├── gateway/HarnessGateway.java# ★ paw 本地版网关（实现 harness 的 Gateway 接口）
│   ├── session/                   # ★ SessionAgentManager/SessionStore/SessionEntry/SessionsTool…（自研会话栈）
│   └── outbound/                  # agent 主动外发工具（OutboundTool）
├── web/                           # HTTP 层
│   ├── catalog/                   # ★ AgentDefinition/UserAgentDefinitionStore/AgentCatalogService（动态创建）
│   ├── api/                       # Chat/Session/Workspace/Tools/Skills/Marketplaces/Bindings 控制器
│   ├── template/                  # TemplateRegistry（内置模板）
│   ├── scaffold/                  # WorkspaceScaffolder（默认脚手架）
│   ├── ai/                        # AgentDraftService（AI 起草）
│   ├── session/                   # SessionLifecycleScheduler/SessionTurnParser
│   ├── toolbus/                   # ToolEventBus + ToolNotificationMiddleware（工具事件 → SSE）
│   └── config/                    # BuilderConfig（★ ClawBootstrap Bean 装配）/Aistio/Security/Web
└── marketplace/                   # ClawMarketplace(git/nacos) 技能市场
```

---

## 2. Agent 定义载体：JSON 文件 + 三层 Java 模型

### 2.1 两种 agent、两份文件

paw 单用户，但同一运行时并存两种 agent（`web/catalog/AgentDefinition.java:24-35` 类注释、`README_zh.md:88-93`）：

- **内置 agent（built-in）**：定义在 `${claw.home}/agentscope.json`，启动时构建注册，UI 只读（要改直接编辑 JSON），不可经 API 删除。
- **自定义 agent（custom）**：经 UI 或 `POST /api/agents` 创建，持久化到 `${claw.home}/agents.json`，可增删改。

两者的 workspace 目录布局一致：`${claw.home}/agents/{agentId}/workspace/`（`AGENTS.md`、`skills/`、`subagents/`、`tools.json`、`memory/`）。

### 2.2 内置定义模型：`AgentscopeConfig` → `AgentConfigEntry`

`agentscope.json` 的根文档（`runtime/config/AgentscopeConfig.java:34-76`）：

```java
public class AgentscopeConfig {
    @JsonProperty("$schema") private String schema;
    @JsonProperty("main")     private String main;                       // 默认入口 agent id
    @JsonProperty("agents")   private Map<String, AgentConfigEntry> agents = new LinkedHashMap<>();
    @JsonProperty("channels") private Map<String, ChannelConfigEntry> channels = new LinkedHashMap<>();
    @JsonProperty("session")  private SessionLifecycleConfig session;    // 每日/空闲重置策略
    @JsonProperty("marketplaces") private Map<String, MarketplaceConfigEntry> marketplaces = ...;
}
```

每个 `agents.<agentId>` 条目的字段（`runtime/config/AgentConfigEntry.java:42-106`）：

| 字段 | 类型 | 作用 |
|---|---|---|
| `name` / `description` / `sysPrompt` | String | 展示名 / 描述 / 系统提示 |
| `workspace` | String | workspace 根，相对路径按 `claw.home` 解析；缺省 `agents/{id}/workspace` |
| `maxIters` | Integer | 推理迭代上限 |
| `environmentMemory` | String | 环境记忆注入 |
| `skillRepository` / `skillRepositories` | Entry/List | 技能仓库（旧单数 + 新复数合并生效，`getEffectiveSkillRepositories()`，:181-192） |
| `model` | String | 模型 id 覆盖（如 `"anthropic/claude-opus-4-7"`） |
| `tools{allow,deny}` | ToolsConfig | 内置工具白/黑名单 |
| `identity{name,emoji}` | IdentityConfig | 展示身份覆盖 |
| `groupChat{mentionPatterns,requireMention}` | GroupChatConfig | 群聊 @ 触发门控 |
| `sandbox{mode,scope}` | SandboxConfig | 沙箱（paw 里基本闲置，`off` 默认） |
| `skills{allow,deny}` | SkillsConfig | 技能白/黑名单 |

示例工程自带的 `src/main/resources/agentscope.json.example` 展示了双 agent + chatui 通道的最小配置（`main: "default"`、`default`/`worker` 各有独立 workspace 与 `maxIters`）。

### 2.3 自定义定义模型：`UserAgentDefinitionStore.StoredEntry`

`${claw.home}/agents.json` 的 wire 格式是 `Map<String, StoredEntry>`（`web/catalog/UserAgentDefinitionStore.java:190-212`）：

```java
public record StoredEntry(
        String id, String name, String description, String sysPrompt, String model,
        Integer maxIters,
        List<String> toolsAllow, List<String> toolsDeny,
        String identityName, String identityEmoji,
        List<String> groupChatMentionPatterns, Boolean groupChatRequireMention,
        String sandboxMode, String sandboxScope,
        List<String> skillsAllow, List<String> skillsDeny,
        long createdAt, long updatedAt,
        String workspacePath) {

    public AgentDefinition toDefinition() { ... }        // → API 出参（builtin=false）
    public AgentConfigEntry toConfigEntry() { ... }      // → 内置定义格式（:240-278）
}
```

要点：

- **存储即一个 JSON 文件 + 读写锁 + 原子写**（temp 文件 + `ATOMIC_MOVE` rename，崩溃不留半文件；`UserAgentDefinitionStore.java:160-184`），完全没有数据库。
- `toConfigEntry()` 提供了向内置定义格式的转换，但注意 paw 的动态构建**并不走这个方法**（见 §4.2——`buildAndRegisterCustom` 直接逐字段读 `StoredEntry`，等价于手工版的 `applyFileEntry`）。
- 与 dataagent 的 `AgentDefinition` 对比：dataagent 多出 `scope`（global/user）、`ownerId`、`shares`（分享授权）、`runAs`（INVOKER/OWNER）、`forkOf`（克隆来源）、`tierForCurrentUser`（CLONE/RUN/EDIT 权限层级）等一整套多租户字段（`<AS>/.../agentscope-dataagent/.../web/catalog/AgentDefinition.java:71-118`），且其 `UserAgentDefinitionStore` 是接口、由 **JPA 实现**（`JpaUserAgentDefinitionStore`，同文件 :35 注释）。paw 的 StoredEntry 正是把这些字段全部剥掉后的"个人版"。

第三层是 API 出参 `AgentDefinition` record（`web/catalog/AgentDefinition.java:61-82`），仅比 StoredEntry 多一个 `builtin` 标志和一个运行时解析的 `tools` 列表，供前端区分能否显示"删除"按钮。

---

## 3. 静态创建（启动期）：BuilderConfig → ClawBootstrap 三阶段装配

### 3.1 Spring 侧入口：`BuilderConfig#builderBootstrap`

`Claw2App` 只是普通 `@SpringBootApplication`（`Claw2App.java:38-42`）。真正的装配发生在 `web/config/BuilderConfig.java:150-227` 的 `builderBootstrap` Bean：

```java
@Bean
public ClawBootstrap builderBootstrap(Optional<Model> modelOpt, ToolEventBus toolEventBus,
        Optional<AgentScopeAdapter> aistioAdapter, Optional<TranscriptStore> transcriptStore,
        String pawTranscriptTenant) throws IOException {
    Path home = resolveClawHome();
    ensureAgentscopeConfig(home);                                  // ① 无配置则自动生成

    ClawBootstrap.Builder builder = ClawBootstrap.builder().cwd(home);

    if (modelOpt.isPresent()) { builder.model(modelOpt.get()); }
    else { throw new IllegalStateException("agentscope-claw cannot start without a model.\n..."); }

    builder.configureAllAgents(b -> b.middleware(new ToolNotificationMiddleware(toolEventBus)));
    transcriptStore.ifPresent(store -> builder.configureAllAgents(
            b -> b.transcriptStore(store).transcriptTenant(pawTranscriptTenant)));
    aistioAdapter.ifPresent(adapter -> builder.configureAllAgents(b -> b.middleware(adapter.middleware())));

    ClawBootstrap bootstrap = builder.build();                     // ② 三阶段装配
    ...
    bootstrap.start(webChannel);                                   // ③ 启动 chatui + 文件通道
    return bootstrap;
}
```

（节选自 `BuilderConfig.java:150-218`，有删减）

三个细节值得注意：

1. **fail-fast 模型检查**（:163-178）：无 `Model` Bean 且无 DashScope key 时启动即报错，避免对话请求在 `ReActAgent` 深处 NPE。模型 bean 的自动创建条件是 `@ConditionalOnMissingBean(Model.class)` + api-key 非空（:105-115）。
2. **全局 customizer**：`configureAllAgents` 给每个 agent 注入工具事件中间件（驱动前端 SSE 的 `tool_call`/`tool_result` 帧）、共享 transcript store、可选 aistio 观测中间件。注释明确说明 ReActAgent 的 middleware 列表在 build 时固定，所以必须在这里接线（:189-192）。
3. **自动生成 `agentscope.json`**：`ensureAgentscopeConfig`（:266-297）在 `claw.home` 下写一个只含 `default` agent 的最小配置（`workspace: "workspace"` 使主 agent 的树直接位于 `~/.agentscope/claw/workspace`），并对该 workspace 跑一次 `WorkspaceScaffolder.scaffold`。这就是"首次启动零配置可用"的实现。

`bootstrap.start(webChannel)` 之后：chatui 通道（默认 `DmScope.MAIN`，即 Web UI 与 agent 共享单一会话）与 `agentscope.json` 里声明的其它通道（钉钉/企微等，经 `ChannelTypeRegistry` 工厂创建）一起 init + start（`BuilderConfig.java:198-225`、`ClawBootstrap.java:175-194`）。

### 3.2 `ClawBootstrap.Builder#build()`：三阶段装配

`ClawBootstrap` 是 paw 自带的装配门面（**不属于 harness 库**；harness 库另有 `GatewayBootstrap`，paw 没用它）。其 `Builder#build()`（`runtime/ClawBootstrap.java:544-693`）分三阶段：

**阶段 0——归并 agent id 集合**（:557-583）：文件定义（`agentscope.json` 的 `agents` map）∪ 编程式 `prebuilt`（`Builder.agent(id, HarnessAgent)`）∪ `configurators`（`Builder.configureAgent(id, customizer)`）。为空则抛错。`main` 的选取顺序：显式 `mainAgent()` > 配置 `main` 字段 > id 为 `"default"` 的条目 > 第一个 id（:571-578）。

**阶段 1——用 main agent 的定义抽出共享会话设施**（:585-619）：

```java
// 配置一个临时 builder 用于从 main agent 提取 subagent 条目
Path mainWorkspace = resolveAgentWorkspace(cwd, main, fileAgents.get(main));
HarnessAgent.Builder mainEntryBuilder = HarnessAgent.builder();
applyFileEntry(cwd, main, fileAgents.get(main), mainEntryBuilder);
...
List<SubagentEntry> entries = mainEntryBuilder.buildSubagentEntries(mainWorkspace);

WorkspaceManager wsManager = new WorkspaceManager(mainWorkspace);
DefaultAgentManager dam = new DefaultAgentManager(entries, wsManager);

Path storeFile = defaultSessionsStore(cwd, main);
SessionStore sessionStore = new SessionStore(storeFile);
sessionStore.load();

AgentManagerConfig amCfg = resolveAgentManagerConfig(fileConfig);
SessionAgentManager sam = new SessionAgentManager(dam, amCfg, new SubagentRunRegistry(), sessionStore);

ChannelManager channelMgr = new ChannelManager();
HarnessGateway gateway = HarnessGateway.create(sam, channelMgr);
TaskRepository taskRepo = new WorkspaceTaskRepository(wsManager, main);
SessionsTool sessionsTool = new SessionsTool(sam, taskRepo, null, 0);
OutboundTool outboundTool = new OutboundTool(channelMgr);
```

（`ClawBootstrap.java:588-619`）

关键点：**整个多 agent 运行时共享一套会话/子 agent 基础设施**，而它们的"原料"——`SubagentEntry` 工厂列表——来自 main agent 的 workspace：`HarnessAgent.Builder#buildSubagentEntries(workspace)` 会扫描 `workspace/subagents/*.md`（经 harness 的 `AgentSpecLoader.loadFromDirectory`）、合并程序式声明与自定义工厂，并追加一个内置的 `general-purpose` 子 agent（`<AS>/agentscope-harness/.../HarnessAgentBuilderSupport.java:199-242`、`HarnessAgent.java:2178-2185`）。**也就是说：给 main agent 的 workspace 里丢一个 markdown 文件，就"创建"了一个子 agent 类型**——这是 paw 第三条创建路径（见 §5.3）。

**阶段 2——逐个构建 agent**（:621-660）：

```java
for (String id : ids) {
    if (prebuilt.containsKey(id)) { built.put(id, prebuilt.get(id)); continue; }  // 编程式实例优先
    AgentConfigEntry entry = fileAgents.get(id);
    if (entry == null && !configurators.containsKey(id)) continue;

    HarnessAgent.Builder b = HarnessAgent.builder();
    applyFileEntry(cwd, id, entry, b);          // JSON 条目 → builder 逐字段

    if (model != null) b.model(model);          // bootstrap 级模型兜底
    b.externalSubagentTool(sessionsTool);       // 共享的 sessions_spawn/send 工具

    Toolkit agentToolkit = new Toolkit();
    agentToolkit.registerTool(outboundTool);    // 每个预置 outbound_send 主动外发工具
    b.toolkit(agentToolkit);

    Consumer<HarnessAgent.Builder> c = configurators.get(id);
    if (c != null) c.accept(b);
    for (Consumer<HarnessAgent.Builder> gc : globalConfigurators) gc.accept(b);

    built.put(id, b.build());                   // ★ 此处真正实例化 HarnessAgent
}
```

（`ClawBootstrap.java:625-659`，有删减）

`applyFileEntry`（:403-448）是"JSON 定义 → Builder"的逐字段映射，值得完整看一遍因为它定义了内置 agent 的全部语义：

```java
static void applyFileEntry(Path clawHome, String agentId, AgentConfigEntry e, HarnessAgent.Builder b) {
    String name = (e != null && e.getName() != null && !e.getName().isBlank()) ? e.getName() : agentId;
    b.name(name);
    b.agentId(agentId);                                  // 稳定 catalog id，用于 session 路径与 transcript 分段

    if (e != null) {
        if (e.getDescription() != null) b.description(e.getDescription());
        if (e.getSysPrompt() != null)    b.sysPrompt(e.getSysPrompt());
        Path workspace = e.getWorkspace() != null && !e.getWorkspace().isBlank()
                ? clawHome.resolve(e.getWorkspace()).normalize()
                : defaultAgentWorkspace(clawHome, agentId);   // 缺省 agents/{id}/workspace
        b.workspace(workspace);
        if (e.getMaxIters() != null)         b.maxIters(e.getMaxIters());
        if (e.getEnvironmentMemory() != null) b.environmentMemory(e.getEnvironmentMemory());
        if (e.getModel() != null && !e.getModel().isBlank()) b.model(e.getModel());
        for (var entry : e.getEffectiveSkillRepositories()) {
            var repo = SkillRepositorySupport.create(clawHome, entry);
            if (repo != null) b.skillRepository(repo);
        }
        if (e.getIdentity() != null && e.getIdentity().getName() != null) b.name(e.getIdentity().getName());
    } else {
        b.workspace(defaultAgentWorkspace(clawHome, agentId));
    }
}
```

（`ClawBootstrap.java:403-448`）

**阶段 3——注册进网关**（:667-672）：

```java
for (Map.Entry<String, HarnessAgent> e : built.entrySet()) {
    gateway.registerAgent(e.getKey(), e.getValue());
}
gateway.bindMainAgent(built.get(main));
```

`bindMainAgent` 同时把 main agent 按 `getAgentId()` 登记进注册表并设为路由兜底（`runtime/gateway/HarnessGateway.java:197-203`）。

**内置 agent 的生命周期**：构建一次、`Map.copyOf(built)` 不可变持有、JVM 退出时经 `ClawBootstrap#close()` 逐个 `agent.close()` 释放后台任务仓库与 workspace 索引（`ClawBootstrap.java:209-227`）。**进程内单例，与请求无关**——并发对话靠 session 级锁串行（§5.2），而不是每请求新建实例。

---

## 4. 动态创建（运行时）：catalog 链路与懒构建

### 4.1 REST 面

`web/catalog/AgentCatalogController.java:44-88` 暴露 5 个端点，全部委托 `AgentCatalogService`：

| 端点 | 行号 | 行为 |
|---|---|---|
| `GET /api/agents` | :54 | 列出内置（先）+ 自定义（后，插入序） |
| `GET /api/agents/{id}` | :59 | 单个定义 |
| `POST /api/agents` | :72 | 创建自定义 agent（201） |
| `PUT /api/agents/{id}` | :78 | 更新（内置则 409） |
| `DELETE /api/agents/{id}` | :84 | 删除（内置则 409） |

### 4.2 创建：只写定义 + workspace 落盘，**不构建实例**

`AgentCatalogService#createAgent`（`web/catalog/AgentCatalogService.java:136-206`）：

```java
public AgentDefinition createAgent(AgentCreateRequest req) {
    validateRequest(req);                                   // name 必填
    String id = sanitizeId(req.id() != null && !req.id().isBlank()
            ? req.id()
            : UUID.randomUUID().toString().replace("-", "").substring(0, 8));  // 缺省 8 位随机 id
    if (store.findById(id).isPresent())  throw new ResponseStatusException(CONFLICT, ...);
    if (isBuiltin(id))                   throw new ResponseStatusException(CONFLICT, ...);  // 不与内置撞名

    long now = System.currentTimeMillis();
    String workspacePath = normalizeWorkspacePathInput(req.workspacePath());  // 相对路径禁止 ".." 穿越段
    UserAgentDefinitionStore.StoredEntry entry = new StoredEntry(id, .../* 19 个字段 */, workspacePath);
    store.save(entry);                                      // → agents.json 原子写

    // 三选一落盘 workspace：模板 > AI 草稿 > 默认脚手架
    Path workspace = workspacePath(entry);
    if (req.templateId() != null && !req.templateId().isBlank()) {
        boolean ok = templateRegistry.instantiate(req.templateId(), workspace);
        if (!ok) throw new ResponseStatusException(BAD_REQUEST, "Unknown templateId: " + req.templateId());
    } else if (req.aiDraft() != null) {
        writeDraftFiles(workspace, req.aiDraft(), entry);
    } else {
        WorkspaceScaffolder.scaffold(workspace, entry.name(), entry.sysPrompt());
    }
    return entry.toDefinition();
}
```

（`AgentCatalogService.java:136-205`，有删减）

要点：

- **创建动作不触碰 `HarnessGateway`、不构建 `HarnessAgent`**——创建的是"定义 + 一棵 workspace 文件树"，实例化被推迟到首次对话（§4.4）。
- id 规则：小写 + `[a-zA-Z0-9_-]`，其余字符替换为 `-`（`sanitizeId`，:521-523）。
- `AgentCreateRequest`（:530-549）除定义字段外多两个可选参数：`templateId`（选内置/用户模板）与 `aiDraft`（AI 起草结果，见 §6.3）。

### 4.3 更新/删除 = 写定义 + 缓存失效

`updateAgent`（:209-258）重写 StoredEntry（`workspacePath` 创建后不可改），然后**一句关键的缓存失效**：

```java
store.save(updated);
// 删除缓存的网关注册，让下一次对话用新定义重建。
registeredCustomIds.remove(agentId);
```

（`AgentCatalogService.java:251-254`）

`deleteAgent`（:261-271）同理：`store.delete(agentId)` + `registeredCustomIds.remove(agentId)`。内置 agent 的更新/删除一律 409（:211-214、:262-265）。

实现层面的两个小瑕疵（如实记录，不影响理解主线）：
- 更新后旧实例仍在 gateway 的 `agentRegistry` 里，直到下次对话 `registerAgent` 用 `put` 覆盖；旧 `HarnessAgent` 未被 `close()`。
- 删除后 gateway 注册表里同样残留旧实例（不过 `resolveGatewayAgentId` 对已删条目会先抛 404，残留实例不会再被路由到）。

### 4.4 实例化时机：首次对话，`computeIfAbsent` 懒构建

对话入口 `ChatController` 在路由前必经 `AgentCatalogService#resolveGatewayAgentId`（`AgentCatalogService.java:284-297`）：

```java
public String resolveGatewayAgentId(String agentId) {
    if (isBuiltin(agentId)) {
        return agentId;                       // 内置：启动时已注册，直接用裸 id
    }
    UserAgentDefinitionStore.StoredEntry entry = store.findById(agentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId));

    return registeredCustomIds.computeIfAbsent(agentId, k -> buildAndRegisterCustom(entry));
}
```

`registeredCustomIds` 是 `ConcurrentHashMap<String, String>`（:74-75），value 只是"已构建"标记。真正的构建在 `buildAndRegisterCustom`（:354-380）：

```java
private String buildAndRegisterCustom(UserAgentDefinitionStore.StoredEntry entry) {
    Path workspace = workspacePath(entry);           // 显式路径（相对 claw.home / 绝对）或 agents/{id}/workspace

    HarnessAgent.Builder b = HarnessAgent.builder();
    String name = entry.name() != null ? entry.name() : entry.id();
    b.name(name);
    b.agentId(entry.id());
    if (entry.description() != null) b.description(entry.description());
    if (entry.sysPrompt() != null)   b.sysPrompt(entry.sysPrompt());
    if (entry.maxIters() != null)    b.maxIters(entry.maxIters());
    if (entry.model() != null && !entry.model().isBlank()) b.model(entry.model());
    else if (model != null)          b.model(model);  // 兜底全局 Model Bean
    b.workspace(workspace);
    b.middleware(new ToolNotificationMiddleware(toolEventBus));
    transcriptStore.ifPresent(store -> b.transcriptStore(store).transcriptTenant(transcriptTenant));

    HarnessAgent agent = b.build();
    HarnessGateway gateway = bootstrap.gateway();
    gateway.registerAgent(entry.id(), agent);        // ★ 裸 id 注册，无任何前缀

    log.info("Registered custom agent in gateway: agentId={}", entry.id());
    return entry.id();
}
```

（`AgentCatalogService.java:354-380`）

对照 `ClawBootstrap.applyFileEntry` 可见两者是同一套逐字段模式的两个副本（动态版少了 skillRepository/outbound 工具注入——自定义 agent 没有 `outbound_send`，这是与内置 agent 的一个能力差异）。类注释把这一设计意图写得很直白（`AgentCatalogService.java:53-58`）：

> Custom agents — stored in `${clawHome}/agents.json`. The user can create, update, and delete them. **On first conversation they are dynamically built and registered in the gateway under their `agentId` directly (no namespace prefix).**

**"无命名空间前缀"的原因**：paw 单用户，`agents.json` 里一个 id 唯一对应一个 agent，gateway 注册表（`agentRegistry: ConcurrentHashMap<String, HarnessAgent>`，`HarnessGateway.java:82`）不存在跨用户撞名问题。dataagent 因为多用户——不同用户可以各自创建同名 `agentId` 的自定义 agent——必须以 `UCA_PREFIX + userId + "-" + agentId`（即 `uca-{userId}-{agentId}`）作为注册键消歧（dataagent `AgentCatalogService.java:72`、:707-708，懒构建同为 `registeredUcaIds.computeIfAbsent`，:629-630）。**前缀不是技术需要，是多租户命名空间的需要**；paw 剥离多租户后前缀自然消失。

### 4.5 懒构建 + 缓存失效小结（对比 dataagent）

paw 与 dataagent 在这条链路上是**同构**的，差异只在键与存储：

| | paw | dataagent |
|---|---|---|
| 定义存储 | `agents.json` 单文件 + 原子写 | JPA（`JpaUserAgentDefinitionStore`） |
| 懒构建缓存 | `registeredCustomIds.computeIfAbsent(agentId, …)` | `registeredUcaIds.computeIfAbsent(userId+"|"+agentId, …)` |
| 注册键 | 裸 `agentId` | `uca-{userId}-{agentId}` |
| 失效时机 | update/delete 时 `remove` | update/delete/可见性变化时 `remove` |
| 构建注入 | middleware + transcriptStore | middleware + transcriptStore + outbound 工具（含 tier ACL）+ 分层 skill repos |

---

## 5. 实例化与调用链：一次对话如何路由到 agent 实例

### 5.1 入站：ChatController → ChannelRouter → HarnessGateway.run

Web UI 对话走 `POST /api/agents/{agentId}/chat/stream`（SSE）或 `/send`（同步）（`web/api/ChatController.java:56-67`）。核心执行路径（:328-331）：

```java
private Mono<Msg> executeChat(String agentId, String message) {
    RouteResult route = resolveRoute(agentId, message);
    return gateway.run(route.context(), List.of(messageOf(message)), route.outboundAddress());
}
```

`resolveRoute`（:248-265）手工构造一条 `InboundMessage`（`preferredAgentId` = 上一步解析出的网关 agent id），然后**复用 chatui 通道的 `ChannelRouter`** 做同一套路由决策——保证 HTTP 直连对话与 IM 通道入站产生完全一致的 `MsgContext`：

```java
private RouteResult resolveRoute(String agentId, String probeText) {
    String gatewayAgentId = catalogService.resolveGatewayAgentId(agentId);   // ← 懒构建发生在这里
    ChatUiChannel chatui = lookupChatUi();
    InboundMessage inbound = InboundMessage.builder(ChatUiChannel.CHANNEL_ID,
                    io.agentscope.harness.agent.gateway.channel.Peer.direct("__anonymous__"),
                    List.of(Msg.builder().role(MsgRole.USER).textContent(probeText).build()))
            .senderId("__anonymous__")
            .preferredAgentId(gatewayAgentId)
            .build();
    return router.resolveRoute(chatui.config(), inbound);
}
```

（`ChatController.java:248-264`）

`ChannelRouter.resolveRoute` 的优先级（harness 库，`<AS>/agentscope-harness/.../gateway/channel/ChannelRouter.java:82-118`）：**explicit（`preferredAgentId`）> bindings（peer/parentPeer/guildRoles/guild/team/account/channel 七层）> channel 默认 > 全局默认（main）**。路由产物 `RouteResult` 携带 `MsgContext`（extra 里带 `agentId`）与 `OutboundAddress`。`MsgContext#canonicalKey()`（`MsgContext.java:77-101`）把 `channel|g:group|r:room|t:thread|ts:…|x:k=v` 拼成稳定会话键——因此 chatui 下每个 agent 天然得到 `chatui|x:agentId=<id>` 这样的独立会话键。

IM 通道（钉钉等）不走 ChatController，而是各通道适配器收到事件后经 `ChatUiChannel` 同款 `dispatch` 模式进入网关：`ChatUiChannel#dispatch`（`<AS>/.../chatui/ChatUiChannel.java:149-158`）就是 `router.resolveRoute(...)` → `gateway.run(route.context(), …, route.outboundAddress(), …)`。

### 5.2 网关：HarnessGateway（paw 本地副本）

先澄清一个容易混淆的点：harness 库自带一个 847 行的默认网关 `io.agentscope.harness.agent.gateway.HarnessGateway`（支持 MessageBus、SubagentRegistry、跨节点恢复）；**paw 没有用它**，而是在 `io.agentscope.claw2.runtime.gateway.HarnessGateway`（468 行）实现同一个 `Gateway` 接口的本地副本，深度耦合 paw 自研的会话栈 `SessionAgentManager`（dataagent 同样 fork 了自己的 554 行版本）。这个选择的动机在类注释里（`HarnessGateway.java:50-70`）：网关要负责"按 `MsgContext` 路由入站 turn + 把子 agent 完成公告作为新 turn 派发回原请求者"，而会话状态管理全部委托给 `SessionAgentManager`。

`HarnessGateway#run`（:242-272）四步：

```java
public Mono<Msg> run(MsgContext context, List<Msg> messages, OutboundAddress outboundAddress) {
    MsgContext ctx = context != null ? context : MsgContext.defaultContext();
    String gateKey = ctx.canonicalKey();

    String requestedAgentId = ctx.extra() != null ? (String) ctx.extra().get("agentId") : null;
    HarnessAgent ha = resolveAgent(requestedAgentId);          // ① 注册表查 id，未命中回退 main
    if (ha == null) return Mono.error(new IllegalStateException(
            "HarnessGateway.bindMainAgent must be called before run(...)"));

    String sessionKey = resolveOrCreateMainSession(gateKey, ha); // ② gateKey → MAIN session
    String sessionId = sessionAgentManager.viewSession(sessionKey)
            .map(SessionView::sessionId).orElse(sessionKey);

    if (outboundAddress != null) lastRouteBySessionKey.put(sessionKey, outboundAddress);  // 记录回程

    RuntimeContext runtimeContext = RuntimeContext.builder()
            .sessionId(sessionId).put("msgContext", ctx).put("sessionKey", sessionKey).build();
    return withGatedTurn(gateKey, () -> ha.call(messages, runtimeContext));  // ③④ 串行执行
}
```

- **① agent 解析**（`resolveAgent`，:389-398）：`agentRegistry`（启动注册的内置 + 懒注册的自定义）按 id 查；查不到回退 `bindMainAgent` 的主 agent。
- **② 会话解析/创建**（`resolveOrCreateMainSession`，:400-419）：`contextKeyToSessionKey.compute(gateKey, …)`——已有且新鲜（按 `SessionResetPolicy` 评估）则复用；否则 `sessionAgentManager.registerMainSession(agentId, null, gateKey)` 新建并写 `SessionStore`。MAIN session 的 key 形如 `agent:{agentId}:main:main-{uuid}`（`SessionAgentManager.java:353-385`）。**重启恢复**：网关创建时会从持久化的 SessionStore 里把仍新鲜的 MAIN session 路由映射装回内存（`restorePersistedMainSessions`，:159-190）。
- **③ turn 串行化**（`withGatedTurn`，:445-467）：每个 `gateKey` 一把 harness 的 `LocalSessionTurnGate` 公平锁——同一会话同一时刻至多一个 `HarnessAgent.call` 在跑，忙时新 turn 直接返回空（`TurnBusyException` → `Mono.empty()`）。
- **④ 执行**：`ha.call(messages, runtimeContext)` 复用同一个 agent 实例，靠 `RuntimeContext.sessionId` 区分/恢复记忆上下文（HarnessAgent 内部按 sessionId 管理会话状态与 transcript）。

**并发/复用模型总结**：顶级 agent（内置 + 自定义）= **每 agent 一个常驻单例实例**，多会话/多通道并发由 per-gateKey 公平锁 + per-sessionId 状态隔离承载；不存在"每请求新建实例"。

**子 agent 完成回投**（`tryDispatchAnnounce`，:300-356）：异步子任务完成时，网关以 `subagent_announce` 为名组装一条 USER 消息，在**原请求者的 gateKey** 上再起一个 gated turn，让 agent 消化结果；回复经 `ChannelManager.deliver` 送回最初触发对话的 IM 通道（`deliverAnnounceReply`，:362-383；`NO_REPLY` 哨兵值跳过投递）。会话键/agent id/回程地址的继承由 spawn 拦截器 `onSpawn`（:278-293）在子会话诞生时登记。

### 5.3 子 agent 实例化：SessionsTool → SessionAgentManager → 工厂

顶级 agent 在推理中可调 4 个工具：`sessions_spawn` / `sessions_send` / `sessions_list` / `sessions_history`（`runtime/session/tool/SessionsTool.java:42-60`）。这些工具是启动装配时经 `b.externalSubagentTool(sessionsTool)` 注入的**进程级共享单例**（`ClawBootstrap.java:642`）——所有 agent 共用同一套子会话编排。

spawn 的链路（`SessionsTool#sessionsSpawn` :102 起 → `SessionAgentManager#registerSession`）：

```
Agent(推理) → SessionsTool#sessionsSpawn(agent_id, task, …)
           → SessionAgentManager#registerSession(agentId, label, parentKey, depth)   // 登记 SUBAGENT 会话
              ├─ 校验 delegate.hasAgent(agentId)（工厂是否存在）
              ├─ sessionKey = "agent:" + agentId + ":subagent-" + UUID
              ├─ depth ≤ MAX_SPAWN_DEPTH；label 唯一
              ├─ SessionStore.save(entry)（持久化）
              └─ spawnInterceptor.onSpawn(...)（网关登记回程继承）
           → SessionAgentManager#execute(sessionKey, prompt, timeout, …)
              ├─ lane 信号量（SUBAGENT/NESTED 两档并发上限）+ per-session ReentrantLock
              └─ doExecute → getOrCreateAgent(entry)
                    agentCache.computeIfAbsent(sessionKey,
                        k -> delegate.createAgent(entry.agentId(), parentContext(entry)))   // ★ 实例化
           → DefaultAgentManager#invokeAgent(agent, sessionId, userId, prompt) → agent.call(...)
```

`getOrCreateAgent`（`SessionAgentManager.java:509-513`）是子 agent 实例化的核心：

```java
/** Returns a cached agent instance for the session, or creates a new one. */
private Agent getOrCreateAgent(SessionEntry entry) {
    return agentCache.computeIfAbsent(
            entry.sessionKey(),
            k -> delegate.createAgent(entry.agentId(), parentContext(entry)));
}
```

`agentCache` 是 `ConcurrentHashMap<String, Agent>`，**按 sessionKey 缓存活实例**（:99-101）——同一子会话多轮 `sessions_send` 复用同一实例（保住其内存态）；`/reset` 或会话删除时 `evictAgent`/`removeSession` 摘除缓存（:524-528、:670-691）。

`delegate` 即 harness 的 `DefaultAgentManager`（`<AS>/agentscope-harness/.../subagent/DefaultAgentManager.java:37-46`——"纯 agent 工厂与调用器，无会话注册表、无 lane 管理"）。它的工厂表来自启动时 main agent 的 `buildSubagentEntries`（§3.2 阶段 1），`createAgent(agentId, parentRc)` 就是 `factory.create(parentRc)`（:154-160）。而每个工厂的实现（以声明式子 agent 为例，`HarnessAgentBuilderSupport.java:392` 起；内置 `general-purpose` 工厂 :331-386）都是**闭包捕获父 builder 配置、每次调用现场 `HarnessAgent.builder().…().build()` 产出一个新子 HarnessAgent**：

```java
return (RuntimeContext parentRc) -> {
    // general-purpose 子 agent 共享父 workspace，每次 spawn 都是短生命周期；
    // parentRc 仅为接口兼容保留，暂不做按父身份的分桶。
    HarnessAgent.Builder sub = HarnessAgent.builder()
            .name("general-purpose-subagent")
            .description("General-purpose subagent for isolated task execution")
            .sysPrompt(buildSubagentSysPrompt(null))
            .model(capturedModel)
            .toolkit(capturedParentToolkit.copy())
            .workspace(workspace)
            .asLeafSubagent()
            ...;
    return sub.build();
};
```

（`HarnessAgentBuilderSupport.java:331-349`，节选）

harness 还有 `DynamicSubagentsMiddleware` 路线（每个推理步重扫 `subagents/` 目录并原子换工厂表，`DefaultAgentManager#replaceAgents` :91-93），paw 在 `SubagentsMiddleware` 静态路线 + 进程级 `SessionsTool` 的组合下运行——即子 agent 类型在启动时固化，但类型集合本身来自 workspace 文件，删改 `.md` 后重启即生效（自进化的"冷更新"路径）。

### 5.4 会话生命周期

- **会话存储**：`SessionStore`——每 agent 一个 `agents/{agentId}/sessions.json`，读写锁 + 原子写，`StoredEntry` 记录跨重启需要的子集（`runtime/session/SessionStore.java:37-72`）。启动时 `SessionAgentManager` 构造函数 `restoreFromStore()` 全量装回内存（`SessionAgentManager.java:149-164`）。
- **重置**：聊天里的 `/new`、`/reset` 斜杠命令 → `ChatController#handleSlashCommand`（:296-323）→ `SessionAgentManager#resetSession`（:547-582）：换新 `sessionId`、保留 sessionKey/label/agent 绑定、**驱逐 `agentCache` 缓存实例**让下轮从干净状态开始；旧 transcript 留在磁盘交给 maintenance 清理。
- **定时维护**：`SessionLifecycleScheduler`（`web/session/SessionLifecycleScheduler.java:37-50`）按 `agentscope.json` 的 `session` 块驱动每日全量重置、每分钟空闲重置（默认关闭）、每 5 分钟 `runMaintenance()` 修剪过期/超量会话。
- **会话查询 API**：`GET /api/agents/{agentId}/sessions/inbox` 等端点（`web/api/SessionController.java:88-156`）直接读 `SessionAgentManager` 的会话视图 + workspace 里的 transcript 文件（`SessionTurnParser` 解析 JSONL 成 UI 轮次）。

---

## 6. workspace / 模板 / 脚手架 / marketplace / AI 草稿

### 6.1 三种资源目录各司其职

| 资源 | 位置 | 角色 |
|---|---|---|
| `scaffold/default/` | classpath | **默认脚手架**：无模板无草稿时给新 workspace 铺底 |
| `templates/{blank, customer-support, research-assistant}/` | classpath（+ 磁盘覆盖） | **可选启动模板**：创建 agent 时选装的整套 workspace 初始内容 |
| `catalog/mcp-servers.json` | classpath | **MCP 服务器目录**：纯 UI picker 数据（预置高德/魔搭/百炼/12306 等 MCP 接入模板），与 agent 定义无关（`web/api/AgentToolsController.java:411-423` 加载，:66-76 注释） |
| `prompts/agent-draft.md` | classpath | AI 起草 agent 配置时的提示词模板 |

### 6.2 WorkspaceScaffolder 与 TemplateRegistry

`WorkspaceScaffolder#scaffold`（`web/scaffold/WorkspaceScaffolder.java:75-90`）：建 `skills/`、`subagents/`、`memory/` 空目录骨架；从 classpath `scaffold/default/AGENTS.md.template` 渲染 `AGENTS.md`（替换 `{{NAME}}`/`{{SYSPROMPT}}` 占位符）；逐字拷贝 `tools.json`、`skills/example-skill/SKILL.md`、`subagents/README.md`；全部 **write-if-missing**（对已有 workspace 幂等）。类注释明确设计取向：随包发布的内容保持克制，更丰富的启动包走 opt-in 的 TemplateRegistry（:49-51）。

`TemplateRegistry`（`web/template/TemplateRegistry.java:48-64`）双来源：classpath `templates/*/template.json` 启动时扫描一次（`@PostConstruct`，:83-111）；磁盘 `${claw.home}/.agentscope/templates/<id>/` 每次 `list()/get()` 懒扫描，同 id 磁盘覆盖打包版。`instantiate(id, workspaceDir)`（:148-170）把模板目录下除 `template.json` 外的所有文件原子写入新 workspace（同样 write-if-missing）。模板内容即"半个 agent"：以 `customer-support` 为例，包含 `AGENTS.md`（系统提示）、`tools.json`（只读工具白名单——allow 只留读类工具、deny 掉 `execute`/`write_file`/`edit_file`）、`skills/triage-ticket/SKILL.md`、`subagents/escalation-router.md`（带 frontmatter 的子 agent 声明：name/description/tools + 提示词正文，落盘后经 §5.3 的 `buildSubagentEntries` 变成可 spawn 的子 agent 类型）。

**注意**：模板只产文件，不改 `StoredEntry`——即模板中的 `tools.json`/`subagents/*.md` 是运行时由 HarnessAgent 从 workspace 读到的配置，而不是 catalog 定义的字段。这是 paw"定义瘦身、能力进 workspace"哲学的体现。

### 6.3 AI 起草（AgentDraftService）

`POST /api/agents/draft`（`web/ai/AgentDraftController.java`）→ `AgentDraftService#draft`（`web/ai/AgentDraftService.java:101-123`）：用配置的 `Model` 以低温度单次调用 `prompts/agent-draft.md`（`{{DESCRIPTION}}` 替换），流式收集文本后宽松解析 JSON（剥 ``` 围栏、截取首尾大括号，:169-188）得到 `AgentDraft{name, description, sysPrompt, suggestedTools, suggestedSkills[], suggestedSubagents[]}`。用户确认后随 `POST /api/agents` 的 `aiDraft` 字段提交，`AgentCatalogService#writeDraftFiles`（:424-488）把它物化成 workspace 文件（`AGENTS.md`、`tools.json` 的 allow 列表、每个 skill 一个 `skills/<name>/SKILL.md`、每个 subagent 一个 `subagents/<name>.md`）。**AI 起草的产物同样全是文件**，与模板路径殊途同归。

### 6.4 marketplace 与 skill 的关系

`marketplace/` 包是**技能（skill）市场**，与 agent 创建无直接关系，但服务自进化：`ClawMarketplace` 接口（`marketplace/ClawMarketplace.java:27-49`）抽象"list + fetch 一个 skill"，有 `GitClawMarketplace`（git clone 到 `${claw.home}/marketplaces/git/<id>`）与 `NacosClawMarketplace` 两个实现。`ClawMarketplaceRegistry` 启动时按 `agentscope.json` 的 `marketplaces` 块实例化（`initFromBootstrapConfig`，`ClawMarketplaceRegistry.java:56-90`），替换/移除时主动 `close()` 释放 git/nacos 资源（:105-127）。用户在 UI 里从市场选 skill → `AgentSkillsController` 的 `marketplace-install` 端点把它拷进目标 agent 的 `workspace/skills/`（`web/api/AgentSkillsController.java:386` 起）→ agent 下一步推理即加载。它与 `agents.<id>.skillRepositories`（agent 运行时挂载的只读技能仓库）相互独立——前者是"安装进我的 workspace"，后者是"订阅远端仓库"。

---

## 7. paw vs dataagent 对照表

| 维度 | paw | dataagent |
|---|---|---|
| 定位 | 单用户本机个人助手 | 多租户 agent 平台 |
| 定义存储 | `agents.json` 单 JSON 文件（原子写 + 读写锁） | Spring Data JPA（`JpaUserAgentDefinitionStore`） |
| 定义模型字段 | StoredEntry：19 字段，无 scope/owner/share | AgentDefinition：+ scope、ownerId、shares、runAs、forkOf、sandboxMode/Scope、tierForCurrentUser 等 |
| **注册键** | **裸 `agentId`** | **`uca-{userId}-{agentId}`**（`UCA_PREFIX`，:72） |
| 内置 agent | 启动即构建（ClawBootstrap 三阶段），JVM 单例 | 同（BuilderBootstrap） |
| 自定义 agent 实例化 | 首次对话 `computeIfAbsent` 懒构建；update/delete 缓存失效重建 | 同构（`registeredUcaIds`） |
| 子 agent | workspace `subagents/*.md` + general-purpose；`agentCache` 按 sessionKey 缓存 | 同一套（SessionAgentManager 为同源 fork） |
| 隔离 | 无（`LocalFilesystemWithShell` 语义，直接本机 FS+Shell） | userId 命名空间 workspace + 可选 Docker 沙箱（`IsolationScope`） |
| 认证 | 无 | JWT + 用户表 |
| 独有能力 | IM 通道全家桶挂本地助手、workspace 文件级自进化、AI 起草、模板/脚手架、marketplace、aistio transcript 联调 | 分享/克隆/tier 权限、用量统计、活动流、身份链接、outbound tier ACL |

两段说明：

**为什么 paw 更简**：paw 的每一次"简"都源于同一个前提——只有一个用户。没有第二个人，就没有命名空间（注册键裸 id）、没有鉴权（permit-all）、没有按用户隔离 workspace、没有分享与权限层级、没有 JPA（单文件即够）。builder.md 记录的迁移史表明这是**刻意的功能剥离**而非未完成：多租户能力整体迁去了 agentscope-service，paw 保留的是"个人 agent 该长什么样"的参考实现。

**结构上仍与 dataagent 同源**：ClawBootstrap/AgentCatalogService/UserAgentDefinitionStore/SessionAgentManager/HarnessGateway/SessionsTool 六件套在两个工程里都能找到几乎同名的对应物（paw 的 gateway 468 行 vs dataagent 554 行，差异主要是 dataagent 版多用户路由与权限穿透）。可以把 paw 理解为 **dataagent 去掉多租户层之后的"最小同构体"**，反过来也可以把 dataagent 理解为 paw + 租户面。这印证了前一篇调研（§5.2）"paw 是 dataagent 姊妹版"的判断，并补全了细节：两者的 `runtime/session` 与 `runtime/gateway` 是各自维护的本地 fork，而非共享库代码。

另一个值得记录的发现：harness 库本身自带一个功能更全的默认网关（`io.agentscope.harness.agent.gateway.HarnessGateway`，847 行，支持 MessageBus/SubagentRegistry/跨节点恢复），但 paw 与 dataagent **都没有复用它**，而是各自 fork 了精简版以耦合自己的 `SessionAgentManager` 会话栈。示例作者显然认为"会话编排属于应用层"——这对理解 harness 的分层边界很有参考价值。

---

## 8. 对 nex-ai 的启示

1. **定义与实例分离 + 懒构建 + 失效重建**是轻量多 agent 管理的最小闭环：`POST /api/agents` 只写 JSON 定义与 workspace 脚手架（毫秒级），实例化推迟到首次对话的 `computeIfAbsent`；更新/删除只需 `map.remove(id)`，无需停机。nex-ai 的 agent 目录（若规划）可直接套这个三件套，代价极低。
2. **stable routing key（canonicalKey）→ sessionKey 单向映射 + per-key 公平锁**是会话串行化的简洁范式：`chatui|x:agentId=x` 这类组合键天然支持"同一 agent 多会话/多通道"，`LocalSessionTurnGate` 一把锁解决并发 turn 竞态，配合 `RuntimeContext.sessionId` 复用单例 agent 实例——比"每请求新建 agent"省得多，也比全局锁并发好得多。
3. **workspace 即配置面**：paw 把能力配置（`tools.json` 工具白名单、`subagents/*.md` 子 agent 声明、`skills/`、`AGENTS.md` 系统提示）全部做成 agent 自己可写的文件，"自进化"不需要任何管理端 API。nex-ai 若做 agent 技能/子 agent，文件化定义 + 启动扫描（`buildSubagentEntries`）是比数据库表更贴近 LLM 生态（Claude Code/Claude Skills 同构）的建模方式。
4. **catalog（只读目录展示）与 runtime registry（实例注册表）分成两个容器**：`UserAgentDefinitionStore`（全部定义）与 `gateway.agentRegistry`（已实例化者）各管各的，懒构建是两者之间唯一的桥。避免"定义列表 = 实例列表"的常见耦合。
5. **懒构建的两个已知瑕疵值得提前规避**：更新/删除后旧实例不被 `close()`（后台任务仓库/索引句柄泄漏风险）、删除后注册表残留。nex-ai 若采用同模式，应在失效时显式 `close()` 并从注册表摘除。

---

## 附：关键文件索引（均为 `<AS>/agentscope-examples/agents/agentscope-paw/src/main/java/io/agentscope/claw2/` 下相对路径）

| 文件 | 角色 | 本文主要引用点 |
|---|---|---|
| `Claw2App.java` | Spring 入口 | :38-42 |
| `runtime/ClawBootstrap.java` | 装配门面（Builder 三阶段） | :403-448 applyFileEntry；:544-693 build；:209-227 close |
| `runtime/config/AgentscopeConfig.java` / `AgentConfigEntry.java` | agentscope.json 模型 | :34-76 / :42-106 |
| `runtime/gateway/HarnessGateway.java` | paw 本地网关 | :197-210 注册；:242-272 run；:400-419 会话解析；:445-467 turn 锁 |
| `runtime/session/SessionAgentManager.java` | 会话/子 agent 编排 | :353-385 registerMainSession；:509-513 getOrCreateAgent；:547-582 resetSession |
| `runtime/session/SessionStore.java` | sessions.json 持久化 | :37-72 |
| `runtime/session/tool/SessionsTool.java` | sessions_spawn 等 4 工具 | :42-60 |
| `web/catalog/AgentCatalogService.java` | 动态创建/懒构建核心 | :136-206 create；:284-297 resolveGatewayAgentId；:354-380 buildAndRegisterCustom |
| `web/catalog/UserAgentDefinitionStore.java` | agents.json 存储 | :160-184 原子写；:193-279 StoredEntry |
| `web/catalog/AgentCatalogController.java` | /api/agents REST | :44-88 |
| `web/api/ChatController.java` | 对话入口 | :248-265 resolveRoute；:328-331 executeChat |
| `web/config/BuilderConfig.java` | Spring 装配 Bean | :105-115 模型；:150-227 builderBootstrap；:266-297 自动生成配置 |
| `web/scaffold/WorkspaceScaffolder.java` | 默认脚手架 | :75-90 |
| `web/template/TemplateRegistry.java` | 模板注册表 | :83-111 扫描；:148-170 instantiate |
| `web/ai/AgentDraftService.java` | AI 起草 | :101-123 draft |
| `marketplace/ClawMarketplaceRegistry.java` | 技能市场注册表 | :56-90；:161-175 build |

harness 侧引用：`<AS>/agentscope-harness/src/main/java/io/agentscope/harness/agent/` 下 `HarnessAgent.java`（:1069 builder()、:2178 buildSubagentEntries）、`HarnessAgentBuilderSupport.java`（:199-242 子 agent 条目、:290-387 general-purpose 工厂、:718-749 子 agent 中间件）、`subagent/DefaultAgentManager.java`（:154-160 createAgent）、`gateway/channel/ChannelRouter.java`（:82-118 七层路由）、`gateway/MsgContext.java`（:77-101 canonicalKey）、`gateway/channel/chatui/ChatUiChannel.java`（:149-158 dispatch）、`gateway/HarnessGateway.java`（harness 库自带版，本文 §7 用于对照）。
