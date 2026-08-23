import request from '@/config/axios'

/** 渠道归属维度（MVP 固定 tenant，platform 预留） */
export type ChannelOwnerType = 'tenant' | 'platform'

/** 模型渠道（BYOK）：对应后端 ChannelDTO */
export interface ChannelVO {
  id: number
  name: string
  /** 提供商类型编码（ai_channel_provider 字典） */
  provider: string
  baseUrl: string
  /** 密钥脱敏展示（前 4 后 4），未配置为 null */
  apiKeyMasked: string | null
  /** 是否已配置密钥（编辑时提示「已配置，留空保留」） */
  apiKeyConfigured: boolean
  enabled: boolean
  ownerType: ChannelOwnerType | string
  createTime: Date
}

/** 模型元数据：对应后端 ModelDTO */
export interface ModelVO {
  id: number
  channelId: number
  /** 所属渠道名称（服务端补充） */
  channelName?: string
  /** 模型标识（传给提供商的 ID，如 gpt-4o） */
  modelId: string
  name: string
  /** 上下文窗口（tokens），未知为 null */
  contextWindow: number | null
  /** 输入单价（元 / 百万 tokens），未定价为 null */
  inputPrice: number | null
  outputPrice: number | null
  enabled: boolean
  createTime: Date
}

/** 渠道分页查询参数 */
export interface ChannelPageParams extends PageParam {
  name?: string
  provider?: string
  enabled?: boolean
}

/** 模型分页查询参数 */
export interface ModelPageParams extends PageParam {
  channelId?: number
  modelId?: string
  name?: string
  enabled?: boolean
}

/** 渠道创建/更新表单。编辑时 apiKey 留空表示保留原密钥 */
export interface ChannelSaveForm {
  id?: number
  name: string
  provider: string
  baseUrl: string
  apiKey?: string
}

/** 模型创建/更新表单 */
export interface ModelSaveForm {
  id?: number
  /** 必填（后端 @NotNull 校验），新建未选时为 undefined */
  channelId?: number
  modelId: string
  name: string
  contextWindow?: number
  inputPrice?: number
  outputPrice?: number
}

/** 连通性探测命令（表单即测；channelId 携带且密钥留空时回退已存密钥） */
export interface ConnectivityTestForm {
  channelId?: number
  provider: string
  baseUrl: string
  apiKey?: string
  modelId: string
}

/** 连通性探测结果 */
export interface ConnectivityTestResult {
  success: boolean
  durationMs: number
  message: string
}

// ==================== 渠道 ====================

// 创建渠道
export const createChannel = (data: ChannelSaveForm) => {
  return request.post({ url: '/ai/channel/create', data })
}

// 更新渠道（apiKey 留空保留原密钥）
export const updateChannel = (data: ChannelSaveForm) => {
  return request.put({ url: '/ai/channel/update', data })
}

// 启停渠道
export const updateChannelStatus = (data: { id: number; enabled: boolean }) => {
  return request.put({ url: '/ai/channel/update-status', data })
}

// 删除渠道（级联删除其下模型）
export const deleteChannel = (id: number) => {
  return request.delete({ url: '/ai/channel/delete?id=' + id })
}

// 查询渠道分页
export const getChannelPage = (params: ChannelPageParams) => {
  return request.get({ url: '/ai/channel/page', params })
}

// 查询渠道详情（密钥脱敏）
export const getChannel = (id: number) => {
  return request.get<ChannelVO>({ url: '/ai/channel/get?id=' + id })
}

// 启用渠道简要列表（模型表单的渠道下拉）
export const getEnabledChannelList = () => {
  return request.get<ChannelVO[]>({ url: '/ai/channel/simple-list' })
}

// 连通性探测（表单即测，不落库）
export const testChannelConnectivity = (data: ConnectivityTestForm) => {
  return request.post<ConnectivityTestResult>({ url: '/ai/channel/connectivity-test', data })
}

// ==================== 模型 ====================

// 登记模型
export const createModel = (data: ModelSaveForm) => {
  return request.post({ url: '/ai/model/create', data })
}

// 更新模型
export const updateModel = (data: ModelSaveForm) => {
  return request.put({ url: '/ai/model/update', data })
}

// 启停模型
export const updateModelStatus = (data: { id: number; enabled: boolean }) => {
  return request.put({ url: '/ai/model/update-status', data })
}

// 删除模型
export const deleteModel = (id: number) => {
  return request.delete({ url: '/ai/model/delete?id=' + id })
}

// 查询模型分页
export const getModelPage = (params: ModelPageParams) => {
  return request.get({ url: '/ai/model/page', params })
}

// 查询模型详情
export const getModel = (id: number) => {
  return request.get<ModelVO>({ url: '/ai/model/get?id=' + id })
}
