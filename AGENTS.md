# AGENTS.md

本仓库中 ZCode Agent 的工作指南。

## 语言规则

- 会话中产生的所有文档、思考链路、代码注释一律使用中文。
- 与用户的交流一律使用中文。

## 项目概况

NexAI —— 基于 ruoyi-vue-pro（芋道/yudao）fork 并重命名的企业级开发脚手架：groupId `com.gkht.ai`、基础包名 `com.gkht.ai.nexai`、配置前缀 `nexai.*`。本仓库为精简版：仅保留 `system` 与 `infra` 两个业务模块，其余 yudao 模块（bpm、pay、mall、crm 等）已在根 `pom.xml` 中注释。技术栈：Java 25、Spring Boot 4.1、PostgreSQL + MyBatis Plus、Redis + Redisson。管理后台前端位于 `nexai-ui/`（Vue 3 + Vite + TypeScript + Element Plus + UnoCSS，pnpm）。

本工作区不是 git 仓库 —— 无历史记录、分支或 diff 可查。

## 目录结构

- `nexai-dependencies/` —— Maven BOM；所有第三方版本统一在此管理。
- `nexai-framework/` —— 自研 Spring Boot Starter（`nexai-common` 提供共享的 `CommonResult`/`PageResult`、错误码体系、工具类；`nexai-spring-boot-starter-*` 覆盖 web、security、mybatis、redis、tenant、test 等）。
- `nexai-module-system/`、`nexai-module-infra/` —— 业务模块。
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

前端（在 `nexai-ui/` 内执行，Node ≥ 20.19，pnpm ≥ 8.6）：

```bash
pnpm i
pnpm dev        # vite --mode env.local，端口 5173，请求 http://localhost:48080/admin-api
pnpm ts:check   # vue-tsc 类型检查
pnpm lint       # eslint + stylelint + prettier 检查模式；lint:eslint / lint:format / lint:style 自动修复
```

## 后端约定（芋道分层）

每个 `nexai-module-*` 在 `module/<name>/` 下使用相同的包结构：

- `controller/admin/**`、`controller/app/**` —— REST 接口。URL 前缀由包位置决定：`admin` 控制器挂在 `/admin-api` 下，`app` 控制器挂在 `/app-api` 下（见 web starter 中的 `WebProperties`）。控制器返回 `CommonResult<T>`，出入参使用 VO（`*SaveReqVO`、`*PageReqVO`、`*RespVO`），存放在控制器旁的 `vo/` 子包中。
- `service/**` —— 业务逻辑，`XxxService` + `XxxServiceImpl`。
- `convert/**` —— MapStruct 转换器（VO ↔ DO）。
- `dal/dataobject/**` —— MyBatis Plus 实体（`*DO`）；`dal/mysql/**` —— Mapper 接口（包名为芋道历史遗留；数据库实为 PostgreSQL）；`dal/redis/**` —— Redis DAO。
- `api/**` —— 模块对外的公开接口；跨模块调用走 `api` 接口，实现位于 `service`。
- `enums/ErrorCodeConstants.java` —— 模块错误码注册表；新增错误码写在这里。
- `framework/**` —— 模块内配置；`job/**` —— Quartz 定时任务；`mq/**` —— 基于 Redis 的消息。

横切规则：

- SaaS 多租户已启用（`nexai.tenant.enable`）：新表默认租户过滤，除非加入 `nexai.tenant.ignore-tables`。
- 逻辑删除通过 `deleted` 字段：1 = 已删除，0 = 存活。
- Lombok + MapStruct 注解处理器在根 pom 的 `annotationProcessorPaths` 中装配（含 `lombok-mapstruct-binding`）；新增处理器需修改该列表。
- 单元测试继承 `nexai-spring-boot-starter-test` 中的基类：`BaseDbUnitTest`（H2 内存库，profile `unit-test`，每个测试后清理 DB）、`BaseDbAndRedisUnitTest`、`BaseRedisUnitTest`、`BaseMockitoUnitTest`。DB 类测试无需外部服务。

## 前端约定（nexai-ui/）

- Element Plus 组件与常用 hooks（`useI18n`、`useMessage`、`useTable`、`useCrudSchemas`）、`required` 校验、`DICT_TYPE` 字典常量均由 unplugin 自动导入（配置见 `build/vite/index.ts`），无需手动 import；类型声明生成在 `src/types/auto-imports.d.ts` 与 `auto-components.d.ts`。
- `src/components/**` 下的自定义组件同样自动注册，直接使用即可。
- 样式使用 UnoCSS 原子类（`uno.config.ts`）；SVG 图标放 `src/assets/svgs`，以 `icon-[dir]-[name]` 形式引用。
- 界面文案接入 vue-i18n（`src/locales/`），新文案写入语言包。

## 注意事项

- 上游芋道品牌信息在多处残留（pom `description`、Swagger 标题、`ruoyi-vue-pro.sql`）；未经要求不要迁移。
- `application.yaml` 携带上游遗留的大段 AI 厂商、工作流、支付配置 —— 大多已禁用或由环境变量驱动；local profile 显式排除了 Quartz 及多个 Spring AI 自动配置。
- UI 构建模式对应 `.env.*` 文件：`pnpm dev` = `env.local`，`build:prod` = `.env.prod`，以此类推。
