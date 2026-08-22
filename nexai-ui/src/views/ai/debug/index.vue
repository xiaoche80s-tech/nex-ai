<template>
  <div
    class="h-[calc(100vh-var(--top-tool-height)-var(--tags-view-height)-var(--app-content-padding)-var(--app-content-padding)-2px)] flex gap-12px"
  >
    <!-- 左栏：会话列表 -->
    <div class="w-260px flex flex-col bg-[var(--app-content-bg-color)] rounded-4px">
      <div class="p-12px flex items-center justify-between">
        <span class="font-bold">调试会话</span>
        <el-button
          type="primary"
          size="small"
          @click="createDialogRef?.open()"
          v-hasPermi="['ai:session:create']"
        >
          <Icon icon="ep:plus" class="mr-3px" /> 新建
        </el-button>
      </div>
      <!-- 按规格/版本筛选（选规格后可再选版本） -->
      <div class="px-12px pb-8px flex gap-6px">
        <el-select
          v-model="filterSpecId"
          placeholder="按规格筛选"
          size="small"
          clearable
          filterable
          class="flex-1"
          @change="handleFilterSpecChange"
        >
          <el-option
            v-for="spec in publishedSpecs"
            :key="spec.id"
            :label="spec.name"
            :value="spec.id!"
          />
        </el-select>
        <el-select
          v-model="filterVersionNo"
          placeholder="版本"
          size="small"
          clearable
          class="w-84px"
          :disabled="!filterSpecId"
          @change="loadSessions"
        >
          <el-option
            v-for="version in filterVersions"
            :key="version.versionNo"
            :label="`v${version.versionNo}`"
            :value="version.versionNo"
          />
        </el-select>
      </div>
      <el-scrollbar class="flex-1">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="mx-8px mb-6px px-10px py-8px rounded-4px cursor-pointer hover:bg-[var(--el-fill-color-light)] group"
          :class="{ 'bg-[var(--el-color-primary-light-9)]': activeSession?.id === session.id }"
          @click="openSession(session)"
        >
          <div class="text-13px truncate flex items-center justify-between">
            <span class="truncate">{{ session.title || `会话 #${session.id}` }}</span>
            <el-tooltip content="克隆为新调试会话" placement="top">
              <el-button
                link
                type="primary"
                size="small"
                class="!px-2px opacity-0 group-hover:opacity-100"
                @click.stop="cloneDialogRef?.open(session)"
                v-hasPermi="['ai:session:clone']"
              >
                <Icon icon="ep:copy-document" />
              </el-button>
            </el-tooltip>
          </div>
          <div
            class="text-12px text-[var(--el-text-color-secondary)] mt-2px flex items-center justify-between"
          >
            <span>规格 #{{ session.specId }} · v{{ session.versionNo }}</span>
            <span>{{ session.messageRounds }} 轮</span>
          </div>
        </div>
        <el-empty
          v-if="!sessions.length && !loadingSessions"
          description="暂无调试会话"
          :image-size="60"
        />
      </el-scrollbar>
    </div>

    <!-- 中栏：事件流对话 -->
    <div class="flex-1 min-w-0 flex flex-col bg-[var(--app-content-bg-color)] rounded-4px">
      <template v-if="activeSession">
        <div
          class="px-16px py-10px border-b border-[var(--el-border-color-lighter)] flex items-center justify-between"
        >
          <span class="font-bold truncate">{{
            activeSession.title || `会话 #${activeSession.id}`
          }}</span>
          <el-tag size="small" type="info" disable-transitions v-if="streaming"
            >事件流接收中…</el-tag
          >
        </div>
        <!-- 历史会话提示：运行时上下文已恢复，历史消息展示待 M3 事件落库 -->
        <el-alert
          v-if="resumedHistory"
          type="info"
          :closable="false"
          class="mx-12px mt-8px"
          title="已接入该会话的运行时上下文，直接发送消息即可继续多轮对话（历史消息回放属后续版本）"
        />
        <el-scrollbar ref="chatScrollbarRef" class="flex-1 px-16px py-12px">
          <template v-for="(entry, index) in entries" :key="index">
            <!-- 用户消息：右对齐气泡 -->
            <div v-if="'kind' in entry" class="flex justify-end mb-16px">
              <div
                class="max-w-70% bg-[var(--el-color-primary)] text-white text-14px leading-22px px-12px py-8px rounded-8px whitespace-pre-wrap break-words"
              >
                {{ entry.text }}
              </div>
            </div>
            <!-- 助手回合：思考块 / 工具卡片 / 确认请求 / 正文 / 错误 -->
            <div v-else class="mb-20px">
              <el-collapse v-if="entry.thinking" class="mb-8px">
                <el-collapse-item title="思考过程" name="thinking">
                  <div
                    class="text-13px text-[var(--el-text-color-secondary)] whitespace-pre-wrap"
                    >{{ entry.thinking }}</div
                  >
                </el-collapse-item>
              </el-collapse>
              <div v-for="tool in entry.tools" :key="tool.toolCallId" class="mb-8px">
                <div
                  class="border border-[var(--el-border-color)] rounded-6px text-13px overflow-hidden"
                >
                  <div
                    class="px-10px py-6px bg-[var(--el-fill-color-light)] flex items-center gap-6px"
                  >
                    <Icon
                      :icon="tool.state === 'error' ? 'ep:circle-close-filled' : 'ep:setting'"
                      :color="
                        tool.state === 'error'
                          ? 'var(--el-color-danger)'
                          : 'var(--el-color-primary)'
                      "
                    />
                    <span class="font-bold">工具调用 · {{ tool.name }}</span>
                    <el-tag
                      size="small"
                      :type="
                        tool.state === 'running'
                          ? 'warning'
                          : tool.state === 'error'
                            ? 'danger'
                            : 'success'
                      "
                      disable-transitions
                    >
                      {{
                        tool.state === 'running'
                          ? '执行中'
                          : tool.state === 'error'
                            ? '失败'
                            : '完成'
                      }}
                    </el-tag>
                  </div>
                  <div
                    v-if="tool.resultText"
                    class="px-10px py-6px whitespace-pre-wrap break-words text-[var(--el-text-color-regular)] max-h-160px overflow-auto"
                  >
                    {{ tool.resultText }}
                  </div>
                </div>
              </div>
              <!-- HITL 确认请求卡片：三态回应（批准 / 改参数后批准 / 拒绝），回应后转只读 -->
              <div
                v-if="entry.pendingConfirm"
                class="mb-8px border border-[var(--el-color-warning)] rounded-6px overflow-hidden text-13px"
              >
                <div
                  class="px-10px py-6px bg-[var(--el-color-warning-light-9)] flex items-center gap-6px"
                >
                  <Icon icon="ep:question-filled" color="var(--el-color-warning)" />
                  <span class="font-bold">工具执行等待确认</span>
                  <el-tag
                    size="small"
                    :type="entry.pendingConfirm.responded ? 'info' : 'warning'"
                    disable-transitions
                  >
                    {{ entry.pendingConfirm.responded ? '已回应，执行中' : '等待你的回应' }}
                  </el-tag>
                </div>
                <div class="px-10px py-8px flex flex-col gap-8px">
                  <div v-for="tool in entry.pendingConfirm.toolCalls" :key="tool.toolCallId">
                    <div class="mb-4px text-[var(--el-text-color-secondary)]">
                      <span class="font-bold text-[var(--el-text-color-regular)]">{{
                        tool.name
                      }}</span>
                      的调用参数（可直接修改后批准）：
                    </div>
                    <el-input
                      v-model="tool.arguments"
                      type="textarea"
                      :autosize="{ minRows: 2, maxRows: 8 }"
                      class="font-mono"
                      :disabled="entry.pendingConfirm.responded || streaming"
                      resize="none"
                    />
                  </div>
                  <div
                    v-if="!entry.pendingConfirm.responded && !streaming"
                    class="flex justify-end gap-8px"
                  >
                    <el-button size="small" type="danger" plain @click="handleConfirm(entry, false)"
                      >拒 绝</el-button
                    >
                    <el-button size="small" type="primary" @click="handleConfirm(entry, true)"
                      >批 准</el-button
                    >
                  </div>
                </div>
              </div>
              <div
                v-if="entry.text"
                class="text-14px leading-22px whitespace-pre-wrap break-words"
                >{{ entry.text }}</div
              >
              <div v-if="entry.error" class="text-13px text-[var(--el-color-danger)]"
                >运行出错：{{ entry.error }}</div
              >
              <div
                v-else-if="!entry.finished"
                class="text-13px text-[var(--el-text-color-secondary)]"
              >
                <Icon icon="ep:loading" class="is-loading" /> 思考中…
              </div>
            </div>
          </template>
        </el-scrollbar>
        <!-- 输入区 -->
        <div class="px-16px py-12px border-t border-[var(--el-border-color-lighter)]">
          <div class="flex items-end gap-8px">
            <el-input
              v-model="inputText"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 6 }"
              :disabled="streaming"
              placeholder="输入消息，Enter 发送，Shift + Enter 换行"
              maxlength="4096"
              resize="none"
              @keydown.enter.exact.prevent="handleSend"
            />
            <el-button
              v-if="streaming"
              type="danger"
              :loading="interrupting"
              @click="handleStop"
              v-hasPermi="['ai:session:interrupt']"
            >
              <Icon icon="ep:video-pause" class="mr-3px" /> 停止
            </el-button>
            <el-button
              v-else
              type="primary"
              :disabled="!inputText.trim()"
              @click="handleSend"
              v-hasPermi="['ai:session:message']"
            >
              发送
            </el-button>
          </div>
        </div>
      </template>
      <el-empty v-else description="从左侧选择或新建一个调试会话" class="m-auto" />
    </div>

    <!-- 右栏：运行详情 -->
    <div class="w-300px flex flex-col bg-[var(--app-content-bg-color)] rounded-4px overflow-auto">
      <div class="px-14px py-10px font-bold border-b border-[var(--el-border-color-lighter)]"
        >运行详情</div
      >
      <template v-if="activeSession">
        <el-descriptions
          :column="1"
          size="small"
          class="px-14px py-10px"
          label-class-name="!w-84px"
        >
          <el-descriptions-item label="规格">#{{ activeSession.specId }}</el-descriptions-item>
          <el-descriptions-item label="绑定版本">
            <el-tag size="small" type="success" disable-transitions
              >v{{ activeSession.versionNo }}</el-tag
            >
            <span v-if="detailVersion?.remark" class="ml-4px text-12px">{{
              detailVersion.remark
            }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="模型">
            <span v-if="detailConfig?.modelName || detailConfig?.modelId">
              {{ detailConfig?.modelName || `#${detailConfig?.modelId}` }}
            </span>
            <span v-else>加载中…</span>
          </el-descriptions-item>
          <el-descriptions-item label="maxIters">
            {{ effectiveMaxIters }}
            <el-tag
              v-if="activeSession?.overrideMaxIters != null"
              size="small"
              type="warning"
              disable-transitions
              class="ml-4px"
              >会话覆盖</el-tag
            >
          </el-descriptions-item>
          <el-descriptions-item label="温度">
            {{ effectiveTemperature }}
            <el-tag
              v-if="activeSession?.overrideTemperature != null"
              size="small"
              type="warning"
              disable-transitions
              class="ml-4px"
              >会话覆盖</el-tag
            >
          </el-descriptions-item>
          <el-descriptions-item label="已发消息"
            >{{ activeSession.messageRounds }} 轮</el-descriptions-item
          >
          <el-descriptions-item label="会话标识">
            <span class="text-12px break-all">{{ activeSession.sessionKey }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <div class="px-14px py-8px">
          <el-collapse>
            <el-collapse-item
              :title="`系统提示${detailConfig?.systemPrompt ? '' : '（空）'}`"
              name="prompt"
            >
              <div
                v-if="detailConfig?.systemPrompt"
                class="text-12px leading-20px whitespace-pre-wrap max-h-200px overflow-auto"
              >
                {{ detailConfig.systemPrompt }}
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
        <div class="px-14px py-10px border-t border-[var(--el-border-color-lighter)]">
          <div class="font-bold text-13px mb-8px">Token 用量（本次打开累计）</div>
          <div class="flex gap-8px">
            <div class="flex-1 text-center border rounded-4px py-8px">
              <div class="text-16px font-bold">{{ usage.inputTokens }}</div>
              <div class="text-12px text-[var(--el-text-color-secondary)]">输入</div>
            </div>
            <div class="flex-1 text-center border rounded-4px py-8px">
              <div class="text-16px font-bold">{{ usage.outputTokens }}</div>
              <div class="text-12px text-[var(--el-text-color-secondary)]">输出</div>
            </div>
            <div class="flex-1 text-center border rounded-4px py-8px">
              <div class="text-16px font-bold">{{ usage.inputTokens + usage.outputTokens }}</div>
              <div class="text-12px text-[var(--el-text-color-secondary)]">合计</div>
            </div>
          </div>
        </div>
      </template>
      <el-empty v-else description="暂无会话" :image-size="60" class="m-auto" />
    </div>
  </div>

  <!-- 新建调试会话弹窗 -->
  <DebugSessionCreateDialog ref="createDialogRef" @created="handleCreated" />
  <!-- 克隆重跑弹窗 -->
  <DebugSessionCloneDialog ref="cloneDialogRef" @cloned="handleCreated" />
</template>
<script lang="ts" setup>
import * as SessionApi from '@/api/ai/session'
import * as SpecApi from '@/api/ai/spec'
import DebugSessionCloneDialog from './DebugSessionCloneDialog.vue'
import DebugSessionCreateDialog from './DebugSessionCreateDialog.vue'
import { useEventStream } from './useEventStream'
import type { AssistantTurn } from './useEventStream'

defineOptions({ name: 'AiDebug' })

const message = useMessage()
const { entries, usage, beginTurn, applyEvent, failTurn, reset } = useEventStream()

// ==================== 左栏：会话列表 ====================
const sessions = ref<SessionApi.SessionVO[]>([])
const loadingSessions = ref(false)
const createDialogRef = ref()
const cloneDialogRef = ref()

/** 规格/版本筛选（选规格后版本下拉可选） */
const filterSpecId = ref<number>()
const filterVersionNo = ref<number>()
const publishedSpecs = ref<SpecApi.AgentSpecVO[]>([])
const filterVersions = ref<SpecApi.AgentSpecVersionVO[]>([])

const loadSessions = async () => {
  loadingSessions.value = true
  try {
    const data = await SessionApi.getSessionPage({
      pageNo: 1,
      pageSize: 100,
      type: 10,
      specId: filterSpecId.value || undefined,
      versionNo: filterVersionNo.value || undefined
    })
    sessions.value = data.list
  } finally {
    loadingSessions.value = false
  }
}

/** 筛选规格变化：清空版本选择并加载该规格版本列表 */
const handleFilterSpecChange = async (specId: number | undefined) => {
  filterVersionNo.value = undefined
  filterVersions.value = specId ? await SpecApi.getVersionList(specId) : []
  await loadSessions()
}

/** 创建成功：刷新列表并直接进入新会话（省一次手动点击） */
const handleCreated = async (sessionId: number) => {
  await loadSessions()
  const created = sessions.value.find((session) => session.id === sessionId)
  if (created) {
    await openSession(created)
  }
}

// ==================== 会话打开与运行详情 ====================
const activeSession = ref<SessionApi.SessionVO | null>(null)
const detailVersion = ref<SpecApi.AgentSpecVersionVO | null>(null)
const resumedHistory = ref(false)

const detailConfig = computed(() => detailVersion.value?.config ?? null)
/** 实际生效推理参数：会话级覆盖优先于版本快照（克隆重跑微调的展示口径） */
const effectiveMaxIters = computed(
  () => activeSession.value?.overrideMaxIters ?? detailConfig.value?.maxIters ?? '默认'
)
const effectiveTemperature = computed(
  () => activeSession.value?.overrideTemperature ?? detailConfig.value?.temperature ?? '默认'
)

/** 打开（或切换）会话：恢复继续对话能力并加载绑定版本快照填充右栏 */
const openSession = async (session: SessionApi.SessionVO) => {
  if (activeSession.value?.id === session.id) {
    return
  }
  activeSession.value = session
  detailVersion.value = null
  reset()
  resumedHistory.value = session.messageRounds > 0
  detailVersion.value =
    (await SpecApi.getVersionList(session.specId)).find(
      (version) => version.versionNo === session.versionNo
    ) ?? null
}

// ==================== 中栏：发消息与事件流 ====================
const inputText = ref('')
const streaming = ref(false)
const chatScrollbarRef = ref()

watch(
  // 新回合建立或最后一条仍在增长的文本/思考增量都触发滚底（流式输出期间持续跟随）
  () =>
    entries.length + ':' + (entries[entries.length - 1] as AssistantTurn | undefined)?.text?.length,
  async () => {
    await nextTick()
    chatScrollbarRef.value?.setScrollTop(99999)
  }
)

/** 发送消息：登记用户条目 → SSE 消费原生事件流折叠渲染 → 失败收口为回合错误 */
const handleSend = async () => {
  const content = inputText.value.trim()
  if (!content || streaming.value || !activeSession.value) {
    return
  }
  inputText.value = ''
  resumedHistory.value = false
  beginTurn(content)
  streaming.value = true
  try {
    await SessionApi.sendDebugMessage(activeSession.value.id, content, {
      onEvent: applyEvent
    })
    activeSession.value.messageRounds++
  } catch (error: any) {
    failTurn(
      error?.message?.includes('Failed to fetch')
        ? '连接中断，请重试'
        : (error?.message ?? '事件流异常')
    )
    message.error('消息发送失败')
  } finally {
    streaming.value = false
  }
}

/** 中断运行中的事件流：后端触发旗标后由服务端正常收尾（AGENT_END 前的事件仍会送达） */
const interrupting = ref(false)
const handleStop = async () => {
  if (!activeSession.value || !streaming.value) {
    return
  }
  interrupting.value = true
  try {
    const interrupted = await SessionApi.interruptSession(activeSession.value.id)
    if (!interrupted) {
      message.warning('会话当前没有运行中的事件流')
    }
  } finally {
    interrupting.value = false
  }
}

/** HITL 三态回应：以卡片中（可能已编辑的）参数逐条回发，后续事件流续入本回合渲染 */
const handleConfirm = async (turn: AssistantTurn, approved: boolean) => {
  if (!activeSession.value || !turn.pendingConfirm || streaming.value) {
    return
  }
  const decisions = turn.pendingConfirm.toolCalls.map((tool) => ({
    toolCallId: tool.toolCallId,
    toolName: tool.name,
    arguments: tool.arguments,
    approved
  }))
  // 直接置卡片自身状态（确认卡片可能属于较早回合，不依赖“当前回合”指针）
  turn.pendingConfirm.responded = true
  streaming.value = true
  try {
    await SessionApi.confirmToolCalls(activeSession.value.id, decisions, {
      onEvent: applyEvent
    })
  } catch (error: any) {
    failTurn(
      error?.message?.includes('Failed to fetch')
        ? '连接中断，请重试'
        : (error?.message ?? '确认回应异常')
    )
    message.error('确认回应失败')
  } finally {
    streaming.value = false
  }
}

onMounted(async () => {
  // 筛选下拉的规格选项与列表并行加载
  const data = await SpecApi.getSpecPage({ pageNo: 1, pageSize: 100 })
  publishedSpecs.value = data.list.filter((spec) => spec.currentVersionNo)
  await loadSessions()
})
</script>
