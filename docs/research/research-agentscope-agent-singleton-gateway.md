# AgentScope Java：HarnessAgent 单例/常驻并发机制与 Gateway 网关调研

> 调研日期：2026-08-23
> 源码版本：2.0.3-SNAPSHOT（`/Users/jerry/agent-project/agentscope-java/pom.xml:30` `<revision>2.0.3-SNAPSHOT</revision>`）
>
> 调研问题：
> - 问题一：HarnessAgent 单例/常驻模式的实现机制（线程模型、隔离、官方实践、配置差异、实例失效/重建）
> - 问题二：网关（GatewayBootstrap / HarnessGateway）的实现与嵌入方式
>
> 方法：全部结论回溯到一手源码（本地仓库 `/Users/jerry/agent-project/agentscope-java/`，下文缩写 `ASJ/`），引用格式为 `相对路径:行号`；在线官方文档另注明 URL。本文只陈述事实与机制，不做架构决策。

---

## 结论速览

1. **官方明确支持单例常驻**：`HarnessAgent` 类 javadoc 原文「stateless between calls and safe to use as a singleton serving multiple users/sessions concurrently」（`ASJ/agentscope-harness/.../HarnessAgent.java:159-163`）；官方 Spring Boot starter 直接以单例 `@Bean` 注册 ReActAgent，注释「ReActAgent in 2.0 is thread-safe, so we just use a singleton instance」（`ASJ/.../AgentscopeAutoConfiguration.java:101-117`）。但**官方文档（going-to-production、agent 两页）并无「数千用户」字面表述**，该说法是转述；原文为 "a singleton handles concurrent requests" / "a single instance can serve multiple users and sessions concurrently"（在线文档，URL 见问题一第 3 节）。
2. **「同一 session 自动串行」的实现**：`ReActAgent.callSerializationKey()` 返回 `(userId, sessionId)` 槽位键（`ReActAgent.java:691-702`），`AgentBase.serializeOnKey()` 用 `callGates`（`ConcurrentHashMap<Object, Mono<Void>>`）尾部队列做 **FIFO 排队等待**（不是拒绝；`AgentBase.java:345-366`）；`streamEvents` 与 `call` 共用同一 lifecycle 与同一序列化门（`ReActAgent.java:996-1071`）。
3. **并发隔离三层**：① per-call `CallExecution` 作用域挂 Reactor Context（`ReActAgent.java:620-672、704-729`）；② 每次 call 从 `AgentStateStore` 重载该槽位状态（分布式下防本地缓存陈旧，`ReActAgent.java:607-615`）；③ workspace 侧由 `workspaceFactory` 按 `(userId, sessionId)` 生成绑定视图（`HarnessAgent.java:2360-2370`，公开 API `workspaceFor`，`HarnessAgent.java:248-253`）。
4. **`ConcurrentSessionModificationException` 是乐观锁失败信号，不是同实例串行机制**：仅当 `ConflictPolicy.FAIL`（或 `APPEND_MERGE` 合并再失败）且 CAS 版本冲突时抛出（`ReActAgent.java:530-605`）；默认策略 `OVERWRITE`（last-writer-wins，`ConflictPolicy.java:34-46`）。单实例内同槽位已被序列化门排队，该异常主要出现在**跨实例/跨节点写同一槽位**的场景。
5. **nex-ai 的 MEMORY.md 互踩有精确的框架级解释**：daily ledger 追加是「读→合并→写」，互斥靠 `WorkspaceManager` 实例内的 per-path `ReentrantLock`（`WorkspaceManager.java:118、368-392`）。**per-请求新建 HarnessAgent = 每实例一个新的 WorkspaceManager = 各持一把锁，锁彼此不可见 → 丢失更新**；单实例常驻后进程内同文件追加被串行化。默认 `flushTrigger = ALWAYS`（每次 call 都 flush，`MemoryConfig.java:228`），`MEMORY.md` 本体由 `MemoryConsolidator` 周期合并、flush 不直接写它（`MemoryFlushManager.java:221-239`）。
6. **官方示例的持有形态**：paw（ClawBootstrap）与 codingagent 均为 `Map<String, HarnessAgent>` 常驻注册表，每 agentId 一个实例，Spring `@Bean` 单例装配，仅在应用停机时统一 `close()`（`agentscope-examples/.../ClawBootstrap.java:95、203-228`；`BuilderConfig.java:150-227`）。**没有任何示例按请求新建实例**。
7. **paw 不用框架 GatewayBootstrap**：paw 自带一个同名 `HarnessGateway`（claw2 包内，`Gateway` 接口的另一实现），组合 `SessionAgentManager`（子代理会话产品化：sessions.json 持久化、MAIN/REVIEWER 会话、announce 分发），外加 agentscope.json 首启生成、WorkspaceScaffolder、ToolEventBus、transcript 注入——这些是 GatewayBootstrap（纯路由容器）不管的（`agentscope-examples/.../claw2/runtime/gateway/HarnessGateway.java:50-137`；`BuilderConfig.java:159-218`）。
8. **网关做了路由/会话映射/turn 互斥，没做认证/多租户/HTTP 传输**：框架 `HarnessGateway` 提供 canonicalKey→稳定 sessionId（确定性哈希）、per-key `SessionTurnGate`（默认本地公平信号量，可换分布式）、出站路由记录与主动投递、subagent 暴露注册表（`HarnessGateway.java:48-100`）；认证与租户身份靠 `ChannelRuntimeContextResolver` 扩展点由嵌入方注入（`GatewayBootstrap.java:249-257`）；SSE/HTTP 完全自建（`ChatUiChannel.java:38-41`「no external transport, no webhook, no websocket」；paw 的 SSE 在 `ChatController.java:107-167` 自建）。
9. **网关的 turn gate 与 agent 层序列化语义不同**：网关 `SessionTurnGate.acquire` 阻塞等待（本地实现，`LocalSessionTurnGate.java:22-43`），但 `TurnBusyException`（分布式实现的 try-acquire 语义）时直接 `Flux.empty()` **跳过本轮**（`HarnessGateway.java:722-747`）；agent 层 `serializeOnKey` 是排队。nex-ai 现状（`runningAgents.putIfAbsent` 抛 `SessionRunningException` 拒绝，`AgentscopeRuntimeGateway.java:186-193`）是第三种语义（立即拒绝）。
10. **单实例化后需要的配置改点集中在四处**：模型注册（`ModelRegistry` 是 JVM 级静态表，注册一次进程生效，但 nex-ai 现状「每次装配覆盖注册实现密钥即时生效」的机制随单例消失，`ModelRegistry.java:44-65` vs `AgentscopeRuntimeGateway.java:360-371`）；close 语义（常驻后 `close()` 只在失效/停机时调，`HarnessAgent.java:453-470`）；AGENTS.md 物化时机（框架每次 call 实时读盘，`WorkspaceContextMiddleware.java:184-201`，写盘时机归嵌入方）；内存治理（stateCache/permissionEngineCache 按 slot 常驻累积，有 `clearStateCache` API，`HarnessAgent.java:370-393`）。

---

## 问题一详查：agent 单例/常驻模式的实现机制

### 1.1 HarnessAgent 的线程模型与「同一 session 自动串行」的实现

**类 javadoc 原文**（`ASJ/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:159-163`）：

> `<b>Thread Safety:</b> {@code HarnessAgent} is stateless between calls and safe to use as a singleton serving multiple users/sessions concurrently. Each {@code call()} uses the {@link io.agentscope.core.agent.RuntimeContext}'s {@code (userId, sessionId)} to isolate state. Calls targeting the same session are serialized automatically; different sessions run in parallel.`

**串行化的具体实现——两层锁**：

(1) agent 层序列化门（`AgentBase`，core 模块）：

- `AgentBase` 持有 `private final ConcurrentHashMap<Object, Mono<Void>> callGates`，javadoc：「Per-key call serialization tails. Each entry holds the completion signal of the most recently enqueued call for that key; the next call for the same key chains after it, so calls sharing a key run one-at-a-time (FIFO) while different keys run concurrently.」（`ASJ/agentscope-core/src/main/java/io/agentscope/core/agent/AgentBase.java:107-113`）。
- 扩展点 `callSerializationKey(RuntimeContext)`：基类默认返回 `null`（不序列化，`AgentBase.java:325-337`）；`ReActAgent` 覆写为 `(userId, sessionId)` 槽位键（`ASJ/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:691-702`，javadoc：「Serialize calls per (userId, sessionId) slot: same-session calls share cached AgentState / conversation history, so they must run one-at-a-time; distinct sessions run in parallel.」）。槽位键格式 `userId/sessionId`（匿名用户为 `__anon__`，`ReActAgent.java:390-394`）。
- 排队实现 `serializeOnKey(key, action)`（`AgentBase.java:339-366`）：`callGates.compute` 原子地把本调用的完成信号（`Sinks.empty`）接成新尾巴，先 `prev.onErrorComplete().then(action)` 等前一个调用终止，`doFinally` 中释放并移除自己的条目——**失败/取消的调用不会卡死队列**（javadoc `AgentBase.java:340-344`）。
- 组装点 `runLifecycle`（`AgentBase.java:246-298`）：`Mono.using(acquireExecution, ...)` 外壳 + `Mono.deferContextual` 内读取 per-subscription 的 `RuntimeContext`，javadoc 明确「Build the per-call lifecycle lazily so it only runs once the serialization gate (if any) admits this call: beforeAgentExecution resolves/loads the session slot and must not race a concurrent same-session call」（`AgentBase.java:270-274`）。`acquireExecution` 仅向 `GracefulShutdownManager` 注册请求，不是并发限制（`AgentBase.java:478-481`）。

(2) 流式路径同样受门约束：

- `streamEvents` 与 `call` 共享 `buildAgentStream` 核心，javadoc「The stream is bookended by AgentStartEvent / AgentEndEvent, wraps the full AgentBase.runLifecycle (shutdown guard, serialization gate, pre/post hooks, tracing)」（`ReActAgent.java:995-1010`，实现 `1024` 行直接调 `runLifecycle`）。`streamEvents(List, RuntimeContext)` 的 javadoc 进一步说明「Concurrent invocations do not share any state; each subscription gets its own event sink」（`ReActAgent.java:1101-1105`）。

**`ConcurrentSessionModificationException` 的抛出条件**（与串行门互补的第二道防线，作用于**持久化保存**）：

- 保存逻辑（`ReActAgent.java:530-605`）：turn 结束时 `stateStore.saveIfVersion(userId, sessionId, "agent_state", toSave, expectedVersion)` 做乐观并发（CAS）保存；CAS 失败后按 `conflictPolicy` 分支：
  - `OVERWRITE`（默认）：无条件覆写并打 warn 日志（`ReActAgent.java:537-564`）；
  - `FAIL`：直接 `throw new ConcurrentSessionModificationException(userId, sessionId, "agent_state", expectedVersion)`（`ReActAgent.java:565-567`）；
  - `APPEND_MERGE`：重载最新基线、追加本轮新消息后重试 CAS，再失败仍抛该异常（`ReActAgent.java:568-601`）。
- `ConflictPolicy` 枚举 javadoc：`FAIL`「Recommended when a distributed session turn gate is enabled」；CAS 失败发生在 turn 末尾、回复已流出，无法回滚（`ASJ/agentscope-core/src/main/java/io/agentscope/core/state/ConflictPolicy.java:18-46`）。异常类本身 javadoc：「Thrown when an optimistic-concurrency save fails because another writer changed the same session slot since it was loaded」（`ASJ/agentscope-core/src/main/java/io/agentscope/core/state/ConcurrentSessionModificationException.java:18-22`）。
- 含义：单实例内同槽位已被 `serializeOnKey` 排队，正常不会走到 CAS 冲突；该异常面向**多实例/多节点写同一 `(userId, sessionId)`** 的场景（例如 nex-ai 当前 per-请求多实例、或分布式部署无 turn gate 时）。

### 1.2 单实例并发多用户时的隔离与共享可变状态

**RuntimeContext 寻址 + AgentStateStore 槽位**（每次调用的状态装载）：

- `activateSlotForContext(RuntimeContext)`（`ReActAgent.java:607-672`）：从 RC 取 `(userId, sessionId)`（缺失回落 `defaultSessionId`），配置了 `stateStore` 时**每次调用都从 store 重载最新持久化状态**（javadoc：「the state is always reloaded from the store at the beginning of each call so that distributed deployments … see the latest persisted state rather than a stale local cache entry」，`ReActAgent.java:612-615`），并写入三个按槽位键索引的 `ConcurrentHashMap` 缓存：`stateCache`（`ReActAgent.java:284-285`）、`slotVersions`（`ReActAgent.java:287-292`）、`permissionEngineCache`（「runtime-added ASK rules accumulate within the owning slot rather than leaking across users / sessions」，`ReActAgent.java:297-302`）。
- 构造 per-call `CallExecution` 作用域并挂在 Reactor Context（`CALL_SCOPE_KEY`），javadoc：「concurrent calls on one instance never share mutable per-call state」（`AgentBase.java:219-224`）；`beforeAgentExecution` 把 call 级 `AgentState` 暴露到 RC：`ctx.setAgentState(scope.state)`，注释「call-scoped, concurrency-safe」vs `agent.getAgentState()`「not call-scoped under concurrency」（`ReActAgent.java:710-719`）。
- `RuntimeContext` 本身是不可变快照（`private final String sessionId / userId` + 属性表，`ASJ/agentscope-core/src/main/java/io/agentscope/core/agent/RuntimeContext.java:33-57`），每次调用由调用方传入新实例。
- `HarnessAgent.ensureSessionDefaults`（`HarnessAgent.java:959-995`）：补默认 sessionId、注入默认 `SandboxContext` / `WorkspaceManager` / `WorkspacePathNormalizer` 到 RC——持久化后端在 builder 时经 `.stateStore(...)` 绑定一次，per-call 只做路由（javadoc `HarnessAgent.java:960-964`）。

**内存中的共享可变状态盘点（单实例上的事实清单）**：

| 组件 | 共享形态 | 并发处理 | 源码 |
|---|---|---|---|
| `Toolkit` | agent 实例级单例（build 时深拷贝，javadoc「Shared across this agent's concurrent calls」） | `ToolGroupManager.activeGroups` 为 `volatile CopyOnWriteArrayList`；每次调用槽位激活时会 `toolkit.setActiveGroups(loaded.getToolContext().getActivatedGroups())`（共享字段被最后激活的调用改写），但**模型可见 schema 走 per-call 变体** `toolkit.getToolSchemas(state.getToolContext().getActivatedGroups())`，从 call 级 state 解析，不受共享字段竞态影响 | `ReActAgent.java:241-249、668-670、2293-2294`；`ASJ/agentscope-core/src/main/java/io/agentscope/core/tool/Toolkit.java:339-350、721-722`；`ToolGroupManager.java:38` |
| `WorkspaceManager` | 实例级单例 + `workspaceFactory` 每 `(uid, sid)` 生成 `BakedContextFilesystem` 绑定视图 | 共享 filesystem 被 Baked 包装；`workspaceFor(uid, sid)` javadoc：「does not mutate any shared state on this agent — so it is safe to call concurrently from per-request controllers without racing with active chats」 | `HarnessAgent.java:242-253、2353-2370` |
| per-path 写锁 `pathLocks` | **WorkspaceManager 实例内** `Map<String, ReentrantLock>`——单实例=进程内同文件互斥；多实例=锁失效 | `appendUtf8WorkspaceRelative` / `updateSessionIndex` 的读→合并→写全程持锁，javadoc「A per-path ReentrantLock serialises concurrent callers so that the read→merge→write cycle is atomic within this process」 | `WorkspaceManager.java:118、368-392、404-412` |
| middleware 实例 | build 时构造一次，所有调用共享（`HarnessAgent.Builder.build()` 内逐个 `inner.middleware(...)`） | 官方约定请求态不得存 middleware 字段；后台节流任务由 `PeriodicGate` 协调 | `HarnessAgent.java:2386-2463` 等 |
| `activeRc` | `volatile`，仅「most-recently-active」快照 | javadoc：「under concurrent calls on one instance this reflects the latest call — middlewares/tools that need their own call's context should read it from the per-subscription RuntimeContext」 | `ReActAgent.java:281-282`；`AgentBase.java:562-572` |
| 内部网关 `internalGateway` | `volatile HarnessGateway`，首次 `channel()/gateway()` 时惰性创建（synchronized 双检） | — | `HarnessAgent.java:196-197、607-663` |

**记忆文件写入的并发语义（与互踩问题直接相关）**：

- flush 不直接写 `MEMORY.md`：「MEMORY.md is intentionally NOT touched here — it is owned by MemoryConsolidator, which periodically merges the daily ledgers into a curated, size-bounded MEMORY.md」（`ASJ/agentscope-harness/.../memory/MemoryFlushManager.java:219-239`；flush 只追加 `memory/{yyyy-MM-dd}.md` daily ledger）。
- flush 触发默认 `ALWAYS`（每次 call 后，`MemoryConfig.java:228` `private FlushTrigger flushTrigger = FlushTrigger.always()`）；`THROTTLED` 模式下经 `PeriodicGate.tryClaim(compositeTimerKey)` 节流，key = `isolationScope + ":" + (userId|sessionId|"")`（`ASJ/agentscope-harness/.../middleware/MemoryFlushMiddleware.java:200-244`）。
- `IsolationScope` javadoc 承认并发面：「`AGENT` / `GLOBAL` — one shared window for the whole agent instance (**prevents concurrent flush races on shared memory files**)」（`MemoryFlushMiddleware.java:63-66`）；`USER`（默认）为每 userId 一个窗口。namespace 侧：`USER → userId 前缀（无 userId 回落 sessionId）、AGENT/GLOBAL → 无前缀`（`ASJ/agentscope-harness/.../IsolationScope.java` 的 `toNamespaceFactory()` 段）。
- **对 nex-ai 互踩的机制解释**：平台级/租户级 spec 下同一 user 开两个会话 = 两个并发流；现状每流一个新 HarnessAgent 实例 → 两个 `WorkspaceManager` → 两把互不相干的 `pathLocks` → 同一 `memory/{date}.md` 的 read→merge→write 交错丢失更新（若 consolidation 同时跑，`MEMORY.md` 同理）。单实例常驻后，同一 `WorkspaceManager` 的 per-path 锁将进程内追加串行化；跨 JVM 仍需分布式手段（`StoreBackedPeriodicGate` / `DistributedStore`，`ASJ/agentscope-harness/.../coordination/StoreBackedPeriodicGate.java:29-34`）。

### 1.3 官方「单实例服务多用户」的实践出处

**框架 starter（最直接的官方形态）**：核心自动配置把 ReActAgent 注册为**单例 bean**：

- `ASJ/agentscope-extensions/agentscope-spring-boot-starters/agentscope-spring-boot-starter/src/main/java/io/agentscope/spring/boot/AgentscopeAutoConfiguration.java:96-117`：javadoc「ReActAgent in 2.0 is thread-safe, so we just use a singleton instance.」；`@Bean`（默认 singleton scope）+ `@ConditionalOnMissingBean` + `@ConditionalOnBean(Model.class)`。
- 同文件中 `Memory`、`Toolkit` 反而被标注 prototype（「stateful and not thread-safe」，`AgentscopeAutoConfiguration.java:60-94`）——注意这是 v1 遗留命名（指 v1 Memory 组件），v2 的 `Toolkit` 在 agent 内是深拷贝单例（见 1.2）。

**在线官方文档**（与本地源码同版语义，2026-08 检索）：

- Going to Production（`https://java.agentscope.io/v2/en/docs/others/going-to-production.html`）：第 7 节示例注释「Singleton agent (created once at startup)」；原文「The agent is stateless between calls — a singleton handles concurrent requests.」「Each call() locates state via RuntimeContext's (userId, sessionId), fully isolated.」「Different sessions run concurrently on the same agent instance」；陷阱清单「without a sessionId, all requests share the defaultSessionId state, causing cross-talk」。
- Agent 文档 Multi-user Concurrency 小节（`https://java.agentscope.io/v2/en/docs/building-blocks/agent.html`）：「ReActAgent is stateless between calls — a single instance can serve multiple users and sessions concurrently.」「Calls targeting the same (userId, sessionId) are serialized」「Calls targeting different sessions run in parallel.」
- **注**：两页均无「thousands of users」字面表述（已逐页核实）；README 的对应表述是「AgentScope 2.0 is built for stateless horizontal scaling」（`ASJ/README.md:191`）。

**paw（agentscope-paw）的持有形态**：

- `ClawBootstrap` 是 Spring `@Bean` 单例（`ASJ/agentscope-examples/agents/agentscope-paw/src/main/java/io/agentscope/claw2/web/config/BuilderConfig.java:150-227`），进程启动时 `builder.build()` 一次。
- 内部持有 `private final Map<String, HarnessAgent> agents`——**每 agentId 一个常驻实例，不是全局一个、也不是每请求一个**（`ASJ/.../claw2/runtime/ClawBootstrap.java:95、688`（`Map.copyOf(built)`）、`agents()` 访问器 `ClawBootstrap.java:251-254`）。类 javadoc：「produces HarnessAgent instances wired with SessionsTool and a shared SessionAgentManager + HarnessGateway」（`ClawBootstrap.java:62-68`）。
- 生命周期：`ClawBootstrap implements AutoCloseable`，`close()` 停 channels 后逐个 `agent.close()`（`ClawBootstrap.java:203-228`），仅应用停机时触发（Spring `@Bean` 默认推断 destroy 方法）；javadoc「Closing agents releases background task repositories and workspace indexes」（`ClawBootstrap.java:203-207`）。
- web 层调用链：`ChatController`（SSE 端点 `POST /api/agents/{agentId}/chat/stream`，`ASJ/.../claw2/web/api/ChatController.java:106-167`）→ `executeChat` → `gateway.run(route.context(), msgs, route.outboundAddress())`（`ChatController.java:328-331`）→ paw 自己的 `HarnessGateway.run` → `withGatedTurn(gateKey, () -> ha.call(...))`（`ASJ/.../claw2/runtime/gateway/HarnessGateway.java:242-272`）。

**codingagent 同款**：`CodingBootstrap` 同样持有 `Map<String, HarnessAgent> agents`（`ASJ/agentscope-examples/agents/agentscope-codingagent/src/main/java/io/agentscope/harness/coding/CodingBootstrap.java:120`；builder 逻辑 `656` 行起）。其会话级子 agent 由 `SessionAgentManager` + `agentCache`（`ConcurrentHashMap<String, Agent>`，`ASJ/.../coding/session/SessionAgentManager.java:101`）另行管理——示例把「常驻主 agent」与「per-session 子 agent」分层。

**GatewayBootstrap 是否官方单例容器**：它是官方的**多 agent + channel 路由**装配入口（见问题二），持有 `Map<String, HarnessAgent>` 并 `bindMainAgent`（`ASJ/agentscope-harness/.../gateway/GatewayBootstrap.java:87-101、289-296`）；但它不是唯一形式——paw/codingagent 用自己的 `Gateway` 实现 + 自建 bootstrap。官方单例实践的核心证据是 starter 单例 bean 与两个示例的常驻 Map，而非 GatewayBootstrap 本身。

### 1.4 per-agent 常驻实例的配置差异（单例 vs per-请求新建）

| 配置项 | per-请求新建（nex-ai 现状） | 单例常驻 | 源码依据 |
|---|---|---|---|
| `stateStore` | 每次装配传入（同一 provider 实例，`AgentscopeRuntimeGateway.java:230`） | builder 时绑定一次；per-call 只按 `(userId, sessionId)` 路由（`HarnessAgent.java:960-964` javadoc「The agent's persistence backend is bound at builder time via .stateStore(...)」） | 两者传法相同，差别只在次数 |
| `ModelRegistry` | 每次装配覆盖注册名（nex-ai 借此实现「渠道密钥变更即时生效」，`AgentscopeRuntimeGateway.java:360-371`） | `ModelRegistry` 是 **JVM 级静态注册表**（`static ConcurrentHashMap namedModels`，`ASJ/agentscope-core/src/main/java/io/agentscope/core/model/ModelRegistry.java:44-65`），`register` 一次进程内全局生效、精确名匹配优先（`resolve` 先查命名注册，`ModelRegistry.java:99-129`）；单例下模型在 build 时固定（builder `.model(String)` 按名解析），密钥/模型变更需要：重建实例、或改用 `registerFactory`（regex 匹配 + 新注册优先，`ModelRegistry.java:76-96`） | `ModelRegistry.java:44-96` |
| `Toolkit` / MCP | 每实例新建 `Toolkit` + 重新注册工具（`AgentscopeRuntimeGateway.java:283-287`） | 一个实例一个 Toolkit（build 深拷贝，`ReActAgent.java:241-246`）；工具与 MCP 客户端（`McpClientManager`，`Toolkit.java:46-47、97-103`）注册一次、生命周期常驻 | — |
| workspace | 每实例 `new WorkspaceManager(...)`（`HarnessAgent.java:2353-2355` 在 build 内）——per-path 锁随之每实例一把 | 单 WorkspaceManager + per-(uid,sid) 视图工厂；per-path 锁进程内唯一 | `HarnessAgent.java:2353-2370`；`WorkspaceManager.java:118` |
| `PeriodicGate`（memory flush/consolidation 节流） | 每次重建 middleware 实例；**`LocalPeriodicGate` 用 static map 保证窗口跨 build 存活**——javadoc 明写这是为 per-request rebuild 兜底：「The map is static so the throttle window survives across HarnessAgent.Builder.build() calls — each rebuild creates new middleware instances, and an instance-level map would reset to Instant.EPOCH on every request」（`ASJ/.../coordination/LocalPeriodicGate.java:24-40`） | gate 实例常驻；多节点需 `StoreBackedPeriodicGate`（`BaseStore.putIfVersion` CAS，`StoreBackedPeriodicGate.java:30-34、69`；build 时按 `distributedStore` 自动选择，`HarnessAgent.java:2277-2280`） | — |
| 子代理 `TaskRepository` 等后台资源 | close 即释放 | 常驻（`TaskRepository.shutdown()` 仅在 `HarnessAgent.close()` 内触发，`HarnessAgent.java:472-482`） | `HarnessAgent.java:453-470` |

### 1.5 实例失效/重建：官方的版本热更与刷新机制

- **框架层没有 agentscope.json 热重载/实例刷新 API**：`GatewayBootstrap.build()`、`ClawBootstrap.build()` 均为一次性装配（`GatewayBootstrap.java:264-329`；`ClawBootstrap.java:560-703`），无 watch/reload 入口（全仓库无相关实现）。
- **paw 的懒重建模式（官方示例的「失效重建」答案）**：自定义 agent 定义更新后执行 `registeredCustomIds.remove(agentId)`，注释「Drop cached gateway registration so the next conversation rebuilds with new definition」（`ASJ/.../claw2/web/catalog/AgentCatalogService.java:253`）；下次对话经 `registeredCustomIds.computeIfAbsent(agentId, k -> buildAndRegisterCustom(entry))` 懒构建并 `registerAgent` 回网关（`AgentCatalogService.java:284-297、354` 起）。即：**注册表移除 + 按需重建新实例**，旧实例不主动 close（由 GC / 后续统一处理；paw 对内置 agent 则要求重启生效）。
- **subagent spec 的热更不需要重建实例**：`DynamicSubagentsMiddleware` 在**每次 reasoning step** 重新解析子代理清单（`onReasoning → reloadEntries(rc)`，`ASJ/agentscope-harness/.../middleware/DynamicSubagentsMiddleware.java:148-190`）；两层加载：Layer 1 `filesystem.glob("*.md", "subagents")` + 逐文件读（走 namespace，per-user 隔离）、Layer 2 本地 workspace `subagents/` 目录扫描（`AgentSpecLoader.loadFromDirectory`），同名 Layer 1 覆盖 Layer 2，builder 静态注册项作前缀（类 javadoc `DynamicSubagentsMiddleware.java:52-64`）。
- **人格/记忆文件热读**：`WorkspaceContextMiddleware.onSystemPrompt` 在**每次 call** 读 `AGENTS.md` / `MEMORY.md` / `KNOWLEDGE.md` 拼进系统提示（`ASJ/agentscope-harness/.../middleware/WorkspaceContextMiddleware.java:184-201`）——文件变更下次调用即生效，无需重建实例（写盘时机归嵌入方；nex-ai 现状是每装配物化比对覆写，`AgentscopeRuntimeGateway.java:331-349`）。
- **skills**：`AgentSkillRepository` 在 build 时注入列表（`HarnessAgent.java:175、264-266`），无运行时刷新 API；skill 仓库内容本身（文件）按需读取。

---

## 问题二详查：网关（GatewayBootstrap / HarnessGateway）的实现

### 2.1 GatewayBootstrap 的完整 API 与装配物

类定位 javadoc：「Multi-agent + channel routing bootstrap. The primary user-facing entry point for building a gateway-managed agent system.」（`ASJ/agentscope-harness/src/main/java/io/agentscope/harness/agent/gateway/GatewayBootstrap.java:35-38`），含单 agent、多 agent 绑定路由、外部 channel 三段用法示例（`GatewayBootstrap.java:39-83`）。

**Builder 方法**：

| 方法 | 作用 | 行号 |
|---|---|---|
| `agent(String id, HarnessAgent)` | 注册预构建实例 | `GatewayBootstrap.java:187-193` |
| `agent(String id, Consumer<HarnessAgent.Builder>)` | lambda 声明式构建（先应用全局 customizer） | `GatewayBootstrap.java:195-209` |
| `mainAgent(String id)` | 主 agent（路由兜底；未设置取第一个） | `GatewayBootstrap.java:211-218` |
| `channel(Channel...)` | 注册外部 channel（Slack/Telegram 等） | `GatewayBootstrap.java:220-226` |
| `configureAllAgents(Consumer<Builder>)` | 对所有 lambda 构建的 agent 应用公共配置（共享 model/workspace） | `GatewayBootstrap.java:228-235` |
| `distributedStore(DistributedStore)` | 构建 durable `SubagentRegistry`（跨节点/重启解析 exposed subagent；缺省回落主 agent 自己的 store） | `GatewayBootstrap.java:237-247` |
| `runtimeContextResolver(ChannelRuntimeContextResolver)` | 每 turn 组装 RC 前的替换扩展点——javadoc「Useful for attaching tenant / auth / tool dependencies from the surrounding request」 | `GatewayBootstrap.java:249-257` |

**`build()` 装配物**（`GatewayBootstrap.java:259-329`）：`ChannelManager`（注册 channels）→ `HarnessGateway.create(cm)` + resolver 注入（`284-287`）→ `bindMainAgent(main)` + 其余 `registerAgent`（`289-296`）→ 组合 subagent materializer（逐 agent 的 `DefaultAgentManager` 依次尝试重建，`298-319`）→ 有 store 则 `StoreBackedSubagentRegistry`（`320-326`）。

**运行期 API**：`gateway()` / `channelManager()` / `agents()`（不可变 Map）/ `mainAgentId()`（`GatewayBootstrap.java:108-126`）；`gatewayBridge()`（把 subagent 暴露为用户可寻址线程，`128-139`）；`chatUiChannel()`（默认 `DmScope.MAIN`——「All conversations share a single session」）/ `chatUiChannel(ChannelConfig)`（`141-155`）；`start()`（`channelManager.initAll + startAll`）/ `stop()`（`157-170`）。

**HarnessAgent 侧捷径**：单 agent 场景可不经 GatewayBootstrap——`agent.channel(ChatUiChannel.perPeer())` 惰性创建内部网关并把自己注册为唯一 main agent（`HarnessAgent.java:590-611`；`ensureGateway` `622-663`：创建 `HarnessGateway` + `bindMainAgent` + subagent bridge/materializer + 可选 store/turnGate 注入）。

### 2.2 HarnessGateway：agent 寻址、channel 体系与完整调用链

**类定位**（`ASJ/agentscope-harness/.../gateway/HarnessGateway.java:48-61`）：「Routes inbound turns by MsgContext#canonicalKey(), serializes concurrent turns per session via SessionTurnGate, and dispatches to the appropriate registered HarnessAgent」；四项能力：多 agent 注册表 + main 兜底、稳定 session 映射（同 canonicalKey → 同 sessionId，各自记忆）、per-session 公平互斥、出站地址跟踪（主动投递）。

**agent 寻址**：`agentRegistry`（`ConcurrentHashMap<String, HarnessAgent>`）+ `mainAgent`（`AtomicReference`）+ `defaultAgentId`（`HarnessGateway.java:73-75`）；`resolveAgent`：`MsgContext.extra().get("agentId")` 指定 → 注册表命中，否则 main（`HarnessGateway.java:519-528`；extra 提取 `212-213、258`）。`bindMainAgent` 同时以 agent 自身 id 注册（`158-165`）。

**会话映射**：`gateKey = ctx.canonicalKey()`（`210`）→ `sessionId = "gw-" + deterministicHash(gateKey)`（`535-537`）；`resolveSessionId` 更新 `sessionToGateKey` 本地缓存并写穿 `BaseStore`（namespace `["gateway","sessions"]`，`571-578、818-833`）——跨节点可恢复。

**channel 体系**（`ASJ/.../gateway/channel/` 包）：

- `Channel` 接口（init/start/stop/channelId/config/dispatch/dispatchStream/deliver/applyRoutingConfig）+ `ChannelManager`（注册与生命周期、出站投递）+ `ChannelRouter`（按 `ChannelConfig` 解析路由）。
- `DmScope` 枚举（`channel/DmScope.java:18-40`）：`MAIN`「all DMs for a given agent share a single session. Suitable for single-user or shared assistant scenarios」；`PER_PEER`「one session per peer id. **Most common for multi-user deployments**」；`PER_CHANNEL_PEER`（同 peer 跨 channel 区分）；`PER_ACCOUNT_CHANNEL_PEER`（多 bot 账号）。默认 `MAIN`（`defaultScope()`，`37-40`）。
- `ChatUiChannel`（`channel/chatui/ChatUiChannel.java`）：类 javadoc「no external transport, no webhook, no websocket — the caller submits a ChatUiRequest programmatically and receives the agent reply reactively」（`38-41`）；`send(String peerId, String text)` → `ChatUiRequest.withPeer` → `dispatch(InboundMessage)` → `router.resolveRoute(config, message)` → `gateway.run(route.context(), messages, outbound, runtimeContext, message)`（`232-237、149-159`）；流式 `sendStream` → `dispatchStream` → `gateway.runStream(...)`（`320-331`）。`SendOptions` 重载可显式指定 `userId`/sessionKey，绕过 DmScope（`252-284、432-441`）。

**paw web 层完整链路**（用户提到的 `chat.send("user-123","hello")` 形态在框架侧对应 `ChatUiChannel.send`，paw 实际走自建 controller）：

1. HTTP `POST /api/agents/{agentId}/chat/stream`（SSE，`ChatController.java:106-167`）；
2. `executeChat` → `resolveRoute`（与 ChatUiChannel 同一 `ChannelRouter` 逻辑预演，保证 gateKey 一致，`ChatController.java:225-265、328-331`）→ paw `HarnessGateway.run`；
3. paw 网关 `run`：`resolveOrCreateMainSession(gateKey, ha)`（`SessionAgentManager` 登记 MAIN 会话、过期滚动）→ 组 `RuntimeContext(sessionId, sessionKey)` → `withGatedTurn(gateKey, () -> ha.call(messages, rc))`（`claw2/runtime/gateway/HarnessGateway.java:242-272、400-419`）；
4. 框架 agent 层再进 `serializeOnKey` 槽位门（1.1 节）。

**turn gate 语义**：`withGatedTurn / withGatedStream` 在 `Mono/Flux.defer` 内 `sessionTurnGate.acquire(gateKey)`，`doFinally` 释放 lease，`subscribeOn(boundedElastic)`（框架版 `HarnessGateway.java:694-748`）。默认 `LocalSessionTurnGate`：每 key 一把公平 `Semaphore(1, true)`，`acquire` **阻塞等待、从不抛 TurnBusyException**（`ASJ/.../gateway/LocalSessionTurnGate.java:21-43`）；抛 `TurnBusyException` 的是 try-acquire 语义实现（如分布式门），此时网关**直接 `Mono.empty()/Flux.empty()` 跳过本轮**而非排队（`HarnessGateway.java:698-709、727-738`）。分布式门经 `setSessionTurnGate` 注入（`435-439`；`DistributedStore.sessionTurnGate()` 挂接点在 `HarnessAgent.ensureGateway`，`HarnessAgent.java:654-659`）。

**主动投递与唤醒**：`lastRouteBySession` 记录最近出站地址（写穿 `["gateway","routes"]`，`99-100、835-846`）；`deliverToSession`（`335-346`）；后台任务/团队消息经 `WakeupDispatcher` → `runWakeup(userId, sessionId)` 以空输入触发一轮（含 userId 槽位对齐，`640-674`）。

**exposed subagent**：`exposeSubagent` 生成 `sub-xxxxxxxx` 句柄、进 `exposedSessions` 缓存 + `SubagentRegistry`（默认 `InMemorySubagentRegistry`，有 store 则 `StoreBackedSubagentRegistry` 跨节点；`369-411、84-96`）；`resolveExposed` 未命中缓存时经 `SubagentMaterializer` 重建（`447-482`）；`runSubagent/runSubagentStream` 直接驱动该 agent（`484-565`）。

### 2.3 网关做了什么 vs 没做什么

**做了**（均为源码可证）：

- 多 agent 注册/寻址（agentId 路由 + main 兜底）；
- 稳定会话映射：canonicalKey → 确定性 hash sessionId，本地缓存 + BaseStore 持久化（跨节点恢复）；
- per-session turn 互斥（SessionTurnGate，可插拔本地/分布式）；
- 每 turn 的 `RuntimeContext` 组装：`callerContext`（可被 `ChannelRuntimeContextResolver` 整体替换）→ 网关身份字段（sessionId/userId/msgContext/gateKey/outboundAddress）**强制覆盖**（`buildRuntimeContext`，`HarnessGateway.java:279-329`）；
- 出站路由记录与主动投递、wakeup 分发、exposed subagent 注册与跨节点重建。

**没做（嵌入方自建）**：

- **认证/授权/多租户强制**：框架网关无任何 auth 代码；租户/身份注入靠 `ChannelRuntimeContextResolver` 扩展点（javadoc 原文即说该扩展点用于「attaching tenant / auth / tool dependencies from the surrounding request」，`GatewayBootstrap.java:249-252`）；官方 going-to-production 明言租户维度自己拼：「Compose any other dimensions (tenant, agent) into the sessionId string yourself」（在线文档 going-to-production 第 1 节）。
- **HTTP/SSE 传输**：`ChatUiChannel` 无 transport（`ChatUiChannel.java:38-41`）；SSE 由嵌入方实现——paw 用 Spring WebFlux `Flux<ServerSentEvent<String>>` 自定义 `token/tool_call/tool_result/done/error` 帧协议，且工具事件走自建 `ToolEventBus` merge 进流（`ChatController.java:106-167、208-223`）。
- **会话列表/历史/管理面**：paw 为此自建 `SessionAgentManager` + `SessionStore`（`sessions.json` 持久化、MAIN/REVIEWER/SUBAGENT 会话种类、新鲜度/重置策略、维护调度；`ASJ/.../claw2/runtime/session/SessionAgentManager.java` 等）。
- **每用户并发限流/排队策略**：gate busy 是「跳过」，排队语义在 agent 层（1.1），拒绝语义需嵌入方自加（nex-ai 现状的 `SessionRunningException` 即此类）。

### 2.4 ClawBootstrap（paw 的包装）与 GatewayBootstrap 的关系

paw **没有使用**框架 `GatewayBootstrap`，而是自带 bootstrap + 自带 `Gateway` 实现。差别（全部为源码事实）：

| 维度 | 框架 GatewayBootstrap | paw ClawBootstrap |
|---|---|---|
| 网关实现 | `io.agentscope.harness.agent.gateway.HarnessGateway`（session 映射=hash、无 session 产品化） | `io.agentscope.claw2.runtime.gateway.HarnessGateway`（同名不同类，实现同一 `Gateway` 接口；组合 `SessionAgentManager`） |
| agent 来源 | 纯编程（`agent(...)`/lambda） | `${clawHome}/agentscope.json` 文件 + 编程合并（`ClawBootstrap.java:570-583`；首启自动生成最小配置 + `WorkspaceScaffolder.scaffold`，`BuilderConfig.java:258-297`） |
| 会话基础设施 | 网关内 hash 映射 + 可选 BaseStore | 共享 `SessionAgentManager`（`sessions.json`、session kind、reset/freshness 策略、announce/spawn 拦截器）+ `SessionsTool` 注入每个 agent（`ClawBootstrap.java:585-623` Phase 1） |
| 额外装配 | — | `ToolEventBus`/`ToolNotificationMiddleware` 全局注入（`BuilderConfig.java:180`）、共享 transcriptStore（`182-187`）、aistio 观测 middleware（`189-192`）、`OutboundTool` 主动推送工具（`ClawBootstrap.java:619、632-646`） |
| 生命周期 | `start()/stop()` 管 channel；agent 关闭归调用方 | `close()` 统一 stop + 逐 agent `close()`（`ClawBootstrap.java:196-216`） |

paw 不用裸 GatewayBootstrap 的原因即在第三、四行：它需要「agentscope.json 首启生成 + workspace 脚手架 + 会话产品化（SessionsTool/SessionAgentManager）+ 工具事件总线 + transcript」这整套应用层设施，GatewayBootstrap 只提供路由容器。codingagent 是同一模式的第二个实现（`CodingBootstrap.java:120` + 自带 gateway/session 包）。

### 2.5 nex-ai 走「自建轻量注册表 + 直调 streamEvents」路线的机制对照（不引入 GatewayBootstrap）

**可行性相关的框架事实**（均已在 1.1–1.5 核实）：

- `HarnessAgent.streamEvents(List<Msg>, RuntimeContext)` 是公开的一等 API，单实例并发安全（类 javadoc + per-call sink + 序列化门，`HarnessAgent.java:159-163、856-875`；`ReActAgent.java:1101-1105`）；框架网关 `runStream` 内部也**正是**直接调 `ha.streamEvents(messages, runtimeContext)`（`HarnessGateway.java:276`）——即「网关外的直调」与「网关内调用」走的是同一条 agent API，网关额外提供的只有：canonicalKey→sessionId 映射、turn gate、agentId 路由、出站路由、subagent 暴露。nex-ai 已自持有 sessionKey→(userId,sessionId) 体系与 SSE 端点，这些对应物均自建。
- 单实例上 per-call 隔离三件套（CallExecution / stateStore 重载 / workspaceFor 视图）不依赖网关存在。

**对照 nex-ai 现状的逐点差异**（现状源码：`nexai-module-ai/src/main/java/com/gkht/ai/nexai/module/ai/session/infrastructure/gateway/AgentscopeRuntimeGateway.java`）：

| # | 现状（per-请求） | 单例常驻后的事实性变化 | 依据 |
|---|---|---|---|
| 1 | 每请求 `assemble(config)` 新建 HarnessAgent（`AgentscopeRuntimeGateway.java:118-127、223-294`） | 改为 `Map<specKey, HarnessAgent>` 常驻（形态对齐 paw `Map<String, HarnessAgent>`，`ClawBootstrap.java:87`）；同一 spec 的并发会话共享实例 | 1.3 节 |
| 2 | `registerModel` 每次装配覆盖注册 `nexai:t{tenant}:m{model}`（`365-371`，注释「渠道密钥/端点变更即时生效」） | 注册在实例构建时一次；模型/密钥变更不再随请求自动刷新——需要失效重建策略，或改 `ModelRegistry.registerFactory`（新工厂优先匹配，`ModelRegistry.java:76-96`） | 1.4 节 |
| 3 | `runningAgents.putIfAbsent(sessionKey)` 同会话并发直接抛 `SessionRunningException`（`186-193`） | 语义差异需择一：保留现有 putIfAbsent 拒绝（注册表照旧可持共享实例）；或接受框架 `serializeOnKey` 的 FIFO 排队（同槽位第二个请求等待而非报错）。两者可叠加（先拒绝再进 agent 层） | `AgentBase.java:345-366` |
| 4 | `doFinally` 中 `assembled.agent().close()`（`203-208`） | 常驻后 per-请求不 close；`close()` 挪到规格失效/应用停机（其清理物：会话/转录镜像排空 `SessionTree.awaitMirrorQuiescence`、`TaskRepository.shutdown`、`ownedWorkspaceIndex.close`、delegate.close，`HarnessAgent.java:453-470`）——这些后台资源由「每请求释放」变为「常驻持有」 | `HarnessAgent.java:453-482` |
| 5 | `interrupt` 经 `runningAgents` 持有的实例 delegate 触发（`156-167`；注释：HarnessAgent 未透传 `interrupt(RuntimeContext)`） | 机制不变：注册表继续持「共享实例 + 该流 context」即可；中断旗标按槽位（`scope.state.interruptControl().reset()` 每次 call 前清旧信号，`ReActAgent.java:727-728`） | — |
| 6 | AGENTS.md 每装配物化比对覆写（`331-349`） | 框架每次 call 实时读盘（`WorkspaceContextMiddleware.java:184-201`），故单例下物化时机改为「构建时 + 规格更新时」即可，读侧天然热更 | 1.5 节 |
| 7 | 每请求重建 Toolkit 并注入 `RuntimeToolContributor`（`283-287`）、移除 web 工具（`291-292`） | 注册一次；MCP 客户端等生命周期常驻；per-call 工具面由槽位 state 的 activatedGroups 决定（`ReActAgent.java:2293-2294`） | 1.2/1.4 节 |
| 8 | workspace per-spec 常驻目录 + `isolationScope`（平台/租户级 `USER`、用户级 `AGENT`，`301-318`） | 不变；单例后 `WorkspaceManager` per-path 锁在进程内对同 user 并发会话的 memory 追加生效（互踩面收窄到：跨 JVM、以及 USER scope 同 user 并发 flush 的 LLM 抽取并发本身——官方以 `IsolationScope.AGENT/GLOBAL` + `PeriodicGate` 缓解，`MemoryFlushMiddleware.java:63-66、236-244`） | 1.2 节 |
| 9 | `stateStore` 每次 assemble 传同一 provider（`230`） | 不变；注意 `stateCache`/`permissionEngineCache`/`slotVersions` 随槽位在实例内**常驻累积**，官方提供 `clearStateCache()`（全清/按槽位清，`HarnessAgent.java:370-393`）供治理 | `ReActAgent.java:284-302` |
| 10 | 单 JVM 部署 | 多节点时框架默认的本地协调（`LocalSessionTurnGate`、`LocalPeriodicGate`、`InMemorySubagentRegistry`）不够，需 `DistributedStore` 换 `StoreBacked*` + 分布式 turn gate（挂接点 `HarnessAgent.ensureGateway` `650-660`；build 自动选择 `HarnessAgent.java:2277-2280`） | 1.4 节 |

**不引入 GatewayBootstrap 的取舍（事实清单，非建议）**：放弃的是 canonicalKey 哈希会话映射（nex-ai 自有 sessionKey 体系对应）、网关层 turn gate（nex-ai 有 putIfAbsent 拒绝 + agent 层排队双保险）、多 agent 注册路由（自建 Map 等价）、exposed-subagent 跨节点句柄与 wakeup 分发（nex-ai 当前无此需求场景则无关）。保留的是全部 agent 层并发与隔离机制——它们不依赖网关。

---

## 附：本文引用的关键源码索引

- `ASJ` = `/Users/jerry/agent-project/agentscope-java/`
- core：`agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java`；`.../core/agent/AgentBase.java`；`.../core/agent/RuntimeContext.java`；`.../core/state/ConflictPolicy.java`；`.../core/state/ConcurrentSessionModificationException.java`；`.../core/tool/Toolkit.java`；`.../core/model/ModelRegistry.java`
- harness：`agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java`；`.../agent/IsolationScope.java`；`.../agent/gateway/GatewayBootstrap.java`；`.../agent/gateway/HarnessGateway.java`；`.../agent/gateway/Gateway.java`；`.../agent/gateway/LocalSessionTurnGate.java`；`.../agent/gateway/channel/DmScope.java`；`.../agent/gateway/channel/chatui/ChatUiChannel.java`；`.../agent/middleware/MemoryFlushMiddleware.java`；`.../agent/middleware/DynamicSubagentsMiddleware.java`；`.../agent/middleware/WorkspaceContextMiddleware.java`；`.../agent/memory/MemoryFlushManager.java`；`.../agent/memory/MemoryConfig.java`；`.../agent/workspace/WorkspaceManager.java`；`.../agent/coordination/LocalPeriodicGate.java`；`.../agent/coordination/StoreBackedPeriodicGate.java`
- 示例：`agentscope-examples/agents/agentscope-paw/src/main/java/io/agentscope/claw2/`（`runtime/ClawBootstrap.java`、`runtime/gateway/HarnessGateway.java`、`web/config/BuilderConfig.java`、`web/api/ChatController.java`、`web/catalog/AgentCatalogService.java`、`web/scaffold/WorkspaceScaffolder.java`）；`agentscope-examples/agents/agentscope-codingagent/src/main/java/io/agentscope/harness/coding/CodingBootstrap.java`
- starter：`agentscope-extensions/agentscope-spring-boot-starters/agentscope-spring-boot-starter/src/main/java/io/agentscope/spring/boot/AgentscopeAutoConfiguration.java`
- nex-ai 现状：`nexai-module-ai/src/main/java/com/gkht/ai/nexai/module/ai/session/infrastructure/gateway/AgentscopeRuntimeGateway.java`
- 在线官方文档（2026-08-23 检索）：`https://java.agentscope.io/v2/en/docs/others/going-to-production.html`；`https://java.agentscope.io/v2/en/docs/building-blocks/agent.html`
