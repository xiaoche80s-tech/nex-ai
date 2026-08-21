# NexAI

<p align="center">
 <img src="https://img.shields.io/badge/JDK-25-blue.svg" alt="JDK" />
 <img src="https://img.shields.io/badge/Spring%20Boot-4.1.0-blue.svg" alt="Spring Boot" />
 <img src="https://img.shields.io/badge/Vue-3.x-brightgreen.svg" alt="Vue" />
 <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="License" />
</p>

**NexAI** 是基于 [ruoyi-vue-pro（芋道）](https://gitee.com/zhijiantianya/ruoyi-vue-pro) 快速开发平台 fork 并重命名的企业级开发脚手架（groupId `com.gkht.ai`，基础包名 `com.gkht.ai.nexai`），正在向 AI 智能体平台方向演进。

本仓库为**精简版**：当前仅保留「系统功能」与「基础设施」两个核心业务模块，原项目的其他业务模块（工作流、支付、商城、CRM、ERP、WMS、MES、IM、AI、IoT、公众号等）未包含在本仓库中。

## 🐯 平台简介

* Java 后端：Java 25 + Spring Boot 4.1 多模块架构，PostgreSQL + MyBatis Plus、Redis + Redisson
* 管理后台前端：`nexai-ui/` —— Vue 3 + Vite + TypeScript + Element Plus + UnoCSS，pnpm workspace
* 权限认证使用 Spring Security & Token & Redis，支持多终端、多种用户的认证系统，支持 SSO 单点登录
* 支持加载动态权限菜单，按钮级别权限控制，Redis 缓存提升性能
* 支持 SaaS 多租户，可自定义每个租户的权限，提供透明化的多租户底层封装
* 高效率开发，使用代码生成器可以一键生成 Java、Vue 前后端代码、SQL 脚本、接口文档，支持单表、树表、主子表
* 集成阿里云、腾讯云等短信渠道，集成 MinIO、阿里云、腾讯云、七牛云等云存储服务
* 基于 Redis 实现分布式锁、幂等、限流功能，满足高并发场景
* 基于 Redis 实现消息队列，Stream 提供集群消费，Pub/Sub 提供广播消费

> 友情提示：本项目基于 RuoYi-Vue 修改，**重构优化**后端的代码，**美化**前端的界面。

🙂 所有功能，都通过 **单元测试** 保证高质量。

## 🐨 内置功能

### 系统功能

|     | 功能    | 描述                              |
|-----|-------|---------------------------------|
|     | 用户管理  | 用户是系统操作者，该功能主要完成系统用户配置          |
| ⭐️  | 在线用户  | 当前系统中活跃用户状态监控，支持手动踢下线           |
|     | 角色管理  | 角色菜单权限分配、设置角色按机构进行数据范围权限划分      |
|     | 菜单管理  | 配置系统菜单、操作权限、按钮权限标识等，本地缓存提供性能    |
|     | 部门管理  | 配置系统组织机构（公司、部门、小组），树结构展现支持数据权限  |
|     | 岗位管理  | 配置系统用户所属担任职务                    |
| 🚀  | 租户管理  | 配置系统租户，支持 SaaS 场景下的多租户功能        |
| 🚀  | 租户套餐  | 配置租户套餐，自定每个租户的菜单、操作、按钮的权限       |
|     | 字典管理  | 对系统中经常使用的一些较为固定的数据进行维护          |
| 🚀  | 短信管理  | 短信渠道、短息模板、短信日志，对接阿里云、腾讯云等主流短信平台 |
| 🚀  | 邮件管理  | 邮箱账号、邮件模版、邮件发送日志，支持所有邮件平台       |
| 🚀  | 站内信   | 系统内的消息通知，提供站内信模版、站内信消息          |
| 🚀  | 操作日志  | 系统正常操作日志记录和查询，集成 Swagger 生成日志内容 |
| ⭐️  | 登录日志  | 系统登录日志记录查询，包含登录异常               |
| 🚀  | 错误码管理 | 系统所有错误码的管理，可在线修改错误提示，无需重启服务     |
|     | 通知公告  | 系统通知公告信息发布维护                    |
| 🚀  | 敏感词   | 配置系统敏感词，支持标签分组                  |
| 🚀  | 应用管理  | 管理 SSO 单点登录的应用，支持多种 OAuth2 授权方式 |
| 🚀  | 地区管理  | 展示省份、城市、区镇等城市信息，支持 IP 对应城市      |

### 基础设施

|     | 功能        | 描述                                           |
|-----|-----------|----------------------------------------------|
| 🚀  | 代码生成      | 前后端代码的生成（Java、Vue、SQL、单元测试），支持 CRUD 下载       |
| 🚀  | 系统接口      | 基于 Swagger 自动生成相关的 RESTful API 接口文档          |
| 🚀  | 数据库文档     | 基于 Screw 自动生成数据库文档，支持导出 Word、HTML、MD 格式      |
|     | 表单构建      | 拖动表单元素生成相应的 HTML 代码，支持导出 JSON、Vue 文件         |
| 🚀  | 配置管理      | 对系统动态配置常用参数，支持 SpringBoot 加载                 |
| ⭐️  | 定时任务      | 在线（添加、修改、删除)任务调度包含执行结果日志                     |
| 🚀  | 文件服务      | 支持将文件存储到 S3（MinIO、阿里云、腾讯云、七牛云）、本地、FTP、数据库等   |
| 🚀  | API 日志    | 包括 RESTful API 访问日志、异常日志两部分，方便排查 API 相关的问题   |
|     | 数据库监控     | 监视当前系统数据库连接池状态，可进行分析 SQL 找出系统性能瓶颈            |
|     | Redis 监控  | 监控 Redis 数据库的使用情况，使用的 Redis Key 管理           |
| 🚀  | 消息队列      | 基于 Redis 实现消息队列，Stream 提供集群消费，Pub/Sub 提供广播消费 |
| 🚀  | Java 监控   | 基于 Spring Boot Admin 实现 Java 应用的监控           |
| 🚀  | 服务保障      | 基于 Redis 实现分布式锁、幂等、限流功能，满足高并发场景              |
| 🚀  | 日志服务      | 轻量级日志中心，查看远程服务器的日志                           |
| 🚀  | 单元测试      | 基于 JUnit + Mockito 实现单元测试，保证功能的正确性、代码的质量等    |

## 🐨 技术栈

### 模块

| 项目                    | 说明                              |
|-----------------------|---------------------------------|
| `nexai-dependencies`  | Maven 依赖版本管理（BOM）               |
| `nexai-framework`     | 自研 Spring Boot Starter 集合（框架拓展） |
| `nexai-server`        | 管理后台的服务端（空壳装配应用）                |
| `nexai-module-system` | 系统功能的 Module 模块                 |
| `nexai-module-infra`  | 基础设施的 Module 模块                 |
| `nexai-ui`            | Vue 3 管理后台前端 SPA                |
| `sql/`                | 数据库初始化脚本（PostgreSQL / MySQL / 达梦 DM） |
| `script/`             | 部署脚本（docker-compose、Jenkins、shell） |
| `docs/`               | 技术文档                            |

### 框架

| 框架                                                                                          | 说明               | 版本             |
|---------------------------------------------------------------------------------------------|------------------|----------------|
| [Spring Boot](https://spring.io/projects/spring-boot)                                       | 应用开发框架           | 4.1.0          |
| [PostgreSQL](https://www.postgresql.org/)                                                   | 数据库服务器（本项目默认使用）  | -              |
| [Druid](https://github.com/alibaba/druid)                                                   | JDBC 连接池、监控组件    | 1.2.28         |
| [MyBatis Plus](https://mp.baomidou.com/)                                                    | MyBatis 增强工具包    | 3.5.16         |
| [Dynamic Datasource](https://dynamic-datasource.com/)                                       | 动态数据源            | 4.5.0          |
| [Redis](https://redis.io/)                                                                  | key-value 数据库    | 5.0 / 6.0 /7.0 |
| [Redisson](https://github.com/redisson/redisson)                                            | Redis 客户端        | 4.6.1          |
| [Spring MVC](https://github.com/spring-projects/spring-framework/tree/master/spring-webmvc) | MVC 框架           | 7.0.8          |
| [Spring Security](https://github.com/spring-projects/spring-security)                       | Spring 安全框架      | 7.1.0          |
| [Hibernate Validator](https://github.com/hibernate/hibernate-validator)                     | 参数校验组件           | 9.1.0          |
| [Quartz](https://github.com/quartz-scheduler)                                               | 任务调度组件           | 2.5.2          |
| [Springdoc](https://springdoc.org/)                                                         | Swagger 文档       | 3.0.3          |
| [Spring Boot Admin](https://github.com/codecentric/spring-boot-admin)                       | Spring Boot 监控平台 | 4.0.4          |
| [Jackson](https://github.com/FasterXML/jackson)                                             | JSON 工具库         | 3.1.4          |
| [MapStruct](https://mapstruct.org/)                                                         | Java Bean 转换     | 1.6.3          |
| [Lombok](https://projectlombok.org/)                                                        | 消除冗长的 Java 代码    | 1.18.46        |
| [JUnit](https://junit.org/junit5/)                                                          | Java 单元测试框架      | 6.0.3          |
| [Mockito](https://github.com/mockito/mockito)                                               | Java Mock 框架     | 5.23.0         |

### 前端

| 技术                                                    | 说明          | 版本    |
|-------------------------------------------------------|-------------|-------|
| [Vue](https://vuejs.org/)                             | 渐进式 JavaScript 框架 | 3.x   |
| [Vite](https://vite.dev/)                             | 前端构建工具      | -     |
| [TypeScript](https://www.typescriptlang.org/)         | 类型安全的 JavaScript 超集 | -     |
| [Element Plus](https://element-plus.org/)             | Vue 3 组件库   | 2.13.7 |
| [UnoCSS](https://unocss.dev/)                         | 即时原子化 CSS 引擎 | -     |
| [pnpm](https://pnpm.io/)                              | 包管理器（workspace） | -     |

## 🚀 快速开始

开发环境要求：JDK 25 + Maven 3.9+（后端），Node.js + pnpm（前端）。

```bash
# 后端：聚焦构建单个模块及其依赖（全量构建见 AGENTS.md）
mvn -pl nexai-module-system -am package

# 启动后端：运行 nexai-server 中的 com.gkht.ai.nexai.server.NexaiServerApplication
# 默认 profile：local，端口 48080

# 前端（在 nexai-ui/ 目录内执行）
pnpm i
pnpm dev          # vite，mode env.local，端口 5173
```

更多构建、测试、运行命令与架构约定，请参考根目录的 [AGENTS.md](./AGENTS.md)。

## 😎 开源协议

本项目采用比 Apache 2.0 更宽松的 [MIT License](./LICENSE) 开源协议，个人与企业可 100% 免费使用，不用保留类作者、Copyright 信息。

上游原项目：[ruoyi-vue-pro（芋道）](https://gitee.com/zhijiantianya/ruoyi-vue-pro)。
