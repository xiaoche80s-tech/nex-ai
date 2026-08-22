# AgentSpec 配置三层分组（语义对齐 agentscope）

AgentSpec 的配置值对象（草稿与版本快照共用）按 agentscope 的分层组织为**三层 + 执行环境层**，并以「影响行为与否」为判据划分配置与元数据的归属。2026-08-22 经 grill 采访定案（工单 05 结构重设）；**2026-08-23 两次修订**：①对齐判据从「结构镜像」改口为「**语义对齐 + 运行时映射**」——AgentSpecConfig 是**平台配置模型**而非框架配置的镜像，与 agentscope 的命名/类型/形态差异统一收敛到 M2 网关映射层翻译；运行时基座同日切换 agentscope-harness（ADR-0007）。②新增**执行环境层**（workspace/沙箱/执行能力按规格配置）与 **spec_code** 业务编码。

修订动因：源码核验发现初版引用的挂载机制（SubagentDeclaration/agent_spawn/ToolsConfig/SkillFilter）全部位于 agentscope-harness，而运行时嵌入的是 core 的 ReActAgent——参照物错位；且「引用外部规格 + 版本快照」是平台层概念，agentscope 无对应物（SubagentDeclaration 是自包含声明，主 Agent 无声明式 spec，只有 builder）。

## 分层结构

```
AgentSpecConfig
├── agent 层        modelId（模型引用）/ description（给 LLM 的自描述，必填）/ systemPrompt / maxIters
├── 模型调用层      GenerateOptions { temperature, topP, maxTokens }（与 agentscope GenerateOptions 同名同义）
├── 挂载层（M2）    skillIds（Long ID，扁平）
│                   mcpServers [ { serverId, allowedTools? } ]
│                   subagents  [ { specId, tools? } ]
└── 执行环境层      ExecutionEnvConfig { workspaceEnabled, sandboxEnabled, capabilities: [SHELL|PYTHON|NODE] }
```

与 2026-08-22 版差异：**删除 knowledgeBaseIds**（agentscope 2.0 无对应物，RAG 检索走自建链路 ADR-0004，工单 13 定义时零迁移可加）；**新增执行环境层**（2026-08-23）。

## 核心决策

1. **行为性判据**：影响智能体行为的配置进版本快照（AgentSpecConfig 全部字段），纯管理元数据留规格主体。`description` 从表列迁入 config JSON——语义是**给 LLM 的自描述**（经核验：harness 的 SubagentsMiddleware 将其渲染进系统提示作委派决策依据），影响行为必须随版本固化；列删除避免「列 + JSON 双写」漂移。列表/详情出参由服务端从草稿/默认版本快照解析填充。
2. **temperature 归调用层**：经核验 agentscope 的 temperature/topP/maxTokens 同名同义且不在 agent builder 顶层。首批收三件套；penalty 类等 M2+ 按需在 JSON 内加字段，零迁移。
3. **挂载层统一模式 =「引用 + 可选工具白名单」，命名保持平台语义**：白名单语义经核验镜像一致（agentscope MCP 侧 `enableTools` 非空即白名单、子代理侧 `SubagentDeclaration.tools` 空 = 继承全部，与我们「空 = 全部」对应）。MCP 挂载单位是**服务而非工具名列表**。**字段名不追框架**（enableTools 是框架「注册时启用」动作语义），差异由映射层翻译。
4. **子智能体是动态 spawn 不是静态挂载**：运行时经 `agent_spawn` 元工具实例化（harness 机制；切换后基座即具备，内置 general-purpose 子代理开箱可用——见 ADR-0007）。`SubagentMount{specId, tools}` 是**平台层引用模型**，M2 由网关解析子规格快照展开为自包含 SubagentDeclaration。M1 守护：拒绝挂载自己；A↔B 互挂检测留 M2。
5. **skills 保持扁平 Long ID 引用**：agentscope 挂载用名字 allowlist（SkillFilter.only），平台内技能以主键引用（ai_skill，工单 10），映射层按 ID 构造稳定注册名翻译。
6. **执行环境层（2026-08-23 新增，推翻原「workspaceMode 不收」）**：执行能力（文件读写/shell/代码执行）是智能体规格语义的一部分，进版本快照：
   - `workspaceEnabled=false`：纯对话智能体，零落盘（无文件工具、无 workspace、transcript 禁用）；
   - `workspaceEnabled=true`：per-spec 常驻 workspace（**布局按归属层级**，见决策 8），文件六件套开启（隔离机制见 ADR-0007）；
   - `sandboxEnabled` 仅 workspaceEnabled=true 时有意义：true → Docker 沙箱容器（文件与执行都在容器内），false → 本地落盘但**禁执行能力**；
   - `capabilities: [SHELL, PYTHON, NODE]` 仅 sandboxEnabled=true 可非空——**非沙箱下执行能力在宿主机裸奔（官方文档明示生产禁跑不可信代码），保存草稿时校验拒绝**；
   - 校验规则：`!workspaceEnabled → sandboxEnabled=false ∧ capabilities=∅`；`!sandboxEnabled → capabilities=∅`。
   - 能力→镜像映射放服务级 yaml（环境事实，不进快照）；无 docker 环境装配报错（见 ADR-0007）。
7. **spec_code 业务编码（2026-08-23 新增，同日归属层级轮修订唯一性 scope）**：AgentSpec 引入 slug 编码（`^[a-z][a-z0-9-]{1,63}$` 手填必填），**创建后不可变**——数据库主键不外溢到文件系统/日志/运行时标识。用途：workspace 目录段、装配 agentName（`{spec_code}-v{versionNo}`，替换原 `spec-{specId}-v{versionNo}`）。归属规格主体元数据（同 name/icon），**不进版本快照**——workspace 本来就 per-spec 跨版本共享。**唯一性按归属层级**（见决策 8）：平台级全局唯一、租户级租户内唯一、用户级同一用户内唯一；唯一索引 = `owner_level + tenant_id + owner_user_id + spec_code` 组合。
8. **归属层级三级（2026-08-23 新增）**：AgentSpec 增加 `owner_level ∈ { PLATFORM, TENANT, USER }` + `owner_user_id`（用户级 = 创建者）。「spec 归属谁，workspace 挂在谁的树下」：
   - **用户级**：仅创建者可见可编辑；workspace `{root}/t{tenantId}/u{userid}/{specCode}/`——整棵 workspace 即创建者私享，物理目录即隔离（IsolationScope.AGENT，无 namespace）；
   - **租户级**（默认）：租户内可见、创建者/租户管理员可编辑；workspace `{root}/t{tenantId}/{specCode}/`——种子租户内一份、记忆 per-user（IsolationScope.USER，`<userId>/` namespace）；
   - **平台级**：全租户可见只读、仅平台运营方编辑（同工单 24 平台渠道模式）；workspace `{root}/platform/{specCode}/`——种子跨租户一份、运行时数据按租户+用户复合隔离（IsolationScope.USER，namespace `t{tenantId}-u{userId}`）；
   - **M1 实现范围**：租户级 + 用户级开放；平台级字段先建、跨租户可见性查询与运营入口 M2+（与工单 24 同期，沿用其平台共享数据机制）。

## 实施补记（2026-08-23，随工单 05 重写二落地）

- **spec_code 唯一索引形态**：PG 部分唯一索引 `uk_ai_agent_spec_code (owner_level, tenant_id, COALESCE(owner_user_id, 0), spec_code) WHERE deleted = 0`——COALESCE 使非用户级的 owner_user_id=NULL 参与判重（PG 默认 NULL 不判等会放过重复）；仅存活行参与（逻辑删除后同名可重建）。H2 测试库不支持部分索引，不建约束、应用层预校验兜底（同 ai_skill_resource 先例）。
- **执行环境 JSON 缺省兼容**：draft/snapshot JSON 中 `executionEnv` 缺失（null）= 全关（纯对话）——存量快照无需迁移即获得纯对话语义。
- **纯对话矩阵口径**：无 workspace 规格的 memory 工具同样禁用（memory_save 是落盘入口，见 ADR-0007 实施补记）；「memory 四件套存在」的通用断言适用于 workspace 用例。

## Considered Options

- description 双写（表列 + 发布时塞进快照 JSON）——被否：发布链路的隐式约定必漂移
- GenerateOptions 全量镜像 agentscope 十余参数——被否：YAGNI，JSON 加字段零成本
- MCP 直接挂工具名列表——被否：发现动态性导致引用悬空
- 字段名同名化（allowedTools → enableTools）——被否：与「平台配置模型」定位自相矛盾；映射层本就存在，多翻译一个字段名无架构成本
- 保留 knowledgeBaseIds 空预留——被否：悬空字段暗示不存在的框架落点
- workspaceMode 不收（2026-08-22 决策）——被 2026-08-23 修订推翻：执行能力是规格语义而非框架内部细节；「不收」的前提（零落盘）同日被推翻
- spec_code 用数据库主键代替（目录/agentName 用 specId）——被否：主键外溢到文件系统与日志，不可读且迁移敏感
- spec_code 创建后可改（改名迁移目录）——被否：迁移逻辑不值当；不可变更简单
- 用户组级（USER_GROUP）归属层——被否：系统无用户组实体（bpm UserGroup 随模块注释、dept 是行政树非协作单元），以用户级替代；组级若将来需要再立项组实体

## 与框架差异的收敛点（M2 网关映射层职责）

| 平台模型 | agentscope（harness） | 翻译 |
|---|---|---|
| `mcpServers[{serverId, allowedTools}]` | `ToolsConfig.mcpServers: Map<serverId, McpServerConfig{enableTools}>` | 字段名 + List→Map |
| `subagents[{specId, tools}]` | `SubagentDeclaration`（自包含声明） | 解析子规格快照展开；tools 白名单直传 |
| `skillIds: List<Long>` | `skills: List<String>`（SkillFilter.only） | ID → 稳定注册名 |
| `ExecutionEnvConfig` | `HarnessAgent.Builder.workspace/filesystem(DockerFilesystemSpec)/LocalFilesystemSpec` + disable 开关 + toolkit 注册 | 三开关组合 → builder 配置矩阵 |

M2 映射现成挂点（经核验）：`presetParameters`（平台预注入参数，从 LLM 可见 schema 剔除——租户/密钥注入）、ToolGroup `active/scope`（可见性策略）、`PermissionContextState` 规则表（deny→ask→自检→allow，工单 16 权限引擎挂点）、`createSkillToolGroup`（技能加载自动激活工具组，工单 11 可用）。

## 连带影响

- 存量数据清库重来（2026-08-22 重设时已执行，不做结构兼容层）
- session 装配链：`AgentRuntimeConfig` 含 agent 层 + 调用层 + 执行环境层；挂载层字段 M2 经映射层进运行时
- 命令层平铺（表单字段一一对应、服务层组装分层），出参 DTO 反映真实嵌套结构
- 运行时基座、落盘布局、沙箱、隔离机制：见 ADR-0007
