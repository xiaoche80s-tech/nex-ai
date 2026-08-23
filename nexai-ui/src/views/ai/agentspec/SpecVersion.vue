<template>
  <Dialog v-model="dialogVisible" :title="t('ai.spec.versionDialogTitle')" width="620">
    <!-- 发布新版本 -->
    <div class="flex gap-8px mb-12px">
      <el-input
        v-model="publishNote"
        :placeholder="t('ai.spec.versionPublishPlaceholder')"
        :maxlength="255"
        clearable
        class="!w-380px"
      />
      <el-button
        type="primary"
        :disabled="publishing"
        v-hasPermi="['ai:spec:publish']"
        @click="handlePublish"
      >
        {{ t('ai.spec.versionPublish') }}
      </el-button>
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
        :label="t('ai.spec.versionTableCreateTime')"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column :label="t('table.action')" align="center" width="100">
        <template #default="{ row }">
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
      </template>
    </el-table>
  </Dialog>
</template>
<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
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

/** 打开弹窗（按规格加载版本列表） */
const open = (id: number) => {
  specId.value = id
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

/** 发布新版本（把当前草稿固化为不可变快照） */
const handlePublish = async () => {
  publishing.value = true
  try {
    await SpecApi.publishSpec({ id: specId.value!, note: publishNote.value })
    message.success(t('ai.spec.versionPublishSuccess'))
    publishNote.value = ''
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
</script>
