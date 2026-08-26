# MCP 工具治理：ai_tool 注册表与两级敏感定性

MCP Server 的工具从 server 行内 JSON 列（allowed_tools/available_tools）治理升级为独立注册表 `ai_tool`：每行一个工具的快照（名称/描述/inputSchema）与治理标记（sensitive/enabled），由探测同步写入（upsert 保留人工标记、消失软删、复活恢复），仅覆盖 MCP 来源——平台内置工具维持 PlatformToolEntry 稳定编号不落库（ADR/工单 13 边界不变）。敏感定性采用两级：server 级（注册表标记，全局默认）∪ 挂载级（ToolMount 加标），任一命中即 ASK，挂载级只能加严不能放宽；同时非敏感 MCP 工具显式 ALLOW——审批面唯一来源是敏感标记，agentscope 对 readOnlyHint≠true 工具的默认 ASK 被有意覆盖（放宽而非延续）。

## Considered Options

- 敏感归属：仅挂载级（已实现）→ 无法表达"此工具本身就危险，所有挂载方都该审批"；仅 server 级 → 推翻已实现的 ToolMount 链路且丢失按挂载细化能力。取并集单向加严。
- 工具落库范围：统一目录（平台工具也迁入）→ 推翻工单 13 且平台工具注册期即可枚举、无同步需求；仅 JSON 列 → 工具级属性不可查询不可外键。取 MCP-only 注册表。
- 白名单语义：保留「空=全部」与逐工具勾选并存 → 必然歧义；改为启用标记两态，未启用即不暴露。

## Consequences

- `ai_mcp_server.allowed_tools`/`available_tools` 双列退役（运行时 enableTools 改从注册表 enabled 行导出），旧「白名单须为探测清单子集」校验随之删除。
- 运维探测刷新进来的新工具默认启用、立即暴露（探测权限码即门槛）——"白名单是收紧手段而非暴露门槛"语义的延续。
- 显式 ALLOW 的实现须先验证 PermissionEngine 求值序：工具自身 checkPermissions 返回 ask 时 allow 规则是否被短路（见工单 34 风险注记）。
