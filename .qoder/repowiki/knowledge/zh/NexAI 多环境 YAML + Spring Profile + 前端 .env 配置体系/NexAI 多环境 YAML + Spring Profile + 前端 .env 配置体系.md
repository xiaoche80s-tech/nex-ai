---
kind: configuration_system
name: NexAI 多环境 YAML + Spring Profile + 前端 .env 配置体系
category: configuration_system
scope:
    - '**'
source_files:
    - nexai-server/src/main/java/com/gkht/ai/nexai/server/NexaiServerApplication.java
    - nexai-server/src/main/resources/application.yaml
    - nexai-server/src/main/resources/application-local.yaml
    - nexai-server/src/main/resources/application-dev.yaml
    - nexai-server/src/main/resources/application-docker.yaml
    - script/docker/docker.env
    - nexai-ui/.env
    - nexai-ui/.env.dev
    - nexai-ui/vite.config.ts
---

## 1. 整体方案

NexAI 采用 **Spring Boot 原生配置文件 + `spring.profiles.active` 多环境覆盖** 的后端配置体系，配合 **Vite `.env*` 文件 + `loadEnv`** 的前端构建期配置体系，并通过 Docker Compose + `docker.env` 注入运行时环境变量。所有配置以 YAML/ENV 形式声明，不引入第三方配置中心（如 Nacos/Apollo），通过 `application.yaml` 提供默认值、各 profile 文件覆盖差异、`${}` 占位符与 `default` 默认值实现分层装配。

## 2. 核心文件与位置

- 后端主入口：`nexai-server/src/main/java/com/gkht/ai/nexai/server/NexaiServerApplication.java`，使用 `@SpringBootApplication(scanBasePackages = {"${nexai.info.base-package}.server", "${nexai.info.base-package}.module"})`，包扫描路径本身由配置驱动。
- 全局默认配置：`nexai-server/src/main/resources/application.yaml`（390 行），集中定义 `spring.application.name`、`spring.profiles.active: local`、`springdoc/knife4j`、`flowable`、`mybatis-plus`、`spring.data.redis`、`easy-trans`、`aj.captcha`、`rocketmq`、`spring.kafka`、`spring.ai.*`、`nexai.*`（AI 厂商开关、可观测性、Web、XSS、安全白名单、API 加密、WebSocket、Swagger、Codegen、Tenant、SMS、Trade、IoT）等全部默认值。
- 环境覆盖文件：
  - `application-local.yaml`：本地开发（PostgreSQL 远端、Redis 远端、Quartz 关闭自动配置、devtools 热部署、微信测试号、JustAuth 全渠道）。
  - `application-dev.yaml`：测试环境（MySQL 本地、Redis 远程、Quartz JDBC 集群、消息中间件本地地址）。
  - `application-docker.yaml`：容器化运行（PostgreSQL `postgres:5432`、Redis `redis:6379`、关闭验证码与访问日志）。
- 容器环境变量：`script/docker/docker.env`，定义 `MASTER_DATASOURCE_URL`、`REDIS_HOST`、`NODE_ENV`、`PUBLIC_PATH`、`VUE_APP_*` 等，供 docker-compose 注入。
- 前端配置：`nexai-ui/.env`（通用）、`nexai-ui/.env.dev`（开发模式 API 地址、压缩、sourcemap 等）、`nexai-ui/vite.config.ts` 通过 `loadEnv(mode, root)` 读取并映射到 Vite 构建参数。

## 3. 架构与约定

### 3.1 加载顺序与覆盖规则
遵循 Spring Boot 标准优先级：`application.yaml` → 激活的 profile 文件（local/dev/docker）→ 命令行参数 / 环境变量。每个 profile 文件只覆盖差异项，例如数据库连接串、Redis 地址、是否启用 Quartz、是否禁用某些 AI 向量存储自动配置（Qdrant/Redis/Milvus）等。

### 3.2 敏感信息与外部化
- 所有外部服务密钥统一使用 `${ENV_VAR:default}` 语法，如 `OPENAI_API_KEY:sk-xxxx`、`ANTHROPIC_API_KEY:sk-xxxx`、`DASHSCOPE_API_KEY`、`DEEPSEEK_API_KEY`、`GEMINI_API_KEY`、`DOUBAO_API_KEY`、`HUNYUAN_API_KEY`、`SILICONFLOW_API_KEY`、`XINGHUO_API_KEY`、`BAICHUAN_API_KEY`、`YIYAN_API_KEY`、`ZHIPU_API_KEY`、`MINIMAX_API_KEY`、`MOONSHOT_API_KEY`、`STEPFUN_API_KEY`、`GROK_API_KEY`、`MIDJOURNEY_API_KEY`、`WEB_SEARCH_API_KEY` 等，确保密钥不入库。
- 容器场景通过 `docker.env` 中的 `MASTER_DATASOURCE_*`、`REDIS_HOST` 等变量注入；前端通过 `VITE_APP_*` 系列变量控制构建产物行为（租户开关、验证码开关、文档开关、百度统计、API 加解密开关与算法）。

### 3.3 功能开关与特性门控
大量能力通过 `nexai.*` 或第三方前缀的布尔开关控制：
- `nexai.ai.doubao.enable`、`nexai.ai.hunyuan.enable`、`nexai.ai.midjourney.enable` 等按厂商独立启停。
- `nexai.observability.audit.enabled`、`usage.enabled`、`otel.enabled` 控制审计、用量采集与 OpenTelemetry。
- `nexai.tenant.enable`、`nexai.websocket.enable`、`nexai.api-encrypt.enable`、`nexai.xss.enable` 等横切能力开关。
- `spring.ai.mcp.server.enabled`、`spring.ai.mcp.client.enabled` 控制 MCP 服务端/客户端。
- `spring.autoconfigure.exclude` 在 dev/local/docker 中显式排除 Qdrant/Redis/Milvus 向量存储自动配置及 DashScope 自动配置，避免依赖缺失导致启动失败。

### 3.4 多数据源与多库支持
`application-local.yaml` 与 `application-docker.yaml` 均使用 `spring.datasource.dynamic` 多数据源（master/slave），但 local 指向 PostgreSQL，dev 指向 MySQL，体现“同一份配置模板适配不同数据库”的约定。

### 3.5 前端配置约定
- 所有 Vite 相关配置集中在 `vite.config.ts`，通过 `loadEnv(command === 'build' ? mode : process.argv[...], root)` 动态加载 `.env` 或 `.env.dev`。
- 业务开关（租户、验证码、文档、API 加解密、百度统计）以前缀 `VITE_APP_` 暴露给运行时代码；构建期开关（端口、代理、压缩、sourcemap、输出目录）以前缀 `VITE_` 暴露给构建脚本。
- 生产构建时 `NODE_ENV=production`，且 `docker.env` 中 `VUE_APP_BASE_API=/prod-api` 决定后端 API 前缀。

## 4. 约束与规范

- **禁止硬编码外部地址**：所有数据库 URL、Redis 地址、AI 厂商 base-url/api-key 必须通过 `${ENV_VAR:default}` 或 profile 文件注入，不得直接写死在生产配置中。
- **新增厂商/能力必须带 `enable` 开关**：参考 `nexai.ai.*.enable` 模式，新增外部集成需在 `application.yaml` 中提供默认 false 的 enable 开关，并在对应 profile 中按需开启。
- **profile 仅覆盖差异**：`application-local.yaml`、`application-dev.yaml`、`application-docker.yaml` 不应重复声明已在 `application.yaml` 中的公共配置，保持单一事实来源。
- **自动配置排除需注释原因**：当前各 profile 中对 `spring.autoconfigure.exclude` 的每一项都附带注释说明为何禁用（如“手动创建”“暂不兼容 Spring Boot 4”），新增排除项应遵循此惯例。
- **前端环境变量命名空间隔离**：`VITE_APP_*` 用于运行时业务开关，`VITE_*` 用于构建期参数，不得混用。
- **容器部署必须配套 `docker.env`**：任何新加入的外部依赖（数据库、缓存、消息队列）都应先在 `docker.env` 中声明变量名，再在 `application-docker.yaml` 中引用。
