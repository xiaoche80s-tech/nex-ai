# agentscope 库级全用，禁止对其已有能力重复造轮子

旧版 nexai-module-ai 因"未按 agentscope 的功能开发、大量重复造轮子"被整体删除（b2ab4a9），本次重开确立第一禁忌：**凡是 agentscope-java v2 已有的能力，一律直用，禁止自建替代**——模型接入直用 ModelRegistry 与官方五家扩展、会话状态直用 PostgresDistributedStore/AgentStateStore、沙箱直用 Docker 扩展、权限与人工审批直用 PermissionEngine 与 HITL 事件流、SSE 直用 AG-UI starter、IM 渠道直用官方渠道扩展、OpenAI 兼容出口直用 ChatCompletionsStreamingAdapter。允许自建的仅限 agentscope 没有的：DAG 工作流、pgvector 级 RAG 管道、评测体系、计价计费，以及复用芋道自带底盘（租户/RBAC/审计框架/Quartz）。

复用边界为**库级**：agentscope 以 Maven 依赖（core + harness + 官方 extensions + starters）嵌入 nexai-server 单体，**不引入 agentscope-service 四平面微服务集群**。

## Considered Options

- 引入 agentscope-service 四平面（gateway/controlplane/dataplane/scheduler），nexai 作为其管理控制台二次开发——**拒绝**：其租户/RBAC/审计与芋道体系是两套平行宇宙，跨体系对齐成本高于自建；引入即两个平台并存打架，违背减少造轮子的初衷。service 平面仅作设计参考（如 scheduler 的定时唤醒思路）。

## Consequences

- 平台的管理面与执行面自建于芋道单体底盘上；agentscope-service 集群永不出现在部署拓扑中。
- 下列技术结论随本决策沿用（经用户确认）：库嵌入单体、HarnessAgent 基座、AgentSpec→Builder 零子类翻译、AgentInstanceManager 常驻实例（任何链路不得 per-请求新建实例）、spec 四层结构 + 三级归属 + 版本快照、AG-UI 流式协议。
- 旧版 8 份 ADR 与术语表已废弃不恢复；本文档为白纸重开后的第 0001 号决策，`docs/research/` 六份调研文档仍是有效输入。
