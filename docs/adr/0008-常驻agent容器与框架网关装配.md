# 常驻 Agent 容器与框架网关装配

运行时从 per-会话装配演进为「agentscope `HarnessGateway` 路由 + per-spec 懒构建常驻实例」：默认版本的无覆盖会话走单例快路径，其余回退现有装配路径。2026-08-23 经 grill 采访定案（工单 27 承载），机制依据见 `docs/research/research-agentscope-agent-singleton-gateway.md`，延续 ADR-0007（运行时基座切换）的并发章节。

## 背景与动机

ADR-0007 决策 9 以「M1 接受多实例并发写风险」冻结了并发问题；机制调研给出精确根源：**memory 写互斥是 `WorkspaceManager` 实例内的 per-path 锁**——per-会话装配 = 每实例一把锁、锁彼此不可见，同 spec 并发会话的 daily ledger 追加是结构性丢失更新（调试台并发低未爆发）。根治即实例常驻（进程内同文件追加被串行化），同时消除每请求的装配开销。官方形态佐证：HarnessAgent javadoc 明示单例安全；paw/codingagent 均为 `Map<String, HarnessAgent>` 常驻，**无任何按请求新建实例的先例**。

用户方针：**agentscope 功能能用尽用，不重复造轮子**——路由容器直接用框架 `HarnessGateway`，自建仅限框架明确不做的实例生命周期管理。

## 核心决策

1. **直接装配框架 `HarnessGateway`**：经公开静态工厂 `HarnessGateway.create()` 编程实例化（Spring 单例 bean + setter 注入 turn gate 等），**不自写 `implements Gateway`、不用 GatewayBootstrap 的静态 channel 装配**（M1 无 channel 诉求；未来接 channel/announce 生态即插即用）。经核验：`create()` 可脱离 Bootstrap；`registerAgent` 运行期可动态注册（重复注册静默覆盖）。
2. **自建件收敛为 `AgentInstanceManager`**（`session/infrastructure/gateway/`）：specId → 常驻 HarnessAgent 的懒构建、缓存、失效、close。框架网关只管路由不管实例生死（已核验无 close/unregister）——这不是造轮子，是框架边界的补位。
3. **快路径/fallback 分流**（本 ADR 的核心分流规则，三条件合流一个 fallback 通道）：
   - **快路径**：会话绑定版本 == 当前默认版本，且无 maxIters/temperature 覆盖，且其执行环境与实例一致 → 取常驻实例；
   - **fallback**：非默认版本（回滚调试）、执行环境不符（如 v1 纯对话/实例已是 workspace 形态）、或带推理参数覆盖（克隆重跑微调）→ **沿用现有 per-会话装配路径**（旧代码整体保留）。
   - 依据（源码核验）：`streamEvents` 全系重载无 per-call 推理参数通道（`StreamOptions` 纯流控；temperature 仅可经 PreReasoning hook 间接注入、maxIters 完全不可），覆盖语义不可能无损单例化——fallback 是语义正确的唯一解，不改框架。
4. **人格双通道**：workspace 模式下 AGENTS.md 只物化**默认版本**的 systemPrompt（种子语义）；非默认版本会话经 `builder.sysPrompt` 直传，接受系统提示与 AGENTS.md 拼接并存（回滚是低频路径）。修复了既有隐患：per-会话装配下同 spec 多版本会话交错覆写 AGENTS.md 本就互踩，双通道显式消解。
5. **失效 = 版本戳比对，不用领域事件**：`getOrCreate` 时比对 `channel.updateTime + model.updateTime + spec.currentVersionNo`（装配链本就查这些表，零额外成本）——发布、切换默认版本、渠道密钥轮换、模型停用全部天然触发「标记失效 + 惰性重建」，新实例重建时重新物化 AGENTS.md。不引入跨聚合事件（发布在 agentspec 聚合、容器在 session 侧，事件机制比版本戳贵且引入时序问题）。依据（源码核验）：常驻 agent 构建期即持有 resolve 出的 Model 实例，`ModelRegistry` factory 形态救不了已构建实例——密钥热更新只能靠重建。
6. **旧实例引用计数善后**：运行流持有实例引用，流 `doFinally` 时引用归零且已失效 → `close()`（现 `runningAgents` 注册表升级）。网关注册表僵尸条目保留无害（`ConcurrentHashMap` 无 unregister；会话创建已校验规格存在，不会再有新路由）。
7. **并发语义保留现状**：入口 `SessionRunningException` 立即拒绝不动；框架 turn gate（busy 跳过、返回空流）仅作底层兜底——跳过对 SSE 调试台是静默丢消息，必须挡在入口。现有会话链路测试零改动。
8. **MsgContext 组装规范**：`tenantId`/`sessionKey`/`agentId` 走 extra（canonicalKey 哈希含 extra，但同会话 agentId 恒定故无害），`userId` 走独立字段（框架语义：userId 不进 key、仅作状态命名空间）——与 ADR-0007 的 USER scope 分桶语义吻合。
9. **端口无感知**：`AgentRuntimeGateway`（DDD 端口）签名不动，改造全部发生在实现内部（`AgentscopeRuntimeGateway` 内部按分流规则走 Manager 或旧路径）；SessionServiceImpl 与控制器零改动。

## Considered Options

- 自写类 `implements Gateway`（paw 形态）——被否：paw 是为会话产品化（SessionAgentManager/announce 分发）才自写，我们没有这些诉求，自写即重复造轮子
- GatewayBootstrap 静态装配——被否：启动期静态注册覆盖不到运行期动态创建的规格
- 实例 key = specId + versionNo（版本级实例）——被否：多版本并存时实例膨胀，且 workspace 跨版本共享与版本级 filesystem 配置互相矛盾
- 版本子目录 workspace（每版本一棵）——被否：违背「workspace per-spec 跨版本共享」既有定案（ADR-0007 决策 3），种子/记忆全分裂
- 限制会话只能绑默认版本——被否：砍掉切换默认版本的回滚语义
- 同会话第二条流改排队（框架原生语义）——被否：SSE 客户端无限挂起，调试台交互语义劣化
- per-call 推理参数经 middleware/hook 注入——被否：maxIters 无通道，temperature 的 hook 注入是为低频覆盖场景改核心链路，fallback 更便宜
- ModelRegistry factory + 缓存策略做密钥热更新——被否：救不了已构建实例（构建期持有 Model 引用），版本戳重建是唯一有效点
- 领域事件驱动失效（发布/切换事件）——被否：版本戳零成本覆盖全部触发源（含渠道/模型变更），事件只覆盖发布与切换且引入跨聚合时序
- AGENTS.md 按会话版本动态切换物化——被否：多版本会话并存时仍互踩，双通道显式消解

## 连带影响

- 实施工单 27（插在 09 集成验收前），回归口径：现有 194 测试全绿 + 新增断言（同 spec 复用同实例、发布后惰性重建、覆盖/非默认版本会话走 fallback、AGENTS.md = 默认版本人格）
- AGENTS.md 物化时机从「每次装配」变为「实例构建时」（快路径）；fallback 路径维持装配时物化（单实例语义下 fallback 是临时实例，覆写行为天然正确）
- 矩阵测试改造：`assemble()` 直调改为经 Manager `getOrCreate` 断言装配产物
- 未来 channel/announce 生态（终端门户工单 17、workflow 工单 19）经框架 Gateway 接入，不再另设入口
