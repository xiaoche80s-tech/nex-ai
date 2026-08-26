# agentscope-java v2 Workspace 实例化链与生命周期调研

> 调研日期：2026-08-25
> 调研对象：本机 `/Users/jerry/agent-project/agentscope-java`（main 分支，2.0.3-SNAPSHOT）之 `agentscope-harness` 源码；辅以 `agentscope-examples`（documentation / dataagent）与 `docs/v2/zh` 官方文档。
> 前置报告：`docs/research/2026-08-23-agentscope-workspace-目录结构.md`（下称「旧报告」）。**本报告不重复旧报告内容**（目录布局清单、根路径三级解析、NamespaceFactory 隔离前缀细节、AGENTS.md 机制、沙箱投影、RemoteFilesystemSpec 路由表等，引用时标注「旧报告 §N」）。差异化焦点：**Workspace 相关对象如何被创建、按什么时机创建、生命周期如何管理、多用户/多会话/多 agent 场景如何实例化**。
> 引用约定：`<AS>` = `/Users/jerry/agent-project/agentscope-java`；行号以当前工作区文件为准。

---

## 0. 结论速览（TL;DR）

- **实例化时机分三层**：① **build 时**——一次 `HarnessAgent.Builder.build()` 创建 1 个主 `WorkspaceManager` + 1 棵 `AbstractFilesystem` 对象图（常驻不可变）；② **首次使用时**——子代理的 workspace 目录、Remote 模式的 WorkspaceIndex、dataagent 式常驻沙箱，在首次触达时惰性创建；③ **每次 call 时**——`RuntimeContext` 驱动的命名空间解析每次调用现算；沙箱模式下每次 call acquire/release 一个真实容器；`workspaceFor(uid, sid)` 每次调用 new 一个轻量视图（不缓存）。
- **一个 HarnessAgent 持有恰好 1 个主 WorkspaceManager**（final 字段，build 时创建，单例语义）；另持 1 个 `BiFunction<String,String,WorkspaceManager>` 工厂（`workspaceFactory`），按需为带外 IO 生成"身份烘焙视图"。视图**共享** workspace Path、底层 filesystem 实例引用与 WorkspaceIndex，**只新建** `BakedContextFilesystem` 包装器 + `WorkspaceManager` 外壳 + 一个固定只看 uid 的 nsFactory——外壳无状态、无锁竞争、close 是 no-op，随用随丢。
- **HarnessAgent 官方定位就是单例**：javadoc 明确 "stateless between calls and safe to use as a singleton serving multiple users/sessions concurrently"（`HarnessAgent.java:L159-163`）。多用户隔离不靠多实例，靠每次 call 的 `(userId, sessionId)`。
- **沙箱是"稳定代理 + 每 call 注入"**：build 时 `new SandboxBackedFilesystem()` 只是个空壳代理（volatile sandbox 字段）；真实容器在每次 call 前 acquire（4 优先级解析，含从 `_sandbox.json` 快照 resume）、call 结束 persistState + stop + shutdown 后置空。
- **子代理每次 spawn 完整 build 一个新 HarnessAgent**（含自己的 WorkspaceManager），用完即弃；SHARED 模式共享父的 filesystem 实例，ISOLATED 模式每次 spawn new 一棵新 filesystem 对象图。
- **close() 不删任何 workspace 数据**：只排空异步转录镜像、关 TaskRepository、关（可能自有的）SQLite index、委托 delegate.close()。agent 锄毁后磁盘数据/AgentStateStore/沙箱快照全部留存，同 agentId + 同 workspace 重建即恢复。
- **对 NexAI**：官方姿势支持「每个 AgentSpec 一个常驻 HarnessAgent（平台缓存）+ 每请求传 RuntimeContext」，不必每请求 build；需要重建的信号是 tools.json/子代理注册/模型/技能仓库等 build 时快照的配置变更，而非 AGENTS.md 变更（后者每 call 重读）。详见第 8 节。

---

## 1. WorkspaceManager 的创建链：build() 全流程时序

### 1.1 入口与总体结构

`HarnessAgent` 是对 `ReActAgent` 的包装（`<AS>/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:L164`：`public class HarnessAgent implements Agent, AutoCloseable`），Workspace 相关字段只有三个（`:L169-171`）：

```java
private final ReActAgent delegate;
private final WorkspaceManager workspaceManager;          // 主 manager，build 时创建，常驻
private final BiFunction<String, String, WorkspaceManager> workspaceFactory;  // 视图工厂
private final WorkspaceIndex ownedWorkspaceIndex;          // 仅 Remote 模式非 null
```

构造器 private（`:L199`），唯一创建途径是 `Builder.build()`（`:L2224-2830`）。以下按代码顺序列出与 Workspace 实例化直接相关的步骤（省略与本主题无关的中间件装配细节）：

| 步骤 | 代码位置（HarnessAgent.java） | 动作 |
|---|---|---|
| 1 | `:L2227` | `toolkit.copy()` 深拷贝，保证跨 build 不串工具 |
| 2 | `:L2230-2243` | **validate**：`sandboxFilesystemSpec` / `remoteFilesystemSpec` / `localFilesystemSpec` 三者至多配一个（`specCount > 1` 抛 `IllegalStateException`）；`abstractFilesystem(...)` 逃生舱与三种 spec 互斥 |
| 3 | `:L2244` | `resolvedWorkspace = workspace != null ? workspace : resolveDefaultWorkspace()`——三级解析（系统属性 > 环境变量 > `${user.dir}/.agentscope/workspace`，`:L1117-1127`，详见旧报告 §1.1） |
| 4 | `:L2245-2248` | `resolvedAgentId` = agentId →（回落）name →（回落）`"ReActAgent"` |
| 5 | `:L2252-2275` | **DistributedStore 自动装配**：stateStore 缺省则取 `distributedStore.agentStateStore()`；remote spec 缺 store 则 `injectStoreIfAbsent(baseStore)`；sandbox spec 缺省注入 snapshotSpec / executionGuard；messageBus / asyncToolRegistry 同理 |
| 6 | `:L2283-2293` | **IsolationScope → NamespaceFactory**：`fsIsolationScope` 按 remote → sandbox → local spec 的顺序读 `getIsolationScope()`，都没有则默认 `IsolationScope.USER`；`nsFactory = fsIsolationScope.toNamespaceFactory()`（`IsolationScope.java:L102-126`） |
| 7 | `:L2294-2297` | AgentStateStore 缺省 = `new JsonFileAgentStateStore(defaultStateDir(agentId))`——落在 workspace **外**的 `~/.agentscope/state/<agentId>/`（`:L1085-1092`） |
| 8 | `:L2299-2307` | Remote spec + 本地 store（JsonFile/InMemory）→ 直接抛异常（fail-fast） |
| 9 | `:L2308-2309` | `WorkspaceIndex workspaceIndex = remoteFilesystemSpec != null ? WorkspaceIndex.open(resolvedWorkspace) : null`——**仅 Remote 模式**在 build 时打开 SQLite（`.index/workspace.db`）；`open` 是 best-effort，失败返回 null（`WorkspaceIndex.java:L50-80`） |
| 10 | `:L2310-2312` | **`filesystem = HarnessAgentBuilderSupport.resolveFilesystem(this, resolvedWorkspace, resolvedAgentId, workspaceIndex, nsFactory)`**（见 §2.1） |
| 11 | `:L2314-2349` | **沙箱集成**（仅 sandboxFilesystemSpec 非 null 时，见 §2.4）：`new SandboxBackedFilesystem()` → 替换/包装 filesystem → `toSandboxContext` → `SandboxManager` + `SandboxLifecycleMiddleware` |
| 12 | `:L2350-2352` | 非沙箱但配了 `filesystemRoute(...)`：`filesystem = new CompositeFilesystem(filesystem, filesystemRoutes)` |
| 13 | `:L2353-2355` | **`wsManager = new WorkspaceManager(resolvedWorkspace, filesystem, workspaceIndex, nsFactory); wsManager.validate();`**——主 WorkspaceManager 诞生于此 |
| 14 | `:L2357-2370` | 构造 **workspaceFactoryFn**（视图工厂，闭包捕获共享引用，见 §3.2） |
| 15 | `:L2375-2384` | messageBus / asyncToolRegistry 缺省回落 workspace 内实现（`.agentscope/bus` 等） |
| 16 | `:L2386-2478` | 中间件装配——`WorkspaceContextMiddleware(wsManager, ...)`、`AtPathExpansionMiddleware(wsManager)`、`TranscriptMiddleware(wsManager, ...)`、MemoryFlush/MemoryMaintenance（持 wsManager + effectiveIsolationScope）、Compaction 等，全部**注入同一个 wsManager 引用** |
| 17 | `:L2498-2529` | 子代理中间件（Dynamic 优先）——**只创建 SubagentEntry（含 lazy factory），不 build 子 agent**（见 §4） |
| 18 | `:L2553-2558` | Memory 工具族注册（`MemorySearchTool(wsManager)` 等 4 个） |
| 19 | `:L2559-2585` | `pathNormalizer` 三分支（Local overlay / sandbox / 其他）→ `FilesystemTool(filesystem, pathNormalizer)`；sandbox 时再注册 `ShellExecuteTool` |
| 20 | `:L2588-2607` | Plan 模式：`new PlanModeManager(wsManager, planFileDir)` + 三个工具 |
| 21 | `:L2609-2620` | **tools.json 在 build 时一次性读取**：`ToolsConfigLoader.load(wsManager)` → 注册 MCP server、apply allow/deny（官方文档 `docs/v2/zh/docs/harness/filesystem.md:L499`："都是在 build 时读取配置，不受运行时 filesystem 模式影响"） |
| 22 | `:L2623-2632` | `currentRcSupplier`（经 `AtomicReference<ReActAgent> selfRef` 取运行时当前 rc）→ `composeSkillRepositories`（4 层技能仓库，最高优先层是 per-user namespaced 的 `WorkspaceSkillRepository(filesystem, "skills", currentRcSupplier, ...)`，`HarnessAgentBuilderSupport.java:L838-849`） |
| 23 | `:L2808-2810` | `delegate = inner.build()` 后 `selfRef.set(delegate)` |
| 24 | `:L2812-2829` | `new HarnessAgent(delegate, wsManager, workspaceFactoryFn, workspaceIndex, ...)` |

`build()` 末尾的日志（`:L2800-2805`）会打印 `[workspace={}, filesystem={}, subagents={}]`，可作为运行期核对实例化结果的观察点。

### 1.2 WorkspaceManager 构造参数全景

`<AS>/agentscope-harness/src/main/java/io/agentscope/harness/agent/workspace/WorkspaceManager.java:L136-167` 提供 4 个 public 构造器 + 1 个 private 汇总构造器：

```java
public WorkspaceManager(Path workspace)                                        // 仅路径（index=null, fs=null, ns=null）
public WorkspaceManager(Path workspace, AbstractFilesystem filesystem)          // 自动 WorkspaceIndex.open(workspace)，ownsIndex=true
public WorkspaceManager(Path workspace, AbstractFilesystem filesystem, WorkspaceIndex index)  // ownsIndex=false
public WorkspaceManager(Path workspace, AbstractFilesystem filesystem, WorkspaceIndex index, NamespaceFactory namespaceFactory)  // harness build 走这个
```

- `workspace`（Path）：宿主侧 workspace 根路径。注意（dataagent 已验证，§7.4）：沙箱/Remote 模式下它更多是**模板/标签/兜底读层**，不是数据唯一落点。
- `filesystem`（`AbstractFilesystem`，可 null）：写通道 + 读优先层；null 时读写全落本地磁盘（`appendUtf8WorkspaceRelative` 的 `:L380-382` 分支）。
- `index`（`WorkspaceIndex`，可 null）：SQLite 加速索引，仅 Remote 模式有意义。
- `namespaceFactory`（`NamespaceFactory`，可 null）：按每次调用的 rc 现算相对路径前缀（`resolveRuntimeDataPath`，`:L237-246`）；null 则不加前缀。
- `ownsIndex`（private）：决定 `close()` 是否要关 index——**harness build 传入的 index 由 HarnessAgent 持有并在其 close() 关闭**，因此主 manager 的 close() 实际是 no-op（`:L128-134` javadoc 明说）。

### 1.3 validate() 做什么

`:L200-224`：只做两级存在性检查且**只 warn 不抛**——(1) workspace 目录不存在 → log.warn 后直接 return；(2) `AGENTS.md` 本地不存在时再试 `filesystem.exists(RuntimeContext.empty(), AGENTS.md)`，仍无 → log.warn。即：**build 允许在一个尚不存在的目录上完成**，首次写入时才由 filesystem 层建目录。这与旧报告 §4.1（AGENTS.md 由使用方提供）一致，不赘述。

### 1.4 数量与单例语义

- **一个 `build()` = 一个 HarnessAgent = 一个主 WorkspaceManager**。字段 final，没有 setter；一个进程内多次 build 落同一 workspace 路径时，多个 agent 实例各持自己的 manager/对象图，但指向同一磁盘目录（隔离靠使用方，旧报告 §3.1）。
- **官方单例语义**：`HarnessAgent.java:L159-163` javadoc——"HarnessAgent is stateless between calls and safe to use as a **singleton** serving multiple users/sessions concurrently. Each call() uses the RuntimeContext's (userId, sessionId) to isolate state. Calls targeting the same session are serialized automatically; different sessions run in parallel."。即：**单 agent 实例多用户并发是设计内用法**，身份全部经 rc 传递（这正是 `NamespaceFactory`、`resolveRuntimeDataPath`、各中间件都收 `RuntimeContext` 参数的原因）。
- 主 WorkspaceManager 内部唯一的可变态是进程内 `pathLocks`（per-path `ReentrantLock`，`:L108-118`，跨副本 last-writer-wins——旧报告 §3.5 已述）；其本身无 per-user 可变字段，线程安全。

---

## 2. filesystem 对象图的实例化

类继承层次（源码核对）：

```
AbstractFilesystem (接口)
├── LocalFilesystem                      (local/LocalFilesystem.java:L76)
│   └── LocalFilesystemWithShell          (local/LocalFilesystemWithShell.java:L48, 同时 implements AbstractSandboxFilesystem)
├── RemoteFilesystem                      (remote/RemoteFilesystem.java:L63)
├── OverlayFilesystem                     (OverlayFilesystem.java:L56)
│   └── ProjectAwareOverlay               (ProjectAwareOverlay.java:L42, 同时 implements AbstractSandboxFilesystem)
├── CompositeFilesystem                   (CompositeFilesystem.java:L64)
├── BakedContextFilesystem                (BakedContextFilesystem.java:L41, final 包装器)
└── (AbstractSandboxFilesystem 接口)
    └── BaseSandboxFilesystem (抽象类, sandbox/BaseSandboxFilesystem.java:L55)
        ├── SandboxBackedFilesystem       (sandbox/SandboxBackedFilesystem.java:L51)
        └── dataagent 的 SharedSandboxFilesystem（示例侧）
RoutedSandboxFilesystem（RoutedSandboxFilesystem.java:L40, implements AbstractSandboxFilesystem, 内嵌 CompositeFilesystem）
```

### 2.1 `resolveFilesystem()` 的分派顺序

`HarnessAgentBuilderSupport.resolveFilesystem`（`HarnessAgentBuilderSupport.java:L147-168`）：

```java
if (b.abstractFilesystem != null)  return b.abstractFilesystem;                    // ① 逃生舱：用户自带实例，原样返回
if (b.remoteFilesystemSpec != null) {                                               // ② Remote
    if (workspaceIndex != null) b.remoteFilesystemSpec.workspaceIndex(workspaceIndex);
    return b.remoteFilesystemSpec.toFilesystem(workspace, agentId, nsFactory);
}
if (b.localFilesystemSpec != null)                                                  // ③ Local
    return b.localFilesystemSpec.toFilesystem(workspace, nsFactory);
// ④ 默认分支：未配置任何 filesystem 时
return new LocalFilesystemSpec().toFilesystem(workspace, nsFactory);
```

**默认分支的具体行为**：临时 `new LocalFilesystemSpec()`（全部字段取缺省）后调 `toFilesystem`，等价于"workspace 为读写上层、`${user.dir}` 为只读下层的 Claude-Code 式 overlay"（注释 `:L165-166` 原文）。即**不调 `filesystem(...)` 的 agent 也有完整两层 filesystem**，不是裸磁盘。

### 2.2 `LocalFilesystemSpec.toFilesystem(workspace, nsFactory)`（`:L288-316`）

new 出的对象与组合关系：

```
LocalFilesystemWithShell upper = new LocalFilesystemWithShell(
        workspace, mode, pathPolicy, executeTimeoutSeconds, maxOutputBytes,
        env, inheritEnv, nsFactory, effectiveProject)     // 读写上层：根=workspace，带 shell
LocalFilesystem lower = new LocalFilesystem(effectiveProject, /*virtualMode=*/true, 10, /*ns=*/null)
                                                            // 只读下层：根=project（默认 ${user.dir}），无命名空间
projectWritable=false（默认）→ OverlayFilesystem.of(upper, lower)
projectWritable=true        → ProjectAwareOverlay((AbstractSandboxFilesystem) upper, lower, projectFs, workspace)
                              其中 projectFs = new LocalFilesystem(effectiveProject, mode, pathPolicy, 10, nsFactory)
```

要点：`PathPolicy.of([project, workspace, ...additionalRoots])` 先构建允许根列表；**nsFactory 只挂在 upper**（下层模板不按用户隔离）；LocalFilesystem 第二参数是 `virtualMode`（路径锚定根并阻止穿越，`LocalFilesystem.java:L119-121` javadoc）。

### 2.3 `RemoteFilesystemSpec.toFilesystem(workspace, agentId, nsFactory)`（`:L204-251`）

store 为 null 直接抛 `IllegalStateException`（build 时即失败）。对象图：

```
CompositeFilesystem(local, routes)
├── 默认 backend: local = new LocalFilesystem(workspace, virtualMode=false, 10, nsFactory)   // 非虚拟、带用户命名空间
├── workspaceTemplate = new LocalFilesystem(workspace, virtualMode=true, 10, null)            // 只读模板视图（exact-file 路由共用）
└── routes（LinkedHashMap，前缀→OverlayFilesystem）：
    ├── "AGENTS.md" / "MEMORY.md" / "tools.json"  → OverlayFilesystem(RemoteFilesystem(root-segment), workspaceTemplate)
    ├── "memory/" "skills/" "subagents/" "knowledge/" "plans/"
    │   "agents/<agentId>/sessions/" "agents/<agentId>/tasks/"（+ extraSharedPrefixes）
    │   → 每条 = OverlayFilesystem(
    │           upper = RemoteFilesystem(store, extendedNs).withIndex(workspaceIndex),
    │           lower = new LocalFilesystem(<workspace 下对应模板目录>, virtualMode=true, 10, null))
```

每条路由的 `extendedNs` 在 `storeNamespace(agentId)`（USER → `["agents", agentId, "users", uid|"_default"]` 等，`:L298-316`）之上再追加路由 segment 防键冲突。路由表语义与 KV namespace 结构旧报告 §6 已详述，此处只强调**实例化侧**：build 一次性 new 出 `1 个 CompositeFilesystem + 1 个默认 LocalFilesystem + 1 个 workspaceTemplate + 每路由 1 个 RemoteFilesystem + 1 个只读 LocalFilesystem 下层 + 1 个 OverlayFilesystem`（默认 10 条路由 ≈ 21 个对象），全部常驻到 agent 关闭。

### 2.4 `SandboxFilesystemSpec`（含 `DockerFilesystemSpec`）：build 时**不建 filesystem 对象图**

`SandboxFilesystemSpec` javadoc（`spec/SandboxFilesystemSpec.java:L36-38`）："this type is not a runtime filesystem implementation. It only describes how to create a sandbox-backed filesystem at build time."。build() 的沙箱分支（`HarnessAgent.java:L2318-2349`）按序 new：

1. **`capturedSandboxFs = new SandboxBackedFilesystem()`**（`:L2319`）——无参构造，只生成 `fsId = "sandbox-" + UUID 前 8 位`，`volatile Sandbox sandbox` 字段初始为 null（`SandboxBackedFilesystem.java:L44-60`，javadoc 原文："**Stable proxy created at agent build time; a fresh Sandbox is injected on each call** via the volatile sandbox field"）。
2. `filesystem = filesystemRoutes.isEmpty() ? capturedSandboxFs : new RoutedSandboxFilesystem(capturedSandboxFs, filesystemRoutes)`（`:L2320-2323`；RoutedSandboxFilesystem 把 execute/id 恒走 primary、文件操作走内嵌 Composite，`RoutedSandboxFilesystem.java:L38-47`）。
3. `defaultSandboxContext = sandboxFilesystemSpec.toSandboxContext(resolvedWorkspace)`（`:L2325`）——这是 spec 真正的"工厂方法"（`SandboxFilesystemSpec.java:L110-141`）：`createClient()` + `clientOptions()` + snapshotSpec + `workspaceSpec()`（并把宿主 workspace 根包装成 `__workspace_projection__` entry 加入 entries，`:L127-141`；投影根列表默认 `AGENTS.md/skills/subagents/knowledge/.skills-cache`，`:L41-42`）。子类 `DockerFilesystemSpec`（`sandbox/impl/docker/DockerFilesystemSpec.java:L36`）四个抽象方法分别返回 `options.createClient()` / options / snapshotSpec / defaultWorkspaceSpec——**全部是 build 时一次性确定的配置快照**。
4. `SessionSandboxStateStore stateStore = new SessionSandboxStateStore(effectiveSession, resolvedAgentId)`（`:L2336-2337`）——沙箱状态挂在 AgentStateStore 上。
5. `SandboxManager sandboxManager = new SandboxManager(client, stateStore, agentId, executionGuard)`（`:L2342-2347`）。
6. `sandboxLifecycleMw = new SandboxLifecycleMiddleware(sandboxManager, capturedSandboxFs)`（`:L2348-2349`）。

即：**build 时创建的都是"代理 + 配置 + 管理器"，真实 Docker 容器延迟到每次 call**（见 §3.3）。`DockerSandbox` 的 create/start/resume/快照细节属旧报告 §5 范围，不重复。

---

## 3. 运行时按请求实例化：RuntimeContext 驱动

### 3.1 call() 入口：`ensureSessionDefaults` 每 call 装配 rc

所有带 rc 的 `call(...)` / `streamEvents(...)` 重载第一步都是 `ensureSessionDefaults(ctx)`（`HarnessAgent.java:L708-873` 各入口；实现 `:L965-995`）。它做四件事：

1. sessionId 缺省补 `getName()`；
2. **把 `defaultSandboxContext`（build 时创建的那个）注入 rc**（rc 已带 SandboxContext 则尊重调用方——这是"外部沙箱"逃生口）；
3. 把主 filesystem、**主 WorkspaceManager**、pathNormalizer `put` 进 rc（`:L985-993`）——下游中间件/工具从 rc 取到的是**同一个常驻 manager 实例**，不是新实例；
4. 返回新的 RuntimeContext（RuntimeContext 本身按调用构造，轻量）。

因此：**call 路径上的 workspace 不按次实例化**——每 call 复用主 WorkspaceManager 与整棵 filesystem 对象图；"按请求"的只有 rc 本身与 namespace 前缀的计算（`NamespaceFactory` 是函数式接口，每次读写现算，`NamespaceFactory.java`）。技能仓库的 per-user 视图同理：`composeSkillRepositories` 在 build 时注册了一个持有 `currentRcSupplier` 的 `WorkspaceSkillRepository`，每次推理经 supplier 取**当前** delegate 的 rc（`HarnessAgent.java:L2623-2629`）。

### 3.2 `workspaceFor(userId, sessionId)`：带外 IO 视图

`HarnessAgent.workspaceFor`（`:L242-253`）：

```java
public WorkspaceManager workspaceFor(String userId, String sessionId) {
    if (workspaceFactory == null) return workspaceManager;   // 防御分支；build() 总会传工厂，实际不可达
    return workspaceFactory.apply(userId, sessionId);
}
```

工厂实现即 build() `:L2357-2370` 的闭包，**每次 apply 新建两个轻量对象**：

```java
BiFunction<String, String, WorkspaceManager> workspaceFactoryFn = (uid, sid) -> {
    RuntimeContext bakedRc = HarnessAgentBuilderSupport.buildBakedRuntimeContext(uid, sid);  // :L177-189
    NamespaceFactory ctxNs = rc -> (uid == null || uid.isBlank()) ? List.of() : List.of(uid);
    AbstractFilesystem ctxFs = new BakedContextFilesystem(sharedFilesystemRef, bakedRc);     // 包装主 filesystem
    return new WorkspaceManager(capturedWorkspace, ctxFs, capturedIndex, ctxNs);              // ownsIndex=false
};
```

**与主 manager 的共享/新建关系**：

| 成员 | 视图（workspaceFor 产物） | 主 manager |
|---|---|---|
| workspace Path | **共享**（同一 `capturedWorkspace` 引用） | 同 |
| 底层 AbstractFilesystem | **共享实例引用**，仅外包一层 `BakedContextFilesystem`（该包装器唯一职责是把每次调用的 rc 替换成 bakedRc 再委托，`BakedContextFilesystem.java:L31-49`） | 原实例 |
| WorkspaceIndex | **共享**（`capturedIndex`；视图 ownsIndex=false，close 为 no-op，不会误关） | 同 |
| nsFactory | **新建且语义收窄**：只看 uid，uid 空 → 空前缀；**没有**主 nsFactory 的 uid 回落 sid 逻辑（对比 `IsolationScope.toNamespaceFactory` USER 分支，`IsolationScope.java:L104-115`） | IsolationScope 派生 |

设计意图（`:L242-247` javadoc）："does not mutate any shared state on this agent — so it is safe to call concurrently from per-request controllers without racing with active chats"——即视图是给**平台侧带外文件操作**（管理界面读写用户 memory/文件）用的，与 agent 的 call 路径互不干扰。视图**不缓存**：每次 `workspaceFor` 都 new，用完交给 GC 即可（两个轻量对象 + 一个 rc，无需要显式关闭的资源）。

一个值得记录的细节：ctxNs 不回落 sid 意味着 `workspaceFor(null, someSessionId)` 的写会落到 workspace 根而非 session 前缀下——平台侧调用时应始终传 uid。

### 3.3 沙箱模式：每 call 的容器 acquire/release

流式路径 `wrappedStreamEvents`（`HarnessAgent.java:L942-957`）用 `Flux.using` 包裹：订阅时 acquire、终止时 release——**精确覆盖单次调用窗口**：

- **acquireForCall**（`SandboxLifecycleMiddleware.java:L86-133`）：先跑 `beforeStartCallback`（技能市场预物化钩子，build 时由 `sandboxLifecycleMw.setBeforeStartCallback(skillMiddleware::prestageMarketplaceSkills)` 挂上，`HarnessAgent.java:L2785-2788`）→ `sandboxManager.acquire(sandboxContext, ctx)` → `sandbox.start()` → `filesystemProxy.setSandbox(sandbox)`。
- **`SandboxManager.acquire` 的 4 优先级**（`SandboxManager.java:L66-146`）：① rc 里有外部 Sandbox（用户自管，不 stop）；② 外部 SandboxState（resume）；③ 按 `SandboxIsolationKey`（由 IsolationScope + rc + agentId 解析）从 `SessionSandboxStateStore` load `_sandbox.json` 并 `client.resume(state)`（**同 scope 顺序复用**）；④ 全新 `client.create(spec, snapshotSpec, clientOptions)`。③④ 会先取 executionGuard 的 `SandboxLease`（AGENT/GLOBAL 并发守卫）。
- **releaseForCall**（`:L141-159`）：`persistState`（把容器 state 序列化回 stateStore）→ `release`（selfManaged 才 `sandbox.stop()` + `sandbox.shutdown()`）→ `lease.close()` → `filesystemProxy.setSandbox(null)`。

关键结论：**沙箱模式下"每次 call 一个真实容器"**，跨 call 的连续性靠快照 resume（IsolationScope javadoc `:L49-51`："sequential-reuse sharing, not live-instance sharing. Concurrent calls at the same scope each get their own running container; they converge on the last persisted snapshot"）。想要"常驻容器跨 call 复用"，官方出口是**优先级 ①外部沙箱**——dataagent 的 `UserSandboxRegistry` 正是走这条路（§7.4）。

---

## 4. 子代理 workspace 的实例化

### 4.1 build 时：只注册 lazy factory，不建子 agent

build() 的子代理分支（`HarnessAgent.java:L2498-2529`）在 filesystem 非空且未禁用时优先建 `DynamicSubagentsMiddleware`（每步推理动态扫描 `subagents/*.md`），否则建静态 `SubagentsMiddleware`。两者都调用 `HarnessAgentBuilderSupport.buildSubagentEntries / buildStaticSubagentEntries`（`:L199-285`）——这里只 `new SubagentEntry(name, desc, factoryLambda, decl)`，**factory 是捕获了父 Builder 全部相关配置的 lambda，子 HarnessAgent 此刻并不存在**。

### 4.2 spawn 时：每次完整 build 一个新 HarnessAgent

真正实例化发生在 task/agent_spawn 工具触发时：`AgentSpawnTool` → `DefaultAgentManager.createAgentIfPresent(agentId, parentRc)` → `factory.create(parentRc)`（`DefaultAgentManager.java:L107-119`、`AgentSpawnTool.java:L350`）。两类 factory：

**① general-purpose**（`buildGeneralPurposeFactory`，`HarnessAgentBuilderSupport.java:L290-387`）：lambda 内 `HarnessAgent.builder()....workspace(workspace).asLeafSubagent()....build()`——**与父共享 workspace 路径与 backend 实例**（`capturedBackend = sandboxFs != null ? sandboxFs : b.abstractFilesystem`，`:L295-296`，经 `sub.abstractFilesystem(capturedBackend)` 传入，`:L366`），但 build 出**新的子 HarnessAgent + 新的子 WorkspaceManager**（子 build 内部同样走 §1.1 全流程）。注释 `:L332-334`："shares the parent's workspace and is short-lived per spawn"。

**② 声明式子代理**（`buildDeclaredFactory`，`:L392-522`）每次 spawn 时：

1. `resolveDeclaredWorkspace(decl, mainWorkspace)`（`:L635-658`，决策表旧报告 §3.4 已列）：**ISOLATED 且未指定 path 时在此刻 `Files.createDirectories(mainWorkspace/agents/<name>/workspace/)`**——这是"首次使用时创建目录"的实例化点（目录创建发生在第一次 spawn 该子代理时，而非主 agent build 时）。
2. filesystem 分支（`:L487-492`）：`SHARED` → `sub.abstractFilesystem(capturedSharedBackend)`（**直接复用父的 filesystem 实例**，含 sandboxFs）；非 SHARED（ISOLATED）→ `sub.filesystem(cloneLocalSpecForSubagent(capturedLocalFilesystemSpec))`——`cloneLocalSpecForSubagent`（`:L553-566`）复制 project/mode/additionalRoots 生成**新 spec**，子 build 时 new 出**全新一棵 filesystem 对象图**（根在子 workspace）。
3. `deriveChildSessionId`（`:L603-617`）：`{declName}[@{parentSid}][#{uid}]` 作子代理持久化 bucket（SHARED 回落裸 declName）。
4. `sub.build()`——完整走一遍 §1.1。

**共享/隔离总结**：子代理与主 agent 之间**共享**的只有 workspace 路径（SHARED）或 filesystem 实例（SHARED / general-purpose）；**每 spawn 必新建**的是子 HarnessAgent、子 WorkspaceManager，以及（ISOLATED 声明式的）整棵 filesystem 对象图。子代理没有 close 钩子暴露给使用方——短生命周期、用完即弃（`SUBAGENT_CONTEXT_SECTION`："You may be terminated after task completion"）。

另外，`DynamicSubagentsMiddleware` 每步推理重新扫 `subagents/`（`SubagentsMiddleware.loadSubagentSnapshot`，`:L608-636`）：新发现的声明即时 `factoryBuilder.apply(decl)` 生成 entry——仍只是注册 lazy factory。

---

## 5. 生命周期与 close

### 5.1 各层 close() 清理什么

| 对象 | close() 内容 | 代码位置 |
|---|---|---|
| `WorkspaceManager` | 仅当 `ownsIndex`（自开 index 的构造器路径）时 `index.close()`；harness build 的主 manager 与 workspaceFor 视图均为 no-op | `WorkspaceManager.java:L176-181` |
| `HarnessAgent` | ① `SessionTree.awaitMirrorQuiescence(5s)` 排空异步转录镜像；② `shutdownTaskRepository()`（子代理 TaskRepository.shutdown）；③ finally 链：`ownedWorkspaceIndex.close()`（Remote 模式 SQLite 句柄）→ `delegate.close()`（ReActAgent） | `HarnessAgent.java:L453-482` |
| `WorkspaceIndex` | 关 SQLite Connection（AutoCloseable） | `WorkspaceIndex.java` |
| 沙箱容器 | **不在 HarnessAgent.close() 里**——每次 call 结束时已由 releaseForCall stop+shutdown + 快照持久化；close() 无沙箱清理步骤 | §3.3 |
| Local/Remote filesystem | 无 close 概念（无句柄持有；RemoteFilesystem 的连接由 BaseStore 拥有，其生命周期归使用方/分布式组件） | —— |

### 5.2 agent 锄毁后的数据留存

`close()` **不删除任何数据**。留存：workspace 树全部文件（memory/sessions/skills/AGENTS.md…）、workspace 外的 `~/.agentscope/state/<agentId>/`（AgentState + `_sandbox.json` 沙箱快照）、KV store 中的 Remote 数据。因此**同 agentId + 同 workspace 路径重新 build 即恢复全部长期状态**（会话历史经 AgentStateStore 按 (uid,sid) 恢复；沙箱经快照 resume）。丢失面：`InMemoryAgentStateStore` 进程内状态、内存中的 TaskRepository 运行态、以及（多副本未配 DistributedStore 时）各节点本地 JsonFile 状态不一致——build() 对 Remote 模式已 fail-fast 强制分布式 store（`:L2299-2307`），沙箱模式对本地 store 只 warn（`:L2327-2334`）。

### 5.3 官方工程建议（常驻 vs 每请求 build）

官方没有 Spring 专门章节，工程姿势由源码契约 + 示例归纳：

1. **单例常驻是设计内用法**（`HarnessAgent.java:L159-163`，见 §1.4）。build 是重操作：工具/MCP 注册、tools.json 读取、技能仓库装配、子代理 factory 捕获、（Remote）SQLite 打开都在 build 完成；且 **tools.json 是 build 时一次性快照**（§1.1 步骤 21），改了 tools.json 不重建 agent 不生效。
2. **官方示例全部是"启动 build、进程内常驻"**：dataagent 的 `DataAgentBootstrap` 在启动 Phase 2 循环 `b.build()` 后注册进 gateway 常驻（§7.4）；documentation 示例是 main() 一次 build 多次 call。
3. **"多次 build 复用"（每请求 build）无官方支持姿势**：无 per-request build 的示例；相反 `WorkspaceScaffolder.writeIfMissing` 等脚手架语义（旧报告 §2.2）隐含 workspace 是长生命周期资产。dataagent 甚至演示了**反向操作**——平台侧绕开 build 直接 `new WorkspaceManager(...)` 做文件管理视图（§7.4）。
4. 并发注意：同 session 的 call 自动串行、不同 session 并行（javadoc 同段）；跨副本无 CAS（pathLocks 注释 `WorkspaceManager.java:L113-116`），多副本需 DistributedStore + （沙箱）sticky 路由（`UserSandboxRegistry.java:L62-65` 注释明说多副本须按 userId 粘性负载均衡）。

---

## 6. 官方示例的实例化姿势（关键代码摘录）

### 6.1 `WorkspaceSetupExample`（一次性 build + 单 session）

`agentscope-examples/documentation/src/main/java/io/agentscope/examples/documentation2/harness/workspace/WorkspaceSetupExample.java:L151-161`：

```java
HarnessAgent agent = HarnessAgent.builder()
        .name("phoenix-assistant")
        .sysPrompt("You are the Phoenix project assistant.")
        .model("dashscope:qwen-plus")
        .workspace(workspace)                    // 临时目录，预先脚手架好 AGENTS.md/knowledge/subagents
        .additionalContextFile("PREFERENCES.md")
        .maxContextTokens(4000)
        .build();
RuntimeContext ctx = RuntimeContext.builder().sessionId("workspace-demo").build();
// 之后多次 streamEvents(msg, ctx) 复用同一 agent
```

演示模式：**进程生命周期 = agent 生命周期**；workspace 内容在 build 前脚手架完成。

### 6.2 `UserIsolatedMultiTurnsExample`（单实例多用户——平台服务标准姿势）

`.../documentation2/quickstart/UserIsolatedMultiTurnsExample.java:L49-93`：

```java
HarnessAgent agent = HarnessAgent.builder()
        .name("quickstart-agent")
        .workspace(workspace)
        .stateStore(stateStore)          // InMemoryAgentStateStore
        .compaction(CompactionConfig.builder()....build())
        .build();

RuntimeContext ctx = RuntimeContext.builder().sessionId("demo-session").userId("alice").build();
Msg turn1 = agent.call(..., ctx).block();          // alice 的记忆落 <workspace>/alice/...
ctx = RuntimeContext.builder().sessionId("demo-session").userId("ken").build();
Msg turn2 = agent.call(..., ctx).block();          // ken 独立命名空间，同一 agent 实例
```

核心演示：**一个 build 出的 agent 实例交替服务两个 userId**，隔离完全由每 call 的 rc 驱动（namespace 前缀细节见旧报告 §3.2）。这就是 javadoc"singleton serving multiple users"的可执行证明。

### 6.3 `WorkspaceSandboxExample`（沙箱：同用户跨 call 快照恢复、异用户隔离）

`.../documentation2/harness/workspace/WorkspaceSandboxExample.java:L93-160`：

```java
HarnessAgent agent = HarnessAgent.builder()
        .name("sandbox-agent")
        .workspace(workspace)
        .filesystem(new DockerFilesystemSpec().image("ubuntu:24.04")
                .isolationScope(IsolationScope.USER))
        .build();
// alice call1 建文件 → alice call2 读到（同 uid：快照顺序复用）；bob call 看不到（独立容器）
```

javadoc（`:L38-43`）明确演示目标："each user gets a separate sandbox instance; the same user's calls reuse (or restore from snapshot) the same sandbox"——即 §3.3 的 per-call acquire + 快照 resume。多副本写法（`:L53-64`）：`distributedStore(RedisDistributedStore.fromJedis(jedis))` 一行装配状态/快照/守卫。

`WorkspaceSharedStoreExample`（`:L87-97`）补充分布式实例化：`distributedStore(store)` + `filesystem(new RemoteFilesystemSpec().isolationScope(IsolationScope.USER))`，**两个 replica 各自 build 独立 HarnessAgent 实例、共享同一 KV workspace**——证明多实例共享 workspace 的合法路径是 Remote 模式而非"同路径多 build 的 Local 模式"。

### 6.4 dataagent：平台级实例化的两种高级姿势

**(a) 启动期批量 build 常驻**：`agentscope-examples/agents/agentscope-dataagent/src/main/java/io/agentscope/dataagent/runtime/DataAgentBootstrap.java:L577-626`：

```java
Map<String, HarnessAgent> built = new LinkedHashMap<>();
for (String id : ids) {
    HarnessAgent.Builder b = HarnessAgent.builder();
    applyFileEntry(cwd, id, entry, b);
    b.externalSubagentTool(sessionsTool); ...
    built.put(id, b.build());                       // 每个 agent 一次 build
}
for (var e : built.entrySet()) gateway.registerAgent(e.getKey(), e.getValue());  // 常驻注册
```

**(b) per-(userId, agentId) 常驻容器 + 按需 WorkspaceManager 视图**：`web/workspace/WorkspaceManagerFactory.java:L61-67`：

```java
public WorkspaceManager forAgent(String ownerId, String agentId, String workspacePath) {
    Sandbox sb = registry.borrow(ownerId, agentId);          // 常驻容器，首次借用才创建并启动
    Path dataPath = resolveAgentDataPath(workspacePath, agentId);
    return new WorkspaceManager(dataPath, new SharedSandboxFilesystem(sb));   // 每次调用 new 轻量视图
}
```

配套的 `UserSandboxRegistry`（`UserSandboxRegistry.java:L67-156`）：`ConcurrentHashMap<Key,Entry>` 缓存活容器，`borrow` 首次 `createAndStart`、后续复用并 touch 空闲计时；后台 evictor 按 `idleTtl` 关闭闲置容器；`invalidate(userId, agentId)` 用于内容更新后强制重建。javadoc `:L35-38` 说明该沙箱经 `SandboxContext.externalSandbox`（即 `SandboxManager.acquire` 优先级 ①）注入 agent 运行时，从而实现**容器跨 call 常驻**（对比内建的每 call acquire/release）。`getWorkspace()` 返回的 Path "is a host-side label, not the on-disk location"（`:L35-38`）——平台侧直连容器文件系统的视图不依赖宿主目录真实存在。

---

## 7. （简核）core 层规划状态

`agentscope-core` 的 `io.agentscope.core.workspace` 包**至今仍只有 package-info**（`<AS>/agentscope-core/src/main/java/io/agentscope/core/workspace/package-info.java:L17-23`：规划 `WorkspaceBase`（initialize/close/getInstructions/listTools/listSkills/offloadContext/offloadToolResult）与 `LocalWorkspace` 默认实现），无任何实现类——与旧报告 §6 末注一致，本次复核无变化。workspace 实例化机制整体仍全部实装于 harness 模块。

---

## 8. 对 NexAI 的集成建议

结合 NexAI 现状（Spring Boot 4.1 多租户管理平台、AI 模块重建中、AgentSpec 聚合已完成、旧报告 §7 已定「workspace 根 = `<dataRoot>/t{tenantId}/u{userId}/{specCode}`」的落位），本次实例化调研补充以下决策依据：

1. **WorkspaceManager / HarnessAgent 实例：常驻，不要每请求 build**。
   - 依据：javadoc 单例契约（§1.4）+ 全部官方示例姿势（§6）+ build 是重操作（MCP/tools.json/技能仓库/子代理 factory 均为 build 时快照，§1.1）。
   - 落地形态建议：Spring 侧建 `AgentInstanceManager`（应用层服务），以 `(tenantId, specCode)` 为缓存 key 持有 `HarnessAgent`（用户身份不进 key——多用户靠 rc）；agent 实现 `AutoCloseable`，缓存失效（spec 变更/驱逐）时调 `close()` 再重建。等价于 dataagent 的 `DataAgentBootstrap` + gateway 常驻模式（§6.4a）。
   - 若后续上沙箱并需要"容器常驻跨 call"（而非每 call 快照 resume），采用 dataagent 的 `UserSandboxRegistry` 模式：平台自管容器 + `SandboxContext.externalSandbox`（`SandboxManager.acquire` 优先级 ①），并按 userId 做粘性路由（多副本前提）。
2. **重建（re-build）的触发条件**——差异化管理：
   - **不需要重建**：AGENTS.md / MEMORY.md / knowledge/ 内容变更（每 call / 每推理步重读，旧报告 §4）；`subagents/*.md` 新增（Dynamic 中间件每步重扫，§4）。
   - **需要重建**：tools.json（MCP/allow-deny）变更、模型/执行配置变更、技能仓库组成变更、workspace 根路径变更。AgentSpec 聚合发布领域事件后，由 `AgentInstanceManager` 失效对应 (tenant, specCode) 的缓存条目。
   - **配对约束**：agentId 固定为 specCode（旧报告 §7.7 已建议）在此获得额外意义——AgentStateStore 与沙箱快照都按 agentId 寻址（`defaultStateDir(agentId)`、`SessionSandboxStateStore`），agentId 漂移 = 会话历史与快照失联。
3. **租户/用户路径注入**：沿用旧报告 §7.1/7.2 结论（根路径承载 `t{tid}/u{uid}/{specCode}`，rc 不再传 userId，或传 userId 但不叠加路径——二选一）。实例化侧新增注意：若选择"路径承载用户"（方案 a），多个用户 = 多个 workspace 根 = **每个 (tenant, user, spec) 各一个 HarnessAgent**，缓存 key 相应为三元组；若选择"rc userId"（方案 b），缓存 key 是二元组 `(tenant, spec)`，单实例多用户（§6.2 姿势）。后者实例数更少、与官方单例姿势最贴合；前者物理隔离最硬。NexAI 多租户 + 会话调试场景建议方案 b（tenant 进路径、user 进 rc），并把 `RuntimeContext.userId` 设为平台内部用户 ID（避免空 uid 触发 sid 回落造成意外前缀）。
4. **平台侧文件管理 API 不走 call 路径**：用 `agent.workspaceFor(uid, sid)` 视图做带外读写（§3.2）——它是为 per-request controller 并发设计的官方出口；视图随用随 new、无需 close。注意其 nsFactory 只认 uid（§3.2 末尾细节），调用时必须传 uid。管理端"工作区文件树/编辑器"类接口（对标 dataagent 的 `AgentWorkspaceController` + `WorkspaceManagerFactory.userDataFs`）直接复用此机制即可，无需自建。
5. **与 AgentSpec 聚合的装配序列**（实例化时序建议）：
   ```
   AgentSpec 发布/更新 → 应用服务取 DB 快照 → 物化 workspace 目录（AGENTS.md/skills/subagents/knowledge，
   writeIfMissing 或内容比对覆写）→ HarnessAgent.builder().workspace(根).agentId(specCode)...build()
   → 存入 AgentInstanceManager 缓存 → 会话 call 时 RuntimeContext(userId, sessionId)
   ```
   首次调用前目录必须就绪（validate 只 warn，但缺 AGENTS.md 的 agent 人设为空）；workspace 目录创建放在物化阶段而非依赖框架惰性创建。
6. **多副本部署（NexAI 生产形态）**：本地 Local 模式多实例共挂同一 workspace 路径不可取（无 CAS）；要么单写者，要么上 `RemoteFilesystemSpec + RedisDistributedStore`（一行装配，§6.3 SharedStore 示例），并接受其 build 时 fail-fast 约束（必须分布式 AgentStateStore）。沙箱多副本需 DistributedStore 承载快照 + executionGuard。

---

## 9. 来源清单

**harness 源码（`<AS>/agentscope-harness/src/main/java/io/agentscope/harness/agent/`）**

- `HarnessAgent.java`：L159-163（单例 javadoc）、L164-235（类/字段/构造）、L242-253（workspaceFor）、L453-482（close/shutdownTaskRepository）、L708-873（call/streamEvents 入口）、L942-957（wrappedStreamEvents）、L965-995（ensureSessionDefaults）、L1085-1127（defaultStateDir/resolveDefaultWorkspace）、L2224-2830（build 全流程，重点 L2230-2370、L2559-2620、L2623-2632、L2800-2829）
- `HarnessAgentBuilderSupport.java`：L147-168（resolveFilesystem）、L177-189（buildBakedRuntimeContext）、L199-285（buildSubagentEntries）、L290-387（buildGeneralPurposeFactory）、L392-522（buildDeclaredFactory）、L553-566（cloneLocalSpecForSubagent）、L603-617（deriveChildSessionId）、L635-658（resolveDeclaredWorkspace）、L718-750（子代理中间件构建）、L803-852（composeSkillRepositories）
- `workspace/WorkspaceManager.java`：L97-167（类与构造器族）、L176-181（close）、L200-224（validate）、L237-246（resolveRuntimeDataPath）、L108-118（pathLocks 语义）
- `workspace/WorkspaceIndex.java`：L50 起（类、INDEX_DIR/INDEX_DB、open best-effort）
- `filesystem/spec/LocalFilesystemSpec.java:L288-316`（toFilesystem）
- `filesystem/spec/RemoteFilesystemSpec.java:L204-316`（toFilesystem/overlayRoute/exactFileOverlay/remoteForRoute/storeNamespace）
- `filesystem/spec/SandboxFilesystemSpec.java:L36-141`（spec 契约、toSandboxContext、投影）
- `sandbox/impl/docker/DockerFilesystemSpec.java:L36-102`
- `filesystem/BakedContextFilesystem.java:L31-113`
- `filesystem/sandbox/SandboxBackedFilesystem.java:L44-70`（稳定代理 + volatile sandbox）
- `filesystem/RoutedSandboxFilesystem.java:L38-60`
- `filesystem/local/LocalFilesystem.java:L102-146`（构造器/virtualMode 语义）、`local/LocalFilesystemWithShell.java:L48`、`OverlayFilesystem.java:L56`、`CompositeFilesystem.java:L64`、`ProjectAwareOverlay.java:L42`、`remote/RemoteFilesystem.java:L63`、`sandbox/BaseSandboxFilesystem.java:L55`
- `IsolationScope.java:L49-126`（顺序复用注释、toNamespaceFactory）
- `middleware/SandboxLifecycleMiddleware.java:L29-159`（acquireForCall/releaseForCall）
- `sandbox/SandboxManager.java:L40-216`（acquire 4 优先级/release/persistState）
- `middleware/SubagentsMiddleware.java:L608-636`（loadSubagentSnapshot 动态扫描）
- `subagent/DefaultAgentManager.java:L107-119`（createAgentIfPresent）、`tool/AgentSpawnTool.java:L350`

**core**：`agentscope-core/src/main/java/io/agentscope/core/workspace/package-info.java:L17-23`

**官方文档（docs/v2/zh）**：`docs/harness/workspace.md`、`docs/harness/filesystem.md`（L407 顺序复用、L499 tools.json build 时读取）

**示例（agentscope-examples）**：`documentation/.../workspace/WorkspaceSetupExample.java:L151-161`、`WorkspaceSandboxExample.java:L93-160`、`WorkspaceSharedStoreExample.java:L87-97`、`quickstart/UserIsolatedMultiTurnsExample.java:L49-93`；`agents/agentscope-dataagent/.../runtime/DataAgentBootstrap.java:L539-626`、`web/workspace/WorkspaceManagerFactory.java:L40-106`、`web/workspace/UserSandboxRegistry.java:L60-156`

**前置报告**：`docs/research/2026-08-23-agentscope-workspace-目录结构.md`（目录布局、NamespaceFactory 前缀、路由表、沙箱投影、AGENTS.md 机制等以该报告为准）
