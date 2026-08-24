import request from '@/config/axios'

/** 模型调用参数（与 agentscope GenerateOptions 同名同义） */
export interface GenerateOptions {
  temperature: number | null
  topP: number | null
  maxTokens: number | null
}

/** 工具来源（MCP = MCP Server；PLATFORM = 平台工具库 @Tool 业务工具；内置工具随执行环境启用不走挂载） */
export type ToolSource = 'MCP' | 'PLATFORM'

/** 工具挂载（来源 + 引用 + 放行面 + 敏感面，编辑面后置） */
export interface ToolMount {
  source: ToolSource
  /** MCP = MCP Server 编号；PLATFORM = 平台工具库条目编号 */
  sourceId: number
  /** 放行面：空 = 该来源全部工具 */
  allowedTools?: string[]
  /** 敏感面：名单内工具调用前挂起等人工审批（HITL）；allowedTools 非空时须为其子集 */
  sensitiveTools?: string[]
}

/** 执行能力（仅沙箱模式可选） */
export type ExecutionCapability = 'SHELL' | 'PYTHON' | 'NODE'

/** 执行环境配置（workspace / 沙箱 / 执行能力，随版本快照固化） */
export interface ExecutionEnv {
  workspaceEnabled: boolean
  sandboxEnabled: boolean
  capabilities: ExecutionCapability[]
}

/** 智能体规格配置（草稿与版本快照共用，按 agentscope「三层 + 执行环境层」分组） */
export interface AgentSpecConfig {
  // —— agent 层 ——
  modelId: number | null
  /** 自描述（列表展示用） */
  description: string | null
  systemPrompt: string | null
  /** 推理参数：最大迭代轮数，null 表示运行时取默认 */
  maxIters: number | null
  // —— 模型调用层 ——
  generateOptions: GenerateOptions | null
  // —— 挂载层（编辑面后置）——
  skillIds?: number[]
  tools?: ToolMount[]
  // —— 执行环境层 ——
  /** null 表示全关（纯对话智能体） */
  executionEnv?: ExecutionEnv | null
}

/** 归属层级（MVP 开放 TENANT/USER，平台级后置） */
export type OwnerLevel = 'TENANT' | 'USER'

/** 智能体规格列表项（对应后端 AgentSpecDTO） */
export interface AgentSpecVO {
  id: number
  name: string
  /** 业务编码（创建后不可变） */
  specCode: string
  /** 归属层级 */
  ownerLevel: string
  /** 归属用户编号（用户级 = 创建者） */
  ownerUserId: number | null
  /** 描述（服务端从草稿 JSON 解析填充） */
  description: string | null
  icon: string | null
  /** 是否有未发布草稿（当前无发布能力时恒为草稿态） */
  hasDraft: boolean
  /** 当前生效版本号（当前版本指针，运行寻址），null = 从未发布 */
  currentVersionNo: number | null
  createTime: Date
}

/** 版本快照列表项（不可变快照的元信息，不含全量配置） */
export interface AgentSpecVersionVO {
  id: number
  /** 版本号（规格内严格递增，1 起；发布后不可变，运行寻址用） */
  versionNo: number
  /** 发布备注 */
  note: string | null
  /** 是否为当前生效版本 */
  current: boolean
  createTime: Date
}

/** 发布命令（把当前草稿固化为不可变版本快照并推进当前版本指针） */
export interface AgentSpecPublishForm {
  id: number
  /** 发布备注 */
  note?: string
}

/** 切换当前版本命令（仅回退当前版本指针，快照本身不可变） */
export interface AgentSpecSwitchVersionForm {
  id: number
  /** 目标版本号 */
  versionNo: number
}

/** 规格分页查询参数 */
export interface AgentSpecPageParams extends PageParam {
  name?: string
  specCode?: string
}

/** 规格创建表单。调用参数平铺，提交时由服务端组装为分层结构 */
export interface AgentSpecCreateForm {
  name: string
  /** 创建后不可变 */
  specCode: string
  /** 默认租户级；创建后不可变 */
  ownerLevel?: OwnerLevel
  icon?: string
  modelId?: number
  description?: string
  systemPrompt?: string
  maxIters?: number
  temperature?: number
  topP?: number
  maxTokens?: number
  /** 挂载层：技能引用（随版本快照固化，发布后生效） */
  skillIds?: number[]
  /** 挂载层：工具挂载（MCP Server / 平台工具库，随版本快照固化） */
  tools?: ToolMount[]
  workspaceEnabled?: boolean
  sandboxEnabled?: boolean
  capabilities?: ExecutionCapability[]
}

// 创建规格（携带首个草稿）
export const createSpec = (data: AgentSpecCreateForm) => {
  return request.post({ url: '/ai/spec/create', data })
}

// 查询规格分页
export const getSpecPage = (params: AgentSpecPageParams) => {
  return request.get({ url: '/ai/spec/page', params })
}

// 发布规格版本（返回新版本号）
export const publishSpec = (data: AgentSpecPublishForm) => {
  return request.post({ url: '/ai/spec/publish', data })
}

// 查询规格版本列表
export const getSpecVersionPage = (specId: number) => {
  return request.get({ url: '/ai/spec/version-page', params: { specId } })
}

// 切换当前版本（回退指针）
export const switchSpecVersion = (data: AgentSpecSwitchVersionForm) => {
  return request.put({ url: '/ai/spec/switch-version', data })
}
