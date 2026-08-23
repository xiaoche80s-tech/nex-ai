import request from '@/config/axios'

/** 会话状态（READY/ACTIVE/ASKING/CLOSED） */
export type SessionStatus = 'READY' | 'ACTIVE' | 'ASKING' | 'CLOSED'

/** 调试会话列表项（对应后端 SessionDTO） */
export interface SessionVO {
  id: number
  /** 会话业务键（agentscope 槽位 sessionId） */
  sessionKey: string
  title: string
  type: string
  status: SessionStatus
  specId: number
  versionNo: number | null
  specCode?: string
  createTime: Date
}

/** 挂起审批上下文（RequireUserConfirmEvent 的 ToolUseBlock 翻译） */
export interface PendingConfirmationVO {
  toolCallId: string
  toolName: string
  argumentsJson: string | null
}

/** 创建调试会话命令 */
export interface DebugSessionCreateForm {
  title: string
  specId: number
  versionNo?: number
}

/** 发送消息命令 */
export interface DebugSessionMessageForm {
  content: string
}

/** HITL 审批决定 */
export interface ConfirmDecision {
  toolCallId: string
  toolName: string
  approved: boolean
  argumentsJson?: string
}

/** HITL 审批命令 */
export interface DebugSessionConfirmForm {
  decisions: ConfirmDecision[]
}

/** 会话分页查询参数 */
export interface SessionPageParams extends PageParam {
  specId?: number
  status?: SessionStatus
}

// 发起调试会话
export const createDebugSession = (data: DebugSessionCreateForm) => {
  return request.post({ url: '/ai/session/debug/create', data })
}

// 查询会话分页
export const getSessionPage = (params: SessionPageParams) => {
  return request.get({ url: '/ai/session/page', params })
}

// 会话详情
export const getSession = (id: number) => {
  return request.get<SessionVO>({ url: '/ai/session/' + id })
}

// 会话挂起审批上下文（ASKING 恢复渲染）
export const getPendingConfirmations = (id: number) => {
  return request.get<PendingConfirmationVO[]>({ url: '/ai/session/' + id + '/pending' })
}

// 加载会话历史消息（重开调试台恢复，工单 09）
export const loadSessionHistory = (id: number) => {
  return request.get<Record<string, any>[]>({ url: '/ai/session/' + id + '/history' })
}

// 中断会话（幂等）
export const interruptSession = (id: number) => {
  return request.post({ url: '/ai/session/' + id + '/interrupt' })
}

// HITL 审批（确认/拒绝/改参数）→ SSE 续行流
export const confirmDebugCalls = (id: number, data: DebugSessionConfirmForm) => {
  return request.post({ url: '/ai/session/' + id + '/confirm', data })
}

// 列出会话 workspace 文件（工单 07 文件栏）
export const listWorkspaceFiles = (id: number, path?: string) => {
  return request.get<string[]>({ url: '/ai/session/' + id + '/workspace/files', params: { path } })
}

// 读取会话 workspace 文件内容
export const readWorkspaceFile = (id: number, path: string) => {
  return request.get<string>({ url: '/ai/session/' + id + '/workspace/file', params: { path } })
}
