<template>
  <div class="h-full flex flex-col">
    <!-- 顶部工具栏：会话选择 + 发起调试 -->
    <ContentWrap class="!mb-10px">
      <div class="flex items-center gap-12px">
        <el-select
          v-model="currentSpecId"
          placeholder="选择智能体规格"
          class="!w-260px"
          @change="handleSpecChange"
        >
          <el-option
            v-for="spec in specList"
            :key="spec.id"
            :label="`${spec.name}（${spec.specCode}）`"
            :value="spec.id"
          />
        </el-select>
        <el-button type="primary" :disabled="!currentSpecId" @click="handleCreateSession">
          <Icon icon="ep:plus" class="mr-5px" /> {{ t('ai.session.create') }}
        </el-button>
        <el-select v-model="currentSessionId" placeholder="选择调试会话" class="!w-260px" clearable>
          <el-option
            v-for="session in sessionList"
            :key="session.id"
            :label="`${session.title}（${session.status}）`"
            :value="session.id"
          />
        </el-select>
        <el-button :disabled="!currentSessionId" @click="handleLoadSession">
          <Icon icon="ep:refresh" class="mr-5px" /> {{ t('ai.session.reload') }}
        </el-button>
      </div>
    </ContentWrap>

    <!-- 三栏工作台：会话流（左）| 事件流（中）| workspace 文件（右，07 填充） -->
    <div class="flex-1 flex gap-10px min-h-0">
      <!-- 会话流 -->
      <ContentWrap class="!w-300px flex flex-col min-h-0">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="text-14px font-bold">{{ t('ai.session.chatFlow') }}</span>
            <el-tag v-if="currentSession" :type="statusTagType(currentSession.status)" size="small">
              {{ currentSession.status }}
            </el-tag>
          </div>
        </template>
        <div ref="chatFlowRef" class="flex-1 overflow-y-auto px-4px space-y-8px">
          <div v-for="(msg, index) in chatMessages" :key="index" class="flex flex-col">
            <div v-if="msg.role === 'user'" class="self-end">
              <div class="bg-primary text-white rounded-8px px-12px py-8px max-w-260px">
                {{ msg.content }}
              </div>
            </div>
            <div v-else class="self-start">
              <div class="bg-[var(--el-fill-color-light)] rounded-8px px-12px py-8px max-w-260px whitespace-pre-wrap">
                {{ msg.content }}
              </div>
            </div>
          </div>
          <div v-if="streaming" class="text-gray-400 text-12px">
            {{ t('ai.session.streaming') }}…
          </div>
        </div>
        <div class="pt-8px border-t border-[var(--el-border-color-light)] flex gap-8px">
          <el-input
            v-model="inputContent"
            :placeholder="t('ai.session.inputPlaceholder')"
            :disabled="!currentSessionId || streaming"
            @keyup.enter="handleSend"
          />
          <el-button type="primary" :disabled="!currentSessionId || streaming || !inputContent" @click="handleSend">
            {{ t('ai.session.send') }}
          </el-button>
        </div>
      </ContentWrap>

      <!-- 事件流（类型化渲染） -->
      <ContentWrap class="flex-1 flex flex-col min-h-0">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="text-14px font-bold">{{ t('ai.session.eventFlow') }}</span>
            <div class="flex items-center gap-8px">
              <el-tag v-if="sseConnected" type="success" size="small">{{ t('ai.session.connected') }}</el-tag>
              <el-tag v-else type="warning" size="small">{{ t('ai.session.disconnected') }}</el-tag>
              <el-button size="small" :disabled="!currentSessionId || streaming" @click="handleInterrupt">
                <Icon icon="ep:video-pause" class="mr-4px" />{{ t('ai.session.interrupt') }}
              </el-button>
            </div>
          </div>
        </template>
        <div ref="eventFlowRef" class="flex-1 overflow-y-auto font-mono text-12px space-y-2px px-4px">
          <div
            v-for="(event, index) in eventList"
            :key="index"
            :class="['px-8px py-2px rounded-4px', eventClass(event.type)]"
          >
            <span class="text-gray-400 mr-8px">{{ formatEventTime(event.time) }}</span>
            <span class="font-bold mr-8px">{{ event.type }}</span>
            <span class="break-all">{{ event.summary }}</span>
          </div>
          <!-- HITL 审批卡片（工单 09）：挂起中的敏感工具调用三态审批 -->
          <div
            v-for="(pending, index) in pendingConfirmations"
            :key="'pending-' + index"
            class="border border-[#f56c6c] rounded-8px p-8px mt-8px"
          >
            <div class="text-[#f56c6c] font-bold mb-4px">
              <Icon icon="ep:warning" class="mr-4px" />{{ t('ai.session.hitlPending') }}
            </div>
            <div class="mb-4px">
              <span class="font-bold">{{ pending.toolName }}</span>
              <span class="text-gray-500 ml-8px">{{ pending.argumentsJson }}</span>
            </div>
            <div class="flex gap-8px">
              <el-button size="small" type="primary" @click="handleConfirm(pending, true)">
                {{ t('ai.session.approve') }}
              </el-button>
              <el-button size="small" type="danger" @click="handleConfirm(pending, false)">
                {{ t('ai.session.reject') }}
              </el-button>
            </div>
          </div>
          <div v-if="streaming" class="text-gray-400">{{ t('ai.session.streaming') }}…</div>
        </div>
      </ContentWrap>

      <!-- workspace 文件栏（07 填充） -->
      <ContentWrap class="!w-300px flex flex-col min-h-0">
        <template #header>
          <span class="text-14px font-bold">{{ t('ai.session.workspace') }}</span>
        </template>
        <div class="flex-1 overflow-y-auto px-4px">
          <div v-if="!currentSessionId" class="text-gray-400 text-12px text-center mt-20px">
            {{ t('ai.session.selectFirst') }}
          </div>
          <div v-else class="space-y-4px">
            <div
              v-for="file in workspaceFiles"
              :key="file"
              class="flex items-center gap-4px cursor-pointer px-8px py-4px rounded-4px hover:bg-[var(--el-fill-color-light)]"
              @click="handleReadFile(file)"
            >
              <Icon :icon="file.endsWith('/') ? 'ep:folder' : 'ep:document'" class="text-gray-400" />
              <span class="text-12px">{{ file }}</span>
            </div>
            <el-button size="small" class="mt-8px" @click="handleRefreshFiles">
              <Icon icon="ep:refresh" class="mr-4px" />{{ t('common.refresh') }}
            </el-button>
          </div>
        </div>
        <el-dialog v-model="fileDialogVisible" :title="currentFilePath" width="600px">
          <pre class="bg-[var(--el-fill-color-light)] p-12px rounded-8px text-12px whitespace-pre-wrap max-h-400px overflow-auto">{{
            currentFileContent
          }}</pre>
        </el-dialog>
      </ContentWrap>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getAccessToken, getTenantId } from '@/utils/auth'
import {
  createDebugSession,
  getSessionPage,
  getSession,
  getPendingConfirmations,
  loadSessionHistory,
  interruptSession,
  listWorkspaceFiles,
  readWorkspaceFile,
  type SessionVO,
  type PendingConfirmationVO
} from '@/api/ai/session'
import { getSpecPage } from '@/api/ai/spec'

defineOptions({ name: 'AiSessionDebug' })

const { t } = useI18n()

// ---------- 规格与会话列表 ----------
const specList = ref<any[]>([])
const sessionList = ref<SessionVO[]>([])
const currentSpecId = ref<number>()
const currentSessionId = ref<number>()
const currentSession = ref<SessionVO>()

const loadSpecs = async () => {
  const data = await getSpecPage({ pageNo: 1, pageSize: 100 })
  specList.value = data.list
}

const handleSpecChange = async () => {
  await loadSessions()
}

const loadSessions = async () => {
  const data = await getSessionPage({ pageNo: 1, pageSize: 50, specId: currentSpecId.value })
  sessionList.value = data.list
}

// ---------- 会话生命周期 ----------
const chatMessages = ref<{ role: string; content: string }[]>([])
const eventList = ref<{ type: string; summary: string; time: number }[]>([])
const streaming = ref(false)
const sseConnected = ref(false)
const inputContent = ref('')
const chatFlowRef = ref<HTMLElement>()
const eventFlowRef = ref<HTMLElement>()
let abortController: AbortController | null = null

const handleCreateSession = async () => {
  if (!currentSpecId.value) return
  const id = await createDebugSession({ title: '调试会话 ' + new Date().toLocaleTimeString(), specId: currentSpecId.value })
  currentSessionId.value = id
  await loadSessions()
  await handleLoadSession()
}

const handleLoadSession = async () => {
  if (!currentSessionId.value) return
  chatMessages.value = []
  eventList.value = []
  currentSession.value = await getSession(currentSessionId.value)
  // 恢复会话历史消息（工单 09：经 AgentStateStore 读槽位上下文，重开调试台续接）
  try {
    const history = await loadSessionHistory(currentSessionId.value)
    history.forEach((msg: any) => {
      const role = msg.role === 'USER' ? 'user' : 'assistant'
      const text = msg.content
        ? (Array.isArray(msg.content)
            ? msg.content
                .map((b: any) => b.text || '')
                .filter(Boolean)
                .join('')
            : msg.content)
        : ''
      if (text) chatMessages.value.push({ role, content: text })
    })
  } catch {
    // 历史加载失败不阻断（可能槽位无持久化状态）
  }
  // ASKING 状态恢复：渲染挂起审批上下文（工单 09 会话恢复）
  if (currentSession.value.status === 'ASKING') {
    const pending = await getPendingConfirmations(currentSessionId.value)
    pending.forEach((p: PendingConfirmationVO) => {
      eventList.value.push({
        type: 'REQUIRE_USER_CONFIRM',
        summary: `${p.toolName} 等待审批：${p.argumentsJson || ''}`,
        time: Date.now()
      })
      pendingConfirmations.value.push(p)
    })
  }
  await handleRefreshFiles()
}

const handleSend = async () => {
  if (!currentSessionId.value || !inputContent.value || streaming.value) return
  const content = inputContent.value.trim()
  inputContent.value = ''
  chatMessages.value.push({ role: 'user', content })
  streaming.value = true
  sseConnected.value = true
  abortController = new AbortController()
  try {
    await streamChat(content, abortController.signal)
  } catch (err: any) {
    if (err.name !== 'AbortError') {
      ElMessage.error(err.message || t('ai.session.streamError'))
    }
  } finally {
    streaming.value = false
    sseConnected.value = false
    abortController = null
  }
}

// 原生 fetch 消费 SSE（POST + Accept: text/event-stream，绕过 axios 拦截器）
const streamChat = async (content: string, signal: AbortSignal) => {
  const baseUrl = import.meta.env.VITE_BASE_URL + import.meta.env.VITE_API_URL
  const tenantEnable = import.meta.env.VITE_APP_TENANT_ENABLE
  const response = await fetch(`${baseUrl}/ai/session/${currentSessionId.value}/message`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: 'Bearer ' + getAccessToken(),
      ...(tenantEnable === 'true' ? { 'tenant-id': String(getTenantId() || '') } : {})
    },
    body: JSON.stringify({ content }),
    signal
  })
  if (!response.ok || !response.body) {
    throw new Error(t('ai.session.streamError') + '（HTTP ' + response.status + '）')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let assistantText = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    // 按 \n\n 切帧（兼容 \r\n\r\n）
    const frames = buffer.split(/\r?\n\r?\n/)
    buffer = frames.pop() || ''
    for (const frame of frames) {
      const dataLine = frame.split('\n').find((l) => l.startsWith('data:'))
      if (!dataLine) continue
      const payload = dataLine.slice(5).trim()
      if (!payload) continue
      handleEventPayload(payload)
      // 文本增量累积到会话流
      const parsed = JSON.parse(payload)
      if (parsed.type === 'TEXT_BLOCK_DELTA' && parsed.delta) {
        assistantText += parsed.delta
        upsertAssistantMessage(assistantText)
      }
      if (parsed.type === 'AGENT_END') {
        assistantText = ''
      }
      // HITL 挂起 → 追加审批卡片
      if (parsed.type === 'REQUIRE_USER_CONFIRM') {
        const toolCalls = parsed.toolCalls || []
        toolCalls.forEach((tc: any) => {
          pendingConfirmations.value.push({
            toolCallId: tc.id,
            toolName: tc.name,
            argumentsJson: JSON.stringify(tc.input || {})
          })
        })
      }
    }
  }
}

// 事件流类型化渲染
const handleEventPayload = (payload: string) => {
  let parsed: any
  try {
    parsed = JSON.parse(payload)
  } catch {
    return
  }
  const type: string = parsed.type || 'UNKNOWN'
  let summary = ''
  if (type === 'TEXT_BLOCK_DELTA') summary = (parsed.delta || '').slice(0, 60)
  else if (type === 'THINKING_BLOCK_DELTA') summary = (parsed.delta || '').slice(0, 60)
  else if (type === 'TOOL_CALL_START') summary = `→ ${parsed.toolCallName}`
  else if (type === 'TOOL_CALL_END') summary = `← ${parsed.toolCallName}`
  else if (type === 'TOOL_RESULT_TEXT_DELTA') summary = (parsed.delta || '').slice(0, 60)
  else if (type === 'MODEL_CALL_END') {
    const usage = parsed.usage
    summary = usage ? `tokens in=${usage.inputTokens} out=${usage.outputTokens}` : ''
  } else if (type === 'REQUIRE_USER_CONFIRM') {
    const names = (parsed.toolCalls || []).map((tc: any) => tc.name).join(', ')
    summary = `需审批：${names}`
  } else if (type === 'USER_CONFIRM_RESULT') summary = '审批已回传'
  else if (type === 'SESSION_ERROR') summary = '⚠ ' + (parsed.message || '')
  else summary = ''
  eventList.value.push({ type, summary, time: Date.now() })
  nextTick(() => {
    eventFlowRef.value?.scrollTo({ top: eventFlowRef.value.scrollHeight })
    chatFlowRef.value?.scrollTo({ top: chatFlowRef.value.scrollHeight })
  })
}

const upsertAssistantMessage = (text: string) => {
  const last = chatMessages.value[chatMessages.value.length - 1]
  if (last && last.role === 'assistant') {
    last.content = text
  } else {
    chatMessages.value.push({ role: 'assistant', content: text })
  }
  nextTick(() => chatFlowRef.value?.scrollTo({ top: chatFlowRef.value.scrollHeight }))
}

const handleInterrupt = async () => {
  if (!currentSessionId.value) return
  await interruptSession(currentSessionId.value)
  ElMessage.success(t('ai.session.interrupted'))
}

// HITL 审批（确认/拒绝）→ SSE 续行流消费
const handleConfirm = async (pending: PendingConfirmationVO, approved: boolean) => {
  if (!currentSessionId.value || streaming.value) return
  streaming.value = true
  sseConnected.value = true
  abortController = new AbortController()
  try {
    await streamConfirm(pending, approved, abortController.signal)
    // 审批已回传：移除该审批卡片
    pendingConfirmations.value = pendingConfirmations.value.filter(
      (p) => p.toolCallId !== pending.toolCallId
    )
  } catch (err: any) {
    if (err.name !== 'AbortError') {
      ElMessage.error(err.message || t('ai.session.streamError'))
    }
  } finally {
    streaming.value = false
    sseConnected.value = false
    abortController = null
  }
}

// 原生 fetch 消费 HITL 审批 SSE 续行流
const streamConfirm = async (
  pending: PendingConfirmationVO,
  approved: boolean,
  signal: AbortSignal
) => {
  const baseUrl = import.meta.env.VITE_BASE_URL + import.meta.env.VITE_API_URL
  const tenantEnable = import.meta.env.VITE_APP_TENANT_ENABLE
  const response = await fetch(`${baseUrl}/ai/session/${currentSessionId.value}/confirm`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: 'Bearer ' + getAccessToken(),
      ...(tenantEnable === 'true' ? { 'tenant-id': String(getTenantId() || '') } : {})
    },
    body: JSON.stringify({
      decisions: [
        {
          toolCallId: pending.toolCallId,
          toolName: pending.toolName,
          approved,
          argumentsJson: pending.argumentsJson || undefined
        }
      ]
    }),
    signal
  })
  if (!response.ok || !response.body) {
    throw new Error(t('ai.session.streamError') + '（HTTP ' + response.status + '）')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let assistantText = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const frames = buffer.split(/\r?\n\r?\n/)
    buffer = frames.pop() || ''
    for (const frame of frames) {
      const dataLine = frame.split('\n').find((l) => l.startsWith('data:'))
      if (!dataLine) continue
      const payload = dataLine.slice(5).trim()
      if (!payload) continue
      handleEventPayload(payload)
      const parsed = JSON.parse(payload)
      if (parsed.type === 'TEXT_BLOCK_DELTA' && parsed.delta) {
        assistantText += parsed.delta
        upsertAssistantMessage(assistantText)
      }
      if (parsed.type === 'AGENT_END') {
        assistantText = ''
      }
    }
  }
}

// ---------- workspace 文件栏（07）----------
const workspaceFiles = ref<string[]>([])
const currentFilePath = ref('')
const currentFileContent = ref('')
const fileDialogVisible = ref(false)
const pendingConfirmations = ref<PendingConfirmationVO[]>([])

const handleRefreshFiles = async () => {
  if (!currentSessionId.value) return
  workspaceFiles.value = await listWorkspaceFiles(currentSessionId.value)
}

const handleReadFile = async (file: string) => {
  if (!currentSessionId.value) return
  if (file.endsWith('/')) return // 目录：进入
  const content = await readWorkspaceFile(currentSessionId.value, file)
  currentFilePath.value = file
  currentFileContent.value = content || ''
  fileDialogVisible.value = true
}

// ---------- 工具函数 ----------
const statusTagType = (status: string) => {
  const map: Record<string, string> = {
    READY: 'info',
    ACTIVE: 'primary',
    ASKING: 'warning',
    CLOSED: 'success'
  }
  return (map[status] || 'info') as any
}

const eventClass = (type: string) => {
  if (type === 'TEXT_BLOCK_DELTA' || type === 'TEXT_BLOCK_END') return 'text-[#409eff]'
  if (type === 'THINKING_BLOCK_DELTA') return 'text-gray-500 italic'
  if (type.startsWith('TOOL_CALL')) return 'text-[#e6a23c]'
  if (type.startsWith('TOOL_RESULT')) return 'text-[#67c23a]'
  if (type === 'REQUIRE_USER_CONFIRM') return 'text-[#f56c6c] font-bold'
  if (type === 'SESSION_ERROR') return 'text-[#f56c6c] bg-[#fef0f0]'
  if (type === 'MODEL_CALL_END') return 'text-gray-400'
  return 'text-gray-600'
}

const formatEventTime = (time: number) => {
  const d = new Date(time)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(
    d.getSeconds()
  ).padStart(2, '0')}`
}

// ---------- 初始化与清理 ----------
loadSpecs()
loadSessions()

onBeforeUnmount(() => {
  abortController?.abort()
})
</script>
