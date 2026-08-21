import { reactive } from 'vue'
import type { AgentEvent } from '@/api/ai/session'

/** 工具调用卡片（TOOL_CALL_* 建立，TOOL_RESULT_* 回填；参数明细 M2 挂载工具后按需补充） */
export interface ToolCallCard {
  toolCallId: string
  name: string
  resultText: string
  state: 'running' | 'done' | 'error'
}

/** 助手回合：一次 AGENT_START → AGENT_END 的事件折叠结果 */
export interface AssistantTurn {
  /** 回答正文（TEXT_BLOCK_DELTA 增量拼接） */
  text: string
  /** 思考过程（THINKING_BLOCK_DELTA 增量拼接），空则不渲染思考块 */
  thinking: string
  tools: ToolCallCard[]
  /** 本回合是否已终结（AGENT_RESULT/AGENT_END/出错） */
  finished: boolean
  /** 运行中出错的提示（SESSION_ERROR 或流异常） */
  error: string | null
}

/** 用户消息条目 */
export interface UserEntry {
  kind: 'user'
  text: string
}

/** 对话区条目 = 用户消息 | 助手回合 */
export type ChatEntry = UserEntry | AssistantTurn

/** 会话累计用量（跨回合累加，MODEL_CALL_END 驱动） */
export interface ChatUsage {
  inputTokens: number
  outputTokens: number
}

/**
 * 原生 AgentEvent 流 → 对话渲染模型的折叠器。
 *
 * 只消费调试台关心的九类事件，其余（MODEL_CALL_START、块 START/END、AGENT_START 等
 * 纯生命周期标记）不产生独立 UI——块边界由渲染层按内容有无处理，
 * 保证「事件流可序列化还原为对话」这一外部行为可断言。
 */
export const useEventStream = () => {
  const entries = reactive<ChatEntry[]>([])
  const usage = reactive<ChatUsage>({ inputTokens: 0, outputTokens: 0 })
  /** 当前进行中的助手回合（AGENT_START 后建立，AGENT_END 收口） */
  let currentTurn: AssistantTurn | null = null

  /** 开始一个用户回合：登记用户消息并预置助手回合（等待事件流填充） */
  const newTurn = (): AssistantTurn =>
    reactive({ text: '', thinking: '', tools: [], finished: false, error: null }) as AssistantTurn

  const beginTurn = (text: string) => {
    entries.push({ kind: 'user', text })
    currentTurn = newTurn()
    entries.push(currentTurn)
  }

  /** 按标识找当前回合的工具卡片（结果事件可能不带 id，退化按名称匹配） */
  const findToolCard = (event: AgentEvent): ToolCallCard | null => {
    if (!currentTurn) {
      return null
    }
    return (
      currentTurn.tools.find((tool) => event.toolCallId && tool.toolCallId === event.toolCallId) ??
      currentTurn.tools.find((tool) => event.toolCallName && tool.name === event.toolCallName) ??
      null
    )
  }

  /** 折叠一条原生事件；SESSION_ERROR 与流异常共用 failTurn 收口 */
  const applyEvent = (event: AgentEvent) => {
    switch (event.type) {
      case 'AGENT_START': {
        // 兜底：beginTurn 未建立时（异常场景）补建回合
        if (!currentTurn) {
          currentTurn = newTurn()
          entries.push(currentTurn)
        }
        break
      }
      case 'TEXT_BLOCK_DELTA': {
        if (currentTurn) currentTurn.text += event.delta ?? ''
        break
      }
      case 'THINKING_BLOCK_DELTA': {
        if (currentTurn) currentTurn.thinking += event.delta ?? ''
        break
      }
      case 'TOOL_CALL_START': {
        currentTurn?.tools.push(
          reactive({
            toolCallId: event.toolCallId ?? '',
            name: event.toolCallName ?? '(unknown)',
            resultText: '',
            state: 'running'
          })
        )
        break
      }
      case 'TOOL_RESULT_TEXT_DELTA': {
        const card = findToolCard(event)
        if (card) card.resultText += event.delta ?? ''
        break
      }
      case 'TOOL_RESULT_END': {
        const card = findToolCard(event)
        if (card) card.state = event.state === 'error' ? 'error' : 'done'
        break
      }
      case 'MODEL_CALL_END': {
        if (event.usage) {
          usage.inputTokens += event.usage.inputTokens ?? 0
          usage.outputTokens += event.usage.outputTokens ?? 0
        }
        break
      }
      case 'AGENT_RESULT':
      case 'AGENT_END': {
        if (currentTurn) currentTurn.finished = true
        break
      }
      case 'SESSION_ERROR': {
        failTurn(event.message ?? '运行出错')
        break
      }
      default:
        break
    }
  }

  /** 流异常收口（fetch 失败/中断）：当前回合标记错误并终结 */
  const failTurn = (message: string) => {
    if (currentTurn) {
      currentTurn.error = message
      currentTurn.finished = true
    }
  }

  /** 清空（切换会话时） */
  const reset = () => {
    entries.splice(0, entries.length)
    usage.inputTokens = 0
    usage.outputTokens = 0
    currentTurn = null
  }

  return { entries, usage, beginTurn, applyEvent, failTurn, reset }
}
