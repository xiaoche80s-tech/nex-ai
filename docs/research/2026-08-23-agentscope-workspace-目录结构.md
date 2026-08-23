# agentscope-java v2 HarnessAgent 运行时 workspace 目录结构调研

> 调研日期：2026-08-23
> 调研对象：本机 `~/agent-project/agentscope-java`（GitHub agentscope-ai/agentscope-java，main 分支，主干 2.0.3-SNAPSHOT）之 `agentscope-harness`、`agentscope-core` 源码；辅以 `agentscope-examples`（documentation / dataagent）与 `.qoder/repowiki`。
> 调研纪律：**只采信 v2 主干源码**；`.qoder/repowiki` 部分页面引用的是 v1 包路径（如 `io/agentscope/harness/workspace/...`，无 `agent` 层），仅作交叉佐证、不作论据；v1 文档不作论据。
> 引用约定：`~/agent-project/agentscope-java/` 简写为 `<AS>`；行号以当前工作区文件为准。除特殊说明，"workspace" 均指 `WorkspaceManager` 绑定的 agent workspace 根目录。

---

## TL;DR

- **根路径**：默认 `${user.dir}/.agentscope/workspace`；`Builder.workspace(Path)` 显式指定，未指定时按 `agentscope.workspace` 系统属性 → `AGENTSCOPE_WORKSPACE` 环境变量 → 内置默认 三级解析（`HarnessAgent.resolveDefaultWorkspace()`）。
- **一个 HarnessAgent 实例 = 一个 workspace 根**；根内的 per-user / per-session 隔离不靠子目录约定，靠 `NamespaceFactory` 在读写时给**相对路径动态加前缀**（默认 `IsolationScope.USER` → `<userId>/` 前缀；无 userId 回落 sessionId）。
- workspace 内布局是**框架硬编码的相对路径集合**：`AGENTS.md`、`MEMORY.md`、`memory/YYYY-MM-DD.md`、`skills/`、`knowledge/`、`subagents/`、`tools.json`、`plans/`、`agents/<agentId>/sessions/`、`agents/<agentId>/tasks/`、`agents/<name>/workspace/`、`.index/workspace.db`、`.agentscope/bus`、`.agentscope/transcripts/`——这些名字/位置不能被平台侧布局破坏。
- **AGENTS.md 由使用方（用户/平台）提供，框架从不生成**；运行时每次 `call()` 经 `WorkspaceContextMiddleware` 重读并注入 system prompt，改完下一次 call 即生效。
- 沙箱模式下 workspace 分两面：宿主根目录继续作为模板/投影源，容器内 `/workspace` 是运行面（投影 + bind mount + tar 快照）。
- 对 NexAI：把 agentscope 的 workspace 根指到 `t{tenantId}/u{userId}/{specCode}` 的 **specCode 层**，即「一个 AgentSpec 一个 workspace 根」；原生相对布局整体下沉到该层之下，一层都不要动。

---

## 1. workspace 根路径：默认值、可配置项、Builder 用法

### 1.1 默认值与覆盖顺序

路径常量：`<AS>/agentscope-harness/src/main/java/io/agentscope/harness/agent/workspace/WorkspaceConstants.java:L23`

```java
public static final String DEFAULT_WORKSPACE_ROOT = ".agentscope/workspace";
```

未显式指定时的解析逻辑（`<AS>/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:L1094-1127`）：

```java
static final String WORKSPACE_PROPERTY = "agentscope.workspace";   // 系统属性
static final String WORKSPACE_ENV = "AGENTSCOPE_WORKSPACE";        // 环境变量

static Path resolveDefaultWorkspace() {
    // 1) 系统属性 agentscope.workspace
    // 2) 环境变量 AGENTSCOPE_WORKSPACE
    // 3) ${user.dir}/.agentscope/workspace （内置默认，相对于进程工作目录）
}
```

即优先级：**系统属性 > 环境变量 > `${cwd}/.agentscope/workspace`**。注释明确该机制面向容器镜像部署时运行期注入 workspace 位置。

### 1.2 Builder 指定方式

`HarnessAgent.Builder.workspace(Path)` / `workspace(String)`（同文件 `:L1694-1731`）：传 `null` 时在 `build()` 时回落到 `resolveDefaultWorkspace()`。build 时的实际解析点在同文件 `:L2244`：

```java
Path resolvedWorkspace = workspace != null ? workspace : resolveDefaultWorkspace();
```

### 1.3 与 workspace 并列、但位于其外的目录

agent 自身状态（AgentStateStore）默认落在 **`~/.agentscope/state/<agentId>/`**（`JsonFileAgentStateStore`，同文件 `:L1073-1092`，`agentscope.state.home` 系统属性可改根）——注释明说"lives outside any workspace so agent state ... is not entangled with workspace data"。沙箱的 `_sandbox.json` 状态也挂在这个 store 上。

### 1.4 默认（不调 `filesystem(...)`）时的文件系统

`HarnessAgentBuilderSupport.resolveFilesystem()`（`<AS>/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgentBuilderSupport.java:L146-168`）：三种 spec 都未配置时，默认 `new LocalFilesystemSpec().toFilesystem(workspace, nsFactory)` —— 即 workspace 为读写上层、`${user.dir}`（用户工程目录）为只读下层的 Claude-Code 式 overlay。

---

## 2. 目录内容清单：有什么、谁写入、何时写入

### 2.1 路径模板（权威出处：`WorkspaceManager` 类 javadoc）

`<AS>/agentscope-harness/src/main/java/io/agentscope/harness/agent/workspace/WorkspaceManager.java:L81-95` 给出的期望布局，结合各写入方代码，完整形态如下（`{ns}` 为运行时命名空间前缀，见第 3 节，无身份时为空）：

```
{workspace}/                          ← Builder.workspace() 指定的根
├── AGENTS.md                         # 人设/本地约定，用户或平台编写，框架只读
├── MEMORY.md                         # 策展长期记忆（有界、去重）
├── tools.json                        # 工具 allow/deny + mcpServers 声明
├── {ns}/                             # ← 命名空间前缀层（默认 USER 时 = <userId>/）
│   ├── MEMORY.md                     #   USER 隔离下 memory 元数据实际落点
│   ├── memory/
│   │   └── YYYY-MM-DD.md             #   每日记忆账本（append-only）
│   └── agents/
│       └── <agentId>/
│           ├── workspace/            #   ISOLATED 子代理的运行时根（自动创建，运行时决定）
│           ├── sessions/
│           │   ├── sessions.json     #   会话索引（sessionId → {summary, updatedAt}）
│           │   ├── <sessionId>.jsonl        # 会话转录：LLM 视图（可被压缩）
│           │   └── <sessionId>.log.jsonl    # 会话转录：完整历史（append-only 永不压缩）
│           └── tasks/
│               └── <sessionId>.json  #   该会话的 TaskRecord 表（taskId → record）
├── skills/
│   ├── <skill-name>/SKILL.md         # 用户技能
│   └── _drafts/<name>/               # 技能草稿区（SkillManageTool 写，SkillPromoter 升级搬移）
├── knowledge/
│   ├── KNOWLEDGE.md                  # 领域知识入口（全文注入）
│   └── *.md                          # 其余知识文件（只列路径、按需读）
├── subagents/
│   └── <name>.md                     # 声明式子代理定义（文件名即子代理名）
├── plans/                            # Plan 模式产物（PlanModeManager.DEFAULT_PLAN_DIR）
├── .index/workspace.db               # SQLite 索引（仅 Remote 模式 build 时打开）
└── .agentscope/                      # harness 内部元数据（同样在 workspace 之下）
    ├── bus/                          # WorkspaceMessageBus（多 agent 频道消息）
    ├── bus/async-tools/              # WorkspaceAsyncToolRegistry
    └── transcripts/                  # FilesystemTranscriptStore 兜底段存储
        └── <tenant>/<agentId>/<sessionId>/events/<seqStart>-<seqEnd>-<writer>.jsonl
```

所有目录名的字符串常量集中在 `WorkspaceConstants.java:L25-48`（`AGENTS_MD`、`MEMORY_MD`、`TOOLS_JSON`、`MEMORY_DIR="memory"`、`SKILLS_DIR="skills"`、`KNOWLEDGE_DIR="knowledge"`、`AGENTS_DIR="agents"`、`SESSIONS_DIR="sessions"`、`TASKS_DIR="tasks"`、`SESSIONS_STORE="sessions.json"`、`SESSION_CONTEXT_EXT=".jsonl"`、`SESSION_LOG_EXT=".log.jsonl"`）。

### 2.2 每个条目的写入者与时机

| 路径 | 写入者 | 时机 | 追加/覆盖 |
|---|---|---|---|
| `AGENTS.md` | **使用方（用户/平台），框架不写** | 预先准备 | ——（框架只读） |
| `MEMORY.md` + `memory/YYYY-MM-DD.md` | `MemorySaveTool`（`memory_save` 工具，`<AS>/.../tool/MemorySaveTool.java:L73-80` 双写）；`MemoryFlushManager`（记忆压缩 flush 追加当日账本，`<AS>/.../memory/MemoryFlushManager.java:L121-123`）；`MemoryConsolidator` 周期整理 `MEMORY.md`（javadoc `:L46-49`：ledger 仅 FlushManager 写、MEMORY.md 仅 Consolidator 写） | 会话中工具调用 / 压缩触发 / 周期维护 | ledger 追加；MEMORY.md 读改写 |
| `agents/<agentId>/sessions/<sessionId>.jsonl` + `.log.jsonl` | `TranscriptMiddleware` → `SessionTranscriptWriter.appendMessages`（`<AS>/.../middleware/TranscriptMiddleware.java:L68-78`：`next.apply(input).concatWith(appendTranscript)`，即**每次 agent call 完成后**；writer 按 entry id 去重幂等追加，`<AS>/.../memory/session/SessionTranscriptWriter.java:L86-146`；`.log.jsonl` 由 `SessionTree` 构造时按 `contextFile.resolveSibling(baseName + ".log.jsonl")` 派生，`<AS>/.../memory/session/SessionTree.java:L185`） | 每 call 末尾（boundedElastic 异步） | `.log.jsonl` 追加；`.jsonl` 是 LLM 上下文可被 compaction 压缩（`SessionTree.java:L52-58` 双文件模式说明） |
| `agents/<agentId>/sessions/sessions.json` | `WorkspaceManager.updateSessionIndex`（`:L404-435`，sessionId → `{summary, updatedAt}`，带 `version:1`） | 每次转录追加成功后 | 读改写（per-path 锁） |
| `agents/<agentId>/tasks/<sessionId>.json` | `WorkspaceTaskRepository` / `TaskTool` → `WorkspaceManager.writeTaskRecord`（`:L446-477`，路径拼接 `taskRecordPath` `:L599-601` = `agents/<agentId>/tasks/<sessionId>.json`） | 子代理任务状态变更 | 读改写（taskId upsert） |
| `agents/<name>/workspace/` | `HarnessAgentBuilderSupport.resolveDeclaredWorkspace`（`:L635-656`）：ISOLATED 且未指定 path 时 `mainWorkspace/agents/<name>/workspace/` 并 `createDirectories`——**运行时决定**（首次构建该子代理时创建） | 子代理首次装配 | —— |
| `skills/`、`knowledge/`、`subagents/`、`tools.json`、`AGENTS.md` | 使用方手写或平台脚手架；示例：dataagent `WorkspaceScaffolder.scaffold()`（`<AS>/agentscope-examples/agents/agentscope-dataagent/src/main/java/io/agentscope/dataagent/web/scaffold/WorkspaceScaffolder.java:L40-63`：createDirectories + `writeIfMissing` 生成 AGENTS.md/tools.json/example-skill/subagents README/memory/.gitkeep） | agent 创建时 | 只补缺不覆盖 |
| `skills/_drafts/` | `SkillManageTool` 写草稿（`<AS>/.../tool/SkillManageTool.java:L56,78`），`SkillPromoter` 把 `_drafts/<name>/` move 到 `skills/<name>/`（`<AS>/.../skill/curator/SkillPromoter.java:L75,139`，默认 `draftsDir = "skills/_drafts"`） | 技能自学习流程 | —— |
| `plans/` | `PlanModeManager`，`DEFAULT_PLAN_DIR = "plans"`（`<AS>/.../workspace/plan/PlanModeManager.java:L42`） | Plan 模式 | —— |
| `.index/workspace.db` | `WorkspaceIndex.open(workspace)`（`<AS>/.../workspace/WorkspaceIndex.java:L57-58`：`INDEX_DIR=".index"`、`INDEX_DB="workspace.db"`，SQLite）；仅在 `build()` 中 `remoteFilesystemSpec != null` 时打开（`HarnessAgent.java:L2308-2309`） | Remote 模式 build 时 | 索引表 |
| `.agentscope/bus`、`.agentscope/bus/async-tools` | `WorkspaceMessageBus` / `WorkspaceAsyncToolRegistry`，build 时未显式配置且 filesystem 非空时的默认值（`HarnessAgent.java:L2375-2384`） | 消息/异步工具收发 | —— |
| `.agentscope/transcripts/...` | `FilesystemTranscriptStore`（`<AS>/.../transcript/FilesystemTranscriptStore.java:L34-36`：每段为不可变文件 `{root}/{tenant}/{agentId}/{sessionId}/events/`，段名 `{seqStart}-{seqEnd}-{writer}.jsonl`，`CREATE_NEW` 不覆盖）；仅当 filesystem 为 null 才启用（`HarnessAgent.java:L2410-2422`：有 filesystem 时优先 `ObjectStoreTranscriptStore`） | 每次转录 flush | 段文件只增不改 |

注意：`.agentscope/transcripts` 与 `.agentscope/bus` 是 **workspace 内的相对路径**（`wsManager.getWorkspace().resolve(".agentscope/transcripts")`、filesystem 相对键 `.agentscope/bus`），与 workspace **外**的进程级默认根 `${cwd}/.agentscope/workspace` 是两回事。

### 2.3 读写通道：两层模型

`WorkspaceManager` javadoc（`WorkspaceManager.java:L66-80`）：**读**——先问 `AbstractFilesystem`（带 user/session 命名空间），非空即用（filesystem 覆盖），否则回落本地磁盘；**写**——全部走 filesystem；**列目录**——两层取并集按相对路径去重。本地磁盘兜底意味着：即使元数据实际存在远程 KV，workspace 根下的模板文件仍可被读到。

---

## 3. 隔离粒度：实例、用户、会话

### 3.1 实例级：一个 HarnessAgent 一个 workspace 根

`workspace` 是 Builder 字段（`HarnessAgent.java:L1172`），build 时固化进 `WorkspaceManager`（`:L2353-2354`）。不同 agent 实例默认共享同一个 `${cwd}/.agentscope/workspace`（都未指定时），**不会**自动按 agentName 建子目录——隔离靠使用方传不同 `workspace(Path)`，或靠下面的命名空间机制。

### 3.2 用户/会话级：NamespaceFactory 动态前缀，不建固定子目录

隔离由 `IsolationScope` 定义（`<AS>/.../harness/agent/IsolationScope.java:L53-127`）：

- `USER`（**默认**）：namespace = `[userId]`；无 userId 回落 `[sessionId]`；
- `SESSION`：namespace = `[sessionId]`；
- `AGENT` / `GLOBAL`：无前缀（workspace 本身已按 agent 划分）。

前缀作用点有两处、逻辑一致：

1. `LocalFilesystem.applyNamespacePrefix`（`<AS>/.../filesystem/local/LocalFilesystem.java:L686-704`）：相对路径 → `<ns>/<relPath>`（绝对路径不加前缀）——**本地模式下磁盘上真实出现 `workspace/<userId>/memory/...`、`workspace/<userId>/agents/<agentId>/sessions/...`**；
2. `WorkspaceManager.resolveRuntimeDataPath`（`WorkspaceManager.java:L237-246`）：manager 侧本地磁盘读取/list 时同样拼 `workspace/<ns>/<relPath>`。

因此**per-session 没有独立子目录**（session 只体现在文件名 `<sessionId>.jsonl`）；**per-user 有目录级隔离**（默认 USER scope）。`RuntimeContext` 无身份时 namespace 为空，直接写 workspace 根。

### 3.3 共享单实例的多用户视图：`workspaceFor(userId, sessionId)`

`HarnessAgent.workspaceFor`（`HarnessAgent.java:L243-252` + build 内 `workspaceFactoryFn` `:L2360-2370`）：返回一个把 `userId`/`sessionId` 烘焙进 `BakedContextFilesystem` 与 `nsFactory`（`rc -> List.of(uid)`）的 `WorkspaceManager` 视图——平台用一个 agent 实例服务多用户时的官方姿势（示例 `UserIsolatedMultiTurnsExample.java:L33-84`：alice/ken 各自 `RuntimeContext.builder().userId(...)`）。

### 3.4 子代理隔离：`WorkspaceMode` 五行决策表

`<AS>/.../subagent/WorkspaceMode.java:L23-40`：

| workspacePath | mode | 运行时根 |
|---|---|---|
| set | ISOLATED | workspacePath（定义目录即运行根） |
| set | SHARED | mainWorkspace（其 skills/knowledge 被忽略） |
| null | ISOLATED | **mainWorkspace/agents/\<name\>/workspace/**（自动创建，运行时决定） |
| null | SHARED | mainWorkspace |
| general-purpose | （恒 SHARED） | mainWorkspace |

实现于 `HarnessAgentBuilderSupport.resolveDeclaredWorkspace`（`:L635-656`）。注意与 sessions/tasks 的 `agents/<agentId>/` 同住 `agents/` 目录但用途不同：`agents/<name>/workspace/` 是子代理完整工作区，`agents/<agentId>/sessions|tasks/` 是运行数据。

### 3.5 多次运行/重启的行为

- **追加**：`memory/YYYY-MM-DD.md`（append 语义，`appendUtf8WorkspaceRelative` `:L368-395`——远程模式是 read-merge-write 模拟追加）、`<sessionId>.log.jsonl`、`.agentscope/transcripts` 段文件；
- **按 session 分文件**：`sessions/<sessionId>.jsonl/.log.jsonl`、`tasks/<sessionId>.json`；
- **读改写覆盖**（进程内 per-path `ReentrantLock` 串行化，跨副本 last-writer-wins，`:L108-118` 注释明说无服务端 CAS）：`MEMORY.md`、`sessions.json`、task json、`tools.json`；
- 本地写走 temp 文件 + `ATOMIC_MOVE` 原子替换（`writeLocalFile` `:L844-883`）；
- 重启恢复：agent 状态在 workspace **外**的 `~/.agentscope/state/<agentId>/`；沙箱模式经其内 `_sandbox.json` + 快照恢复容器。

---

## 4. AGENTS.md 机制

1. **谁生成**：不是框架。`WorkspaceManager.validate()`（`:L196-224`）在 build 时只做存在性检查——目录不存在或 `AGENTS.md` 缺失仅 `log.warn`（"AGENTS.md defines persona and local conventions"），不阻断。官方姿势是使用方预写（示例 `UserIsolatedMultiTurnsExample.java:L99-101`、`WorkspaceSetupExample.java:L66-79`）或平台脚手架生成（dataagent `WorkspaceScaffolder.java:L51-63`，模板即"这个文件夹就是 agent 本身"的约定说明，`:L84-100`）。
2. **格式/内容**：自由 Markdown；dataagent 模板惯例为 `# <displayName>` + 系统提示词正文 + "How this folder works"（说明 tools.json/skills/subagents/memory 的用途）。
3. **运行时消费**：`WorkspaceContextMiddleware.onSystemPrompt`（`<AS>/.../middleware/WorkspaceContextMiddleware.java:L177-188`）——**每次 `call()`** 把 `readAgentsMd(rc)` 的内容包进 system prompt 尾部的 `<loaded_context><agents_context>` 块（`buildLoadedContextSection` `:L448-464`），同场注入的还有 `<memory_context>`（MEMORY.md，超 `maxContextTokens` 预算截断）、`<domain_knowledge_context>`（KNOWLEDGE.md 全文 + knowledge/ 文件路径清单）、`additionalContextFiles` 自定义块；并附加一段 "AGENTS.md defines persona and local conventions — honor them..." 的指令（`:L366-368`）。读取走两层：filesystem（可带用户 namespace）优先、本地磁盘兜底（`readWithOverride` `:L788-794`）。
4. **改了会怎样**：因为每次 call 都重读，修改在**下一次 call 立即生效**，无需重建 agent。Remote 模式下 `AGENTS.md` 是 exact-file 路由（`RemoteFilesystemSpec.java:L220`）：KV 里按用户 namespace 存的版本覆盖 workspace 根的模板版本——存在"用户级覆盖平台模板"的语义。
5. **子代理的 AGENTS.md**：ISOLATED 子代理若指定了 workspacePath，则读 `<workspacePath>/AGENTS.md` 作 sysPrompt（`HarnessAgentBuilderSupport.java:L660-664`）；SHARED 子代理定义目录里的 AGENTS.md 也可作提示词正文但 skills/knowledge 被忽略。

---

## 5. 沙箱（Docker）模式下 workspace 的形态变化

沙箱模式 = `filesystem(DockerFilesystemSpec 等 SandboxFilesystemSpec)`。build 时（`HarnessAgent.java:L2318-2349`）：文件系统整体替换为 `SandboxBackedFilesystem`，文件工具与 shell 全部路由进容器；`WorkspaceManager` 的宿主 `resolvedWorkspace` 仍保留，作为**模板/投影源**。

- **容器内根**：`WorkspaceSpec.root` 默认 `/workspace`（`<AS>/.../sandbox/WorkspaceSpec.java:L45`）。
- **投影（projection）**：宿主 workspace 的 `AGENTS.md`、`skills`、`subagents`、`knowledge`、`.skills-cache`（`SandboxFilesystemSpec.DEFAULT_WORKSPACE_PROJECTION_ROOTS`，`<AS>/.../filesystem/spec/SandboxFilesystemSpec.java:L40-41`）在每次沙箱启动时打 tar 投入容器，按内容哈希跳过未变更投影（`WorkspaceProjectionApplier.java:L33-80`；entry 定义 `WorkspaceProjectionEntry.java:L22-36`）。可 `workspaceProjectionEnabled(false)` / `workspaceProjectionRoots(...)` 调整。
- **bind mount**：spec entries 中的 `BindMountEntry` 转成 `docker run -v <host>:<containerPath>:{ro|rw}`（`<AS>/.../sandbox/impl/docker/DockerSandbox.java:L504-525`；容器侧路径 = `workspaceRoot + "/" + entryKey`，`WorkspaceMountSupport.containerMountPath` `:L53-66`）。
- **持久化**：`doPersistWorkspace` 用 `docker exec tar`（排除 bind mount 路径）把容器内整个 workspaceRoot 打包存入 `SandboxSnapshot`（`DockerSandbox.java:L193-204`）；状态键 `_sandbox.json` 挂 AgentStateStore，按 `IsolationScope`（默认 USER：同用户顺序复用同一沙箱，`IsolationScope.java:L28-51`）。多副本需配 `DistributedStore`（Redis 快照），否则仅本地。
- **路径映射总结**：宿主 `{workspace}/AGENTS.md|skills/... →（投影，单向）→ 容器 /workspace/...`；bind mount 双向；memory/sessions 等元数据写入也经 `SandboxBackedFilesystem` 落在容器 `/workspace` 下，随快照持久化。
- **示例佐证**：`WorkspaceSandboxExample.java:L33-67`（HOST projection + IsolationScope.USER + Redis DistributedStore 写法）；dataagent 的终极形态是 **per-(userId, agentId) 常驻容器**：`WorkspaceManagerFactory.forAgent`（`<AS>/agentscope-examples/agents/agentscope-dataagent/src/main/java/io/agentscope/dataagent/web/workspace/WorkspaceManagerFactory.java:L25-66`）——注释明确 "`getWorkspace()` 返回的 Path 只是宿主侧标签（展示/审计用），不是数据落盘位置，实际读写经 SharedSandboxFilesystem 进容器的 /workspace"。
- system prompt 会如实告知模型（`WorkspaceContextMiddleware.buildWorkspaceParagraph` `:L334-341`："Sandbox root: /workspace (container id: ...) ... host filesystem is not directly accessible"）。

---

## 6. RemoteFilesystemSpec（多副本升级路径）的契约

`<AS>/.../filesystem/spec/RemoteFilesystemSpec.java`（javadoc `:L36-74`）：

- **契约主体**：`BaseStore`（KV 存储，乐观并发在 `BaseStore#putIfVersion`——`WorkspaceManager.java:L113-117` 注释）+ `NamespaceFactory`（每次操作按 `RuntimeContext` 现算 namespace 元组的函数式接口，`<AS>/.../filesystem/remote/store/NamespaceFactory.java:L18-40`）。本地实现与远程实现共同遵守的抽象是 `AbstractFilesystem`（`LocalFilesystem` / `RemoteFilesystem` 同为其子类），workspace 相对路径是统一寻址键。
- **KV namespace 结构**（`storeNamespace` `:L298-316`）：
  - `USER`（默认）→ `["agents", <agentId>, "users", <uid|"_default">]`
  - `SESSION` → `["agents", <agentId>, "sessions", <sid|"default">]`
  - `AGENT` → `["agents", <agentId>, "shared"]`；`GLOBAL` → `["global"]`
- **路由表**（`toFilesystem` `:L204-251`）：`CompositeFilesystem`，默认 backend 是无 shell 的 `LocalFilesystem`（本地工作区文件），以下前缀路由到 KV，每路由附加独立 segment 防键冲突：`AGENTS.md` / `MEMORY.md` / `tools.json`（segment `root`）、`memory/`、`skills/`、`subagents/`、`knowledge/`、`plans/`、`agents/<agentId>/sessions/`、`agents/<agentId>/tasks/`；`addSharedPrefix()` 可加自定义共享前缀。
- **模板兜底**：每条 KV 路由都是 `OverlayFilesystem`：上层 per-user `RemoteFilesystem`，下层只读 `LocalFilesystem`（根在宿主 workspace 对应目录）——脚手架模板内容作基线，用户改动 copy-on-write 落 KV 并覆盖后续读取（`:L182-202`）。
- **护栏**：必须配分布式 `AgentStateStore`，build 时对本地 JsonFile/InMemory store 直接 fail-fast（`HarnessAgent.java:L2299-2307`）；`WorkspaceIndex`（workspace 下 `.index/workspace.db` SQLite）用于加速远程 ls/glob/exists/grep（`RemoteFilesystemSpec.java:L172-179`）。
- 本地模式（`LocalFilesystemSpec.java:L288-316`）遵守同一契约：`LocalFilesystemWithShell`（上层，根=workspace，ROOTED/SANDBOXED/UNRESTRICTED 路径策略）+ 只读 `LocalFilesystem`（下层，根=project，默认 `${user.dir}`）；`projectWritable(true)` 时切换 `ProjectAwareOverlay` 把非 workspace 元数据的写入路由到 project 目录。

另注：`agentscope-core` 的 `io.agentscope.core.workspace` 包目前**只有 package-info**（规划中的 `WorkspaceBase`/`LocalWorkspace` 契约，`<AS>/agentscope-core/src/main/java/io/agentscope/core/workspace/package-info.java:L17-23`），无实现——workspace 机制全部实装在 harness 模块。

---

## 7. 对 NexAI 工单 07（workspace/沙箱/AGENTS.md 物化）的落位建议

1. **workspace 根指到 specCode 层**：`builder.workspace(<dataRoot>/t{tenantId}/u{userId}/{specCode})`。原生 `IsolationScope` 没有 tenant 概念（只有 USER/SESSION/AGENT/GLOBAL，`IsolationScope.java:L53-87`），tenant 隔离只能靠"每个 (租户,用户,规格) 一个独立 workspace 根"实现——平台的层级布局做在**根路径**上，而不是塞进 workspace 内部。这也与 dataagent 的 per-`(userId, agentId)` 独立 workspace/沙箱实践一致（`WorkspaceManagerFactory.java`）。
2. **userId 的两种处置二选一**：(a) userId 只出现在平台路径里，`RuntimeContext` 不传 userId（或 `IsolationScope.AGENT`），workspace 内不再有 `<uid>/` 前缀层——布局最平整，推荐；(b) 传 userId 保留原生 USER 隔离，但要知道磁盘会多出 `workspace/<userId>/memory/...` 前缀层（`LocalFilesystem.applyNamespacePrefix`）。两者不要叠加，否则同一份元数据出现两条路径。
3. **不可破坏的原生路径约定**（均为硬编码相对路径，平台不得在 workspace 根内占用或改名）：根级 `AGENTS.md`、`MEMORY.md`、`tools.json`；目录 `memory/`、`skills/`、`knowledge/`、`subagents/`、`plans/`、`agents/`（内含 `<agentId>/sessions|tasks` 与 `<name>/workspace`）、`.index/`、`.agentscope/`。尤其 `agents/` 前缀有双重用途，平台不要往里写自己的东西。
4. **AGENTS.md 物化与"DB 唯一权威"兼容但有一个坑**：框架每次 call 重读 `{workspace}/AGENTS.md`，因此「DB 版本快照 → 物化到 workspace 根 → 装配时内容比对」直接可行（参考 `WorkspaceScaffolder.writeIfMissing` 的只补缺语义，平台可改为"内容不一致即覆写"实现权威源）。坑在 Remote 模式：`AGENTS.md` 是按用户 namespace 可覆盖的 exact-file 路由（`RemoteFilesystemSpec.java:L220`）——若走 KV 路线，需保证 AGENTS.md 不进用户可写 namespace（或平台不暴露写该文件的工具），否则"DB 唯一权威"会被运行期用户覆盖打破。
5. **沙箱开关的落位**：沿用 `DockerFilesystemSpec + IsolationScope.USER`；宿主侧 `{dataRoot}/t{tid}/u{uid}/{specCode}` 天然充当投影模板源（AGENTS.md/skills/knowledge 自动进容器，`SandboxFilesystemSpec.java:L40-41`），容器内 `/workspace` 为运行面。多副本部署需 `DistributedStore`（Redis）承载快照与状态，且 workspace 根在本地模式仅是"标签"（dataagent 已验证，`WorkspaceManagerFactory.java:L25-31`）。
6. **平台自己的会话/审计数据不要写进 workspace**：原生 sessions/transcripts 已按 `<sessionId>` 分文件且 `.log.jsonl` 永不压缩；NexAI 的会话管理（DB 侧）与 agentscope 的 session 转录是两套账，各管各的，不要试图复用或清理对方的文件（`MemoryMaintenanceMiddleware.java:L257` 会对 `agents/` 下 `*.log.jsonl` 做维护扫描）。
7. **agentName/agentId 命名**：`agents/<agentId>/sessions|tasks` 中的 agentId 取 `builder.agentId(...)`（缺省回落 name，`HarnessAgent.java:L2245-2248`）。平台应为每个 AgentSpec 实例固定 agentId = specCode（稳定、目录可预测），避免用随机 UUID 导致 sessions 目录随重建漂移。

---

## 附：本次调研核对过但无实质内容的点

- `agentscope-core` 的 `io.agentscope.core.workspace` 包：仅 package-info（规划占位），无 `Workspace` 类（见第 6 节末注）。
- wiki `.qoder/repowiki/zh/content/HarnessAgent企业级功能/工作空间与文件管理.md`：结构综述与主干一致，但其文件引用为 v1 包路径（无 `agent` 层、`FilesystemTool` 在 `harness/tool/`），仅作交叉参考。
