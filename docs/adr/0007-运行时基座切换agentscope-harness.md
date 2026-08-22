# 运行时基座切换 agentscope-harness

`AgentscopeRuntimeGateway` 的运行时基座从 agentscope-core 的 `ReActAgent` 切换为 agentscope-harness 的 `HarnessAgent`；workspace 落盘与沙箱按规格（ExecutionEnvConfig）可配置；M1 链路回归。2026-08-23 经多轮 grill 采访定案（工单 05 重写二），延续 ADR-0001（agentscope 库嵌入单体）的运行时细节。

> 决策演变：本 ADR 初版（同日早些）定为「本地零落盘 + harness 内置行为全关（与 M1 行为等价）」；经后续采访改写为「按规格落盘 + 可配置沙箱 + 内置行为开启（文件/subagents/memory/AGENTS.md 注入）」——执行能力是智能体平台的核心场景，回归口径从「行为等价」改为「规格驱动矩阵」。

## 背景与动机

ADR-0006（2026-08-22 版）的挂载语义——SubagentDeclaration、agent_spawn、ToolsConfig、SkillFilter——经源码核验**全部位于 agentscope-harness**，而运行时嵌入的是 core 的 `ReActAgent`：core 里子智能体只有 `SubAgentTool`（无白名单字段、无 spawn 元工具）。M2 挂载需求恰好是 harness 的功能集，留在 core 自搭等于在网关重造 harness 子集。

嵌入成本经调查核验为**低**：

- **先例**：agentscope-paw 即 Spring Boot WebFlux 应用嵌入 HarnessAgent 的完整示例（非 CLI），dataagent/codingagent 同路；
- **同构**：`streamEvents(Msg, RuntimeContext)` 同名同签名返回同一 `Flux<AgentEvent>`；HITL 的 `METADATA_CONFIRM_RESULTS` + `ConfirmResult` 确认机制在 HarnessAgent 上透明生效——现有 SSE/HITL 代码形态基本平移；
- **依赖零净增**：harness 的 pom 仅依赖 core + jackson-yaml + commons-compress + sqlite-jdbc，后三者 core 本在 compile scope 已带；
- **无入口侵入**：harness 不自带 HTTP 服务器/CLI 入口（那是独立的 agentscope-service 模块，不引即无）；
- **纯编程一等支持**：`SubagentDeclaration.builder()` 直构（官方示例即此形态），无需 workspace 文件。

沙箱与执行能力动机：官方文档明确「本地文件系统模式生产不要跑不可信代码，沙箱模式是首选」——执行能力（shell/python/node）必须与 Docker 沙箱配套，且按规格开放。

## 核心决策

1. **现在就切**：gateway 换 HarnessAgent；挂载层映射（自定义 subagent 声明、MCP 注册、skillFilter 实际接线）留工单 11/15/16（当前无数据源）。
2. **内置行为开启矩阵**：
   - **开**：文件六件套（read/write/edit/grep/glob/list）、subagents（`agent_spawn`/`agent_send`/`agent_list` + task 看板 + 内置 general-purpose 子代理）、memory 四件套与 hooks（默认 retention：memory 90 天归档、会话日志 180 天清理）、transcript 默认持久化（append-only 审计日志，官方「always persist」哲学，`session_search` 数据源）。
   - **移除**：`web_fetch`/`web_search`（框架无开关、无条件注册——toolkit 层显式移除）。
   - **AGENTS.md 注入开启，内容源 = 规格快照 systemPrompt 物化**：装配时网关把快照 `systemPrompt` 写入 `{workspace}/AGENTS.md`，快照是人格唯一事实源、AGENTS.md 只是物化投影（沙箱启动的种子投影也依赖该文件），发布新版本后新会话自动跟新版。
3. **落盘按规格（联动 ADR-0006 执行环境层）**：`workspaceEnabled=false` → 纯对话智能体，零落盘（无文件工具、无 workspace 目录、transcript 禁用）；`=true` → per-spec 常驻 workspace，**布局按归属层级**（ADR-0006 决策 8）：

   | 归属层级 | workspace 布局 | 种子 | 记忆/用户数据 | IsolationScope |
   |---|---|---|---|---|
   | 平台级 | `{root}/platform/{specCode}/` | 跨租户一份 | per 租户+用户（namespace `t{tenantId}-u{userId}`） | USER + 复合 id |
   | 租户级 | `{root}/t{tenantId}/{specCode}/` | 租户内一份 | per 用户（namespace `<userId>/`） | USER |
   | 用户级 | `{root}/t{tenantId}/u{userid}/{specCode}/` | 该用户一份 | 全归创建者 | AGENT（物理目录即隔离） |

   根可配 `nexai.ai.runtime.workspace.root`，默认 `~/.agentscope/nexai/workspace`。目录**不删除**（官方实践：paw/codingagent 均常驻不删，清理是应用责任且官方示例均未实现），膨胀由框架 retention 自治；目录级治理记为已知债务，M2/M3 按运维需要加定时任务。
4. **种子物化时机 = 装配时 + 内容比对跳过**（2026-08-23 归属层级轮定案）：每次装配从 DB 读会话绑定版本的快照（现状已是），写 `AGENTS.md` 前比对内容，相同跳过、不同覆写。**本地盘 = 物化缓存，数据库（版本快照）= 唯一权威源，一致性靠「每次使用前校验」而非「变更时推送」**——新发布后新会话自动拿新版，无广播、无失效、任何副本各自保证新鲜。不是发布时物化（发布只动 DB、无副作用事务）；不是服务启动时物化（覆盖不到启动后新建的 spec/新版本）。M2 的 `subagents/*.md`、`tools.json` 物化复用同一模式。
5. **IsolationScope 按层级取值**：租户级/平台级用 USER（namespace 分桶，官方默认——种子共享一份零复制、记忆 per-user，官方测试 `noDuplicateDataAtWorkspaceRoot` 背书）；用户级用 AGENT（workspace 已在该用户目录下，物理目录即隔离，无需 namespace）。平台级跨租户复合同户 id（`t{tenantId}-u{userId}` 填入 RuntimeContext userId）实现租户隔离，不加自定义 filesystem 代码。
6. **单副本部署假设（2026-08-23 显式化）**：记忆/用户数据是只在副本本地盘产生的增量数据（DB 可重物化种子，但 `<userId>/` 记忆不可），多副本下会话路由到不同副本 = 记忆分裂。M1/M2 **单副本部署**为显式约束；多副本需求出现时升 `RemoteFilesystemSpec`（memory 路由到 PG/Redis store，目录布局不变）或共享卷。**不引入 nacos**（全仓零引用）：装配时物化下无变更可广播；将来 per-spec 常驻实例缓存需要失效广播时用已有 Redis MQ。
7. **沙箱按规格可配置**（`sandboxEnabled` + `capabilities: [SHELL, PYTHON, NODE]`，见 ADR-0006 执行环境层）：
   - `sandboxEnabled=true` → `DockerFilesystemSpec`（docker CLI 驱动容器，默认 `--network=none`，每次调用 stop+rm）；
   - **capabilities 仅沙箱模式可选**，非沙箱下保存草稿校验拒绝（本地模式 shell 在宿主机裸奔，多租户暴露面）；
   - 能力 → 镜像映射放**服务级** `application.yaml`（`nexai.ai.runtime.sandbox.images.*`，按能力组合），内置默认表、环境可覆盖（内网 registry 等差异）；
   - 环境无 docker 而规格要求沙箱 → **装配时显式报错**（不静默降级）；
   - 沙箱跨调用持久化经快照机制（`LocalSnapshotSpec` 或 state store 注入，默认 Noop 即毁——实施时定具体配置）。
8. **M1 回归口径（规格驱动矩阵）**：现有会话链路测试（SSE 事件流 / HITL / 中断 / 会话状态迁移）全绿，外加组合断言——
   - 无 workspace 规格：文件/`execute` 工具不存在、零落盘、transcript 禁用；
   - workspace 无沙箱规格：文件六件套存在、`execute` 不存在、种子共享 + 用户文件 COW 落 `<userId>/`；
   - workspace+沙箱规格：文件 + `execute` 存在（**用例 docker 门控**，无 docker 环境跳过）；
   - 通用断言：`agent_spawn` 系与 memory 四件套存在、`web_fetch`/`web_search` 不存在、AGENTS.md 注入内容 = 快照 systemPrompt、记忆按 `<userId>/` 隔离。
9. **版本基线与并发**：`agentscope-bom:2.0.3-SNAPSHOT`（本地源码 mvn install）不变，API 命名漂移（`maxIters→steps` 信号）由映射层隔离。并发：M1 接受 per-会话装配实例的多实例并发写风险（同 spec 并发会话写同一 MEMORY.md，调试台并发低可容忍），per-spec 常驻实例缓存（官方「单实例服务数千用户」形态）为已知演进方向。

## 实施补记（2026-08-23，随工单 05 重写二落地）

源码级核实后细化的实施决策（与上文决策一致，仅把「怎么做」钉死）：

- **纯对话零落盘的组合**：`disableFilesystemTools + disableShellTool + disableTranscript + disableMemoryHooks + disableMemoryTools`——memory 工具一并禁用：harness 不设 filesystem 时会回落默认本地 overlay（workspace 兜底 `${user.dir}/.agentscope/workspace`），memory_save 是落盘入口，保留即破坏零落盘语义；spawn 系子代理保留（继承禁用开关，spawn 出的也是纯对话子代理）。系统提示直传 builder（无 AGENTS.md 注入面）。矩阵「通用断言」适用于 workspace 用例。
- **非沙箱本地模式禁 execute**：`LocalFilesystemSpec` 产物是 AbstractSandboxFilesystem（本地 shell），框架会注册 `execute`——显式 `disableShellTool()` 移除，与「capabilities 仅沙箱可选」的域校验对齐。
- **本地模式 project 显式指向 workspace 自身**：`LocalFilesystemSpec.project` 缺省 = `${user.dir}`（服务器工作目录作为只读下层 + shell pwd 暴露），必须显式设为 workspace 路径（种子与写层同目录，等价于无外部 project 层）。
- **web 工具移除**：`web_fetch`/`web_search` 框架无条件注册且无 builder 开关，装配后经 `agent.getToolkit().removeTool(...)` 按名移除。
- **docker 前置探测**：harness 构建时不校验 docker（容器首次调用才创建，届时抛 SandboxException）——网关在装配同步段主动探测（`docker info`，60 秒缓存），无 docker 即抛 SessionSandboxUnavailableException（转 SESSION_SANDBOX_UNAVAILABLE）。
- **中断经 delegate**：HarnessAgent 未透传 `interrupt(RuntimeContext)`，经 `getDelegate()`（内层 ReActAgent）触发。
- **矩阵测试的 docker 门控实现**：DockerAvailabilityProbe 结果可注入替身；装配形态断言（工具面/布局/物化）本身不拉容器，无 docker 环境全量可跑；真容器行为回归依赖有 docker 的环境手工/CI 验证。
- **沙箱快照**：M1 维持框架默认 Noop（即毁），跨调用持久化升级点已核实为 `snapshotSpec(LocalSnapshotSpec)`，按需开启不改布局。

## Considered Options

- 维持 core 自搭白名单/子智能体——被否：重造 harness 子集，M2 三张工单重复付成本
- **本地零落盘（初版决策）——被否：文件/执行能力需要真实 workspace；transcript 官方定位「始终持久化」；落盘膨胀已被 USER scope 种子零复制 + retention 化解**
- **AGENT scope（记忆也全局共享）——被否：长期记忆要 per-user**
- **SESSION scope——被否：记忆落会话 namespace，会话结束即孤立，长期记忆失效**
- **per-session 目录 + 会话结束删除——被否：官方无此实践；丢沙箱投影源与记忆连续性**
- 接受 harness 默认落盘路径（`~/.agentscope`、工作目录）——被否：须显式受控根 + per-spec/per-tenant 组织
- 非沙箱也开放执行能力——被否：宿主 shell 无隔离，官方文档明示生产禁跑不可信代码
- 静默降级（无 docker 时非沙箱跑）——被否：规格承诺的能力不可用必须显式失败
- transcript 落 PG 供调试台回放——推迟：工单 08 决策
- **发布时物化种子（+ nacos/消息广播同步各副本）——被否：装配时物化 + 内容比对下同步问题机制性不存在；本地盘只是物化缓存，一致性靠使用前校验（DB 权威源）而非变更推送；引入 nacos 是为不存在的问题加基础设施**
- 服务启动时物化——被否：覆盖不到启动后新建的 spec 与新发布版本
- 多副本 + 本地盘记忆现在就解（共享卷/Remote 模式）——被否：单副本假设显式化 + 升级路径已留，为不存在的负载做设计
- workspace 统一按使用者组织（放弃租户/平台级种子共享）——被否：租户级/平台级的种子共享与 namespace 机制已定案且成本为零；层级差异化布局（归属谁挂谁树下）语义更清晰
- 等工单 11 开工再切——被否：配置结构与基座一次定案

## 连带影响

- 实施由工单 05 重写二承载（checklist 见工单）
- ADR-0006 同日再修订（新增执行环境层 ExecutionEnvConfig、spec_code 字段）
- M2 工单 11/15/16 在 HarnessAgent 上接线，映射点见 ADR-0006 收敛表；`presetParameters`/ToolGroup/`PermissionContextState` 为现成挂点
- HarnessAgent `implements AutoCloseable`：网关管理生命周期（`close()` 释放 SQLite 索引等句柄，不删文件——清理为应用责任）
- 终端用户门户（工单 17）开放时重评估用户间文件隔离（届时升 Remote/Sandbox 物理隔离，目录布局不变）
- 沙箱生产启用时的部署前提：docker CLI in PATH + socket（或 DinD / K8s CRD 扩展 `agentscope-extensions-sandbox-kubernetes`）——按最终部署形态再选，不阻塞当前建模
