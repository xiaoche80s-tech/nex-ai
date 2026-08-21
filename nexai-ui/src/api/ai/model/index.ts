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
