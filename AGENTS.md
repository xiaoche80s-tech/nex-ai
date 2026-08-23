# AGENTS.md

本仓库中 ZCode Agent 的工作指南。

## 语言规则

- 会话中产生的所有文档、思考链路、代码注释一律使用中文。
- 与用户的交流一律使用中文。

## 项目概况

NexAI —— 基于 ruoyi-vue-pro（芋道/yudao）fork 并重命名的企业级开发脚手架：groupId `com.gkht.ai`、基础包名 `com.gkht.ai.nexai`、配置前缀 `nexai.*`。本仓库为精简版：业务模块为 `system` 与 `infra`（其余 yudao 模块 bpm、pay、mall、crm 等已在根 `pom.xml` 中注释）；AI 平台模块 `nexai-module-ai` 旧实现因存在问题已被整体移除，**正在重新开发**（见目录结构与构建注意）。技术栈：Java 25、Spring Boot 4.1、PostgreSQL + MyBatis Plus、Redis + Redisson。管理后台前端位于 `nexai-ui/`（Vue 3 + Vite + TypeScript + Element Plus + UnoCSS，pnpm）。

本工作区是 git 仓库，远程为 `origin` → https://github.com/xiaoche80s-tech/nex-ai （main 分支）。注意：`application-local.yaml`（含真实凭据）已被 `.gitignore` 排除，克隆者需从 `application-local-example.yaml` 复制并填写。

## 目录结构

- `nexai-dependencies/` —— Maven BOM；所有第三方版本统一在此管理。
- `nexai-framework/` —— 自研 Spring Boot Starter（`nexai-common` 提供共享的 `CommonResult`/`PageResult`、错误码体系、工具类；`nexai-spring-boot-starter-*` 覆盖 web、security、mybatis、redis、tenant、test 等）。
- `nexai-module-system/`、`nexai-module-infra/` —— 业务模块。
- `nexai-module-ai/` —— AI 平台模块（agent 规格/skill/模型/会话调试等聚合，DDD 布局，agentscope 接入），**重新开发中，当前不存在**。旧实现在提交 b2ab4a9 中被有意整体删除（连同前端页面 `nexai-ui/src/views/ai/`）；`docs/research/` 下的 agentscope 调研结论仍是有效输入。
- `nexai-server/` —— 装配应用；在此启动 `NexaiServerApplication`。
- `nexai-ui/` —— 前端 SPA。
- `sql/postgresql/` —— 完整数据库初始化脚本 `ruoyi-vue-pro.sql` 与 `quartz.sql`（另有 `sql/mysql/`、`sql/dm/` 变体）。
- `script/docker/` —— docker-compose（MySQL 8 + Redis 6，但应用本身默认使用 PostgreSQL）。

## 常用命令

后端（JDK 25 + Maven 3.9+；根 pom 已预配置华为云/阿里云镜像源）：

```bash
mvn -pl nexai-module-system -am package   # 聚焦构建：单个模块及其依赖（优先于全量构建）
mvn clean package -DskipTests             # 全量构建
mvn test -pl nexai-module-infra           # 单模块测试
```

从 `nexai-server` 启动服务端：profile 为 `local`（默认），端口 48080。`application-local.yaml` 指向共享的远端 Postgres/Redis（`1p.inas.club`，账号密码见该文件），因此无需本地数据库。

**构建注意（实测踩坑）**：`nexai-module-ai` 重新开发落地新 pom 时，需放开根 `pom.xml` 中被注释的 `<module>` 登记、并在 `nexai-server` 的 pom 添加依赖——缺任一处该模块不参与构建/装配（用 `mvn validate` 快速确认）。其余已知坑：打 `nexai-server` 的 fat jar 时 `-am` 不可省——省略会从 `~/.m2` 解析旧版业务模块 jar 嵌进 fat jar（症状：新接口 404/501 兜底）。且旧 target 产物存在时 Maven 增量构建可能误判 up-to-date（server 模块 0.2 秒"构建完成"即为信号），改过模块代码后先 `mvn -pl nexai-server clean` 再打包。遇到「NoSuchMethod/找不到类文件/同名类不兼容」等无源码依据的怪编译错误，先 `mvn clean` 重建再排查。

前端（在 `nexai-ui/` 内执行，Node ≥ 20.19，pnpm ≥ 8.6）：

```bash
pnpm i
pnpm dev        # vite --mode env.local，端口 5173，请求 http://localhost:48080/admin-api
pnpm ts:check   # vue-tsc 类型检查
pnpm lint       # eslint + stylelint + prettier 检查模式；lint:eslint / lint:format / lint:style 自动修复
```

## 开发流程规则

**用户已确立：每次会话遵循 karpathy-guidelines skill**——先想后写（显式陈述假设）、最小实现（不做投机性设计）、外科手术式修改（每行改动可追溯到需求）、目标驱动验证（先定可验证的成功标准再动手）。

**开发一个新功能前，先查看以下资料是否已有相关实现或调研结论**，避免重复设计与实现：

1. 本仓库 `docs/research/` —— 已完成的技术调研文档。
2. `/Users/jerry/agent-project/agentscope-java/.qoder/repowiki` —— agentscope-java 框架 wiki（`zh/` 中文文档、`knowledge/` 知识库）。
3. `/Users/jerry/agent-project/agentscope-java/agentscope-examples` —— agentscope-java 官方示例代码（`agents/`、`agui/`、`documentation/` 等）。

发现相关实现时优先对齐其模式；确认无相关实现后再自行设计。

## 后端开发约定

### 架构总则：新代码基于 DDD（Clean Architecture + 六边形）

**用户已确立：后端新功能一律按 DDD 开发**（参考 skill `clean-ddd-hexagonal`，采用其 Convention B「按聚合优先」布局）。

适用判定：**新增聚合一律走 DDD 结构**——无论位于新建模块还是 `system`/`infra` 等存量模块（在模块包根下新建 `{aggregate}/` 目录，与存量芋道包并存互不干扰）；**存量芋道包内的既有文件只在原结构上修改**，不搬迁、不重构成 DDD。

在 `nexai-module-<name>` 的 `com.gkht.ai.nexai.module.<name>.` 下：

```
{aggregate}/                     # 一个聚合一个目录，按业务命名（如 agent、knowledge）
├── domain/                      # 领域层：零框架依赖（禁止 import MyBatis/Spring Web 等）
│   ├── model/                   # 聚合根 + 实体（充血模型，业务行为写在实体上）
│   ├── valueobject/             # 值对象（不可变，无 setter，按值判等）
│   ├── event/                   # 领域事件（过去时命名，如 AgentPublishedEvent）
│   ├── exception/               # 聚合内异常
│   ├── gateway/                 # 外部系统端口接口（如 agentscope 连通性探测）；隔离第三方类型，出参入参用领域类型
│   └── repository/              # Repository 接口（端口；一个聚合一个，按聚合不按表）
├── application/                 # 应用层：用例编排，事务边界在此（@Transactional）
│   ├── command/                 # 命令 DTO（写）——即 HTTP 写接口的请求体
│   ├── query/                   # 查询 DTO（读）——即 HTTP 读接口的请求参数
│   ├── dto/                     # 出参 DTO——即 HTTP 接口的响应体
│   └── service/                 # 应用服务（只编排，不写业务规则；实现 api/ 接口）
└── infrastructure/              # 基础设施层：适配器
    ├── dataobject/              # MyBatis Plus DO（贫血，继承 BaseDO/TenantBaseDO）
    ├── mapper/                  # Mapper 接口（加 @Mapper 即可被发现，包名不限）
    ├── converter/               # DO ↔ 领域模型、领域模型 ↔ 出参 DTO 转换（MapStruct）
    ├── gateway/                 # 外部系统端口实现（实现 domain/gateway 接口，如 agentscope 适配器）
    └── repository/              # Repository 实现（实现 domain/repository 接口）

interfaces/                      # 入口层
├── controller/admin/            # REST 控制器（URL 挂 /admin-api，机制见下）
└── controller/app/              # 同理挂 /app-api
api/                             # 模块对外契约：跨模块调用接口 + Api DTO；实现位于 application/service/
shared/                          # 跨聚合共享：util/、constant/、enums/（仅限 2+ 聚合使用）
framework/                       # 模块内 Spring 配置
```

依赖方向与关键规则：

- 依赖只向内：`interfaces → application → domain`；`infrastructure → domain`（实现端口）；`api → application`。domain 层只依赖 JDK 与纯工具库（如 hutool-core、slf4j-api、reactor-core——运行时端口返回 `Flux` 属此类，与 slf4j 同级），禁止 MyBatis、Spring、`nexai-common` 框架设施（`CommonResult`、`ServiceException` 等）——能脱离 UI 和数据库单测领域逻辑即边界正确。
- 充血模型：业务规则进实体/值对象；应用服务只做编排。出现"实体只有 getter/setter、逻辑全在服务"即为贫血反模式，需回移。
- Controller 禁止直接调 Repository，必须经应用服务。出入参**直接复用 application 层的 command/query/dto，不另设控制器旁 VO**（芋道 `*ReqVO/*RespVO` 惯例仅存在于存量模块）；简单列表查询可由应用服务经 Mapper 直查转 DTO，无需绕经领域模型（轻量读写分离即可，勿引入完整 CQRS/Event Sourcing，除非确有诉求）。
- 聚合边界：同一事务内必须一致的属同一聚合；跨聚合只读可直接调对方 Repository 查询，**修改必须走领域事件**（最终一致），禁止跨聚合直接 save；外部只引用聚合根 ID。
- 领域事件落地：`domain/event/` 中为纯 POJO（过去时命名），由聚合根登记产生；应用服务在事务内经 Spring `ApplicationEventPublisher` 发布，监听方同事务处理或 `@TransactionalEventListener(AFTER_COMMIT)` 异步处理；确需跨实例/可靠投递才走 infra 的 Redis MQ（`mq/**`），勿默认引入 outbox/事件溯源。
- 领域异常落地：`domain/exception/` 中为纯 Java 异常、携带业务语义；由 application 层捕获并转 `ServiceException` + 错误码（注册于模块 `enums/ErrorCodeConstants.java`），交全局异常处理器渲染 `CommonResult`——domain 不 import `ServiceException`。
- DO 与领域模型严格分离，经 converter 转换（converter 亦负责领域模型 ↔ 出参 DTO），禁止 DO 出现在 domain/application 层。
- 跨模块调用统一走目标模块 `api/` 接口（存量模块的 `api/**` 惯例继续有效）；模块**内**的跨聚合协作才适用上述聚合规则，模块**间**不得绕过 api 直接调对方的 Repository 或应用服务。

与芋道框架的衔接（已验证机制，保持使用）：

- URL 前缀由包通配符决定：web starter 的 `WebProperties` 默认按 `**.controller.admin.**` 挂 `/admin-api`、`**.controller.app.**` 挂 `/app-api`——所以 DDD 模块的 REST 层包名必须包含 `controller/admin`（或 `controller/app`）段。
- Mapper 扫描：mybatis starter 的 `@MapperScan` 按 `nexai.info.base-package` + `@Mapper` 注解扫描，包名不限，`infrastructure/mapper/` 可直接生效。
- Controller 仍返回 `CommonResult<T>`；出入参直接使用 application 层的 command/query/dto（存量模块继续用控制器旁 `vo/` 的 VO 惯例，两套不混用）。
- 错误码仍注册在模块 `enums/ErrorCodeConstants.java`。
- 新建 `nexai-module-<name>` 时：须在根 `pom.xml` 的 `<modules>` 登记，并在 `nexai-server` 的 pom 中添加依赖——缺任一处该模块不参与构建/装配。

横切规则（新旧代码一致）：

- SaaS 多租户已启用（`nexai.tenant.enable`）：新表默认租户过滤，除非加入 `nexai.tenant.ignore-tables`。
- 逻辑删除通过 `deleted` 字段：1 = 已删除，0 = 存活。
- Lombok + MapStruct 注解处理器在根 pom 的 `annotationProcessorPaths` 中装配（含 `lombok-mapstruct-binding`）；新增处理器需修改该列表。
- 禁止使用 BeanUtils 做对象转换（Spring `BeanUtils`、Apache `BeanUtils`、hutool `BeanUtil`，以及 `nexai-common` 封装的 `BeanUtils.toBean()` 等运行时反射拷贝均含）：同名异义字段会静默错配且无法在编译期暴露。新写代码一律用 MapStruct 编译期生成——DDD 模块放 `{aggregate}/infrastructure/converter/`，存量模块放 `convert/`；存量代码不做专项迁移，仅当改动到某处转换时顺手替换为 MapStruct。
- 单元测试继承 `nexai-spring-boot-starter-test` 中的基类：`BaseDbUnitTest`（H2 内存库，profile `unit-test`，每个测试后清理 DB）、`BaseDbAndRedisUnitTest`、`BaseRedisUnitTest`、`BaseMockitoUnitTest`。DB 类测试无需外部服务。两个例外：① DDD 的 domain 层用纯 JUnit 直接构造实体测试，不继承任何基类；② **用户已确立**：依赖 PG 专属能力（agentscope 状态存储、PG 方言 DDL）的运行时链路测试经 `nexai-module-ai` 测试源集的 `BasePgDbAndRedisUnitTest`（profile `pg-test`）直连真实 PostgreSQL（连接信息 `application-pg-test.yaml`，环境变量可覆盖；测试数据落专用租户、按租户清理）——该基类随旧实现一并移除，`nexai-module-ai` 重新开发时此约定应随新模块重建。

### 存量芋道分层（维护 system/infra 时遵循）

每个 `nexai-module-*` 在 `module/<name>/` 下使用相同的包结构：

- `controller/admin/**`、`controller/app/**` —— REST 接口。URL 前缀由包名通配符决定（机制同上）：`admin` 控制器挂在 `/admin-api` 下，`app` 控制器挂在 `/app-api` 下（见 web starter 中的 `WebProperties`）。控制器返回 `CommonResult<T>`，出入参使用 VO（`*SaveReqVO`、`*PageReqVO`、`*RespVO`），存放在控制器旁的 `vo/` 子包中。
- `service/**` —— 业务逻辑，`XxxService` + `XxxServiceImpl`。
- `convert/**` —— MapStruct 转换器（VO ↔ DO）。
- `dal/dataobject/**` —— MyBatis Plus 实体（`*DO`）；`dal/mysql/**` —— Mapper 接口（包名为芋道历史遗留；数据库实为 PostgreSQL）；`dal/redis/**` —— Redis DAO。
- `api/**` —— 模块对外的公开接口；跨模块调用走 `api` 接口，实现位于 `service`。
- `enums/ErrorCodeConstants.java` —— 模块错误码注册表；新增错误码写在这里。
- `framework/**` —— 模块内配置；`job/**` —— Quartz 定时任务；`mq/**` —— 基于 Redis 的消息。

## 前端约定（nexai-ui/）

- Element Plus 组件与常用 hooks（`useI18n`、`useMessage`、`useTable`、`useCrudSchemas`）、`required` 校验、`DICT_TYPE` 字典常量均由 unplugin 自动导入（配置见 `build/vite/index.ts`），无需手动 import；类型声明生成在 `src/types/auto-imports.d.ts` 与 `auto-components.d.ts`。
- `src/components/**` 下的自定义组件同样自动注册，直接使用即可。
- 样式使用 UnoCSS 原子类（`uno.config.ts`）；SVG 图标放 `src/assets/svgs`，以 `icon-[dir]-[name]` 形式引用。
- 界面文案接入 vue-i18n（`src/locales/`），新文案写入语言包。

## Agent skills

### Issue tracker

工单以本地 markdown 文件跟踪于 `.scratch/<feature>/`（已 gitignore）。见 `docs/agents/issue-tracker.md`。

### Triage labels

使用五个默认角色标签：needs-triage / needs-info / ready-for-agent / ready-for-human / wontfix。见 `docs/agents/triage-labels.md`。

### Domain docs

单上下文布局：根 `CONTEXT.md` + `docs/adr/`，由 `/domain-modeling` 惰性创建。见 `docs/agents/domain.md`。

## 注意事项

- 上游芋道品牌信息在多处残留（pom `description`、Swagger 标题、`ruoyi-vue-pro.sql`）；未经要求不要迁移。
- `application.yaml` 携带上游遗留的大段 AI 厂商、工作流、支付配置 —— 大多已禁用或由环境变量驱动；local profile 显式排除了 Quartz 及多个 Spring AI 自动配置。
- UI 构建模式对应 `.env.*` 文件：`pnpm dev` = `env.local`，`build:prod` = `.env.prod`，以此类推。
