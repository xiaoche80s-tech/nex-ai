# agentscope 以库形式嵌入 nexai-server 单体

agentscope-java v2 作为智能体运行时基座，以 Maven 依赖形式嵌入 nexai-server 单体进程（core + harness + 按需扩展 jar），而非独立部署运行时服务（agentscope-service 形态）。理由：core/harness 零 Spring 依赖、Bean 手工装配可行，单体内嵌避免分布式复杂度。代价：接受 Jackson 2/3 双 databind 并存（nexai-common 的 JsonUtils 已有双栈先例）与 Reactor→Servlet 桥接。DDD 的 domain 层以 port 接口隔离 agentscope 类型（domain 不 import agentscope 包），保留将来拆分独立运行时的演进路径。

前置 spike 工单（依赖引入 + 启动 + SSE 返回真实 AgentEvent + 状态存取冒烟）在 M1 动工前执行；spike 失败则重议本决策。

## Considered Options

- 库嵌入单体（选定）
- 独立运行时服务，经 HTTP/A2A 调用
- 先嵌入后拆分（当前即此路径的起点）
