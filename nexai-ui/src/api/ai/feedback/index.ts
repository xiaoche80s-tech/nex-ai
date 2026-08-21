import request from '@/config/axios'

/** 问题反馈 VO（对应后端 FeedbackDTO） */
export interface FeedbackVO {
  id: number | undefined
  content: string
  screenshotUrls: string[] | null
  sessionId: string | null
  status: number
  submitterId: number
  createTime: Date
}

/** 问题反馈分页查询参数 */
export interface FeedbackPageParams extends PageParam {
  status?: number
  content?: string
}

// 提交问题反馈
export const createFeedback = (data: {
  content: string
  screenshotUrls?: string[]
  sessionId?: string
}) => {
  return request.post({ url: '/ai/feedback/create', data })
}

// 查询问题反馈分页
export const getFeedbackPage = (params: FeedbackPageParams) => {
  return request.get({ url: '/ai/feedback/page', params })
}

// 查询问题反馈详情
export const getFeedback = (id: number) => {
  return request.get({ url: '/ai/feedback/get?id=' + id })
}

// 流转问题反馈处理状态
export const transitionFeedback = (data: { id: number; targetStatus: number }) => {
  return request.put({ url: '/ai/feedback/transition', data })
}
