<template>
  <el-drawer v-model="drawerVisible" :title="`版本历史 — ${skillName}`" size="720px">
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
      <el-table-column label="资源数" align="center" width="80">
        <template #default="scope">
          <span>{{ Object.keys(scope.row.content?.resources ?? {}).length }}</span>
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
            v-hasPermi="['ai:skill:update']"
          >
            设为默认
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 快照查看弹窗：只读展示 SKILL.md 与资源文件 -->
    <Dialog v-model="snapshotVisible" :title="`v${snapshotVersionNo} 内容快照（只读）`" width="720">
      <el-descriptions v-if="snapshotVersion" :column="2" border size="small" class="mb-12px">
        <el-descriptions-item label="发布说明">{{
          snapshotVersion.remark || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{
          snapshotVersion ? formatDate(snapshotVersion.createTime) : '—'
        }}</el-descriptions-item>
      </el-descriptions>
      <div class="text-14px font-bold mb-4px">SKILL.md</div>
      <el-input
        :model-value="snapshotVersion?.content?.skillMd"
        type="textarea"
        :rows="10"
        readonly
        spellcheck="false"
        class="font-mono mb-12px"
      />
      <template v-for="(content, path) in snapshotVersion?.content?.resources ?? {}" :key="path">
        <div class="text-14px font-bold mb-4px font-mono">{{ path }}</div>
        <el-input
          :model-value="content"
          type="textarea"
          :rows="4"
          readonly
          spellcheck="false"
          class="font-mono mb-12px"
        />
      </template>
    </Dialog>
  </el-drawer>
</template>
<script lang="ts" setup>
import { dateFormatter, formatDate } from '@/utils/formatTime'
import * as SkillApi from '@/api/ai/skill'

defineOptions({ name: 'AiSkillVersionHistory' })

const message = useMessage() // 消息弹窗

const drawerVisible = ref(false) // 抽屉的是否展示
const loading = ref(false) // 列表的加载中
const skillId = ref<number>(0) // 当前技能编号
const skillName = ref('') // 当前技能名（标题展示）
const currentVersionNo = ref<number | null>(null) // 当前默认版本号
const versions = ref<(SkillApi.SkillVersionVO & { switching?: boolean })[]>([]) // 版本列表

/** 打开抽屉并加载版本历史 */
const open = async (row: SkillApi.SkillVO) => {
  skillId.value = row.id!
  skillName.value = row.name
  drawerVisible.value = true
  await loadVersions()
}
defineExpose({ open }) // 提供 open 方法，用于打开抽屉

/** 刷新版本列表与默认版本标记 */
const emit = defineEmits(['switched']) // 默认版本切换成功后通知父级刷新列表
const loadVersions = async () => {
  loading.value = true
  try {
    const detail = await SkillApi.getSkill(skillId.value)
    currentVersionNo.value = detail.currentVersionNo
    versions.value = await SkillApi.getVersionList(skillId.value)
  } finally {
    loading.value = false
  }
}

/** 切换默认版本（回滚/迭代入口，运行时读取随之切换） */
const handleSwitch = async (row: SkillApi.SkillVersionVO & { switching?: boolean }) => {
  row.switching = true
  try {
    await SkillApi.switchDefaultVersion({ id: skillId.value, versionNo: row.versionNo })
    message.success(`已把默认版本切换到 v${row.versionNo}，智能体运行时读取随之切换`)
    await loadVersions()
    emit('switched')
  } finally {
    row.switching = false
  }
}

// ==================== 快照查看 ====================
const snapshotVisible = ref(false)
const snapshotVersionNo = ref(0)
const snapshotVersion = ref<SkillApi.SkillVersionVO | null>(null)

const openSnapshot = (row: SkillApi.SkillVersionVO) => {
  snapshotVersionNo.value = row.versionNo
  snapshotVersion.value = row
  snapshotVisible.value = true
}
</script>
