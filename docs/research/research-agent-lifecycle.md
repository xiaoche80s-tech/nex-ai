# AgentScope Java：Agent 实例创建与配置更新同步机制研究

> 研究范围：
> - A. agentscope-paw（Claw2）如何创建 Agent 实例，提示词/工具更新如何同步
> - B. HarnessAgent / ReActAgent 核心运行时原理（workspace 上下文、middleware、tools.json、skills、subagent）
> - C. 横向对比：paw / dataagent / codingagent / builder 数据面（service-dataplane）的 Agent 生命周期管理
>
> 方法：全部结论回溯到仓库一手源码，引用格式为 `相对路径:行号`。

---

## A. agentscope-paw 的实例创建与更新同步

### A.1 两类 Agent 定义

`AgentCatalogService` 类注释明确划分两类 Agent
（`agentscope-examples/agents/agentscope-paw/src/main/java/io/agentscope/claw2/web/catalog/AgentCatalogService.java:46-60`）：

| 类型 | 定义来源 | 可编辑性 | 实例化时机 |
|---|---|---|---|
| 内置 Agent | `${clawHome}/agentscope.json` | API 只读（编辑返回 409） | 应用启动时 |
| 自定义 Agent | `${clawHome}/agents.json`（`UserAgentDefinitionStore`） | REST 增删改 | **首次对话时懒加载构建** |

`clawHome` 默认为 `~/.agentscope/claw`，是所有磁盘状态（配置、catalog、workspace、会话存储）的根
（`.../claw2/runtime/ClawBootstrap.java:61-73`）。

### A.2 启动期创建：内置 Agent

Spring 配置类 `BuilderConfig.builderBootstrap()` 构建单例 `ClawBootstrap`
（`.../claw2/web/config/BuilderConfig.java:150-227`）：

1. 若 `agentscope.json` 不存在，自动生成一个最小 `default` agent 配置并脚手架其 workspace
   （`BuilderConfig.ensureAgentscopeConfig`，同文件 `:266-297`）。
2. 通过 `configureAllAgents` 给所有 agent 注入 `ToolNotificationMiddleware`（工具事件总线）、
   TranscriptStore、aistio adapter middleware（`:180-192`）。
3. 调用 `ClawBootstrap.Builder.build()`，三阶段装配
   （`.../claw2/runtime/ClawBootstrap.java:544-693`）：
   - **阶段 1**：从主 agent 的 builder 提取 subagent entries → `WorkspaceManager` →
     `DefaultAgentManager` → `SessionStore`（磁盘加载）→ `SessionAgentManager` →
     `ChannelManager` → `HarnessGateway` → `SessionsTool` / `OutboundTool`。
   - **阶段 2**：对每个 agent id，`applyFileEntry()` 把 `AgentConfigEntry`
     （name、agentId、description、sysPrompt、workspace、maxIters、model、skill 仓库、identity）
     灌入 `HarnessAgent.Builder`，再注入 `externalSubagentTool(sessionsTool)`、
     预注册 `OutboundTool` 的独立 Toolkit，最后应用 customizer 并 `b.build()`
     （`applyFileEntry` 位于同文件 `:403-448`）。
   - **阶段 3**：`gateway.registerAgent(id, agent)` 逐个注册 + `gateway.bindMainAgent(main)`
     （`:667-672`）。网关注册即 `agentRegistry.put`，可覆盖同 id 旧实例
     （`.../claw2/runtime/gateway/HarnessGateway.java:206-210`）。

### A.3 运行时创建：自定义 Agent 懒加载

`POST /api/agents` 只落盘定义 + 脚手架 workspace，不构建实例
（`AgentCatalogService.createAgent`，`:136-206`；脚手架来源优先级：template > AI draft > 默认脚手架，
AI draft 会物化 `AGENTS.md`/`tools.json`/`skills/`/`subagents/`，见 `writeDraftFiles` `:424-488`）。

首次对话链路：

```
ChatController.stream/send  (.../claw2/web/api/ChatController.java:107-167, 328-331)
  → resolveGateKey → AgentCatalogService.resolveGatewayAgentId (:284-297)
      → registeredCustomIds.computeIfAbsent → buildAndRegisterCustom (:354-380)
          HarnessAgent.builder() + sysPrompt/workspace/model/middleware → build()
          → gateway.registerAgent(entry.id(), agent)
  → gateway.run(route.context(), msgs, outbound)
```

`registeredCustomIds`（`ConcurrentHashMap<String,String>`）是"本进程已构建"缓存
（`AgentCatalogService.java:74-75`）。

### A.4 会话级实例缓存

`SessionAgentManager` 用 `agentCache`（`ConcurrentHashMap<sessionKey, Agent>`）按会话缓存实例，
避免每次 `execute()` 重建（`.../claw2/runtime/session/SessionAgentManager.java:99-101, 508-513`）。
淘汰点：`evictAgent`（`:523-528`）、`resetSession`（`/new`、`/reset`、空闲/每日自动重置，`:547-614`）、
`removeSession`（维护清理，`:670-691`）。

### A.5 提示词更新同步

提示词分两层，同步机制不同：

**层次 1 — 定义中的 sysPrompt：失效缓存 + 下次对话重建。**
`PUT /api/agents/{id}` → `updateAgent`（`AgentCatalogService.java:209-258`）：

1. 合并字段写回 `agents.json`；
2. `registeredCustomIds.remove(agentId)` —— 清除构建缓存（`:253-254`）；
3. 下次对话触发 `resolveGatewayAgentId` 缓存缺失 → 用新定义重建 `HarnessAgent` 并覆盖注册。

没有对运行中实例的热更新，策略是"定义落盘 → 缓存失效 → 懒重建"的最终一致。
内置 agent 走 API 编辑直接 409（`:211-214`）。

**层次 2 — workspace 上下文文件（AGENTS.md 等）：每次调用实时读取。**
`WorkspaceContextMiddleware.onSystemPrompt` 在每次 `call()` 时执行
`buildWorkspaceSection` → `workspaceManager.readAgentsMd(rc)` / `readMemoryMd` / `readKnowledgeMd`
（`agentscope-harness/.../middleware/WorkspaceContextMiddleware.java:38-43, 184-227`）。
因此通过 workspace 编辑器修改 `AGENTS.md`/`MEMORY.md` 下一轮对话立即生效，无需重建实例。

### A.6 工具更新同步

**tools.json（allow/deny + MCP）——构建时固化。**
`PUT /api/agents/{id}/tools/config` 只是把 JSON 写回 `workspace/tools.json`
（`.../claw2/web/api/AgentToolsController.java:225-252`），**不失效任何运行实例缓存**（注意：此处与
dataagent 不同，见 C.4）。由于 `ToolsConfigLoader.load` + `McpServerRegistrar.register` +
`ToolFilter.apply` 都发生在 `HarnessAgent.Builder.build()` 内
（`agentscope-harness/.../HarnessAgent.java:2609-2620, 2794-2798`），已构建实例的工具集不变；
需要走 `updateAgent` 触发重建，或重启（内置 agent）。
`GET /tools/active` 用 NoopModel 现场构建临时 agent 做内省，仅供展示（`AgentToolsController.java:159-166`）。

**每次调用动态刷新的部分（改文件立即生效）：**

| 能力 | 机制 | 出处 |
|---|---|---|
| Skills（`skills/*/SKILL.md`） | `HarnessSkillMiddleware` 每次调用动态加载（除非 `disableDynamicSkills`） | `HarnessAgent.java:2762-2778` |
| Subagent 声明（`subagents/*.md`） | `SubagentsMiddleware` 每次 `onAgent` 从 workspace 文件系统重新加载 | `SubagentsMiddleware.java:65-69, 609-637` |
| Workspace 提示词文件 | `WorkspaceContextMiddleware` 每次调用读取 | 见 A.5 |

---

## B. HarnessAgent / ReActAgent 核心运行时原理

### B.1 HarnessAgent 定位

`HarnessAgent` 是面向用户的 harness API，包装一个内部 `ReActAgent`，叠加
workspace / filesystem / sandbox / subagent / skill / plan-mode / MCP 编排；
它在调用之间无状态，可作单例并发服务多用户/会话，靠 `RuntimeContext` 的 `(userId, sessionId)` 隔离
（`agentscope-harness/.../HarnessAgent.java:139-163`）。

### B.2 build() 期固化的内容

`Builder.build()`（`HarnessAgent.java:2153-2830`）期间一次性完成：

- Toolkit 深拷贝（每次 build 独立，工具不跨实例泄漏，`:2154-2156`）；
- filesystem 解析（sandbox / remote / local spec 互斥，`:2158-2172`）；
- `workspace/tools.json` 加载 → MCP server 注册 + allow/deny 过滤（`:2609-2620, 2794-2798`）；
- Skill 仓库组合与 skill 自学习管线（`:2622-2723`）；
- 各 middleware 装配（plan mode、skill、subagents、workspace context 等），最终 `inner.build()`
  产出内部 `ReActAgent`（`:2807-2810`）。

**推论**：middleware 列表、toolkit、tools.json 过滤结果均在 build 时冻结；
这正是 A.5/A.6 中"定义变更必须重建实例"的底层原因。
`BuilderConfig` 的注释也直接点明："A ReActAgent's middleware list is fixed at build time"
（`BuilderConfig.java:189-192`）。

### B.3 call() 期动态加载的内容

每次调用通过 middleware 链实时读取 workspace：

- **Workspace 上下文**：`WorkspaceContextMiddleware` 把会话信息、AGENTS.md、MEMORY.md、
  knowledge 目录清单按 token 预算（默认 8000）拼接进 system prompt
  （`WorkspaceContextMiddleware.java:128, 184-227`）；MEMORY.md 按剩余预算截断（`:213-219`）。
- **Subagent 声明**：`SubagentsMiddleware.loadSubagentSnapshot` 每次 `onAgent` 用
  `AgentSpecLoader.loadFromFilesystem` 重载声明并重建快照级 `DefaultAgentManager`
  （支持按用户隔离；文件系统/工厂缺失时退化为静态 baseEntries）
  （`SubagentsMiddleware.java:609-637`）。
- **Skills**：`HarnessSkillMiddleware` 动态模式每次调用从仓库加载（`HarnessAgent.java:2762-2778`）。

### B.4 子代理编排

启动期 `ClawBootstrap` 用 `buildSubagentEntries(workspace)` 提前抽取 subagent 工厂
（general-purpose + 声明式 + 自定义工厂），交给共享的 `DefaultAgentManager`
（`ClawBootstrap.java:602-605`）；`SessionAgentManager` 只委托它做 `createAgent`/`invokeAgent`
（类注释 `SessionAgentManager.java:62-63`）。声明式 subagent 的 sysPrompt 可在运行时从
`<workspace.path>/AGENTS.md` 读取（`AgentSpecLoader.java:69`；`WorkspaceMode.java:49-63`）。

---

## C. 多工程横向对比：Agent 生命周期管理

### C.1 对比总表

| 维度 | paw (Claw2) | dataagent | codingagent | builder 数据面 (service-dataplane) |
|---|---|---|---|---|
| Bootstrap | `ClawBootstrap`（启动期全量构建内置 agent） | `DataAgentBootstrap`（同构，`:563-626` 与 paw 几乎相同） | `CodingBootstrap`（纯启动期，无 catalog） | 无统一 bootstrap；按会话 turn 构建 |
| 自定义 Agent 存储 | `${clawHome}/agents.json`，本地单用户 | `.agentscope/users/{userId}/agents.json`，**多用户**（可 JPA 持久化） | 无 | 控制面（aistiod）下发 snapshot + definitionFiles |
| 实例缓存键 | agentId（`registeredCustomIds`） | `userId+agentId`（`registeredUcaIds`） | 无（仅网关注册） | `sessionOwner/agentId/spec.cacheSuffix()`（team 会话追加 sessionId） |
| 定义更新同步 | `updateAgent` 清缓存 → 下次对话重建 | 同 paw（`:453-454`） | 不适用 | **版本化缓存键**：spec 变化 → 键变化 → 自然新建实例 |
| tools.json/skills 变更同步 | **不失效**实例（仅写文件） | `invalidateUca()` 显式失效，下次对话重建 | 不适用 | 随 snapshot 版本走；另有 `evict(owner, agentId)` |

### C.2 paw vs dataagent：同源但策略更完整

两者 catalog 服务同构，但 dataagent 多了一个 paw 缺失的关键机制 ——
`AgentCatalogService.invalidateUca(userId, agentId)`，注释明确其用途：
"Intended for controllers that mutate per-agent runtime resources (tools.json, skills/, etc.)
after the agent has already been instantiated"
（`agentscope-examples/agents/agentscope-dataagent/.../web/catalog/AgentCatalogService.java:571-580`）。

dataagent 的 `AgentToolsController`（写 tools.json 后，`:286`）与 `AgentSkillsController`
（skill 新增/删除/安装后，`:214, 242, 407, 502`）都会调用它，保证工具/技能变更下次对话生效。

**结论：paw 的 `PUT /tools/config` 缺少等价的失效调用** —— 只改 tools.json 而不走
`updateAgent`，已构建的自定义 agent 实例不会感知工具变更（直到定义更新触发重建或重启）。
这是两个工程在"工具更新同步"上的实质性差异。

### C.3 codingagent：无动态生命周期

`agentscope-codingagent` 仅有 `CodingBootstrap` 启动期构建 + `HarnessGateway.registerAgent`
（`.../coding/CodingBootstrap.java:699`），无 catalog、无 REST CRUD，配置变更依赖重启。

### C.4 builder 数据面（service-dataplane）：版本化快照驱动

数据面工厂 `HarnessAgentBuildService` 为托管会话构建并缓存 `HarnessAgent`
（`agentscope-service/service-dataplane/.../web/catalog/HarnessAgentBuildService.java:59-77`）：

- Managed session 的构建**只**依据控制面 resolve 下发的 `agentSnapshot` / `workspacePath` /
  `definitionFiles`，无本地 JPA catalog 回退（`:62-66`）。
- 缓存键 `cacheKey = sessionOwner/agentId/spec.cacheSuffix()`，team 会话再拼 sessionId
  （team 角色在 build 时固定，必须独占实例）（`:145-148` 及其 Javadoc `:139-144`）。
- `SessionAgentBuildSpec.cacheSuffix()` 由 `version`（定义版本，缺省 "head"）、environmentId、
  memoryStoreIds、vaultIds、overridesJson/resources 哈希拼成
  （`agentscope-service/service-common/.../SessionAgentBuildSpec.java:47-69`）。
  **控制面发布新版本 → cacheSuffix 变化 → 缓存键 miss → 自动构建新实例**；
  旧键实例不再命中（另有 `evict(owner, agentId)` 按前缀清除，`:156-160`）。
- 执行侧 `SessionTurnRunner.runTurn` 每轮调用 `agentBuildService.getOrBuildAgent(session, spec)`
  （`.../web/managed/SessionTurnRunner.java:255-273`）。

**结论**：数据面用"定义版本号进入缓存键"实现了声明式的更新传播，无需显式失效调用；
paw/dataagent 则用"显式清缓存 + 懒重建"。三种模式（重启生效 / 显式失效重建 / 版本键自然替换）
构成该仓库 Agent 配置更新的完整谱系。

---

## 附：关键文件索引

| 主题 | 文件 |
|---|---|
| paw 启动装配 | `agentscope-examples/agents/agentscope-paw/src/main/java/io/agentscope/claw2/web/config/BuilderConfig.java` |
| paw Bootstrap | `.../claw2/runtime/ClawBootstrap.java` |
| paw catalog（创建/更新/懒构建） | `.../claw2/web/catalog/AgentCatalogService.java` |
| paw 会话管理 | `.../claw2/runtime/session/SessionAgentManager.java` |
| paw 工具配置端点 | `.../claw2/web/api/AgentToolsController.java` |
| HarnessAgent 构建 | `agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java` |
| workspace 上下文中间件 | `agentscope-harness/.../middleware/WorkspaceContextMiddleware.java` |
| subagent 动态重载 | `agentscope-harness/.../middleware/SubagentsMiddleware.java` |
| tools.json 加载/过滤 | `agentscope-harness/.../tools/ToolsConfigLoader.java`、`ToolFilter.java`、`McpServerRegistrar.java` |
| dataagent 失效机制 | `agentscope-examples/agents/agentscope-dataagent/.../web/catalog/AgentCatalogService.java` |
| 数据面版本化构建 | `agentscope-service/service-dataplane/.../web/catalog/HarnessAgentBuildService.java`、`agentscope-service/service-common/.../SessionAgentBuildSpec.java` |
