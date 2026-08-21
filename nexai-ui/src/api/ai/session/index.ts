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

// 创建调试会话（绑定规格的已发布版本），返回会话编号
export const createDebugSession = (data: DebugSessionCreateForm) => {
  return request.post<number>({ url: '/ai/session/debug/create', data })
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
}

/** 发消息 SSE 回调 */
export interface SendMessageCallbacks {
  /** 每收到一条原生 AgentEvent JSON 解析后回调 */
  onEvent: (event: AgentEvent) => void
}

/**
 * 发送调试消息并消费 SSE 事件流（POST + text/event-stream，经 fetch-event-source 桥接；
 * 鉴权与租户头与 axios 服务同源注入）。Promise 在流终结（或出错终止）后 resolve。
 */
export const sendDebugMessage = async (
  sessionId: number,
  content: string,
  callbacks: SendMessageCallbacks
): Promise<void> => {
  await fetchEventSource(`${config.base_url}/ai/session/${sessionId}/message`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: 'Bearer ' + getAccessToken(),
      'tenant-id': getTenantId()
    },
    body: JSON.stringify({ content }),
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
      // 抛出即终止内置重试：调试消息是显式用户动作，失败由用户重发而非自动重试
      throw err
    }
  })
}
