# AgentSpec 配置三层分组对齐 agentscope

AgentSpec 的配置值对象（草稿与版本快照共用）按 agentscope 的分层组织为**三层**，并以「影响行为与否」为唯一判据划分配置与元数据的归属。2026-08-22 经 grill 采访定案（工单 05 结构重设）。

## 分层结构

```
AgentSpecConfig
├── agent 层        modelId（模型引用）/ description（给 LLM 的自描述，必填）/ systemPrompt / maxIters
├── 模型调用层      GenerateOptions { temperature, topP, maxTokens }（与 agentscope GenerateOptions 同名同义）
└── 挂载层（M2）    skillIds / knowledgeBaseIds（扁平）
                   mcpServers [ { serverId, allowedTools? } ]
                   subagents  [ { specId, tools? } ]
```

## 核心决策

1. **行为性判据**：影响智能体行为的配置进版本快照（AgentSpecConfig 全部字段），纯管理元数据（name/icon）留规格主体。由此 `description` 从 `ai_agent_spec` 表列迁入 config JSON——它语义是**给 LLM 的自描述**（agentscope 的 SubagentDeclaration 语义，M2 子智能体挂载时作 spawn 路由依据），影响行为必须随版本固化；列删除避免「列 + JSON 双写」的漂移。列表/详情的 description 出参由服务端从草稿/默认版本快照解析填充。
2. **temperature 归调用层**：agentscope 中温度不在 agent 顶层而在 `GenerateOptions`（模型调用参数，同层还有 topP/maxTokens 等）。首批收 temperature/topP/maxTokens 三件套（跨提供商通用，调试台调参最常用）；penalty 类等 M2+ 按需在 JSON 内加字段，零迁移。
3. **挂载层统一模式 =「引用 + 可选工具白名单」**：对齐 agentscope 的 Toolkit 白名单语义。MCP 挂载单位是**服务而非工具名列表**——工具清单随 server 动态增减，「服务 + 白名单」下清单变化时白名单外新增工具默认不可用（与 US25 默认拒绝精神一致），工具名列表会悬空。`workspaceMode` 明确不收（agentscope 的文件系统 workspace 概念，库内运行语义对不上）。
4. **子智能体是动态 spawn 不是静态挂载**：agentscope 无静态 subAgents builder 参数，运行时经 `agent_spawn` 元工具实例化；M2 挂载语义 = 把子规格转为可 spawn 的定义（挂载级 tools 白名单已留语义位，精确执行语义由工单 11/16 定）。M1 守护：草稿保存时**拒绝挂载自己**（防循环 spawn）；A↔B 互挂检测留 M2。
5. **skills/knowledgeBases 保持扁平**：spec 未给它们挂载级控制语义（知识库的 ragMode/retrieveConfig 是检索配置非白名单），M2 各自工单定义后再结构化——快照用 JSON 列的红利即在于此。

## Considered Options

- description 双写（表列 + 发布时塞进快照 JSON）——被否：发布链路的隐式约定必漂移
- GenerateOptions 全量镜像 agentscope 十余参数——被否：YAGNI，JSON 加字段零成本
- MCP 直接挂工具名列表——被否：发现动态性导致引用悬空
- 收 workspaceMode——被否：文件系统概念与库内运行语义不符

## 连带影响（2026-08-22 重设时落地）

- 存量数据（4 规格 + 4 版本 + 8 旧结构会话）为验收期测试数据，清库重来，不做结构兼容层
- session 聚合装配链同步：`AgentRuntimeConfig.temperature` 升级为整组 `GenerateOptions`（会话级温度覆盖在应用层合成进组，topP/maxTokens 恒取快照值）
- 命令层平铺（表单字段一一对应、服务层组装三层），出参 DTO 反映真实嵌套结构
