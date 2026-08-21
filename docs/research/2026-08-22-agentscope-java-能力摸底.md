# AgentScope Java 能力摸底报告

> 调研日期：2026-08-22
> 调研对象：本机 `~/agent-project/agentscope-java`（GitHub `agentscope-ai/agentscope-java`，main 分支，最后提交 2026-08-18，主干版本 2.0.3-SNAPSHOT）
> 调研目的：评估其作为 nex-ai（芋道脚手架 fork，Java 25 + Spring Boot 4.1 + PostgreSQL + Redis）之上"企业级 AI 智能体开发平台"的运行时基座的可行性与风险。

---

## 一、结论速览

| 平台需求域 | 支持度 | 依据（详见分章节） |
|---|---|---|
| 大模型管理 | **强** | Credential/ChatModel 两层抽象 + `ModelRegistry` 全局注册中心 + `ModelCard` 模型发现（`credential.listModels()`），5 个官方提供商扩展 + OpenAI 兼容端点可覆盖 DeepSeek/GLM/Kimi 等；官方 Spring Boot starter 按 provider 出 `Model` bean（§4） |
| skill 管理 | **强（超出预期）** | 四层 skill 来源（项目全局/市场/工作区/用户隔离），市场后端含 Git/Nacos/MySQL/classpath/自定义；自带自学习闭环（propose → 审核闸门 → curator 归档）；`SKILL.md` 格式与 Anthropic 技能规范同构（§8） |
| agentspec 管理 | **中强** | 子 agent 以 `workspace/subagents/<id>.md` 声明（front matter 定义 model/tools/steps/workspace 模式），也可编程式 `SubagentDeclaration`；`agent_generate` 工具可让 agent 起草 spec（默认关闭）。是"文件即定义"，无版本管理/审批 UI（§5） |
| agent 治理 | **中** | 三态权限引擎（allow/ask/deny）+ PermissionMode + 危险路径保护 + OTel 全链路追踪 + 优雅停机；但无租户级配额、成本归集、审计报表（§3.4、§12） |
| RAG 知识库 | **中强** | core 抽象（`Knowledge`/`RAGMode`/`GenericRAGHook`）+ `rag-simple` 扩展（6 种 Reader、TextChunker、4 家 Embedding、5 种向量库含 **PgVector**）+ 外部平台集成（Dify/RAGFlow/Bailian/Haystack）。缺知识库生命周期管理 UI、rerank 本地实现较少（§6） |
| MCP 管理 | **强（客户端）** | MCP Client 支持 stdio/SSE/StreamableHTTP 三种传输（官方 SDK 0.17.0），elicitation、动态 token 注入、协议版本协商；`workspace/tools.json` 声明式管理 MCP server + 工具白名单。**无 MCP Server 实现**（§7） |
| AI 工作流 | **弱（框架内无 DAG 引擎）** | v2 编排 = ReAct 循环 + 子 agent 委派（同步 fan-out/fan-in + 后台任务）+ Agent Teams；v1 文档的 Pipeline/StateGraph 是借 Spring AI Alibaba 的示例，v2 代码中已无此依赖。确定性 DAG/可视化工作流需 nex-ai 自建或外接（§5） |
| 智能体/skill 调试台 | **中** | 28 种类型化事件流（`streamEvents`）、HITL 暂停/恢复、`agent.interrupt()`、子 agent 流式转发（带 source 标记）、`agentscope-extensions-studio` 可视化组件、agentscope-service Dashboard（独立部署）（§3.3、§5.4） |

**一句话结论**：AgentScope Java 2.0 是目前 Java 生态中完成度最高的 agent 运行时之一（模型/工具/记忆/权限/分布式/沙箱俱全，GA 于 2026-07），可作为 nex-ai 平台的**智能体执行内核**；但**可视化工作流引擎、平台级管理面（多租户配额/审计/密钥管理/资产版本化）需要 nex-ai 自建**，且存在 Jackson 2 vs 3、Spring Boot 4.0.4 vs 4.1 两处依赖对齐风险（§13）。

---

## 二、项目概况

| 维度 | 事实 | 来源 |
|---|---|---|
| 定位 | 阿里 AgentScope 的 Java 实现（"Agent-Oriented Programming for Building LLM Applications"），2.0 自称 production-ready | 本地 `README.md`、根 `pom.xml` |
| 维护方 | Alibaba（AgentScopeTeam），Apache-2.0 | 根 `pom.xml` `<organization>`/`<developers>` |
| 版本 | 主干 `2.0.3-SNAPSHOT`；Maven Central 最新 `2.0.2`（另有 `2.0.2-subagent-bugfix`）；GA 里程碑 v2.0.0 发布于 2026-07 | 根 `pom.xml` `revision`；Maven Central `maven-metadata.xml`（repo1.maven.org/maven2/io/agentscope/agentscope-core/） |
| Maven 坐标 | `io.agentscope:agentscope-core`（裸 ReActAgent）/ `io.agentscope:agentscope-harness`（全功能）/ `io.agentscope:agentscope-extensions-model-*`（按需）/ `io.agentscope:agentscope-spring-boot-starter` 等 11 个 starter；`agentscope-bom` 可统一管版本 | `README.md` Quickstart；repo1.maven.org 目录列表 |
| 活跃度 | GitHub 5.2k star / 1.2k fork / 580 open issues（2026-08-22 查）；仓库首提交 2025-09-23，最后提交 2026-08-18（几乎每天有 commit）；tags：v1.1.0 → v2.0.0（RC1~RC5）→ v2.0.1 → v2.0.2 | `git log`；WebFetch GitHub 仓库页 |
| 成熟度信号 | 2026-07 GA；中文/英文双语文档站（java.agentscope.io）；单测覆盖广（core 的 mcp/rag/state 均有测试）；持续 dependabot 活跃（BOM 升级 PR 分支可见）；有生产部署文档与多副本恢复设计 | `README.md` News、`docs/v2/`、`.git/refs/remotes/origin/dependabot/*` |
| 模块全景 | `agentscope-core`（消息/事件/ReAct/模型 API/工具/权限/RAG 抽象/状态）→ `agentscope-harness`（工作区/记忆/子 agent/沙箱/Channel，核心生产层）→ `agentscope-extensions`（19 个扩展族：model/rag/mem/sandbox/redis/mysql/postgresql/nacos/skills/scheduler/studio/protocol/...）→ `agentscope-service`（独立控制平面 + Dashboard，Spring Boot 4.0.4 + Spring Cloud 2025.1.2）→ `agentscope-distribution`（BOM/all） | 根 `pom.xml` `<modules>`；各目录 |

---

## 三、核心 Agent 抽象

### 3.1 分层：ReActAgent → HarnessAgent

- **`Agent` 接口**（`io.agentscope.core.agent.Agent`）：`call(List<Msg>)` → `Mono<Msg>`、`streamEvents(...)` → `Flux<AgentEvent>`、`observe(...)`（注入上下文不推理）。默认实现 `io.agentscope.core.ReActAgent`（单文件 5247 行）。来源：`agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java`、`docs/v2/zh/docs/building-blocks/agent.md`。
- **构成**（builder 组装）：`name` / `sysPrompt` / `model`（字符串 id 或 `Model` 实例）/ `toolkit`（build 时深拷贝）/ `middlewares` / `stateStore` / `maxIters`（默认 10）/ `permissionContext` / `modelConfig`（重试+fallback 模型）/ `reactConfig`。来源：`agent.md` 参数表。
- **`HarnessAgent`**（`agentscope-harness`）= ReActAgent 的薄包装，叠加：工作区驱动人格、双层长期记忆、对话压缩、子 agent 编排、可插拔文件系统（本地/共享存储/沙箱）、Plan Mode、技能装配、Channel 路由。所有能力以 middleware 挂载，不改推理循环。来源：`docs/v2/zh/docs/harness/architecture.md`。
- **多模态消息模型**：`ContentBlock`（Text/Thinking/ToolUse/ToolResult/DataBlock）+ 角色严格校验。来源：`README.md` Key Design。

### 3.2 并发与线程模型（Spring 集成关键）

- v2.0 中 ReActAgent **调用间无状态**：单实例可服务多用户多会话，每次 `call()` 由 `RuntimeContext` 的 `(userId, sessionId)` 激活对应状态槽；**同一 session 的并发调用按到达顺序串行化**，跨 session 并行。来源：`agent.md` "多用户 / 多会话并发"；源码 `ReActAgent.activateSlotForContext()`（5247 行文件的 per-call slot 激活 + CAS 版本策略 OVERWRITE/FAIL/APPEND_MERGE）。
- **注意矛盾点**：`ReActAgent.java` 头部 javadoc 与 `StreamingWebExample.java` 注释仍写"NOT thread-safe, create per request"，但 starter 源码注释明确 "ReActAgent in 2.0 is thread-safe, so we just use a singleton instance"（`AgentscopeAutoConfiguration.java` L101-103），中文文档同。以 2.0 行为为准；**旧示例注释过时，采信需以实测验证**（未确认 2.0.2 是否完全消除并发限制，建议 nex-ai 集成时先按"单例 + RuntimeContext 隔离"压测）。
- 整体基于 **Project Reactor**（`Mono`/`Flux`）：`call().block()` 阻塞式与 `streamEvents()` 流式两套消费方式；异步工具执行、后台任务、节流 flush 均走响应式管道。来源：`ReActAgent.java` imports、`README.md`。

### 3.3 扩展点

1. **Middleware**（AOP 核心）：5 个挂载位 `onAgent`/`onReasoning`/`onActing`/`onModelCall`/`onSystemPrompt`（洋葱式 + 变换式），可注入日志、审计、访问控制、输入改写。来源：`docs/v2/zh/docs/building-blocks/middleware.md`。
2. **Hook**：旧式 hook 体系仍兼容（`LegacyHookDispatcher`）。来源：`ReActAgent.java` imports。
3. **结构化输出**：`call(msgs, WeatherResponse.class)` 原生 `response_format` 或 `generate_response` 合成工具双路径，自动降级。来源：`agent.md`。
4. **事件系统**：28 种类型化事件（文本/思考增量、工具调用生命周期、确认请求、子 agent 转发等），SSE 直出。来源：`README.md`。
5. **HITL**：`RequireUserConfirmEvent`（权限 ASK）与 `RequireExternalExecutionEvent`（外部执行工具）暂停 → `ConfirmResult` 经 metadata 随下一次 `call()` 恢复；规则可持久化自动放行。来源：`agent.md` 人机交互节。
6. **中断**：`agent.interrupt(userId, sessionId[, msg])` per-session 中断，状态自动保存可续。来源：`agent.md`。

### 3.4 治理相关

- **权限系统**：三态决策（allow/ask/deny）+ PermissionMode（DEFAULT/ACCEPT_EDITS/EXPLORE/BYPASS/DONT_ASK）+ 规则匹配（工具可提供 `matchRule`）+ 内置危险路径保护（`.ssh/`、`.env`、`.aws/` 等）。来源：`docs/v2/zh/docs/building-blocks/permission-system.md`。
- **可观测**：`OtelTracingMiddleware` 按 GenAI semconv 打点 `invoke_agent`/`chat`/`execute_tool` span；无 OTel SDK 时近零开销。来源：`middleware.md`。
- **优雅停机**：`GracefulShutdownManager` + partial reasoning policy。来源：`ReActAgent.java` imports。

---

## 四、模型接入

### 4.1 架构

`Model`（接口，`stream(messages, tools, options)` → `Flux<ChatResponse>`）← `ChatModelBase` ← 各提供商实现；`CredentialBase` 承载鉴权并支持 `listModels()` → `List<ModelCard>`（modelName/displayName/contextSize），与"平台先登记凭证、再拉模型列表"的管理流天然契合。来源：`docs/v2/zh/docs/building-blocks/model.md`。

### 4.2 提供商矩阵

| 提供商 | 模块 | 说明 |
|---|---|---|
| DashScope | `agentscope-extensions-model-dashscope` | Qwen，多模态，阿里官方 SDK |
| OpenAI | `agentscope-extensions-model-openai` | 兼容 vLLM 及 OpenAI 兼容端点（DeepSeek/Kimi 等由此覆盖） |
| Anthropic | `agentscope-extensions-model-anthropic` | Claude，prompt 缓存 + thinking |
| Gemini | `agentscope-extensions-model-gemini` | Google |
| Ollama | `agentscope-extensions-model-ollama` | 本地部署 |
| DeepSeek/Kimi/XAI | core 内 OpenAI 兼容 Credential | `DeepSeekCredential` 等 |

来源：`model.md` 表格、`agentscope-extensions/agentscope-extensions-model/` 目录。

### 4.3 配置与扩展

- **字符串解析**：`"dashscope:qwen-plus"` 经 `ModelRegistry.resolve()` → 命名注册 → 用户工厂（regex）→ SPI（`ModelProvider`，`META-INF/services` 自动发现）→ 环境变量取 key。来源：`ModelRegistry.java`。
- **多租户场景**：`ModelCreationContext`（apiKey/baseUrl/options/components）供"多租户网关、插件系统"动态建模型，带缓存策略（DEFAULT/DISABLED/ENABLED+cacheId）防止租户配置串用。来源：`model.md` "高级集成上下文"、`ModelRegistry.cacheKey()`。
- **自定义 provider 最小路径**：实现 `CredentialBase` + `ChatModelBase.doStream()`，可选注册 `ModelRegistry.registerFactory("myprov:.*", ...)`。来源：`model.md` "自定义模型提供商"。

**对 nex-ai 大模型管理域的判断**：能力完整。平台侧可把"模型凭证表"映射为 `Credential`，"模型表"映射为 `ModelCard`/named model 注册；API key 需从环境变量改为从 nex-ai 数据库/密钥管理注入（走 `ModelCreationContext` 或显式 builder，均已支持）。

---

## 五、工作流 / 编排

### 5.1 v2 的编排模型（与"可视化工作流"需求的差距是最大短板）

v2 **没有** DAG/pipeline/workflow 引擎类。编排能力由三层构成：

1. **ReAct 循环本身**：LLM 自主决定步骤顺序（agentic 模式）。
2. **子 agent 委派**（`docs/v2/zh/docs/harness/subagent.md`）：
   - 声明：`workspace/subagents/<id>.md`（front matter：description/model/steps/temperature/tools/workspace mode/hidden/expose_to_user）或编程式 `SubagentDeclaration`（含远程 `url()` 模式，走 Agent Protocol HTTP）；
   - 执行：`agent_spawn`/`agent_send` 工具，`timeout_seconds>0` 同步（默认 30s，最大 600s，超时自动 promote 为后台）、`=0` 后台（返回 task_id）；`task_output`/`wait_async_results`/`task_cancel`/`task_list` 管理后台任务；
   - **并行**：同一轮多个同步 spawn 借 Toolkit 默认并行实现 fan-out/fan-in；后台任务完成后以 `<system-reminder>` 自动反向通知；
   - 递归保护：子 agent 不能再 spawn（叶子），硬上限 3 层；权限 DENY 规则自动继承；userId 透传。
3. **Agent Teams**（`harness/agent/team` 包 + agentscope-service）：Lead 分解任务、Members 认领、任务板 + 消息路由，跨进程协作。

### 5.2 v1 的 pipeline/workflow 是外部方案

`docs/v1/zh/docs/multi-agent/pipeline.md`、`workflow.md` 描述的 SequentialAgent/ParallelAgent/LoopAgent/StateGraph 来自 **Spring AI Alibaba**（示例项目 `agentscope-examples/multiagent-patterns/`），v2 代码库中已无 `SequentialAgent`/`StateGraph`/spring-ai-alibaba 依赖（grep 全库确认）。**结论：确定性顺序/分支/循环/并行 DAG 需 nex-ai 自建**（或评估 Spring AI Alibaba StateGraph 作为补充组件，但那是另一条依赖线）。

### 5.3 人工介入

HITL 一等公民：权限 ASK 暂停恢复（§3.3）、Plan Mode（只读规划阶段 + HITL 退出，`docs/v2/zh/docs/harness/plan-mode.md`）、远程子 agent 的 `remoteAskPolicy`（DENY/PROPAGATE）。

**对"可视化 AI 工作流"运行时的判断**：若工作流 = 用户在画布上编排节点（条件分支、循环节点、并行网关、人工审批节点），AgentScope **不能直接当执行引擎**；可行路径是 nex-ai 自建轻量 DAG 执行器，节点类型为"agent 节点（调 ReActAgent）""工具节点""检索节点（Knowledge）""人工节点（HITL 暂停恢复可复用 AgentScope 的事件/恢复机制）"。

---

## 六、RAG

### 6.1 core 抽象（`io.agentscope.core.rag`）

`Knowledge` 接口 + `GenericRAGHook` + `KnowledgeRetrievalTools` + `RAGMode`（如 `AGENTIC`——agent 自行决定何时检索）。Agent 集成一行：`ReActAgent.builder().knowledge(knowledge).ragMode(RAGMode.AGENTIC)`。来源：`agentscope-core/src/main/java/io/agentscope/core/rag/`、`docs/v2/zh/integration/rag/simple.md`。

### 6.2 rag-simple 扩展（自控全链路）

| 环节 | 内置实现 |
|---|---|
| 文档读取 | TextReader / PDFReader（PDFBox）/ WordReader（POI）/ ImageReader / TikaReader / ExternalApiReader（OCR 等外接） |
| 切分 | `TextChunker` + `SplitStrategy` |
| Embedding | DashScope（文本 + 多模态）/ OpenAI 兼容 / Ollama；可实现 `EmbeddingModel` 扩展 |
| 向量库 | **InMemory / PgVector / Milvus / Qdrant / Elasticsearch**（统一 `VDBStoreBase`） |
| 检索 | `RetrieveConfig`（limit/scoreThreshold/metadata 过滤） |

来源：`docs/v2/zh/integration/rag/simple.md`、`agentscope-extensions-rag-simple/src/main/java/io/agentscope/core/rag/store/`。

### 6.3 外部 RAG 平台集成

Bailian（百炼知识库）、Dify、RAGFlow、Haystack 四个扩展模块（`agentscope-extensions-rag-*`），实现同一 `Knowledge` 接口。

**对 nex-ai RAG 知识库域的判断**：检索链路可直接复用，尤其 **PgVectorStore 与 nex-ai 的 PostgreSQL 栈零新增基础设施**。缺口：知识库/文档的 CRUD 管理面、切分策略可视化配置、rerank（本地无；Bailian/Dify 集成里有）、知识库权限（需与 nex-ai 租户体系打通）。

---

## 七、MCP

- **客户端：完整**。`McpClientBuilder`（`agentscope-core/.../tool/mcp/`）基于官方 `io.modelcontextprotocol.sdk:mcp:0.17.0`，支持 **stdio（含 env）/ SSE / StreamableHTTP** 三种传输；header/queryParam、每请求动态 token 注入（`httpRequestCustomizer`，适配 OAuth/STS）、HTTP/2 定制、协议版本协商（`protocolVersions("2024-11-05","2025-03-26")`）、elicitation 处理器。注册：`toolkit.registerMcpClient(client).block()`，工具命名 `mcp__{server}__{tool}`，`readOnlyHint` 工具自动放行。来源：`McpClientBuilder.java` 全文、`docs/v2/zh/docs/building-blocks/tool.md`。
- **声明式管理**：`workspace/tools.json` 可声明 MCP server + 工具白名单（允许/拒绝粒度），与 builder `toolsConfig(...)` 等价——适合 nex-ai 把"MCP 管理域"的数据落到该文件或等价 API。来源：`docs/v2/zh/docs/harness/workspace.md`。
- **Nacos 发现**：`agentscope-extensions-mcp-nacos`（Maven Central 有 `agentscope-extensions-mcp-nacos`）。
- **服务端：无**。全库未发现 MCP Server 实现（仅 client wrapper）。nex-ai 若要把自身 API 反向暴露为 MCP server，需另选方案（如 Spring AI 的 MCP server 或 fastmcp 类工具）。

**对 MCP 管理域的判断**：client 侧接入、鉴权、白名单、工具过滤齐全，管理面数据模型可直接抄 `tools.json` 语义；server 侧为缺口。

---

## 八、工具 / Skill 机制

### 8.1 工具

- 注册三途径：`@Tool`/`@ToolParam` 注解反射注册（`registerTool(Object)`，schema 从 Java 类型自动推导）；继承 `ToolBase`（自定义权限检查 `checkPermissions`、外部执行 `isExternalTool`、复杂 schema）；MCP 客户端注册。来源：`docs/v2/zh/docs/building-blocks/tool.md`。
- 高级特性：工具组（`ToolGroup` 按需激活）、meta 工具（`list_tools`/`activate_group`）、`SkillToolGroup`（skill 加载时联动激活工具组，减少 schema 噪音）、RuntimeContext 类型化参数自动注入（业务 POJO 直接进工具签名）、异步工具、工具结果验证。
- 内置工具：TodoTools（任务清单）、文件读写、shell（tree-sitter Bash AST 安全分析）、coding 工具族（`tool/coding`、`tool/file` 包）。

### 8.2 Skill（概念完整存在，且是平台级亮点）

- 格式：目录 + `SKILL.md`（YAML frontmatter：name/description + agent 指令）+ `references/` + `scripts/`，与 Claude/Anthropic 技能规范同构。来源：`docs/v2/zh/docs/harness/skill.md`。
- **四层来源**（同名时高优先级覆盖）：项目全局目录 → 市场（`skillRepository`：Git/Nacos/MySQL/classpath/自定义，可叠加）→ 工作区 `skills/` → 用户隔离 `<userId>/skills/`。`MysqlSkillRepository(writeable=true)` 明确面向"平台侧统一管理 skill"场景。
- **自学习闭环**：`propose_skill`（草稿）→ promotion gate（本地审批/推消息审批 + 环境/灰度/白名单过滤）→ skill curator（30 天 stale / 90 天归档 + LLM 合并 dry-run）；使用计数记录于 `skills/.usage.json`；程序化 API `agent.promoteSkill()/runCuratorOnce()/queryAudit()`。
- 加载：system prompt 注入 `<available_skills>` 块，agent 用 `load_skill_through_path` 按需读取；脚本经 `<files-root>` 在沙箱内执行（物化 → 投影 → 容器执行三步）。
- core 层另有轻量 `SkillRegistry`/`SkillBox`/`DynamicSkillMiddleware`（`io.agentscope.core.skill`）。

**对 skill 管理域的判断**：后端能力基本现成（MySQL 仓储 + 审批 + 灰度 + 归档），nex-ai 主要工作是把 admin-ui 管理界面接到这些 API 上，并把 MySQL 仓储适配为 PostgreSQL（或直接用 nex-ai 的表结构自实现 `SkillRepository` 接口）。

---

## 九、会话与记忆

### 9.1 状态与持久化

- `AgentState`（对话上下文、压缩摘要、权限规则、工具状态）+ `AgentStateStore` 抽象，按 `(userId, sessionId)` 寻址，每次 call 后自动保存/加载；**版本化 CAS 写入**（`saveIfVersion`）支持多副本并发冲突策略（OVERWRITE/FAIL/APPEND_MERGE）。来源：`agent.md`、`ReActAgent.java` §持久化。
- 实现：InMemory / JsonFile（core 内置）；**PostgreSQL**（`agentscope-extensions-postgresql`：`PostgresAgentStateStore` + `PostgresDistributedStore` + 快照/沙箱守卫）；**Redis**（jedis/lettuce/**redisson** 三客户端适配器——可复用 nex-ai 已有 Redisson）；MySQL（含 H2/SQLite dialect）。来源：各扩展模块源码目录。
- 分布式：`distributedStore(...)` 一行开启跨副本会话恢复、子 agent 路由粘性、滚动发布不断会话。来源：`README.md`、`subagent.md`。

### 9.2 记忆分层

- 短期：对话上下文（可压缩：条数/token 触发、摘要 prompt 可定制、溢出自动强制压缩重试、大工具结果落盘卸载）。
- 长期（Harness）：日流水账 `memory/YYYY-MM-DD.md` + LLM 合并的 `MEMORY.md` 每轮注入 system prompt；三处 LLM 调用（flush/consolidation/compaction）各自可换轻量模型。
- 外部长期记忆扩展：mem0、Bailian、ReMe（`agentscope-extensions-mem*`）。
- 会话日志：`sessions/<id>.log.jsonl` 永不压缩，供审计与 `session_search`。
来源：`docs/v2/zh/docs/harness/memory.md`、`architecture.md`。

**对 nex-ai 的判断**：PostgreSQL/Redis(son) 两个持久化后端与 nex-ai 基础设施完全重合；`RuntimeContext.put(TenantContext.class, ...)` 类型化属性 + `IsolationScope`（SESSION/USER/AGENT/GLOBAL）提供了租户隔离挂点，但**平台级多租户（租户维度的资源隔离、按租户的会话管理界面）仍需 nex-ai 在外层组织**。

---

## 十、Spring Boot 集成

- **官方 starter 族**（`agentscope-extensions/agentscope-spring-boot-starters/`）：核心 `agentscope-spring-boot-starter`（auto-config 出 prototype `Memory`/`Toolkit` + 单例 `ReActAgent`，`agentscope.agent.*` 属性）；provider starter 五个（openai/dashscope/gemini/anthropic/ollama，出 `Model` bean + `BuilderCustomizer` 有序定制）；另有 a2a/admin/agui/nacos/chat-completions-web starter。来源：starter 目录、`AgentscopeAutoConfiguration.java`、`model.md` Spring Boot 节。
- **纯库嵌入**：不引 starter，直接 `agentscope-core`/`agentscope-harness` + 手工 builder 也完全可行（starter 极薄）。官方 Spring WebFlux SSE 示例：`agentscope-examples/documentation/.../streaming/StreamingWebExample.java`（Controller 返回 `Flux<String>` 直出 `TEXT_EVENT_STREAM`）。
- **线程/生命周期注意**：全响应式（Reactor）；在 Spring MVC（阻塞栈）中需 `block()` 或 `subscribeOn(boundedElastic)`；agent `implements AutoCloseable`（需关闭）；MCP 客户端注册是异步 `Mono`（`.block()` 完成 initialize）；flush/后台任务 fire-and-forget，不影响响应返回；Redisson/Jedis 客户端由宿主提供（`RedissonAgentStateStore` 等适配器复用现有连接）。
- **agentscope-service**（可选整体方案）：独立控制平面（gateway + aistiod + dataplane + scheduler，Spring Boot 4.0.4 + Spring Cloud 2025.1.2），提供 agent 注册发现、Dashboard、Managed Agents、Teams。对 nex-ai 而言是**竞品关系大于依赖关系**（nex-ai 要做自己的管理面），不建议引入，但其数据模型与 dashboard 能力可作参考。

---

## 十一、约束与关键依赖（与 nex-ai 的对齐）

| 项 | agentscope-java | nex-ai | 兼容性评估 |
|---|---|---|---|
| Java | 17+（release 17 编译） | 25 | **兼容**（17 字节码跑在 25 JVM） |
| Spring Boot（starter/service 编译基线） | 4.0.4（BOM `spring-boot.version`） | **4.1.0** | 风险低但**需实测**：autoconfigure API 在 4.1 的兼容性未验证（未确认） |
| Jackson | **2.21.1**（`com.fasterxml.*`，core/harness/extensions 全线） | Boot 4.1.0 默认 jackson-bom **3.1.4**（`tools.jackson.*`） | **主要风险**：双 Jackson 共存（Spring Framework 7 支持两者，包名不冲突可同 classpath），但 agentscope 的 Spring 集成件（如 agui 的 "Jackson 2 codec" 提交）在 Boot 4.1 默认 Jackson 3 下需验证；JSON 行为一致性需回归 |
| Reactor | BOM 2025.0.2 | 随 Boot 4.1 | 版本接近，由依赖调解统一即可 |
| PostgreSQL 驱动 | 42.7.11 | 42.7.11（Boot 4.1.0 同版本） | 一致 |
| Redisson | 4.2.0（可选适配器） | nex-ai 自带 Redisson（版本未核对） | 通过 `RedissonClientAdapter` 复用 nex-ai 客户端，注意版本对齐 |
| MCP SDK | 0.17.0 | — | 新增传递依赖 |
| OTel | 1.61.0 | — | 仅 API + reactor instrumentation，可选 |
| 其他显著传递依赖 | OkHttp 5.3.2（模型 transport）、victools jsonschema、networknt validator、snakeyaml、Tika/POI/PDFBox（rag-simple）、tree-sitter（bash 安全分析，含 native 库）、JGit、fabric8（k8s 沙箱）、sqlite-jdbc（harness 内嵌任务存储） | — | tree-sitter native 库在 ARM macOS/Linux 部署需验证；sqlite-jdbc 会随 harness 引入 |

来源：`agentscope-dependencies-bom/pom.xml`；`repo1.maven.org/.../spring-boot-dependencies-4.1.0.pom`（jackson-bom 3.1.4）；`nexai-dependencies/pom.xml`、nex-ai 根 `pom.xml`。

---

## 十二、明显缺口（nex-ai 需自建的平台能力）

以下能力 AgentScope **刻意不做或不完整**，属 nex-ai 平台层职责：

1. **可视化工作流引擎**：确定性 DAG（顺序/分支/循环/并行网关）、画布 DSL 解析、节点编排调度、流程级重试与回滚（§5）。
2. **多租户 SaaS 治理**：租户维度的模型凭证/配额/计费、token 成本归集与账单、租户级资源隔离策略（框架只有 userId/isolation 挂点，无租户管理）。
3. **管理后台 CRUD/审批流**：大模型/skill/agentspec/MCP server 的版本管理、发布审批、灰度分发界面（框架提供 `MysqlSkillRepository` 等后端接口，无 UI；且为 MySQL，需 PostgreSQL 化）。
4. **密钥管理**：API key 从环境变量改为企业密钥库（KMS/Vault/nexai 自管加密表）——框架已留 `ModelCreationContext` 注入口。
5. **审计与合规报表**：有 OTel trace 与会话日志，但无操作审计流水（谁改了 prompt/skill/权限规则）、数据出境/内容合规过滤。
6. **配额与限流**：无 per-tenant/per-user 的调用频控、token 配额、并发会话上限（Channel 有 per-session 并发控制，粒度不够）。
7. **MCP Server**：无法把平台自身能力暴露为 MCP server（§7）。
8. **调试台产品化**：事件流/HITL/中断原语齐全，但缺 Web 调试器（会话回放、工具调用钻取、prompt diff 试运行）——可参考 `agentscope-extensions-studio` 与 agentscope-service Dashboard 的交互设计。
9. **RAG 管理面**：知识库/文档集/切分策略/rerank 的管理与评测（§6）。
10. **内容安全**：无 prompt 注入防护、输出内容审核、PII 脱敏等中间件（可自行以 Middleware 实现，挂载点已具备）。

---

## 十三、与 nex-ai 集成的风险清单（按优先级）

1. **【高】Jackson 2 vs Jackson 3 分裂**：agentscope 全线 Jackson 2（2.21.1），nex-ai 的 Spring Boot 4.1.0 默认 Jackson 3（3.1.4）。同 classpath 可共存，但：agentscope starter/agui 等 Spring 集成件按 Jackson 2 编写；跨两套栈的 JSON 序列化行为（日期、空值、record）需统一回归；`spring-boot-jackson` 模块共存需验证。建议：PoC 首先验证双栈共存与 WebFlux SSE 链路。来源：§十一。
2. **【高】可视化工作流缺口**：平台八大域中"AI 工作流"无现成运行时（v2 移除了 v1 借 Spring AI Alibaba 的 pipeline 方案），需自建 DAG 执行器并把 AgentScope 作为节点执行内核；这直接影响架构设计与工作量估算。来源：§5。
3. **【中】Spring Boot 4.0.4 → 4.1.0 的 starter 兼容性未验证**：agentscope starter 编译基线落后 nex-ai 一个 minor 版本；autoconfigure/properties 绑定 API 一般稳定，但需实测（未确认）。来源：§十一。
4. **【中】并发模型文档不一致**：ReActAgent javadoc/旧示例称"非线程安全、每请求新建"，v2 文档与 starter 称"线程安全单例"。集成模式（单例 vs 工厂）需以压测定案，避免踩并发会话串扰。来源：§3.2。
5. **【中】skill/state 仓储 MySQL 优先**：`MysqlSkillRepository`、文档表格未列 PostgreSQL state store（但 `agentscope-extensions-postgresql` 存在且完整），PostgreSQL 路径属次级受支持状态，需自测；skill 仓储大概率要自实现 PostgreSQL 版 `SkillRepository`。来源：§8、§9.1。
6. **【中】生态绑定阿里云倾向**：DashScope/Bailian 为一等公民（embedding、长期记忆、RAG 均有 Bailian 专属扩展）；国产化场景友好，但多云中立性需靠 OpenAI 兼容层维持。
7. **【低】体积与传递依赖**：harness 引入 sqlite-jdbc、tree-sitter native、commons-compress；k8s 沙箱引 fabric8。按需裁剪（core-only + 选定扩展）可控制。
8. **【低】API 稳定性**：2.0 GA 至今仅 3 个 patch，`stream()` 已标 `@Deprecated(forRemoval=true)`、`ToolExecutionContext` 弃用桥接等表明 2.x 内仍有 API 演进；建议锁 `agentscope-bom` 单一版本并跟踪 release notes。
9. **【低】MCP Server 缺失**（§7）、**生产案例公开信息有限**（README 未列 reference 客户；阿里内部使用推测，未确认）。

---

## 十四、参考来源

### 本地一手来源（`~/agent-project/agentscope-java/`）

- `README.md`、`pom.xml`（版本/模块/组织信息）
- `agentscope-dependencies-bom/pom.xml`（全部第三方版本）
- `agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java`（主循环、持久化、并发）
- `agentscope-core/src/main/java/io/agentscope/core/model/ModelRegistry.java`（模型解析/SPI）
- `agentscope-core/src/main/java/io/agentscope/core/tool/mcp/McpClientBuilder.java`（MCP 传输）
- `agentscope-extensions/agentscope-spring-boot-starters/agentscope-spring-boot-starter/src/main/java/io/agentscope/spring/boot/AgentscopeAutoConfiguration.java`
- `agentscope-extensions/agentscope-extensions-rag/`、`agentscope-extensions-postgresql/`、`agentscope-extensions-redis/`、`agentscope-extensions-mem/`、`agentscope-extensions-sandbox/`（模块清单）
- `docs/v2/zh/docs/building-blocks/agent.md`、`model.md`、`tool.md`、`middleware.md`、`permission-system.md`
- `docs/v2/zh/docs/harness/architecture.md`、`subagent.md`、`skill.md`、`memory.md`、`workspace.md`
- `docs/v2/zh/integration/rag/simple.md`
- `docs/v1/zh/docs/multi-agent/pipeline.md`、`workflow.md`（v1 借力 Spring AI Alibaba 的证据）
- `agentscope-examples/documentation/src/main/java/io/agentscope/examples/documentation2/streaming/StreamingWebExample.java`
- `agentscope-service/README.md`、`agentscope-service/pom.xml`

### 外部来源

- GitHub 仓库页 `https://github.com/agentscope-ai/agentscope-java`（star/fork/issue，2026-08-22 查询）
- Maven Central `https://repo1.maven.org/maven2/io/agentscope/`（artifact 清单与版本元数据）
- Spring Boot 4.1.0 dependencies POM `https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.0/`（jackson-bom 3.1.4）
- 官方文档站 `https://java.agentscope.io/`（本地 `docs/v2` 即其源，未重复拉取）

### 未确认事项（诚实声明）

- Spring Boot 4.1.0 下 agentscope starter 的实际兼容性（未实测，仅版本推断）
- ReActAgent 2.0.2 单例多会话并发在真实负载下的稳定性（文档与 javadoc 矛盾，未压测）
- nex-ai 侧 Redisson 具体版本与 agentscope 4.2.0 的适配（未核对 nex-ai 依赖树）
- agentscope 生产环境公开案例（未检索到 reference 列表）
