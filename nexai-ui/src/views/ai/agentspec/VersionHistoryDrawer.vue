<template>
  <el-drawer v-model="drawerVisible" :title="`版本历史 — ${specName}`" size="680px">
    <el-table v-loading="loading" :data="versions">
      <el-table-column label="版本" align="center" width="80">
        <template #default="scope">
          <el-tag
            v-if="scope.row.versionNo === currentVersionNo"
            type="success"
            disable-transitions
          >
            v{{ scope.row.versionNo }} 默认
          </el-tag>
          <span v-else>v{{ scope.row.versionNo }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="发布说明"
        align="center"
        prop="remark"
        min-width="140"
        show-overflow-tooltip
      >
        <template #default="scope">
          <span>{{ scope.row.remark || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="模型"
        align="center"
        prop="config.modelName"
        min-width="120"
        show-overflow-tooltip
      >
        <template #default="scope">
          <span>{{ scope.row.config?.modelName || `#${scope.row.config?.modelId}` }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="发布时间"
        align="center"
        prop="createTime"
        width="170"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" align="center" width="170" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openSnapshot(scope.row)">快照</el-button>
          <el-button
            v-if="scope.row.versionNo !== currentVersionNo"
            link
            type="warning"
            :loading="scope.row.switching"
            @click="handleSwitch(scope.row)"
            v-hasPermi="['ai:spec:update']"
          >
            设为默认
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 快照查看弹窗：只读展示全量配置 JSON -->
    <Dialog v-model="snapshotVisible" :title="`v${snapshotVersionNo} 全量快照（只读）`" width="640">
      <el-descriptions v-if="snapshotConfig" :column="2" border size="small" class="mb-12px">
        <el-descriptions-item label="模型">{{
          snapshotConfig.modelName || `#${snapshotConfig.modelId}`
        }}</el-descriptions-item>
        <el-descriptions-item label="温度">{{
          snapshotConfig.generateOptions?.temperature ?? '默认'
        }}</el-descriptions-item>
        <el-descriptions-item label="最大迭代轮数">{{
          snapshotConfig.maxIters ?? '默认'
        }}</el-descriptions-item>
        <el-descriptions-item label="发布说明">{{ snapshotRemark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-input :model-value="snapshotJson" type="textarea" :rows="14" readonly class="font-mono" />
    </Dialog>
  </el-drawer>
</template>
<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import * as SpecApi from '@/api/ai/spec'

defineOptions({ name: 'AiAgentSpecVersionHistory' })

const message = useMessage() // 消息弹窗

const drawerVisible = ref(false) // 抽屉的是否展示
const loading = ref(false) // 列表的加载中
const specId = ref<number>(0) // 当前规格编号
const specName = ref('') // 当前规格名称（标题展示）
const currentVersionNo = ref<number | null>(null) // 当前默认版本号
const versions = ref<(SpecApi.AgentSpecVersionVO & { switching?: boolean })[]>([]) // 版本列表

/** 打开抽屉并加载版本历史 */
const open = async (row: SpecApi.AgentSpecVO) => {
  specId.value = row.id!
  specName.value = row.name
  drawerVisible.value = true
  await loadVersions()
}
defineExpose({ open }) // 提供 open 方法，用于打开抽屉

/** 刷新版本列表与默认版本标记 */
const emit = defineEmits(['switched']) // 默认版本切换成功后通知父级刷新列表
const loadVersions = async () => {
  loading.value = true
  try {
    const detail = await SpecApi.getSpec(specId.value)
    currentVersionNo.value = detail.currentVersionNo
    versions.value = await SpecApi.getVersionList(specId.value)
  } finally {
    loading.value = false
  }
}

/** 切换默认版本（回滚/迭代入口） */
const handleSwitch = async (row: SpecApi.AgentSpecVersionVO & { switching?: boolean }) => {
  row.switching = true
  try {
    await SpecApi.switchDefaultVersion({ id: specId.value, versionNo: row.versionNo })
    message.success(`已把默认版本切换到 v${row.versionNo}`)
    await loadVersions()
    emit('switched')
  } finally {
    row.switching = false
  }
}

// ==================== 快照查看 ====================
const snapshotVisible = ref(false)
const snapshotVersionNo = ref(0)
const snapshotRemark = ref('')
const snapshotConfig = ref<SpecApi.AgentSpecConfig | null>(null)
const snapshotJson = ref('')

const openSnapshot = (row: SpecApi.AgentSpecVersionVO) => {
  snapshotVersionNo.value = row.versionNo
  snapshotRemark.value = row.remark ?? ''
  snapshotConfig.value = row.config
  snapshotJson.value = JSON.stringify(row.config, null, 2)
  snapshotVisible.value = true
}
</script>
