# NexAI 智能体平台

在 NexAI 脚手架之上构建的企业级 AI 智能体开发平台：管理端配置智能体资产（模型、规格、技能、知识、编排），客户端消费智能体能力。

## Language

**AgentSpec（智能体规格）**:
描述一个智能体的声明式配置：模型、系统提示、工具白名单、挂载的技能与子智能体。有版本，是图纸而非实体。其自描述（description）是给 LLM 看的（子智能体路由依据），不是管理备注；影响行为的配置随版本快照固化，管理元数据（名称/图标）留在规格主体。
_Avoid_: 智能体配置、agent 定义、prompt 模板

**Agent（智能体）**:
按某个版本的 AgentSpec 实例化的运行体。
_Avoid_: 助手、bot、assistant

**Skill（技能）**:
可复用的能力包（说明文档 + 附属资源），可被任意智能体挂载。
_Avoid_: 插件、工具、能力包

**Workflow（工作流）**:
确定性编排图，节点引用智能体、技能或人工审批。
_Avoid_: 流程、pipeline、流水线

**Session（会话）**:
用户与某个智能体的一次连续对话，状态可持久化与恢复。调试会话是一种 Session（type = debug），与终端用户会话共用同一聚合。
_Avoid_: 对话、聊天、上下文

**Channel（渠道）**:
一个模型服务接入点：提供商类型、端点地址与密钥。同一提供商可有多个渠道；归属为平台或租户（自带密钥）。
_Avoid_: 提供商、Provider、模型服务

**Model（模型）**:
挂载于某渠道下的具体模型元数据（标识、上下文窗口、单价、能力标签），供 AgentSpec 引用。
_Avoid_: 模型服务、LLM

**MCP Server（MCP 服务）**:
平台注册的外部 MCP 服务连接（端点、传输类型、认证、工具白名单），可被 AgentSpec 挂载。
_Avoid_: MCP 接入、工具服务

**AgentRuntimeGateway（运行时端口）**:
Session 聚合的领域端口（六边形架构语义）：把「渠道 + 模型 + 版本快照 + 会话寻址」交给运行时，换回事件流。是平台自己的接缝，与 agentscope 框架的 Gateway（多 agent 路由容器）同名不同物。
_Avoid_: 与 agentscope Gateway 混称、网关（泛称）

**AgentInstanceManager（智能体实例管理器）**:
Agent 资产侧的运行时组件：按 AgentSpec 惰性构建并常驻 HarnessAgent 实例（懒构建、版本戳失效、引用计数善后），向 agentscope Gateway 注册路由。管实例生死，不管消息路由。
_Avoid_: agent 池、容器（泛称）、与 AgentRuntimeGateway 混同

## 治理对象

治理分两级：AgentSpec 为资产侧（版本、发布、权限），Session 为运行侧（审计、配额）。
