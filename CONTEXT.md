# NexAI 智能体平台

面向对外产品化的企业级 AI Agent 平台：租户在平台上配置、发布、运营智能体；智能体运行时基于 agentscope-java v2。本表是全项目唯一的领域术语表。

## Language

### 资产侧（规格与配置）

**AgentSpec（智能体规格）**：
智能体的声明式图纸——模型、系统提示、挂载与执行环境配置，带不可变版本快照；业务编码 spec_code 创建后不可变。
_Avoid_: 智能体配置、agent 定义、prompt 模板

**挂载（Mount）**:
AgentSpec 声明的外部能力引用，sealed 家族（ToolMount/FolderMount/SkillMount…），共享校验/判等骨架，各挂载为薄 record。
_Avoid_: 挂载项、mount 实体

**草稿（Draft）**:
规格唯一的可编辑配置槽位；发布时被固化并清空，编辑保存后重建。有草稿即草稿态（可发布），无草稿且已发布过即已发布态（稳定）。列表展示三态：从未发布=「草稿」、已发布无草稿=「vN 已发布」、已发布且有草稿=「vN · 编辑中」（编辑中 = 已发布态叠加草稿的展示口径，非独立状态）。
_Avoid_: 工作副本、未发布版本

**发布（Publish）**:
把草稿固化为不可变版本快照、推进当前版本指针并清空草稿的动作，仅草稿态可执行。
_Avoid_: 上线、保存版本

**生效快照（Effective Snapshot）**:
当前版本指针指向的不可变版本快照；由 agentspec 的 resolveCurrentVersion 单一入口解析，运行侧各入口（调试台/OpenAI 出口/终端页面）统一消费，不自行筛选版本。
_Avoid_: 当前配置、运行配置

**Channel（渠道）**:
模型服务接入点（提供商类型/端点/密钥），归属平台级或租户级。
_Avoid_: 提供商、Provider、模型接入

**Model（模型）**:
挂接在渠道下的模型元数据（标识/上下文窗口/计价信息）。
_Avoid_: 模型服务、LLM

**Skill（技能）**：
可复用能力包（文档+资源），可被任意智能体挂载；租户级/用户级经平台资产管理，平台级经 Git 仓库。
_Avoid_: 插件、工具、能力包

**Tool（工具）**：
智能体推理中可调用的原子操作，来源为内置工具、MCP Server 提供的工具或平台注册的业务工具。
_Avoid_: 技能、插件（Tool 是可执行调用，Skill 是文档+资源能力包）

**MCP Server（MCP 服务）**:
注册的外部 MCP 服务连接（端点/传输/认证/工具白名单），供智能体挂载。
_Avoid_: MCP 接入、工具服务

### 运行侧（会话与实例）

**Agent（智能体）**：
按某版本 AgentSpec 常驻运行的运行体，不随请求新建销毁。
_Avoid_: 助手、bot、assistant

**Session（会话）**:
用户与智能体的一次连续对话，状态可持久化恢复；调试会话是其中一类。
_Avoid_: 对话、聊天、上下文

**AgentRuntimeGateway（运行时端口）**:
会话侧的六边形端口——以渠道、模型、版本快照与会话寻址换取智能体事件流。
_Avoid_: 运行时服务、runtime

**AgentInstanceManager（智能体实例管理器）**:
按 AgentSpec 惰性构建并常驻智能体实例、按版本戳失效重建的运行时组件。
_Avoid_: 实例工厂、实例池

**ChatModelProvider（模型装配端口）**:
以渠道 + 模型标识换取 agentscope ChatModel 实例的装配端口（channel 侧接口）；连通性探测与运行时装配共用。
_Avoid_: ChatModelFactory（实现类名）、模型工厂、ModelRegistry（与 agentscope 静态注册表重名）

### 治理对象

治理分两级：**资产侧**治理 AgentSpec/Channel/Model 的版本、发布与权限；**运行侧**治理 Session 的审计、配额与用量。
