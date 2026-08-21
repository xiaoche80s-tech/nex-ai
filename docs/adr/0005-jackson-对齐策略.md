# Jackson 双栈对齐策略与 agentscope 状态存储选型

nexai-server（Spring Boot 4.1，web 层 Jackson 3 / `tools.jackson` 3.1.4）与 agentscope-java（事件/状态序列化绑定 Jackson 2 / `com.fasterxml` 2.21.4）**双 databind 并存**，不做任何单栈统一。边界规则：

1. **AgentEvent 流保真走原生栈**：调试台/门户的 SSE 端点逐事件经 agentscope 自带 codec（`io.agentscope.core.util.JsonUtils.getJsonCodec()`，Jackson 2）转 JSON 后转发，不让 Spring MVC 的 Jackson 3 直接序列化事件对象——序列化责任与 agentscope 内部（状态持久化、事件流）保持同一栈，杜绝注解兼容性漂移。
2. **业务 VO 走 Spring 栈**：管理端 REST 出入参（command/query/dto）继续由 Spring HTTP 层 Jackson 3 序列化；确需在 HTTP 响应中携带 agentscope 对象时，先转 Map/DTO 或 JSON 字符串，不直接暴露领域对象。
3. **不共享 ObjectMapper，不混用注解**：两栈各自维护 mapper；nexai 代码新写序列化注解一律用 `com.fasterxml.jackson.annotation`（Jackson 3 兼容读取 2.x 注解，JSTEP 路线），禁止 import `tools.jackson` 注解包。

状态存储选型：**PostgresAgentStateStore 为主存储**（容器 DataSource 注入——原生支持，无需改造；独立 schema `agentscope` + 表 `agentscope_sessions`，不侵入业务表；自带乐观锁 version 列），Redis 仍按 spec 只承担中断旗标与调试实时通道，不作为会话主存储。

## Considered Options

- 双 databind 并存 + 按栈划界（选定；spike 实证全绿）
- 统一到 Jackson 2 单栈（不可行：Spring Boot 4.x web 层绑定 Jackson 3）
- 统一到 Jackson 3 单栈（不可行：agentscope 内部序列化闭环在 Jackson 2，改不动）

## Spike 实证（2026-08-22，工单 01）

- **启动兼容**：SB 4.1 + agentscope 2.0.3-SNAPSHOT（core/harness/openai/pg/redis 五构件）8.6s 正常启动，无 Bean 冲突、无 SPI 冲突。
- **Jackson 双栈**：`AgentStartEvent`（扁平）、`TextBlockDeltaEvent`（中文增量）、`ModelCallEndEvent`（嵌套 ChatUsage）三类事件在 Jackson 2 / Jackson 3 下序列化**逐字节一致**；`type` 多态判别字段两栈均保留；Jackson 3 序列化→反序列化往返正确还原子类。
- **SSE 真实调用**：DeepSeek（openai 兼容，凭据经环境变量注入）两轮对话，完整事件序列 `AGENT_START → MODEL_CALL_START → THINKING_BLOCK_* → TEXT_BLOCK_* → MODEL_CALL_END(usage) → AGENT_RESULT → AGENT_END` 经 Servlet/SSE 桥接保真转发；第二轮跨 HTTP 请求从 PG 恢复上下文正确召回第一轮信息（`agentscope.agentscope_sessions` 中 `__anon__:<sessionId>` 行，version 随轮次递增）。
- **状态存取**：PG / Redis / InMemory 三引擎 save→get→versioned→exists→listSessionIds→delete 全链路通过。

## 版本与裁定记录

- agentscope 版本：**2.0.3-SNAPSHOT**（2026-08-22 经用户确认由 2.0.2 发布版切换，推翻"仅发布版"约束，动机为获取乐观锁版本化等主干新 API）。构件经本地 `~/agent-project/agentscope-java` 源码 `mvn install` 获得，远端 snapshots 仓库未发布该版本——**其他环境克隆本仓库后需同样本地 install，或等待 2.0.3 GA 后回落锁定 Maven Central 发布版**（回落时应同步移除本文件与 `nexai-dependencies/pom.xml` 中的 SNAPSHOT 注记）。
- fat jar 实测双栈版本：Jackson 2 = databind/core 2.21.4 + annotations 2.21 + jsr310/yaml；Jackson 3 = databind/core 3.1.4（`third-party-jackson-core-2.46.17` 为他库 shaded 构件，与本议题无关）。
- Redisson 裁定为 **4.2.0**（agentscope-extensions-redis 传递，`nexai-dependencies` 仅管理 `redisson-spring-boot-starter` 4.6.1 而未管理本体，nearest 优先使 core 落在 4.2.0）：starter 4.6.1 + core 4.2.0 混搭在本轮冒烟（芋道 Redis 用法 + agentscope RedisAgentStateStore）下全部通过。**M1 应在 nexai-dependencies 显式管理 `org.redisson:redisson` 本体**至与 starter 一致（4.6.1）并回归冒烟，消除混搭隐患。
- 已知坑：`RedisAgentStateStore.close()` 会**连带关闭传入的 RedissonClient**——挂在容器共享客户端上的 store 实例禁止调用 close()（spike 代码已注释标明）。
- `PostgresAgentStateStore.close()` 不关外部 DataSource，可安全释放；其建表为惰性 `CREATE IF NOT EXISTS`，首次使用即建 schema。
