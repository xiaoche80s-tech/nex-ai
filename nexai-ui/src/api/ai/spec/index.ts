import request from '@/config/axios'

/** 智能体规格配置（草稿与版本快照共用结构，对应后端 AgentSpecConfigDTO） */
export interface AgentSpecConfig {
  modelId: number
  /** 服务端补充的模型显示名 */
  modelName?: string
  systemPrompt: string | null
  /** 推理参数：最大迭代轮数，null 表示运行时取默认 */
  maxIters: number | null
  /** 推理参数：温度，null 表示运行时取默认 */
  temperature: number | null
  /** M2 预留引用列表 */
  skillIds?: number[]
  knowledgeBaseIds?: number[]
  mcpServerIds?: number[]
  subagentSpecIds?: number[]
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

/** 智能体规格列表项（对应后端 AgentSpecDTO） */
export interface AgentSpecVO {
  id: number | undefined
  name: string
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

/** 规格创建/编辑表单（编辑即覆盖草稿；无草稿时生成新草稿） */
export interface AgentSpecSaveForm {
  id?: number
  name: string
  description?: string
  icon?: string
  modelId: number
  systemPrompt?: string
  maxIters?: number
  temperature?: number
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
