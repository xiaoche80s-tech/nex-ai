import request from '@/config/axios'

/** 模型调用参数（与 agentscope GenerateOptions 同名同义） */
export interface GenerateOptions {
  temperature: number | null
  topP: number | null
  maxTokens: number | null
}

/** MCP 服务挂载（服务 + 工具白名单，M2 预留） */
export interface McpServerMount {
  serverId: number
  /** 空 = 该服务全部工具 */
  allowedTools?: string[]
}

/** 子智能体挂载（规格 + 工具白名单，M2 预留） */
export interface SubagentMount {
  specId: number
  /** 空 = 继承父智能体全部工具 */
  tools?: string[]
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
  modelId: number
  /** 服务端补充的模型显示名 */
  modelName?: string
  /** 给 LLM 的自描述（用于展示与子智能体路由），必填 */
  description: string
  systemPrompt: string | null
  /** 推理参数：最大迭代轮数，null 表示运行时取默认 */
  maxIters: number | null
  // —— 模型调用层 ——
  generateOptions: GenerateOptions | null
  // —— 挂载层（M2 预留）——
  skillIds?: number[]
  mcpServers?: McpServerMount[]
  subagents?: SubagentMount[]
  // —— 执行环境层 ——
  /** null 表示全关（纯对话智能体） */
  executionEnv?: ExecutionEnv | null
}

/** 智能体规格版本（不可变快照，对应后端 AgentSpecVersionDTO） */
export interface AgentSpecVersionVO {
  id: number
  specId: number
  versionNo: number
  remark: string | null
  config: AgentSpecConfig
  createTime: Date
}

/** 归属层级（M1 开放 TENANT/USER，平台级 M2+） */
export type OwnerLevel = 'TENANT' | 'USER'

/** 智能体规格列表项（对应后端 AgentSpecDTO） */
export interface AgentSpecVO {
  id: number | undefined
  name: string
  /** 业务编码（创建后不可变） */
  specCode: string
  /** 归属层级 */
  ownerLevel: string
  /** 归属用户编号（用户级 = 创建者） */
  ownerUserId: number | null
  /** 自描述（服务端从草稿/默认版本快照解析填充） */
  description: string | null
  icon: string | null
  /** 已发布的最新版本号，从未发布为 0 */
  latestVersionNo: number
  /** 当前默认版本号，从未发布为 null */
  currentVersionNo: number | null
  hasDraft: boolean
  /** 行内补充的模型显示名（草稿优先，无草稿为默认版本快照） */
  modelName?: string
  createTime: Date
  /** 前端行内状态：发布按钮的加载中 */
  publishing?: boolean
}

/** 智能体规格详情（对应后端 AgentSpecDetailDTO） */
export interface AgentSpecDetailVO extends AgentSpecVO {
  /** 草稿配置，无草稿为 null（编辑表单优先按此预填） */
  draft: AgentSpecConfig | null
  /** 当前默认版本快照，从未发布为 null（无草稿时编辑表单按此预填） */
  currentVersion: AgentSpecVersionVO | null
}

/** 规格分页查询参数 */
export interface AgentSpecPageParams extends PageParam {
  name?: string
}

/** 规格创建/编辑表单（编辑即覆盖草稿；无草稿时生成新草稿）。调用参数平铺、提交时由服务端组装分层结构 */
export interface AgentSpecSaveForm {
  id?: number
  name: string
  /** 仅创建时可填（创建后不可变，编辑态只读展示） */
  specCode?: string
  /** 仅创建时可选（默认租户级；创建后不可变） */
  ownerLevel?: OwnerLevel
  icon?: string
  modelId: number | undefined
  description: string
  systemPrompt?: string
  maxIters?: number
  temperature?: number
  topP?: number
  maxTokens?: number
  workspaceEnabled?: boolean
  sandboxEnabled?: boolean
  capabilities?: ExecutionCapability[]
}

// 创建规格（携带首个草稿）
export const createSpec = (data: AgentSpecSaveForm) => {
  return request.post({ url: '/ai/spec/create', data })
}

// 编辑规格（更新主体信息并覆盖草稿）
export const updateSpec = (data: AgentSpecSaveForm) => {
  return request.put({ url: '/ai/spec/update', data })
}

// 发布规格：草稿固化为不可变新版本，返回新版本号
export const publishSpec = (data: { id: number; remark?: string }) => {
  return request.post<number>({ url: '/ai/spec/publish', data })
}

// 切换默认版本（回滚/迭代入口）
export const switchDefaultVersion = (data: { id: number; versionNo: number }) => {
  return request.put({ url: '/ai/spec/switch-default-version', data })
}

// 删除规格及其全部版本
export const deleteSpec = (id: number) => {
  return request.delete({ url: '/ai/spec/delete?id=' + id })
}

// 查询规格分页
export const getSpecPage = (params: AgentSpecPageParams) => {
  return request.get({ url: '/ai/spec/page', params })
}

// 查询规格详情（含草稿与当前默认版本快照）
export const getSpec = (id: number) => {
  return request.get<AgentSpecDetailVO>({ url: '/ai/spec/get?id=' + id })
}

// 查询版本历史（按版本号倒序）
export const getVersionList = (specId: number) => {
  return request.get<AgentSpecVersionVO[]>({ url: '/ai/spec/version/list?specId=' + specId })
}
