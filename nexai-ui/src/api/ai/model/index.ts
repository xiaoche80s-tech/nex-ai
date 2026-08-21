import request from '@/config/axios'

/** 模型渠道 VO（对应后端 ChannelDTO） */
export interface ChannelVO {
  id: number | undefined
  name: string
  provider: string
  baseUrl: string
  /** 密钥脱敏展示（前 4 后 4），未配置为 null */
  apiKeyMasked: string | null
  enabled: boolean
  ownerType: string
  createTime: Date
  /** 前端行内状态：启停开关的加载中 */
  statusLoading?: boolean
}

/** 渠道分页查询参数 */
export interface ChannelPageParams extends PageParam {
  name?: string
  provider?: string
  enabled?: boolean
}

/** 渠道创建/更新表单（apiKey 留空表示保留原密钥） */
export interface ChannelSaveForm {
  id?: number
  name: string
  provider: string
  baseUrl: string
  apiKey?: string
}

// 创建渠道
export const createChannel = (data: ChannelSaveForm) => {
  return request.post({ url: '/ai/channel/create', data })
}

// 更新渠道
export const updateChannel = (data: ChannelSaveForm) => {
  return request.put({ url: '/ai/channel/update', data })
}

// 启用/停用渠道
export const updateChannelStatus = (data: { id: number; enabled: boolean }) => {
  return request.put({ url: '/ai/channel/update-status', data })
}

// 删除渠道
export const deleteChannel = (id: number) => {
  return request.delete({ url: '/ai/channel/delete?id=' + id })
}

// 查询渠道分页
export const getChannelPage = (params: ChannelPageParams) => {
  return request.get({ url: '/ai/channel/page', params })
}

// 查询渠道详情
export const getChannel = (id: number) => {
  return request.get({ url: '/ai/channel/get?id=' + id })
}

// 查询启用渠道精简列表（模型页签下拉用）
export const getEnabledChannelList = () => {
  return request.get<ChannelVO[]>({ url: '/ai/channel/simple-list' })
}

/** 渠道连通性测试命令（表单凭据不落库直接探测，供保存前验证） */
export interface ChannelConnectivityTestForm {
  provider: string
  baseUrl: string
  /** 留空且传 channelId 时回退已存渠道密钥 */
  apiKey?: string
  /** 编辑已存渠道时传其编号 */
  channelId?: number
  /** 用于探测的模型标识（真实调用必须携带） */
  modelId: string
}

// 渠道连通性测试（保存前验证密钥与端点）
export const testChannelConnectivity = (data: ChannelConnectivityTestForm) => {
  return request.post<ConnectivityTestResult>({ url: '/ai/channel/test-connectivity', data })
}

/** 模型元数据 VO（对应后端 ModelDTO） */
export interface ModelVO {
  id: number | undefined
  channelId: number
  /** 服务端补充的渠道名称与提供商编码 */
  channelName?: string
  channelProvider?: string
  modelId: string
  name: string
  /** 上下文窗口（tokens），未知为 null */
  contextWindow: number | null
  /** 输入单价（元 / 百万 tokens），未定价为 null */
  inputPrice: number | null
  /** 输出单价（元 / 百万 tokens），未定价为 null */
  outputPrice: number | null
  capabilities: string[]
  enabled: boolean
  createTime: Date
  /** 前端行内状态：启停开关的加载中 */
  statusLoading?: boolean
  /** 前端行内状态：连通性测试按钮的加载中 */
  testing?: boolean
}

/** 模型分页查询参数 */
export interface ModelPageParams extends PageParam {
  channelId?: number
  modelId?: string
  name?: string
  capability?: string
  enabled?: boolean
}

/** 模型创建/更新表单 */
export interface ModelSaveForm {
  id?: number
  channelId: number
  modelId: string
  name: string
  contextWindow?: number
  inputPrice?: number
  outputPrice?: number
  capabilities?: string[]
}

/** 连通性测试结果（对应后端 ConnectivityTestDTO） */
export interface ConnectivityTestResult {
  success: boolean
  durationMs: number
  message: string
}

// 登记模型
export const createModel = (data: ModelSaveForm) => {
  return request.post({ url: '/ai/model/create', data })
}

// 更新模型
export const updateModel = (data: ModelSaveForm) => {
  return request.put({ url: '/ai/model/update', data })
}

// 启用/停用模型
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
  return request.get({ url: '/ai/model/get?id=' + id })
}

// 连通性测试：用所属渠道凭据做一次轻量真实调用
export const testModelConnectivity = (id: number) => {
  return request.post<ConnectivityTestResult>({ url: '/ai/model/test-connectivity?id=' + id })
}

// 查询启用模型精简列表（智能体规格编辑下拉用）
export const getEnabledModelList = () => {
  return request.get<ModelVO[]>({ url: '/ai/model/enabled-list' })
}
