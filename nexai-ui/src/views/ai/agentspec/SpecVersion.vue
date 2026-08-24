<template>
  <Dialog v-model="dialogVisible" :title="t('ai.spec.versionDialogTitle')" width="620">
    <!-- 发布新版本（发布门禁：已发布且无草稿时禁用并提示，工单 23） -->
    <div class="flex gap-8px mb-12px">
      <el-input
        v-model="publishNote"
        :placeholder="t('ai.spec.versionPublishPlaceholder')"
        :maxlength="255"
        clearable
        class="!w-380px"
      />
      <el-tooltip
        :disabled="hasDraft"
        :content="t('ai.spec.publishDisabledTip')"
        placement="top"
      >
        <span class="inline-flex">
          <el-button
            type="primary"
            :disabled="publishing || !hasDraft"
            v-hasPermi="['ai:spec:publish']"
            @click="handlePublish"
          >
            {{ t('ai.spec.versionPublish') }}
          </el-button>
        </span>
      </el-tooltip>
    </div>

    <!-- 版本列表 -->
    <el-table v-loading="loading" :data="versionList" max-height="420">
      <el-table-column :label="t('ai.spec.versionTableVersionNo')" align="center" width="90">
        <template #default="{ row }">
          <div class="flex items-center justify-center gap-4px">
            <span>v{{ row.versionNo }}</span>
            <el-tag v-if="row.current" type="success" disable-transitions size="small">
              {{ t('ai.spec.versionCurrent') }}
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.spec.versionTableNote')"
        align="center"
        prop="note"
        min-width="160"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span>{{ row.note || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.spec.versionTablePublisher')"
        align="center"
        prop="publisherName"
        width="110"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span>{{ row.publisherName || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.spec.versionTableCreateTime')"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column :label="t('table.action')" align="center" width="140" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            v-hasPermi="['ai:spec:query']"
            @click="openPreview(row)"
          >
            {{ t('ai.spec.versionPreview') }}
          </el-button>
          <el-button
            link
            type="primary"
            :disabled="row.current"
            v-hasPermi="['ai:spec:update']"
            @click="handleSwitch(row)"
          >
            {{ t('ai.spec.versionSwitch') }}
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span>{{ t('ai.spec.versionEmpty') }}</span>
      </el-table-column>
    </el-table>

    <!-- 版本快照只读预览抽屉（固化保真的全量四层配置，不含主体元数据） -->
    <el-drawer
      v-model="previewVisible"
      :title="t('ai.spec.previewTitle', { version: previewDetail ? 'v' + previewDetail.versionNo : '' })"
      size="560px"
    >
      <div v-loading="previewLoading" class="flex flex-col gap-16px">
        <template v-if="previewDetail">
          <!-- 版本元信息 -->
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item :label="t('ai.spec.versionTableVersionNo')">
              <div class="flex items-center gap-4px">
                <span>v{{ previewDetail.versionNo }}</span>
                <el-tag v-if="previewDetail.current" type="success" size="small" disable-transitions>
                  {{ t('ai.spec.versionCurrent') }}
                </el-tag>
              </div>
            </el-descriptions-item>
            <el-descriptions-item :label="t('ai.spec.versionTableCreateTime')">
              {{ previewDetail.createTime ? formatDate(previewDetail.createTime) : '—' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('ai.spec.versionTableNote')" :span="2">
              {{ previewDetail.note || '—' }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- 分区一：模型与提示 -->
          <el-divider content-position="left">{{ t('ai.spec.sectionModel') }}</el-divider>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item :label="t('ai.spec.formModel')">
              {{ previewDetail.modelId ?? t('ai.spec.previewUnset') }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('ai.spec.formMaxIters')">
              {{ previewDetail.maxIters ?? '—' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('ai.spec.formDescription')" :span="2">
              {{ previewDetail.description || '—' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('ai.spec.formSystemPrompt')" :span="2">
              <pre class="whitespace-pre-wrap font-sans text-13px m-0">{{
                previewDetail.systemPrompt || '—'
              }}</pre>
            </el-descriptions-item>
            <el-descriptions-item
              v-if="previewDetail.temperature != null || previewDetail.topP != null || previewDetail.maxTokens != null"
              :label="t('ai.spec.generateOptions')"
              :span="2"
            >
              temperature {{ previewDetail.temperature ?? '—' }} · topP
              {{ previewDetail.topP ?? '—' }} · maxTokens {{ previewDetail.maxTokens ?? '—' }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- 分区二：挂载 -->
          <el-divider content-position="left">{{ t('ai.spec.sectionMount') }}</el-divider>
          <div class="flex flex-col gap-8px">
            <div>
              <div class="text-13px mb-4px">{{ t('ai.spec.formSkills') }}</div>
              <template v-if="(previewDetail.skillIds || []).length">
                <el-tag
                  v-for="skillId in previewDetail.skillIds"
                  :key="skillId"
                  size="small"
                  class="mr-4px mb-4px"
                  disable-transitions
                >
                  #{{ skillId }}
                </el-tag>
              </template>
              <span v-else class="text-13px text-gray-400">{{ t('ai.spec.previewEmpty') }}</span>
            </div>
            <div>
              <div class="text-13px mb-4px">{{ t('ai.spec.formTools') }}</div>
              <el-table
                v-if="(previewDetail.tools || []).length"
                :data="previewDetail.tools || []"
                size="small"
              >
                <el-table-column :label="t('ai.spec.mountSource')" width="110">
                  <template #default="{ row }">
                    {{ row.source === 'MCP' ? 'MCP Server' : t('ai.spec.mountSourcePlatform') }}
                  </template>
                </el-table-column>
                <el-table-column :label="t('ai.spec.mountTarget')" width="80" align="center">
                  <template #default="{ row }">#{{ row.sourceId }}</template>
                </el-table-column>
                <el-table-column :label="t('ai.spec.mountWhitelist')">
                  <template #default="{ row }">
                    <span v-if="(row.allowedTools || []).length" class="font-mono text-12px">
                      {{ row.allowedTools!.join('、') }}
                    </span>
                    <span v-else class="text-gray-400">{{ t('ai.spec.previewAllTools') }}</span>
                  </template>
                </el-table-column>
                <el-table-column :label="t('ai.spec.mountSensitive')">
                  <template #default="{ row }">
                    <span v-if="(row.sensitiveTools || []).length" class="font-mono text-12px">
                      {{ row.sensitiveTools!.join('、') }}
                    </span>
                    <span v-else class="text-gray-400">—</span>
                  </template>
                </el-table-column>
              </el-table>
              <span v-else class="text-13px text-gray-400">{{ t('ai.spec.previewEmpty') }}</span>
            </div>
            <div>
              <div class="text-13px mb-4px">{{ t('ai.spec.formFolders') }}</div>
              <template v-if="(previewDetail.folders || []).length">
                <div
                  v-for="(folder, index) in previewDetail.folders"
                  :key="index"
                  class="border border-gray-200 rounded-4px p-8px mb-8px"
                >
                  <div class="flex items-center gap-8px mb-4px">
                    <el-tag size="small" :type="folder.type === 'ASSET' ? 'success' : 'warning'">
                      {{
                        folder.type === 'ASSET'
                          ? t('ai.spec.folderTypeAsset')
                          : t('ai.spec.folderTypeToolset')
                      }}
                    </el-tag>
                    <span class="font-mono text-13px">{{ folder.name }}</span>
                    <span class="text-12px text-gray-400">
                      {{ folder.files.length }} {{ t('ai.spec.previewFileCount') }}
                    </span>
                  </div>
                  <el-table :data="folder.files" size="small">
                    <el-table-column prop="path" :label="t('ai.spec.folderFilePath')" min-width="150">
                      <template #default="{ row }">
                        <span class="font-mono text-12px">{{ row.path }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column
                      prop="size"
                      :label="t('ai.spec.folderFileSize')"
                      width="100"
                      align="right"
                    >
                      <template #default="{ row }">{{ formatSize(row.size) }}</template>
                    </el-table-column>
                    <el-table-column :label="t('ai.spec.folderFileHash')" min-width="130">
                      <template #default="{ row }">
                        <span class="text-12px text-gray-400 font-mono">
                          {{ row.contentHash.slice(0, 12) }}…
                        </span>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </template>
              <span v-else class="text-13px text-gray-400">{{ t('ai.spec.previewEmpty') }}</span>
            </div>
          </div>

          <!-- 分区三：执行环境 -->
          <el-divider content-position="left">{{ t('ai.spec.sectionEnv') }}</el-divider>
          <el-descriptions :column="3" size="small" border>
            <el-descriptions-item :label="t('ai.spec.formWorkspace')">
              {{ previewDetail.workspaceEnabled ? t('ai.spec.previewEnabled') : t('ai.spec.previewDisabled') }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('ai.spec.formSandbox')">
              {{ previewDetail.sandboxEnabled ? t('ai.spec.previewEnabled') : t('ai.spec.previewDisabled') }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('ai.spec.formCapabilities')">
              <span v-if="(previewDetail.capabilities || []).length">
                {{ previewDetail.capabilities!.join('、') }}
              </span>
              <span v-else class="text-gray-400">—</span>
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
    </el-drawer>
  </Dialog>
</template>
<script lang="ts" setup>
import { dateFormatter, formatDate } from '@/utils/formatTime'
import * as SpecApi from '@/api/ai/spec'

defineOptions({ name: 'AiSpecVersion' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const loading = ref(false) // 列表的加载中
const publishing = ref(false) // 发布的提交中
const versionList = ref<SpecApi.AgentSpecVersionVO[]>([]) // 版本列表
const publishNote = ref('') // 发布备注
const specId = ref<number>() // 当前规格编号
const hasDraft = ref(true) // 当前规格是否有草稿（发布门禁依据，随打开传入、发布后同步为 false）

/** 打开弹窗（按规格加载版本列表；hasDraft 决定发布按钮门禁） */
const open = (row: { id: number; hasDraft: boolean }) => {
  specId.value = row.id
  hasDraft.value = row.hasDraft
  publishNote.value = ''
  dialogVisible.value = true
  getList()
}
defineExpose({ open })

/** 查询版本列表（open 后必然已设置 specId） */
const getList = async () => {
  loading.value = true
  try {
    versionList.value = await SpecApi.getSpecVersionPage(specId.value!)
  } finally {
    loading.value = false
  }
}

/** 发布新版本（把当前草稿固化为不可变快照；发布清空草稿，门禁随即落下） */
const handlePublish = async () => {
  publishing.value = true
  try {
    await SpecApi.publishSpec({ id: specId.value!, note: publishNote.value })
    message.success(t('ai.spec.versionPublishSuccess'))
    publishNote.value = ''
    hasDraft.value = false
    getList()
  } finally {
    publishing.value = false
  }
}

/** 切换当前版本（回退指针） */
const handleSwitch = async (row: SpecApi.AgentSpecVersionVO) => {
  await SpecApi.switchSpecVersion({ id: specId.value!, versionNo: row.versionNo })
  message.success(t('ai.spec.versionSwitchSuccess'))
  getList()
}

// ---------- 版本快照只读预览（工单 24：固化保真的全量四层配置） ----------

const previewVisible = ref(false) // 预览抽屉的是否展示
const previewLoading = ref(false) // 预览的加载中
const previewDetail = ref<SpecApi.AgentSpecVersionDetailVO>() // 预览的版本快照详情

/** 打开只读预览抽屉（按需读取版本全量配置） */
const openPreview = async (row: SpecApi.AgentSpecVersionVO) => {
  previewDetail.value = undefined // 切换预览目标时先清空旧内容，避免闪现
  previewVisible.value = true
  previewLoading.value = true
  try {
    previewDetail.value = await SpecApi.getSpecVersionDetail(specId.value!, row.versionNo)
  } finally {
    previewLoading.value = false
  }
}

/** 字节数人类可读化（与编辑表单同口径） */
const formatSize = (size: number): string => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
</script>
