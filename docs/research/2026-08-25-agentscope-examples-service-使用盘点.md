# agentscope-examples 中 agentscope-service 模块使用盘点

> 调研日期：2026-08-25
> 调研对象：`/Users/jerry/agent-project/agentscope-java/`（下文所有路径均相对该根目录）
> 方法：一手源码逐条核实（pom 依赖 grep、Java import grep、配置文件 find、源码 diff、官方文档/README 引读），无训练记忆成分。

---

## 一、结论速览（TL;DR）

**严格口径下，agentscope-examples 中没有任何示例"使用"了 agentscope-service 模块。**

- 6 个示例模块（`agentscope-examples/pom.xml:35-40`：`documentation`、`agui`、`agents/agentscope-paw`、`agents/agentscope-dataagent`、`agents/agentscope-codingagent`、`agentscope-copilotkit`）的 pom 中，**零个**声明 `io.agentscope:agentscope-service`、`service-common`、`service-gateway`、`service-dataplane`、`service-scheduler` 中的任何一个依赖。
- 全部示例源码中，**没有任何 Java 文件 import `io.agentscope.builder.*`**（service-common 的包根）。唯一 grep 命中的两个文件位于 `agentscope-paw` 的**测试源码**，它们只是自己**声明了** `package io.agentscope.builder;`（历史 fork 痕迹），import 的实际是 paw 自己的 `io.agentscope.claw2.*` 类。
- examples 中唯一的 `agentscope.json`（`agentscope-dataagent/src/main/resources/workspace-template/agentscope.json`）由 **dataagent 自己的 fork 代码解析**，不经 service 模块。

但存在**三种宽口径关联**（不是 Maven/代码依赖，报告中详述）：

| 关联类型 | 涉及示例 | 性质 |
|---|---|---|
| ① 源码级 fork：各自携带 `runtime/config/AgentscopeConfig.java` 等九个同构类 | paw、dataagent、codingagent（全部三个完整应用） | 复刻 service-common（前身 agentscope-builder）的 agentscope.json 声明式装配机制，但独立演进 |
| ② 运行时 HTTP 对接：aistio BYO 自注册到 service 控制面 | paw（经 `agentscope-extensions-aistio`，该扩展属 extensions 不属 service） | 部署期协同，非编译期依赖 |
| ③ 文档互链与迁移指引 | codingagent（`[builder]` 链接）、paw（"sister projects"） | 仅 README 引用 |

**命中清单表（宽口径，穷尽）**：

| 示例 | 路径 | 依赖 service？ | 与 service 的关系（一句话） |
|---|---|---|---|
| agentscope-paw | `agentscope-examples/agents/agentscope-paw` | 否 | 从 builder（service 前身）fork 出的单机精简版；保留同构配置解析代码与 `io.agentscope.builder` 测试包名；经 aistio 扩展可自注册到 service 控制面 |
| agentscope-dataagent | `agentscope-examples/agents/agentscope-dataagent` | 否 | 自带 agentscope.json 的 fork 解析器，做多人多租户数据 agent 平台（README 明言"需要团队化可迁移到 agentscope-service"是 paw 侧的姊妹表述） |
| agentscope-codingagent | `agentscope-examples/agents/agentscope-codingagent` | 否 | 携带同构 `config/AgentscopeConfig.java`；README 以 `[builder]` 链接指向 agentscope-service 并自述"比 builder 更单一用途" |
| documentation（49 个单类示例） | `agentscope-examples/documentation` | 否 | 纯 core/harness API 教学（ReActAgent/HarnessAgent builder 直用） |
| agui | `agentscope-examples/agui` | 否 | AG-UI 协议接入示例（agentscope-core + dashscope） |
| agentscope-copilotkit | `agentscope-examples/agentscope-copilotkit` | 否 | AgentScope × CopilotKit v2（AG-UI）端到端示例 |

---

## 二、agentscope-service 模块概览

### 2.1 Maven 结构与坐标

`agentscope-service/pom.xml`：

- 坐标：`io.agentscope:agentscope-service`，`packaging=pom`（聚合器，父级为 `io.agentscope:agentscope-parent`）。
- 描述（第 34 行）：**"Four-plane agent builder platform: gateway + control + data + scheduler, built on HarnessAgent"**。
- 四个子模块（第 37-40 行）：
  - `io.agentscope:service-common` —— 共享库：agentscope.json 解析（`io.agentscope.builder.runtime.config` 包）+ builder Web 平台（catalog、managed、persistence/JPA、share、workspace、auth、coord 等，约 100 个类）；
  - `io.agentscope:service-gateway` —— Spring Cloud Gateway 流量入口（`spring-cloud-starter-gateway-server-webflux`）；
  - `io.agentscope:service-dataplane` —— 数据面（入口 `io.agentscope.builder.DataApp`）；
  - `io.agentscope:service-scheduler` —— 调度面（入口 `io.agentscope.builder.SchedulerApp`）。
- 技术栈：Spring Boot 4.0.4 + Spring Cloud 2025.1.2 + JPA（父 pom 属性与 dependencyManagement）。`maven.deploy.skip=true`——**不发布到仓库**，只能源码级使用。
- 另含非 Java 组件：`aistio/`（Go 语言控制面 + Helm + UI）、`frontend/`（平台前端）、`docker/`、`docs/`（controlplane、managed_agents 文档）。

### 2.2 提供什么能力

`agentscope-service/README.md`（第 3 行起）定位：**"An Agent control and orchestration platform built on AgentScope Harness — a unified control plane for the enterprise"**，三大能力：

1. **Control Plane（组件名 aistio）**：企业全量 Agent 注册/发现/分布式协调，兼容 AgentScope、LangChain、ADK、Claude/Qoder 等运行时；Dashboard 提供 agent 在线状态、session、token 消耗观测与上下文压缩等操作。
2. **Managed Agents（低代码托管）**：README 原文 "Managed Agents evolve from the `agentscope-builder` platform"——由原 builder 平台演进的 SaaS 式 Agent 定义与托管执行，Brain（推理，平台托管 Harness）/Hands（工具执行，用户自控 sandbox）分离，强调事件持久化、状态可重建、HITL 可暂停恢复。
3. **Agent Teams**：把注册进来的各路 Agent 编成可运营的协作单元（任务认领、计划审批、成员唤醒）。

数据面职责（`service-dataplane/src/main/java/io/agentscope/builder/DataApp.java` javadoc，第 18-47 行）：托管 `/api/sessions/{id}/events/**`（事件日志 + SSE）、`/api/environments/{id}/work/**`、以及 **`/agentscope/**`——aistio 数据面 HTTP 合约 + 向 aistiod 自注册**；"The data plane instantiates `HarnessAgent` instances from the version snapshot pinned on each session row"。

### 2.3 agentscope.json 声明式配置（与 examples 关联最密切的部分）

`service-common/src/main/java/io/agentscope/builder/runtime/config/AgentscopeConfig.java`（第 26-28 行）：

> "Root document for `${cwd}/.agentscope/agentscope.json`." / "Shape is intentionally similar to OpenClaw's top-level config: a `main` entry id, an `agents` map keyed by agent id, and an optional `channels` map keyed by channel id."

顶层键：`$schema`、`main`、`agents`（`AgentConfigEntry`）、`channels`（`ChannelConfigEntry`）、`session`（`SessionLifecycleConfig`）。同包还有 `BindingConfigEntry`、`MarketplaceConfigEntry`、`SkillRepositoryConfigEntry`、`SkillRepositorySupport`、`ChannelTypeRegistry`，共九个文件。

**值得注意**：在 agentscope-service 内部，`AgentscopeConfig` 除被 `ChannelConfigEntry` 的 javadoc 引用外**几乎无消费方**（grep 全模块仅此二文件）——service v2 的托管装配走 JPA 目录 + `SessionAgentBuildSpec` 快照，agentscope.json 解析更像 builder 时代保留的机制，**真正的活跃消费者在 examples 的三个 fork 副本里**。

---

## 三、"命中"示例逐个详解（宽口径三类关联）

### 3.1 agentscope-paw —— 关系最深：源码 fork + aistio 运行时对接

**路径**：`agentscope-examples/agents/agentscope-paw`；入口 `io.agentscope.claw2.Claw2App`（pom 第 169 行 `mainClass`），Spring Boot（WebFlux）+ React 前端。单用户本地助手。

**依赖**（pom.xml）：`agentscope-harness`、`agentscope-extensions-model-dashscope`、`agentscope-extensions-aistio`、五个 IM channel 扩展、`agentscope-extensions-skill-git-repository`、`agentscope-extensions-nacos-skill` 等——**无任何 service 模块**。

**证据一：配置解析是 service-common 的源码 fork**。`src/main/java/io/agentscope/claw2/runtime/config/` 下九个文件与 service-common 的 `io/agentscope/builder/runtime/config/` 一一对应。`diff AgentscopeConfig.java` 结果：仅差包名（`builder` → `claw2`）、个别 javadoc 措辞与 paw 新增的 `marketplaces` 块注释；paw 版注释仍保留 "Programmatic `ClawBootstrap.Builder#mainAgent(String)` overrides this"。同构文件包括 `AgentConfigEntry`（含 `ToolsConfig`/`IdentityConfig`/`GroupChatConfig`/`SandboxConfig`/`SkillsConfig` 内部类）、`ChannelTypeRegistry`、`SkillRepositorySupport` 等。

**证据二：历史命名的直接残留**：

- 测试类包名就叫 `io.agentscope.builder`（`src/test/java/io/agentscope/builder/BuilderAppContextLoadTest.java:16`、`BuilderBootstrapSmokeTest.java:16`），但 import 的是 `io.agentscope.claw2.Claw2App` / `io.agentscope.claw2.runtime.ClawBootstrap`——**这是本次调研中 `io.agentscope.builder` 字符串在 examples 的全部出现位置**。
- Spring 装配类沿用旧名：`src/main/java/io/agentscope/claw2/web/config/BuilderConfig.java`（注释："Spring Boot configuration for agentscope-claw — the local single-user assistant"，负责装配 `ClawBootstrap`、`ChatUiChannel`、`TranscriptStore`、aistio `AgentScopeAdapter` 等）。
- 项目根有 `builder.md`，开篇即说明 paw 是 builder 的单机化改造："agentscope-paw is a single-user local assistant. Everything that used to be scoped per-user / per-tenant is now scoped to the machine"，并列出已移除的 builder 能力（README 第 637 行引用）。

**证据三：运行时与 service 部署协同（aistio BYO，非 Maven 依赖）**：

- `src/main/resources/application.yml:2`："Default 8090 so paw coexists with agentscope-service gateway (:8080)."
- `application.yml:31-37`："Flip enabled to true when running beside agentscope-service (scripts/dev-up.sh) so Operate can discover this agent... control :8081, shared internal token, contract HTTP :18090."——开启 `claw.aistio.enabled` 后 paw 经 `agentscope-extensions-aistio` 实现 `/agentscope/*` 合约并自注册到 service 的 aistiod 控制面。
- README 第 582 行："paw is the reference BYO data plane for verifying session history with [aistio](../../../agentscope-service/aistio/)"——paw 是 service 控制面的**官方参考自带数据面**；第 623 行链接到 `agentscope-service/aistio/docs/zh/controlplane/wrapper-transcript-contract.md` 转写合约。
- README 第 18-21 行：需要多租户/隔离时去找 "sister projects [agentscope-service]... and [agentscope-dataagent]"。

**装配用法关键代码**（`BuilderBootstrapSmokeTest.java`，展示 paw 的声明式+编程式混合装配入口 `ClawBootstrap`）：

```java
try (ClawBootstrap bootstrap =
        ClawBootstrap.builder()
                .skipConfigFile(true)          // 跳过 agentscope.json，纯编程式
                .cwd(tempDir)
                .model(model)
                .configureAgent("main", b -> b.name("main").description("main"))
                .mainAgent("main")
                .build()) {
    ChatUiChannel chat = bootstrap.chatUiChannel();
    Msg reply = chat.send("Hello from test").block();
}
```

`ClawBootstrap`（`claw2/runtime/ClawBootstrap.java`）内部生产 `Map<String, HarnessAgent>`（第 95 行）并支持 `builder().agent(agentId, HarnessAgent)` 预置或 `configureAgent(id, Consumer<HarnessAgent.Builder>)` 编程定制（第 496、462-465 行）——即 paw 的 fork 版"builder runtime"。

**结论**：paw 用 service 的**设计**（agentscope.json 约定 + Bootstrap 装配 + 目录布局），不用 service 的**代码**；在部署形态上可选择性接入 service 控制面（aistio 合约）。

### 3.2 agentscope-dataagent —— 自带 agentscope.json 的完整解析与热更新

**路径**：`agentscope-examples/agents/agentscope-dataagent`；Spring Boot 入口 `io.agentscope.dataagent.web.DataAgentApp`（第 39 行 `main`）。定位（README_zh）：每位数据分析师一个私有进化型 SQL/报表 agent，多人隔离 workspace + 审批制共享能力市场，sandbox 生命周期由应用方掌控。

**依赖**（pom.xml）：`agentscope-harness`、dashscope 模型扩展、dingtalk 通道、git/nacos skill 仓库、redis 扩展、JPA + MySQL/PG/H2、JWT 等——**无 service 模块**。

**agentscope.json 及其消费**：仓库内唯一示例文件 `src/main/resources/workspace-template/agentscope.json`：

```json
{
  "$schema": "https://agentscope.io/schema/agentscope.json",
  "main": "data-agent",
  "agents": {
    "data-agent": {
      "name": "Data Agent",
      "description": "Tenant-isolated data-analysis assistant...",
      "workspace": ".agentscope/workspace",
      "maxIters": 20
    }
  },
  "channels": { "chatui": { "defaultAgentId": "data-agent", "dmScope": "MAIN" } }
}
```

消费链（全部在 `io.agentscope.dataagent` 包内）：

- `web/config/DataAgentConfig.java:169` 起 `@Bean builderBootstrap(...)`：**"Assembles the `DataAgentBootstrap`, loading agent config from `agentscope.json` and starting the `ChatUiChannel` for per-user isolated sessions"**——`DataAgentBootstrap.builder().cwd(cwd)` + `Optional<Model>` 注入 + `AgentStateStore` 选择（缺省回落 `InMemoryAgentStateStore`，生产建议接 `agentscope-extensions-redis`）。
- `web/config/DataAgentConfig.java:385`：不存在时**自动生成**最小 `~/.agentscope/dataagent/agentscope.json`。
- `web/api/BindingPersistence.java:45`：加载-修改-原子回写 agentscope.json 的 `channels` 映射，实现管理端编辑通道绑定并应用到运行中 registry（`AgentBindingController.java:55`）。
- `web/catalog/AgentCatalogService.java:50-56`：把 agentscope.json 里的 **global** agent 与用户自定义（DB 存储）agent 合并成目录，动态实例化——即"声明式全局定义 + 数据库用户定义"两层目录。

`runtime/config/AgentscopeConfig.java` 同样是九文件 fork（diff 与 paw 版仅差包名 `dataagent` 与 javadoc）。

**结论**：dataagent 是三个应用里把 agentscope.json 声明式装配**用得最重**的（读取、生成、热更新、双层目录合并），但解析器是自己的 fork，与 service 模块零编译期关系。

### 3.3 agentscope-codingagent —— 同构 fork + 文档互链

**路径**：`agentscope-examples/agents/agentscope-codingagent`；双入口：`CodingAgentApplication`（Spring Boot，GitHub 代码评审服务）与 `CodingChatCli`（第 66 行 `main`，CLI 形态）。

**依赖**（pom.xml）：`agentscope-harness`、openai/anthropic/dashscope 模型扩展、dingtalk/feishu 通道、SQLite、tink、okhttp、JWT、actuator + prometheus + OTel——**无 service 模块**。

**证据**：

- `src/main/java/io/agentscope/harness/coding/config/AgentscopeConfig.java`（及 `ChannelConfigEntry.java`）与 paw/service 版同构（diff 仅包名 `io.agentscope.harness.coding` 与个别 javadoc）；装配入口 `CodingBootstrap`（注释保留 `CodingBootstrap.Builder#mainAgent(String)` 覆盖 agentscope.json `main` 的语义）。
- README 第 27-29 行："Compared to its siblings: it's more locked-down than [claw]... and more single-purpose than **[builder]** (Builder hosts arbitrary agents). codingagent is the one that **writes the code**."，第 101 行 `[builder]: ../../../agentscope-service/`——codingagent 官方自述与 agentscope-service 是"三兄弟"关系（claw=本机、builder=任意 agent 托管平台、codingagent=专写代码）。

**结论**：codingagent 复用同一套 fork 约定，README 明确把自己定位为 service（builder）的更窄用途兄弟项目。

### 3.4 无关联示例（统计口径）

以下模块经 grep 三项证据（pom 依赖 / builder import / agentscope.json）均无命中，不逐个展开：

- `documentation`：49 个 `*Example` 单类（`find ... -name "*Example*.java" | wc -l` = 49），按 quickstart/tool/middleware/model/skill/hitl/state/streaming/mcp/harness 等分类，依赖仅 `agentscope-harness` + dashscope 扩展。
- `agui`：3 个 Java 文件（`AguiExampleApplication` + config/tools），依赖 `agentscope-core` + dashscope。
- `agentscope-copilotkit`：AgentScope × CopilotKit v2 / AG-UI 端到端示例（多路由 Runtime、线程、共享状态、生成式 UI、HITL），依赖 `agentscope-core`。

---

## 四、"用 vs 不用"对照与 service 的定位

### 4.1 比例概览

按 Maven 模块计：**使用 service 模块 0/6；不使用 6/6（100%）**。按文件粒度：documentation 49 个教学示例 + agui 3 类 + copilotkit 若干类全部直用框架 API；三个完整应用则**复刻**service 前身的配置机制。

### 4.2 同一件事的两种写法（真实对照）

**写法 A：纯代码 Builder（examples 主流，documentation/quickstart/BasicChatExample.java 第 50-56 行）**：

```java
ReActAgent agent =
        ReActAgent.builder()
                .name("Assistant")
                .sysPrompt("You are a helpful AI assistant...")
                .model("dashscope:qwen-plus")
                .toolkit(new Toolkit())
                .build();
```

特征：plain `main()`、无配置文件、一切装配写死在代码里，`mvn exec:java` 直接跑。

**写法 B：Bootstrap + agentscope.json 声明式（三个完整应用的 fork 模式）**：

- Spring Boot 应用启动 → `@Configuration`（如 paw `BuilderConfig` / dataagent `DataAgentConfig`）装配 `XxxBootstrap` bean → Bootstrap 读取 `${cwd}/.agentscope/agentscope.json`（或编程式覆盖：`.skipConfigFile(true)`、`.configureAgent(...)`、`.mainAgent(...)`）→ 产出 `Map<String, HarnessAgent>` + `ChatUiChannel` 等通道 → HTTP/SSE 对外服务。
- agent 的名字、描述、workspace、maxIters、channels（defaultAgentId、dmScope）都声明在 JSON；代码只处理模型密钥、sandbox、持久化等环境性配置。

两种写法的分界很清晰：**小示例用 A，"产品级"应用用 B 的自研版**——而 B 的原版（service-common 的 builder runtime）在 examples 中反而**无人通过依赖使用**，连官方示例都选择 fork 而非引入（推测原因：`agentscope-service` 不发布构件（`maven.deploy.skip=true`），且 service-common 拖带整个托管平台：JPA 实体、auth、coord、managed 服务约百个类，只为拿配置解析引入代价过高）。

### 4.3 service 模块的定位（什么时候需要它）

综合 `agentscope-service/README.md` 与 `docs/v2/zh/blogs/agentscope-service-release.md`（官方发布博客，三大能力与 README 一致）：

- **它不是"装配库"，而是一套企业平台**：Go 写的 aistio 控制面 + Java 四平面（gateway/dataplane/scheduler + service-common）+ 前端 Dashboard，管的是"企业里所有 Agent 的注册、观测、托管、组队"。
- **接入它的标准方式不是 Maven 依赖，而是运行时合约**：任何 Agent 应用实现 aistio `/agentscope/**` 数据面合约（Java 侧用 `agentscope-extensions-aistio` SDK，属 extensions 体系）自注册即可（paw 即此模式）；或干脆把 Agent 定义放上去，用它的 Managed Agents 托管执行。
- 官方演进叙事（`docs/v2/zh/blogs/agentscope-v1-builder.md`）：1.x 时代 Claw（单机）与 **Builder**（多人企业版，"OpenClaw 的分布式版本"）同时发布、同为 Harness 的落地案例；v2 中 Builder 演进为 agentscope-service 的 Managed Agents，单机版演化为 paw。examples 里的三个应用正是这条演化线的三支后裔（paw=Claw 后裔、dataagent=多租户分支、codingagent=专用分支），各自带着 builder 的配置约定 fork。

---

## 五、对 NexAI 的参考建议

背景：NexAI 正在重建 `nexai-module-ai`（DDD），当前规划为**纯代码 Builder 装配**（Java 内 `ReActAgent`/`HarnessAgent` builder + agentscope-java 依赖）。本次调研的启示：

1. **不建议引入 agentscope-service 模块做声明式装配。**
   - 它不发布 Maven 构件（`agentscope-service/pom.xml` 中 `maven.deploy.skip=true`），引入只能源码内嵌或私仓自建，升级成本高；
   - service-common 是"整个托管平台"而非可拆的配置解析库（JPA/auth/coord/managed 强耦合，约百个类）；
   - **官方示例全体（6/6）都不依赖它**，包括最应该用它的大型应用——这是最强的实践信号。

2. **"声明式 agent 规格"有更轻的官方模式可抄：dataagent 模式。**
   NexAI 若需要"平台上可编辑的 agent 定义"（管理后台配置 agent 规格、运行时实例化），对齐 `agentscope-dataagent` 的两层结构即可：全局/默认定义（文件或 DB）+ 用户级定义（DB），由目录服务合并后用 `HarnessAgent.builder()` 动态实例化（参考 `web/catalog/AgentCatalogService.java` 与 `DataAgentConfig.builderBootstrap()`）。这与 NexAI 现有 DDD 规划（agent 规格存 DB、gateway 适配 agentscope）天然契合，且不必引入 agentscope.json 文件约定——若想保留文件形式，fork 那九个 config 类（paw/dataagent/codingagent 均如此）也是官方默认姿势。

3. **将来若要企业控制面能力，走 aistio 合约而非依赖。**
   NexAI 的 agent 运行时若需要被统一观测/管控（多 agent 注册、session 干预、token 统计），官方路径是实现 `/agentscope/**` 数据面合约 + 自注册（`agentscope-extensions-aistio`，属 extensions 体系、正常发布依赖），把 agentscope-service 当**部署期的旁路平台**，而不是编译期依赖。这对应 NexAI 架构中 gateway 适配器（`infrastructure/gateway/`）的一个可选扩展点。

4. **一个折中观察**：agentscope.json 的 schema（`main`/`agents.{id}.name|description|workspace|maxIters`/`channels.{id}`）本身很薄（本质是一个 agent 目录 + 通道路由表）。NexAI 完全可以用自己的 DDD 聚合（AgentSpec）承载同等信息，序列化格式自定义——不必绑定该文件格式，但**schema 字段清单值得参考**（它就是官方认为"声明一个 agent 所需的最小集"）。

---

## 六、来源清单（均为 agentscope-java 仓库内一手材料）

| # | 来源（相对 agentscope-java 根） | 用途 |
|---|---|---|
| 1 | `agentscope-service/pom.xml:22-40` | 模块坐标、四平面描述、子模块清单 |
| 2 | `agentscope-service/README.md`（第 3 行起）、`README_zh.md` | service 定位、三大能力、Managed Agents 源起 builder |
| 3 | `agentscope-service/service-common/.../builder/runtime/config/AgentscopeConfig.java:26-28` 等 9 文件 | agentscope.json schema 原版 |
| 4 | `agentscope-service/service-dataplane/.../builder/DataApp.java:18-47` | dataplane 职责、`/agentscope/**` 合约 |
| 5 | `agentscope-examples/pom.xml:35-40` | 6 个示例模块清单 |
| 6 | 三个示例的 `pom.xml`（paw 第 169 行 mainClass；dataagent、codingagent 依赖段） | 无 service 依赖的证据 |
| 7 | `agentscope-examples/agents/agentscope-paw/src/test/java/io/agentscope/builder/{BuilderAppContextLoadTest,BuilderBootstrapSmokeTest}.java` | examples 中 `io.agentscope.builder` 的全部出现处；ClawBootstrap 用法摘录 |
| 8 | `agentscope-examples/agents/agentscope-paw/src/main/resources/application.yml:2,31-37` | 与 service 共存部署、aistio 自注册 |
| 9 | `agentscope-examples/agents/agentscope-paw/README.md:18-21,27-29,582,623,637`、`builder.md` | 姊妹项目关系、BYO 数据面、fork 溯源 |
| 10 | `agentscope-examples/agents/agentscope-dataagent/src/main/resources/workspace-template/agentscope.json` | examples 唯一 agentscope.json |
| 11 | `agentscope-examples/agents/agentscope-dataagent/.../web/config/DataAgentConfig.java:55,95,169,385`、`web/api/BindingPersistence.java:45`、`web/catalog/AgentCatalogService.java:50-56` | dataagent 自解析/热更新/双层目录 |
| 12 | `agentscope-examples/agents/agentscope-codingagent/README.md:27-29,101`、`.../harness/coding/config/AgentscopeConfig.java` | codingagent 与 builder 的兄弟定位、同构 fork |
| 13 | `agentscope-examples/documentation/.../quickstart/BasicChatExample.java:50-56` | 纯代码 Builder 对照写法 |
| 14 | `docs/v2/zh/blogs/agentscope-v1-builder.md`、`docs/v2/zh/blogs/agentscope-service-release.md` | Claw/Builder 同源发布、service 官方定位 |
| 15 | `.qoder/repowiki/zh/content/示例与教程/实战案例/Paw智能代理.md:28,300` | "需要多租户/分布式建议迁移 agentscope-service"的佐证 |
| 16 | grep 统计命令（可复核）：<br>① `grep -rl "agentscope-service" agentscope-examples --include=pom.xml` → 0 命中<br>② `grep -rl "service-common\|service-dataplane\|service-gateway\|service-scheduler" agentscope-examples --include=pom.xml` → 0 命中<br>③ `grep -rln "io\.agentscope\.builder" agentscope-examples --include="*.java"` → 仅 paw 两个测试文件（包声明，非 import）<br>④ `find agentscope-examples -name "agentscope.json"` → 仅 dataagent（源 + target 副本） | 穷尽判定 |

**repowiki 说明**：`.qoder/repowiki/zh` 中没有 agentscope-service 的专门章节（命中均为 Paw/智能编码助手实战案例里的迁移指引），service 的系统性文档在 `agentscope-service/README(_zh).md`、`agentscope-service/docs/`（controlplane、managed_agents）与 `docs/v2/zh/blogs/` 三处。
