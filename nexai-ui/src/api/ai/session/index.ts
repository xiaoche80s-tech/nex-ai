import { fetchEventSource } from '@microsoft/fetch-event-source'
import request from '@/config/axios'
import { config } from '@/config/axios/config'
import { getAccessToken, getTenantId } from '@/utils/auth'

/** 会话列表项（对应后端 SessionDTO） */
export interface SessionVO {
  id: number
  /** 会话标识（agentscope 状态存储寻址键） */
  sessionKey: string
  /** 会话类型编码：10 调试 / 20 终端用户（M2） */
  type: number
  specId: number
  versionNo: number
  title: string | null
  /** 推理参数覆盖：最大迭代轮数（null 沿用版本快照） */
  overrideMaxIters: number | null
  /** 推理参数覆盖：温度（null 沿用版本快照） */
  overrideTemperature: number | null
  messageRounds: number
  createTime: Date
}

/** 会话分页查询参数 */
export interface SessionPageParams extends PageParam {
  type?: number
  specId?: number
  versionNo?: number
}

/** 新建调试会话表单 */
export interface DebugSessionCreateForm {
  specId: number
  /** 缺省绑定规格当前默认版本 */
  versionNo?: number
  title?: string
}

/** 克隆会话表单（复制对话历史为新调试会话，微调推理参数重跑） */
export interface DebugSessionCloneForm {
  title?: string
  maxIters?: number
  temperature?: number
}

/** HITL 确认决定（对应后端 DebugSessionConfirmCommand.Decision） */
export interface ToolCallConfirmDecision {
  toolCallId: string
  toolName: string
  /** 工具参数 JSON（修改参数后批准时携带改后完整参数） */
  arguments: string
  approved: boolean
}

// 创建调试会话（绑定规格的已发布版本），返回会话编号
export const createDebugSession = (data: DebugSessionCreateForm) => {
  return request.post<number>({ url: '/ai/session/debug/create', data })
}

// 克隆会话为新调试会话（复制对话历史，可微调推理参数），返回新会话编号
export const cloneSession = (id: number, data: DebugSessionCloneForm) => {
  return request.post<number>({ url: `/ai/session/${id}/clone`, data })
}

// 中断会话正在运行的事件流（幂等：无运行中的流返回 false）
export const interruptSession = (id: number) => {
  return request.post<boolean>({ url: `/ai/session/${id}/interrupt` })
}

// 查询会话分页（调试台左侧列表数据源）
export const getSessionPage = (params: SessionPageParams) => {
  return request.get<PageResult<SessionVO[]>>({ url: '/ai/session/page', params })
}

/**
 * 原生 AgentEvent（后端 agentscope codec 序列化的 JSON，spec API 契约：不做 AG-UI 转换）。
 * 这里只声明调试台渲染消费的子集字段，未列出的字段按需再补。
 */
export interface AgentEvent {
  type: string
  /** 文本/思考增量（TEXT_BLOCK_DELTA / THINKING_BLOCK_DELTA / TOOL_RESULT_TEXT_DELTA） */
  delta?: string
  /** 工具调用标识与名称（TOOL_CALL_* / TOOL_RESULT_*） */
  toolCallId?: string
  toolCallName?: string
  /** 工具结果终态（TOOL_RESULT_END）：done 默认 / error */
  state?: string
  /** 模型调用用量（MODEL_CALL_END） */
  usage?: { inputTokens: number; outputTokens: number }
  /** 最终消息（AGENT_RESULT）：result.content[].text */
  result?: { content?: Array<{ type: string; text?: string }> }
  /** 运行中出错的降级事件（后端 SESSION_ERROR） */
  message?: string
  /** 确认请求（REQUIRE_USER_CONFIRM）：待人工回应的工具调用清单 */
  replyId?: string
  toolCalls?: Array<{ id: string; name: string; content?: string }>
}

/** SSE 事件流回调 */
export interface EventStreamCallbacks {
  /** 每收到一条原生 AgentEvent JSON 解析后回调 */
  onEvent: (event: AgentEvent) => void
}

/**
 * POST + text/event-stream 通用消费（经 fetch-event-source 桥接；鉴权与租户头与 axios 服务同源注入）。
 * Promise 在流终结（或出错终止）后 resolve。
 */
const postEventStream = async (url: string, body: unknown, callbacks: EventStreamCallbacks) => {
  await fetchEventSource(`${config.base_url}${url}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: 'Bearer ' + getAccessToken(),
      'tenant-id': getTenantId()
    },
    body: JSON.stringify(body),
    onmessage: (ev) => {
      if (!ev.data) {
        return
      }
      try {
        callbacks.onEvent(JSON.parse(ev.data))
      } catch {
        // 非预期帧（如心跳/半截 JSON）直接丢弃，不打断事件流
      }
    },
    onerror: (err) => {
      // 抛出即终止内置重试：调试动作是显式用户操作，失败由用户重发而非自动重试
      throw err
    }
  })
}

/** 发送调试消息并消费 SSE 事件流 */
export const sendDebugMessage = async (
  sessionId: number,
  content: string,
  callbacks: EventStreamCallbacks
): Promise<void> => {
  await postEventStream(`/ai/session/${sessionId}/message`, { content }, callbacks)
}

/** 回应工具确认请求（HITL 三态：批准 / 改参数后批准 / 拒绝）并消费后续事件流 */
export const confirmToolCalls = async (
  sessionId: number,
  decisions: ToolCallConfirmDecision[],
  callbacks: EventStreamCallbacks
): Promise<void> => {
  await postEventStream(`/ai/session/${sessionId}/confirm`, { decisions }, callbacks)
}
