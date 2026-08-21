# agentscope-java v2 能力摸底（面向 nex-ai AI 智能体开发平台选型）

> 调研日期：2026-08-22
> 调研对象：本机 `~/agent-project/agentscope-java`（GitHub agentscope-ai/agentscope-java，main 分支，最后提交 2026-08-18，主干版本 2.0.3-SNAPSHOT）
> 调研纪律：**只采信 v2 主干代码与 `docs/v2/` 文档**；`docs/v1/` 全目录、git 历史、README 中 v1 历史段落均未作为论据。凡 v2 无代码实证的能力，一律标注"v2 未提供/未确认"。
> 引用约定：`~/agent-project/agentscope-java/` 简写为 `<AS>`。

---

## 0. v1/v2 边界判定（避坑指南）

仓库中 v1/v2 混杂区域清单，后续读者请绕行：

| 位置 | 性质 | 处置 |
|---|---|---|
| `<AS>/docs/v1/` 整个目录 | v1 文档，禁入 | 本次未读取，报告无任何引用 |
| `<AS>/README.md:77`（News 中 `v1.1.0` 发布条目）与 `README.md:204`（"replace v1's flat hooks" 对比句） | v1 历史信息 | 已识别并跳过，未用作论据 |
| `<AS>/agentscope-core/.../core/agent/EventType.java` | v1 粗粒度事件枚举，主干中残留但已 `@Deprecated(since = "2.0.0")`，指向新 `core/event/AgentEventType` | **主干代码里的"活化石"**：deprecated 类仍在 main 源码中，勿将其当作 v2 现行 API |
| `<AS>/agentscope-core/.../core/rag/`（Knowledge、GenericRAGHook、KnowledgeRetrievalTools、RAGMode） | v2 内部新旧并存：core 中的 RAG 入口已 `@Deprecated(forRemoval = true, since = "2.0.0")`，官方路径迁至扩展模块 `agentscope-extensions-rag-simple`（见第 6 章） | 引用 RAG 能力时务必指向 extensions-rag，勿引 core rag |
| `<AS>/agentscope-core/.../core/state/legacy/ToolkitState.java`、`core/hook/LegacyHookDispatcher.java` | 名字带 legacy 的兼容层 | 属 v2 主干（为平滑迁移保留），非 v1 文档 |
| `<AS>/docs/v2/en/docs/others/change-log.md` | v1→v2 迁移指南（链接名 V1 Migration Guide） | 属 v2 文档的一部分，但涉及 v1 API 对照，本报告未引用其 v1 侧内容 |

结论：v2 的权威入口是 `docs/v2/`（中英双语）、七个 Maven 模块的 main 源码、以及 `README.md` 的 "What is AgentScope Java 2.0" 起的正文。

---

## 1. 结论速览表

支持度分级：◎ 强（有成熟代码+文档+多实现） / ○ 有（有代码实证，但单一实现或欠打磨） / △ 弱（接口在、能力缺） / ✗ v2 未提供

| # | 需求域 | 支持度 | 依据（详见分章） |
|---|---|---|---|
| 1 | 项目成熟度/发布渠道 | ○ | 2.0.0 GA（2026-07），Maven Central 24 个版本，GitHub 5.2k star；阿里官方维护 |
| 2 | 大模型管理 | ○ | ModelRegistry + ModelProvider SPI + Credential 抽象；6 家提供商；OpenAI 兼容 baseUrl；模型目录发现（listModels）未实现 |
| 3 | skill 管理 | ◎ | 四层来源 + 仓储接口 + **PostgreSQL 仓储现成实现** + 自学习闭环（propose→gate→curator） |
| 4 | agentspec/子 agent | ◎ | `subagents/*.md` 声明式（YAML front matter）+ 编程式 Builder + 同步/后台委派（agent_spawn/agent_send） |
| 5 | agent 治理 | ○ | 三态权限引擎、OTel 追踪、**PostgreSQL/Redis(Redisson) 会话状态存储现成实现**；无租户/配额/成本归集 |
| 6 | RAG 知识库 | ○ | extensions-rag-simple：reader/chunker/embedding/5 向量库（**PgVector 有实证代码**）；core 旧 RAG 已废弃 |
| 7 | MCP | ○ | client 全量（StdIO/SSE/StreamableHTTP、token header、工具白名单）；**无 Server 实现** |
| 8 | AI 工作流/编排 | ○（原语型） | ReAct 循环 + 中间件 + 子 agent 委派（并行 fan-out/fan-in）+ 后台任务 + Agent Teams；**无任何 DAG/pipeline/StateGraph 引擎** |
| 9 | 调试台支撑 | ◎ | 29 种类型化事件 + streamEvents() 流式订阅 + interrupt + HITL + AG-UI 协议 + Studio + service Dashboard |
| 10 | Spring Boot 集成 | ○ | 11 个官方 starter，标准 auto-configuration；编译基线 Spring Boot 4.0.3/4.0.4，与 nex-ai 4.1.0 有小版本差 |
| 11 | 约束与依赖 | 注意 | Java 17+（nex-ai Java 25 可用）；**Jackson 2.21.1 vs Spring Boot 4.x 默认 Jackson 3 双栈并存**；Redisson 4.2.0（nex-ai 4.6.1） |
| 12 | 平台级能力（多租户/配额/成本/报表/密钥/版本化） | △/✗ | 框架层基本不管，需 nex-ai 自建（service 模块有部分可抄的底座，见第 12 章） |

---

## 2. 项目概况

### 2.1 模块划分（`<AS>/pom.xml:57-63`）

| 模块 | 定位 |
|---|---|
| `agentscope-core` | 精简内核：ReActAgent、消息/事件模型、Model/Tool/Skill/Permission/State 抽象、中间件。零 Spring 依赖（自身带 `JsonCodec`，`core/util/JacksonJsonCodec.java`） |
| `agentscope-harness` | 增强 agent 运行时（pom description：workspace context, subagent orchestration, memory management, backend abstraction）：HarnessAgent、文件系统/沙箱、gateway/Channel、记忆压缩、Teams、skill 运行时与 curator |
| `agentscope-service` | **独立的控制面平台应用**（Aistio + Managed Agents + Agent Teams + Dashboard），Spring Boot 4.0.4 + Spring Cloud 2025.1.2 + JPA（`<AS>/agentscope-service/pom.xml:44`） |
| `agentscope-extensions` | 19 个扩展子模块（`<AS>/agentscope-extensions/pom.xml`）：模型×5、RAG×4、redis/mysql/postgresql/oss/cos、skills、studio、scheduler、training、channel、sandbox、aistio、higress、nacos、protocol、mem、spring-boot-starters |
| `agentscope-examples` | documentation、agui、paw（CLI agent）、dataagent、codingagent（`<AS>/agentscope-examples/pom.xml`） |
| `agentscope-dependencies-bom` | 全量第三方版本 BOM |
| `agentscope-distribution` | `agentscope-all`（聚合包，即 README badge 的 `io.agentscope:agentscope`）+ `agentscope-bom` |

### 2.2 版本与发布渠道

- 主干：`2.0.3-SNAPSHOT`（`<AS>/pom.xml:30`），Java 基线 17（`pom.xml:33`，README badge "JDK 17+"）。
- Maven Central 坐标 `io.agentscope:*`：`agentscope-core` 已发布 24 个版本，最新 `2.0.2-subagent-bugfix`（https://central.sonatype.com/artifact/io.agentscope/agentscope-core/versions ，2026-08-22 查询）。README Quickstart 示例引用 2.0.1（`README.md:107` 附近）。
- 版本节奏（`README.md` News，v2 部分）：v2.0.0-RC1（2026-05）→ GA（2026-07）→ agentscope-service 发布（2026-08）。发布密度高，主干活跃。

### 2.3 成熟度信号

- GitHub 5.2k star / 1.2k fork，Apache-2.0，组织 Alibaba（`<AS>/pom.xml` organization；https://github.com/agentscope-ai/agentscope-java ）。
- 有中英双语文档站（java.agentscope.io）、Discord/钉钉/微信群、codecov、CONTRIBUTING 流程。
- 测试与工程化：spotless（google-java-format）、jacoco、surefire、central-publishing 插件齐备（根 pom）。
- 风险信号：主干为 SNAPSHOT 快节奏演进；2.x 仍在 RC→GA 后短期内（1 个月内连发 2.0.1/2.0.2 补丁），API 稳定性中等；`EventType`、core `rag` 包等 deprecated-for-removal 类说明内部清理仍在进行。

---

## 3. 大模型管理

### 3.1 核心抽象

- **Model 接口 + ChatModelBase**：`core/model/Model.java`、`ChatModelBase.java`；流式返回 `Flux<ChatResponse>`，带 `ChatUsage`（token 用量）与 `GenerateOptions`。
- **ModelRegistry**（`core/model/ModelRegistry.java:36-42`）：字符串解析 `provider:model`（如 `openai:gpt-4.1`、`dashscope:qwen-plus`）；支持命名注册、regex 工厂注册（用户工厂优先于 SPI）、ServiceLoader 发现。
- **ModelProvider SPI**（`core/model/spi/ModelProvider.java`）：`providerId()/supports(modelId)/create(modelId, ModelCreationContext)`，扩展模块通过 SPI 自动注册。
- **Credential 体系**（`core/credential/CredentialBase.java`）：抽象凭证（id + 连接材料 + `getChatModelClass()` + `listModels()`）。**注意**：`listModels()` 默认抛 `UnsupportedOperationException`，`ModelCard` 自述为 "minimal placeholder"（`core/credential/ModelCard.java`）——**模型目录/发现能力 v2 尚未落地**，管理面需自建模型元数据表。

### 3.2 提供商清单（均为主干代码）

| 提供商 | 位置 | 说明 |
|---|---|---|
| OpenAI | `extensions/model/openai/OpenAIChatModel.java` 等 | 兼容端点见 3.3 |
| DashScope（通义） | `extensions/model/dashscope/DashScopeChatModel.java` | 含多模态工具、加密 utils |
| Anthropic | `extensions/model/anthropic/AnthropicChatModel.java` | Claude 系 |
| Gemini | `extensions/model/gemini/GeminiChatModel.java` | google-genai 1.45.0 |
| Ollama | `extensions/model/ollama/OllamaChatModel.java` | 本地模型，含 embedding |
| OpenAI 兼容子品牌 | `extensions/model/openai/compat/{deepseek,glm,kimi,minimax}/` | 各自 Formatter/Provider | 

core 内另有 `DeepSeekCredential/KimiCredential/XAICredential`（`core/credential/`）。

### 3.3 OpenAI 兼容端点（企业网关接入关键）

`OpenAICredential` 带 `apiKey + organization + baseUrl` 三元组（`extensions/model/openai/credential/OpenAICredential.java:34-42,70`，JSON 字段 `base_url`）。**结论：任何 OpenAI 兼容网关（vLLM/OneAPI/Higress 等）只需设 baseUrl 即可接入。**

### 3.4 扩展方式

三条路：① 实现 `ModelProvider` SPI 打包独立 jar；② `ModelRegistry.registerFactory(regex, factory)` 编程注册；③ `ModelRegistry.register(name, modelInstance)` 命名注册。对 nex-ai 而言，"模型管理面→运行时"的映射建议走 ②/③（由管理面数据库驱动注册），SPI 适合第三方生态。

---

## 4. skill 管理

### 4.1 来源层级（四层，同名遮蔽）

依据 `docs/v2/en/docs/harness/skill.md:183-192`：

| 优先级（低→高） | 层 | 说明 |
|---|---|---|
| 1 | Classpath | `src/main/resources/skills/`（`skill.md:117`） |
| 2 | Marketplace 仓储 | `skillRepository(...)`，可多次追加，后注册者胜 |
| 3 | Workspace 共享 | `workspace/skills/` |
| 4 | 用户级 | `workspace/<userId>/skills/`（经 `AbstractFilesystem` 逻辑路径，`skill.md:173-179`） |

### 4.2 仓储接口（可照搬为管理面后端）

`core/skill/repository/AgentSkillRepository.java:26`：

```java
public interface AgentSkillRepository extends AutoCloseable {
    AgentSkill getSkill(String name);
    List<String> getAllSkillNames();     // id 格式 name_version_source
    List<AgentSkill> getAllSkills();
    boolean save(List<AgentSkill> skills, boolean force);
    boolean delete(String skillName);
    boolean skillExists(String skillName);
    AgentSkillRepositoryInfo getRepositoryInfo();
    String getSource();                  // repositoryType_location
    void setWriteable(boolean writeable);
}
```

现成实现：`ClasspathSkillRepository`、`FileSystemSkillRepository`（core）、`GitSkillRepository`、`MysqlSkillRepository`、`PostgresSkillRepository`（extensions-skills）、`NacosSkillRepository`（extensions-nacos）、`WorkspaceSkillRepository`（harness）。

**PostgreSQL 实现已存在**（`extensions/core/skill/repository/postgresql/PostgresSkillRepository.java:123`）：JDBC `DataSource` 直连；表 `agentscope.agentscope_skills`（name UNIQUE、skill_content、metadata_json JSON 文本、时间戳）+ `agentscope_skill_resources`（resource_path/content，外键级联删）；`createIfNotExist=true` 自动建表；Builder 可定制 schema/table 名；兼容 legacy 无 metadata_json 旧表；参数化查询防注入、事务原子操作。
→ **对 nex-ai 的意义：市场后端不必自己写仓储，`AgentSkillRepository` 接口 + 此实现可直接把 skill 存进现有 PostgreSQL；管理面只需围绕这两张表做 CRUD UI。注意：表无 version 列（name 唯一），资产版本化要靠 metadata_json 自行扩展（见第 12 章）。**

### 4.3 SKILL.md 规范

`core/skill/AgentSkill.java` + `core/skill/util/MarkdownSkillParser.java`：YAML front matter（`name`、`description` 必填）+ Markdown 正文（skill 内容）+ resources（`Map<path, content>` 附属脚本文件）。解析入口 `SkillUtil.createFrom(skillMd, resources)`。与 Claude/Anthropic 的 SKILL.md 惯例同构，迁移成本低。

### 4.4 自学习闭环（可选开启）

依据 `docs/v2/en/docs/harness/skill.md:209-268` + 代码：

1. **产出**：`propose_skill` 工具（`harness/agent/tool/ProposeSkillTool.java:44`，name+description+body+scripts 一步建 skill）；另有 `skill_manage`（SkillManageTool）。
2. **审核门**：`SkillPromotionGate` 体系（`harness/agent/skill/curator/`）：`LocalApprovalGate` / `NotifyAndWaitGate`（通知+等待）/ `RejectAllGate`；可见性过滤 `AllowListFilter/CanaryFilter/EnvironmentFilter/CompositeFilter`；安全扫描 `SkillSecurityScanner`；审计 `SkillAuditLog`。
3. **后台整理**：`SkillCurator`（`harness/agent/skill/curator/SkillCurator.java`）：按活跃度做 `ACTIVE → STALE → ARCHIVED` 状态迁移（只归档不删除、pinned/DRAFT 跳过）；LLM 整合报告目前 `DRY_RUN_ONLY`（**真正的自动整合"not implemented yet"**，javadoc 明示）；用量统计 `SkillUsageBackend/SkillUsageStore`。

→ 闭环骨架完整，但"自动整合"止步于报告，深度自动化需 nex-ai 补充。

---

## 5. agentspec / 子 agent 管理

### 5.1 声明式 spec（`subagents/*.md`）

`harness/agent/subagent/AgentSpecLoader.java`（javadoc）：

- 扫描 workspace 的 `subagents/` 目录**直接子级** `.md` 文件；**文件名即 agent id**（front matter 禁写 name）；
- front matter 字段：`description`（必填）、`workspace.mode`（isolated|shared，默认 isolated）、`workspace.path`、`model`、`maxIters`（默认 10）、`tools`（继承父工具的白名单过滤）；
- 三种来源模式互斥（`SubagentDeclaration.java` javadoc）：**definition workspace**（指向含 `AGENTS.md` 的目录，其 skills/knowledge/MEMORY 随 isolated 模式可用）/ **inline body**（front matter 后正文即系统提示）/ **remote HTTP**（指向 AgentScope task HTTP 服务器，子 agent 跨进程运行）；
- `Mode` 枚举：PRIMARY / SUBAGENT / ALL（可否作为入口 agent）。

### 5.2 编程式声明

`SubagentDeclaration.builder().name(...).description(...).workspace(Path).workspaceMode(ISOLATED).model("qwen3-max").tools(List.of(...)).build()`（`SubagentDeclaration.java` javadoc 示例）。`SubagentSpecGenerator` 支持反向生成 spec。

### 5.3 委派机制

依据 `docs/v2/en/docs/harness/subagent.md:106-163` + 代码：

- `agent_spawn`（创建+可带任务）/ `agent_send`（向已有实例追加消息）；
- **同步/后台二态**：`timeout_seconds > 0`（默认 30s、上限 600s）为同步调用，超时后**晋升为后台任务**（`status: timeout_promoted` + task_id）；`= 0` 立即返回 task_id 走后台；
- 应用侧可用 `RuntimeContext` 的 `CTX_FORCE_SYNC` 强制同步语义；
- **并行 fan-out/fan-in**：`ToolkitConfig.parallel=true`（默认），同一推理轮的多个同步子 agent 调用并行执行，形成同步屏障（`subagent.md:131`）；
- 后台结果**无需轮询**：父 agent 下一个推理步前以 system-reminder 注入（`subagent.md:137`）；
- 远程子 agent：`RemoteSubagentStub/RemoteSubagentTransport` + `AgentProtocolTaskClient`（跨副本路由）。

→ 委派是"运行时工具"模式而非静态编排图；管理面可以把 `subagents/*.md` 的生成/编辑做成低代码功能（service 模块的 AgentSpecCodec 即此思路，`builder/web/catalog/spec/AgentSpecCodec.java`）。

---

## 6. RAG 知识库

### 6.1 v2 的真实 RAG 路径：extensions-rag-simple

core 的 `Knowledge/GenericRAGHook/KnowledgeRetrievalTools` 已 `@Deprecated(forRemoval=true, since="2.0.0")`（`core/rag/GenericRAGHook.java:68` 等）；官方文档（`docs/v2/zh/integration/rag/simple.md:1-6`）指路 `agentscope-extensions-rag-simple`："自带文档读取器、分块策略、Embedding 模型适配、以及 5 个开箱即用的向量库适配器"。注意包名仍是 `io.agentscope.core.rag.*`（不同 jar 同包，classpath 上需依赖 extensions jar 才有实现）。

- **Reader/切分**：`TextReader/PDFReader(pdfbox 3.0.7)/WordReader(poi 5.5.1)/TikaReader(tika 3.3.0)/ImageReader/ExternalApiReader` + `TextChunker/SplitStrategy`（`extensions/core/rag/reader/`）。
- **Embedding**：`EmbeddingModel` 接口 + DashScope（文本/多模态）/ OpenAI / Ollama 三实现（`extensions/core/embedding/`）。
- **向量库**：`VDBStoreBase`（`add(List<Document>)` / `search(SearchDocumentDto)`，`extensions/core/rag/store/VDBStoreBase.java:39-63`）+ 5 实现：**PgVectorStore、MilvusStore、QdrantStore、ElasticsearchStore、InMemoryStore**。
- **检索门面**：`SimpleKnowledge implements Knowledge`（`extensions-rag-simple/.../SimpleKnowledge.java:71`）：`addDocuments(List<Document>)` / `retrieve(String query, RetrieveConfig)`。
- 外部 RAG 平台适配：Bailian、Dify、RAGFlow、Haystack（各自 `agentscope-extensions-rag-*` 子模块）。

### 6.2 PgVector 实证代码（重点核查项）

`extensions-rag-simple/.../store/PgVectorStore.java:106`（`public class PgVectorStore implements VDBStoreBase, AutoCloseable`）：

- 使用官方 JDBC 驱动 `com.pgvector:pgvector:0.1.6`（BOM `agentscope-dependencies-bom/pom.xml:89`）+ `postgresql:42.7.11`（BOM:88）；
- 前置条件：PostgreSQL 11+，`CREATE EXTENSION IF NOT EXISTS vector;`（javadoc）；
- 表结构：document id、chunk id、content、向量列 + **JSONB payload**；距离类型可选（`DistanceType.COSINE` 等）；
- Builder：`jdbcUrl/username/password/schema/tableName/dimensions/distanceType`，另有静态工厂 `create(...)`；
- 检索经 `Schedulers` 包裹阻塞 JDBC 返回 `Mono`。

→ **结论：PgVector 支持有一手代码实证，且与 nex-ai 的 PostgreSQL 技术栈直接吻合。** 局限：自带连接（自管 jdbcUrl/密码），不走容器 DataSource——接入 nex-ai 时建议改造成注入 `DataSource` 的小封装；无混合检索（BM25+向量）、无本地 rerank（仅 Bailian/Dify 外部平台有 rerank 配置）。

---

## 7. MCP

### 7.1 Client 能力（`agentscope-core`，官方 SDK `io.modelcontextprotocol:mcp:0.17.0`，BOM:82）

`core/tool/mcp/McpClientBuilder.java`（javadoc + imports）：

- **三种传输**：StdIO（本地进程）、SSE（HTTP 有状态）、StreamableHTTP（无状态流式）；
- **token 注入**：`.header("Authorization", "Bearer " + token)`（javadoc 原生示例）+ `.queryParam/.queryParams`；自定义协议版本协商 `protocolVersions("2024-11-05","2025-03-26")`；
- 同步/异步双封装（`McpSyncClientWrapper/McpAsyncClientWrapper`），工具转 `McpTool` 进 Toolkit，内容转换 `McpContentConverter`；`McpClientManager` 统一生命周期；
- **白名单**：`harness/agent/tools/McpServerConfig.java:73`——"Optional allowlist of tools to import from this server. When null or empty, all"，即 server 级工具导入白名单；另有 `HigressMcpClientBuilder`（Higress MCP 网关动态工具发现）。

### 7.2 Server 实现：v2 未提供

`io.modelcontextprotocol.server.*` 在全部模块 main 源码零引用（grep 实证）；`McpServerRegistrar/McpServerConfig` 实为 client 侧"多 MCP server 配置注册器"。**结论：若 nex-ai 需要把平台工具反暴露为 MCP Server（供第三方 agent 调用），需自建**（可基于官方 mcp SDK 0.17.0 server 端自行实现，或经 A2A/agent-protocol 替代，见 8.5）。

---

## 8. AI 工作流 / 编排（重点澄清章）

### 8.1 明确回答：v2 不存在任何 DAG/pipeline/StateGraph 引擎

- `grep -rniE "stategraph|dag\b|class .*Workflow|class .*Pipeline"` 在 `agentscope-core/src/main` 与 `agentscope-harness/src/main` **零命中**（2026-08-22 实测）；
- `docs/v2/`（en 全部 docs/integration 页面）无 workflow engine / pipeline / state graph 章节；
- v2 的编排哲学是"**ReAct 循环 + 工具化委派**"：确定性流程不是编译期图，而是运行期由 LLM+工具涌现，靠权限、超时、任务板约束。

### 8.2 v2 真实拥有的编排原语（逐项代码实证）

| 原语 | 实证 |
|---|---|
| **ReAct 循环** | `core/ReActAgent.java:214`（reasoning/acting 交替，`maxIters` 上限，`EXCEED_MAX_ITERS` 事件） |
| **中间件 AOP** | `core/middleware/MiddlewareBase.java`：`onAgent/onReasoning/onActing/onModelCall/onSystemPrompt` 五阶段拦截（`middleware/` 包 7 类） |
| **子 agent 委派（sync/background）** | `harness/agent/tool/AgentSpawnTool/AgentSendTool`；`docs/v2/en/docs/harness/subagent.md:106-163`（超时晋升后台、CTX_FORCE_SYNC） |
| **并行 fan-out/fan-in** | `ToolkitConfig.parallel=true` 默认开（`subagent.md:131`） |
| **后台任务管理** | `task_output / wait_async_results / task_cancel / task_list` 工具族（`subagent.md:158-163`）；`AsyncToolMiddleware`、`TaskRepository/WorkspaceTaskRepository`（harness 依赖 sqlite-jdbc 存任务）、`BackgroundTask/TaskRunSpec`（`harness/agent/subagent/task/`） |
| **Agent Teams（多 agent 协作单元）** | `harness/agent/middleware/TeamsMiddleware.java`（注入 Teams 上下文 + role-clipped `TeamTool`/`team_event` + MessageBus 唤醒）；`harness/agent/team/`（TeamClient/TeamContext/TeamMessage/TeamTask，Lead-Member 任务板模式）；分布式形态依托 service 控制面（`agentscope-service/README.md` Agent Teams 节） |
| **计划模式** | `PlanModeMiddleware/PlanModeManager` + `workspace/plans/` 持久化（`harness/agent/workspace/plan/`） |
| **异步工具** | `bus/AsyncToolRegistry + MessageBus + WakeupDispatcher`（跨副本唤醒） |
| **定时任务** | `extensions/scheduler/`：Quartz（`QuartzAgentScheduler`）与 XXL-Job 双实现（BOM quartz 2.5.2 / xxl-job 3.3.2）——**与 nex-ai 的 Quartz 体系同构** |

### 8.3 对"工作流平台"需求的含义

若 nex-ai 的"AI 工作流"指 **LangGraph 式确定性状态图**（节点/边/条件分支/人审节点），v2 不提供，需要：(a) 自研 StateGraph 层并产出为"工具+子 agent"组合（推荐：工作流引擎只做调度，节点执行映射到 agent_spawn/同步屏障）；或 (b) 引入第三方（风险更高）。若指"LLM 自主编排 + 人工闸门"，v2 原语已足够（ReAct + 后台任务 + HITL + Teams）。

---

## 9. 调试台支撑（事件流/HITL/可视化）

### 9.1 事件流（v2 一等公民）

- **29 种类型化事件**：`core/event/AgentEventType.java:42-90`：AGENT_START/END/RESULT、MODEL_CALL_START/END、TEXT_BLOCK_*（3）、THINKING_BLOCK_*（3）、DATA_BLOCK_*（3）、TOOL_CALL_*（3）、TOOL_RESULT_*（4）、EXCEED_MAX_ITERS、REQUIRE_USER_CONFIRM、REQUIRE_EXTERNAL_EXECUTION、USER_CONFIRM_RESULT、EXTERNAL_EXECUTION_RESULT、REQUEST_STOP、SUBAGENT_EXPOSED、HINT_BLOCK、ALL_TOOLS_DENIED、CUSTOM。（README 称 28 typed events，实数 29 含 CUSTOM。）
- 订阅方式：`agent.streamEvents(msg, runtimeContext)` 返回 Reactor `Flux<AgentEvent>`，`agent.call(...)` 阻塞拿 `AgentResultEvent`（README Hello 示例；`docs/v2/en/docs/building-blocks/message-and-event.md`）。
- **子 agent 流式转发**：`SubagentEventBus`（core）+ `SUBAGENT_EXPOSED` 事件 + AG-UI `SubagentEventConverter`。

### 9.2 HITL / interrupt

- 工具参数确认：`REQUIRE_USER_CONFIRM → USER_CONFIRM_RESULT`（`ConfirmResult` 三态）；工具侧 `ToolSuspendException` 挂起-恢复（`core/tool/`）；
- 外部系统移交：`REQUIRE_EXTERNAL_EXECUTION / EXTERNAL_EXECUTION_RESULT`；
- **中断**：`core/interruption/InterruptControl.java`——per `(userId, sessionId)` 槽位的中断旗标（trigger/isInterrupted/reset + 附带用户消息），runtime-only 不序列化；`REQUEST_STOP` 事件通知下游；
- 权限触发的 `ALL_TOOLS_DENIED`。

### 9.3 前端协议与可视化组件

| 通道 | 实证 | 说明 |
|---|---|---|
| **AG-UI 协议** | `extensions/core/agui/`（全量转换器族）+ `agentscope-agui-spring-boot-starter`（MVC 与 WebFlux 双栈） | AgentEvent→AG-UI 事件 over SSE：`RUN_*、TEXT_MESSAGE_*、TOOL_CALL_*、CUSTOM` + HITL interrupts + state 同步（`docs/v2/en/integration/protocol/agui.md:1-19`）；**局限：媒体块（图/音/视/文档）暂不回序列化进 AG-UI 消息**（agui.md:13） |
| **OpenAI Chat Completions 兼容服务端** | `agentscope-chat-completions-web-starter`（`/v1/chat/completions`） | 把 agent 反向暴露为 OpenAI API（`docs/v2/en/integration/ecosystem/chat-completions-web.md:3`） |
| **AgentScope Studio** | `extensions-studio`：socket.io WebSocket 推送、trace 回放、`requestUserInput` 人工输入（`docs/v2/en/integration/ecosystem/studio.md:1-8`） | 独立开源前端仓库 agentscope-studio |
| **service Dashboard** | `agentscope-service`（DataApp/GatewayApp/SchedulerApp + REST + 前端） | 在线 agent/会话/token 全局视图、会话实时上下文检查视与压缩、人工干预（`agentscope-service/README.md`） |

→ nex-ai 调试台前端可直接消费 SSE 事件流（AG-UI 或原生 AgentEvent JSON），无需自创协议。

---

## 10. Spring Boot 集成

### 10.1 Starter 清单（`<AS>/agentscope-extensions/agentscope-spring-boot-starters/pom.xml`）

`agentscope-spring-boot-starter`（核心：Memory/Toolkit/ReActAgent Bean）+ 模型×5（openai/dashscope/gemini/anthropic/ollama）+ `a2a` + `agui` + `chat-completions-web` + `nacos` + `admin`（审计/端点/metrics），共 11 个。

### 10.2 Bean 装配方式

标准 Spring Boot 3+/4 风格（`spring/boot/AgentscopeAutoConfiguration.java:56-58`）：`@AutoConfiguration + @EnableConfigurationProperties(AgentscopeProperties.class) + @ConditionalOnClass(ReActAgent.class)`；Bean 上 `@ConditionalOnProperty("agentscope.agent.enabled") + @ConditionalOnMissingBean + @ConditionalOnBean(Model.class)`，Memory 为 prototype 作用域。模型 starter 提供 `*ChatModelBuilderCustomizer` + 独立 `*Properties`（如 `agentscope.openai.*`）。admin starter 扩展 Actuator：`agentscope/status|usage|permissions|tools|models|subagents|agents|commands|doctor|drain|shutdown` 端点（`spring/boot/admin/endpoint/`），另有 `AdminAuditLogger` 与 `MetricsHook/UsageStats`。

→ 装配是条件化的、可整体让位（ConditionalOnMissingBean），与 nex-ai 的 `nexai-spring-boot-starter-*` 体系可共存：nex-ai 侧用 Java Config 手工装配 agentscope Bean 亦完全可行（core/harness 本身不依赖 Spring）。

### 10.3 与 Spring Boot 4.1 的兼容风险

- **编译基线**：starters 父 pom `spring-boot.version=4.0.3`（`agentscope-spring-boot-starters/pom.xml:35`），BOM 与 service 为 4.0.4（`agentscope-dependencies-bom/pom.xml:106`）。nex-ai 为 **4.1.0**（`nexai-dependencies/pom.xml`）。starter 仅依赖 `spring-boot-autoconfigure/starter-web` 等稳定 API，4.0→4.1 二进制兼容概率高，但**官方未声明 4.1 验证（未确认）**——需在 nex-ai 做 dependency + smoke 验证。
- **Jackson 双栈**：见 11.2。
- **Web 栈**：agui starter 同时提供 MVC 与 WebFlux 自动配置（按 classpath 激活）；nex-ai 为 Servlet 栈，无冲突。
- **Quartz**：scheduler 扩展使用 Quartz 2.5.2（BOM:100 附近），nex-ai 侧 profile 默认排除 Quartz 自动配置（AGENTS.md），若引入 scheduler 扩展需自行协调。

---

## 11. 约束与依赖

### 11.1 Java 版本

基线 **Java 17+**（`pom.xml:33`，README badge）。nex-ai 的 Java 25 向后兼容，无障碍；但也意味着 agentscope 代码未用 21+ 特性，无虚拟线程等假设。

### 11.2 Jackson：2.21.1 vs Spring Boot 4.x 的 Jackson 3（关键风险）

- **实证**：agentscope 全线使用 `com.fasterxml.jackson` **2.21.1**（BOM `agentscope-dependencies-bom/pom.xml:73` import jackson-bom；core 自带 `JacksonJsonCodec`，不依赖 Spring）。所有模型 DTO/事件/状态序列化注解均为 `@JsonProperty`（com.fasterxml.jackson.annotation）。
- nex-ai（Spring Boot 4.1）web 层默认 **Jackson 3**（`tools.jackson`，`nexai-dependencies` 引入 `spring-boot-jackson`）。Jackson 3 的 databind 换了包名/坐标，与 Jackson 2 databind **可在同一 classpath 并存**；Jackson 3 设计上仍识别 `com.fasterxml.jackson.annotation` 的 2.x 注解（JSTEP 路线）。
- **风险定性**：不是编译冲突，而是**双 databind 并存**——(a) 依赖体积与两套 ObjectMapper 配置心智；(b) Spring MVC（Jackson 3）直接序列化 agentscope 对象时行为依赖 Jackson 3 对 2.x 注解的兼容程度（未逐类验证，**未确认**，建议 PoC：用 RestController 返回一个 AgentEvent）；(c) agentscope 内部序列化（状态持久化、skill metadata_json）不受 Spring 影响，自成闭环。
- 结论：可控但必须显式管理——推荐 nex-ai 统一用 BOM 引入 agentscope，验收"事件/状态经 Spring HTTP 层往返"用例。

### 11.3 其他关键依赖（BOM 摘录，`agentscope-dependencies-bom/pom.xml`）

| 依赖 | 版本 | 与 nex-ai 的关系 |
|---|---|---|
| reactor-bom | 2025.0.2 | agentscope API 全面 Reactor 化（Flux/Mono），nex-ai 需接受响应式边界 |
| spring / spring-boot | 7.0.7 / 4.0.4 | nex-ai 4.1.0，兼容性见 10.3 |
| mcp（官方 SDK） | 0.17.0 | MCP client |
| redisson / jedis / lettuce | 4.2.0 / 7.4.1 / 6.4.2 | nex-ai Redisson 4.6.1——`RedissonAgentStateStore`（`extensions-redis/.../RedissonAgentStateStore.java:62`）只用 `RedissonClient` 稳定接口，4.6.1 引擎驱动 4.2.0 编译的类预期兼容（未确认，建议验证） |
| postgresql / pgvector | 42.7.11 / 0.1.6 | 与 nex-ai PostgreSQL 栈同族 |
| OTel | 1.61.0 | nex-ai OTel 1.39.0——版本由各自 BOM 管理，运行时取高版本即可 |
| a2a-sdk | 0.3.3.Final | A2A 协议 |
| pdfbox/poi/tika | 3.0.7 / 5.5.1 / 3.3.0 | RAG reader，注意 tika 体积与 CVE 跟进 |
| tree-sitter | 0.24.4 | shell 命令校验（UnixCommandValidator） |
| k8s fabric8 / jgit / e2b / daytona | 6.13.4 / 7.6.0 等 | 沙箱与 Git skill 仓储，按需引入 |

---

## 12. 缺口清单：v2 不管什么（nex-ai 必须自建）

| 平台能力 | v2 现状 | 依据 / 可复用底座 |
|---|---|---|
| **多租户（业务级）** | 仅 RuntimeContext 四维隔离键（session/user/agent/org，`docs/v2/en/docs/building-blocks/context.md`）贯通 workspace 路径、KV 命名空间、沙箱槽位；**无租户实体/生命周期/租户级路由与数据隔离策略** | 隔离键可对接 nex-ai `nexai.tenant`（把 tenantId 映射进 RuntimeContext.org/user），租户管理面完全自建 |
| **配额与限流** | ✗ 无 API 配额、token 预算、并发会话限制 | admin starter 仅有 `UsageStats` 用量统计（观测非管控）；需 nex-ai 在网关/服务层实现 |
| **成本归集** | `ChatUsage`/`MODEL_CALL_END` 提供 token 数（`core/model/ChatUsage.java`、事件流），**无计价模型、无按租户/项目/agent 归集报表** | 建议订阅事件流落库（类似 service 的 SessionEventEntity）自建成本账本 |
| **审计报表** | 有审计"数据源"：`AdminAuditLogger`、`JsonlTraceExporter`、OTel GenAI semconv（`core/tracing/telemetry/`）、TranscriptStore；**无查询 API/报表** | nex-ai 做审计中心时把上述导出接入 system 模块表结构 |
| **密钥管理** | Credential 是内存对象 + 环境变量读取；**core/harness 无 KMS/Vault 集成** | service 模块有 `VaultService/VaultCrypto/VaultCredentialEntity`（JPA 加密存储，`builder/web/managed/`）可抄实现，但那是 service 应用内部能力，不随 core/harness 提供 |
| **资产版本化** | skill 表无版本列（name UNIQUE，`PostgresSkillRepository` 建表 javadoc）；AgentSkill 仅 metadata 携带 version/source | Managed agent 在 service 有 `AgentVersionEntity/AgentVersionSnapshot`（可参考）；skill/prompt 的版本化、灰度、回滚需 nex-ai 自建 |
| **模型目录/发现** | `CredentialBase.listModels()` 默认 UnsupportedOperation，ModelCard 为占位（`core/credential/`） | 管理面需自维护模型元数据（提供商/端点/价格/上下文窗口——`ModelContextWindows` 类有静态表可参考） |
| **MCP Server（对外暴露工具）** | ✗ 仅 client（第 7.2 节） | 需自建或走 A2A（v2 有完整 A2A server：`extensions/core/a2a/server/`，JSON-RPC transport） |
| **确定性工作流引擎** | ✗ 无 DAG/StateGraph（第 8.1 节） | 自研薄编排层或映射为工具+子 agent |
| **本地混合检索/rerank** | PgVector 仅向量近邻；rerank 仅外部平台（Bailian/Dify） | 可用 PG 全文检索自补 BM25 混合 |
| **消息渠道运营** | channel 扩展（钉钉/飞书/企微/GitHub/GitLab）有接入无运营（群管理、灰度） | nex-ai 按需包装 |
| **国内模型适配广度** | GLM/Kimi/MiniMax/DeepSeek 均为 OpenAI-compat 通道；无百度/讯飞等专有协议实现 | 经 OpenAI 兼容 baseUrl 大多可通，专有能力（如文心 tool 协议差异）需自验 |

---

## 13. 与 nex-ai 集成风险清单（汇总）

1. **Jackson 2/3 双栈**（高优先验证）：agentscope 2.21.1（com.fasterxml）vs Spring Boot 4.1 默认 Jackson 3（tools.jackson）。PoC 验证"Spring MVC 序列化 AgentEvent/AgentSkill"。
2. **Spring Boot 小版本差**（中）：starter 编译基线 4.0.3/4.0.4，nex-ai 4.1.0；预期二进制兼容，官方未声明（未确认）。
3. **Redisson 版本差**（低）：4.2.0 编译 vs nex-ai 4.6.1 运行；接口稳定，建议跑 `RedissonAgentStateStore` 冒烟。
4. **无工作流引擎**（架构级）：确定性 DAG 需求要自研"调度层→agentscope 工具/子 agent"的映射，不要指望 v2 替代。
5. **MCP Server 缺位**（中）：平台工具对外输出需自建（或改用 v2 已有的 A2A server）。
6. **响应式边界**（中）：agentscope API 全 Reactor；nex-ai 的 Servlet/MyBatis 栈需在边界处 `block()`/桥接，注意线程模型（勿在 Reactor 序列里做阻塞 DB 调用）。
7. **自管连接 vs 容器 DataSource**（低）：PostgresSkillRepository/PgVectorStore 均自建 JDBC 连接，接入 nex-ai 时改造为注入 DataSource 以纳入连接池与审计。
8. **skill 表结构自定义**（低）：agentscope 表无 version/tenant 列——建议沿用接口另建 nex-ai 表实现（接口小、易实现），而不是改官方表。
9. **成熟度节奏**（低-中）：2.0.0 GA 仅月余，deprecated-for-removal 类尚在清理；建议锁定 2.0.x 已发布版本（Maven Central 最新 2.0.2 系）而非 SNAPSHOT，并关注 minor 升级 changelog。
10. **多租户/配额/成本/密钥**（确定性工作）：第 12 章缺口即 nex-ai 管理面的需求边界，v2 只给运行时隔离键与用量事件。

---

## 14. 参考来源

一手（本地代码/文档，路径相对 `~/agent-project/agentscope-java/`）：

- `pom.xml`（模块、2.0.3-SNAPSHOT、Java 17）；`agentscope-dependencies-bom/pom.xml`（全依赖版本）
- `agentscope-core/src/main/java/io/agentscope/core/`：`model/ModelRegistry.java`、`model/spi/ModelProvider.java`、`credential/CredentialBase.java`、`credential/ModelCard.java`、`event/AgentEventType.java`、`interruption/InterruptControl.java`、`permission/PermissionEngine.java`、`permission/PermissionRule.java`、`skill/repository/AgentSkillRepository.java`、`skill/AgentSkill.java`、`rag/Knowledge.java`、`tool/mcp/McpClientBuilder.java`、`ReActAgent.java`、`middleware/`、`tracing/`
- `agentscope-harness/src/main/java/io/agentscope/harness/agent/`：`subagent/AgentSpecLoader.java`、`subagent/SubagentDeclaration.java`、`subagent/task/`、`middleware/TeamsMiddleware.java`、`middleware/SubagentsMiddleware.java`、`skill/curator/SkillCurator.java`、`tool/ProposeSkillTool.java`、`tools/McpServerConfig.java`、`team/`
- `agentscope-extensions/`：`agentscope-extensions-model/**`（openai/dashscope/anthropic/gemini/ollama 及 compat）、`agentscope-extensions-skills/**/PostgresSkillRepository.java`、`agentscope-extensions-postgresql/**/PostgresAgentStateStore.java`、`agentscope-extensions-redis/**/RedissonAgentStateStore.java`、`agentscope-extensions-rag/agentscope-extensions-rag-simple/**`（PgVectorStore、SimpleKnowledge、reader/embedding/store）、`agentscope-spring-boot-starters/**`
- `agentscope-service/README.md`、`agentscope-service/src/main/java/io/agentscope/builder/`（控制面/JPA/Vault/版本化）
- `docs/v2/`：`en/docs/index.md`、`en/docs/harness/skill.md`、`en/docs/harness/subagent.md`、`zh/integration/rag/simple.md`、`en/integration/protocol/agui.md`、`en/integration/ecosystem/{studio,chat-completions-web}.md`、`en/docs/building-blocks/*`

外部（2026-08-22 查询）：

- Maven Central：https://central.sonatype.com/artifact/io.agentscope/agentscope-core/versions （24 版本，最新 2.0.2-subagent-bugfix）
- MVNRepository：https://mvnrepository.com/artifact/io.agentscope/agentscope-core （73 使用方）
- GitHub：https://github.com/agentscope-ai/agentscope-java （5.2k star / 1.2k fork / Apache-2.0）
- 官方文档站：https://java.agentscope.io/ ；AgentScope Studio：https://github.com/agentscope-ai/agentscope-studio
