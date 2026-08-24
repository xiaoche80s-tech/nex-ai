<template>
  <!-- 列表视图 -->
  <div v-if="view === 'list'" class="p-4">
    <el-table :data="specs" border stripe @row-click="(row: SpecRow) => enterDetail(row)">
      <el-table-column label="规格" min-width="220">
        <template #default="{ row }">
          <span class="mr-2">{{ row.icon }}</span>
          <span class="font-semibold">{{ row.name }}</span>
          <span class="text-12px text-#a1a1aa ml-2">{{ row.specCode }}</span>
        </template>
      </el-table-column>
      <el-table-column label="归属" width="110">
        <template #default="{ row }">
          {{ row.ownerLevel === 'TENANT' ? '租户级' : `用户级 · ${row.ownerUserName}` }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="150">
        <template #default="{ row }">
          <el-tag :type="statusOf(row).type" size="small">{{ statusOf(row).text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生效快照" width="200">
        <template #default="{ row }">
          <div class="text-13px">{{ snapshotSummary(row) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="未发布修改" min-width="220">
        <template #default="{ row }">
          <span v-if="row.hasDraft" class="text-13px c-#b45309">▲ {{ row.draftSummary }}</span>
          <span v-else class="text-13px text-#a1a1aa">—</span>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" prop="updateTime" width="150" />
    </el-table>
    <p class="text-12px text-#a1a1aa mt-2">点击行进入详情</p>
  </div>

  <!-- 详情视图：横幅强调「生效快照 vs 草稿」对照 -->
  <div v-else-if="selected" class="p-4">
    <el-breadcrumb class="mb-3">
      <el-breadcrumb-item><a @click.prevent="view = 'list'">智能体规格</a></el-breadcrumb-item>
      <el-breadcrumb-item>{{ selected.name }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 规格头 + 主操作 -->
    <div class="flex items-center gap-3 mb-4">
      <span class="text-28px">{{ selected.icon }}</span>
      <div>
        <div class="flex items-center gap-2">
          <span class="text-18px font-bold">{{ selected.name }}</span>
          <el-tag :type="statusOf(selected).type" size="small">{{ statusOf(selected).text }}</el-tag>
        </div>
        <div class="text-12px text-#71717a">
          {{ selected.specCode }} · {{ selected.ownerLevel === 'TENANT' ? '租户级' : `用户级 · ${selected.ownerUserName}` }}
        </div>
      </div>
      <div class="ml-a flex items-center gap-2">
        <el-button @click="onReadOnly">编辑</el-button>
        <el-tooltip
          :disabled="selected.hasDraft"
          content="已发布且无新草稿；编辑保存后方可发布新版本"
          placement="bottom"
        >
          <span>
            <el-button type="primary" :disabled="!selected.hasDraft" @click="onReadOnly">
              🚀 发布新版本
            </el-button>
          </span>
        </el-tooltip>
      </div>
    </div>

    <!-- 对照横幅：当前生效快照 vs 草稿（本变体的核心信息层级） -->
    <div class="grid grid-cols-2 gap-3 mb-5">
      <div class="p-4 bg-#f0fdf4 rd-2 border border-solid border-#bbf7d0">
        <div class="text-13px font-semibold text-#166534 mb-1">
          当前生效快照
          <b v-if="selected.currentVersionNo">v{{ selected.currentVersionNo }}</b>
          <b v-else class="text-#71717a font-normal">（尚未发布）</b>
        </div>
        <div class="text-13px">{{ snapshotSummary(selected) }}</div>
        <div v-if="currentVersion" class="text-12px text-#71717a mt-1">
          {{ currentVersion.publisher }} 发布于 {{ currentVersion.createTime }} · 运行侧按此寻址
        </div>
      </div>
      <div
        class="p-4 rd-2 border border-solid"
        :class="selected.hasDraft ? 'bg-#fffbeb border-#fde68a' : 'bg-#f5f7fa border-#e4e4e7'"
      >
        <div class="text-13px font-semibold mb-1" :class="selected.hasDraft ? 'text-#92400e' : 'text-#71717a'">
          {{ selected.hasDraft ? '✏️ 未发布草稿' : '🔒 无草稿（稳定）' }}
        </div>
        <div class="text-13px">
          {{ selected.hasDraft ? selected.draftSummary : '编辑保存后产生新草稿，届时可发布新版本' }}
        </div>
      </div>
    </div>

    <!-- 版本演进：横向步骤 + 详情切换 -->
    <el-steps v-if="versions.length" :active="selected.currentVersionNo ?? 0" align-center class="mb-4">
      <el-step
        v-for="v in versions"
        :key="v.versionNo"
        :title="`v${v.versionNo}`"
        :description="v.note"
        @click="previewVersion = v"
      />
    </el-steps>

    <el-table v-if="versions.length" :data="reversedVersions" border size="small">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="p-3">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="模型">{{ row.config.model }}</el-descriptions-item>
              <el-descriptions-item label="温度 / 迭代">{{ row.config.temperature }} / {{ row.config.maxIters }}</el-descriptions-item>
              <el-descriptions-item label="系统提示" :span="2">
                <div class="whitespace-pre-wrap text-13px">{{ row.config.systemPrompt }}</div>
              </el-descriptions-item>
              <el-descriptions-item label="挂载" :span="2">
                <div class="text-13px">
                  🎓 {{ row.config.skills.join('、') }}<br />
                  🔧 {{ row.config.tools.join('；') }}<br />
                  📁 {{ row.config.folders.join('；') }}
                </div>
              </el-descriptions-item>
              <el-descriptions-item label="执行环境" :span="2">
                Workspace {{ row.config.workspace ? '开' : '关' }} · 沙箱 {{ row.config.sandbox ? '开' : '关' }} ·
                {{ row.config.capabilities.join('/') }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="版本" width="70">
        <template #default="{ row }"><b>v{{ row.versionNo }}</b></template>
      </el-table-column>
      <el-table-column label="备注" prop="note" min-width="160" />
      <el-table-column label="发布人" prop="publisher" width="110" />
      <el-table-column label="发布时间" prop="createTime" width="150" />
      <el-table-column label="生效" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.versionNo === selected.currentVersionNo" type="primary" size="small" effect="dark">●</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110">
        <template #default="{ row }">
          <el-button
            link
            :disabled="row.versionNo === selected.currentVersionNo"
            type="warning"
            @click="onReadOnly"
          >
            切换到此版
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else description="尚无版本快照（发布后产生 v1）" :image-size="60" />
  </div>
</template>

<script lang="ts" setup>
/** 变体 C —— 详情页式：列表↔详情页内导航，横幅对照「生效快照 vs 草稿」，版本为步骤条 + 展开行 */
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import { specs, statusOf, versionsBySpec, type SpecRow, type VersionRow } from './mock'

defineOptions({ name: 'VariantC' })

const view = ref<'list' | 'detail'>('list')
const selected = ref<SpecRow | null>(null)
const previewVersion = ref<VersionRow | null>(null)

const versions = computed(() => (selected.value ? versionsBySpec[selected.value.id] ?? [] : []))
const reversedVersions = computed(() => [...versions.value].reverse())
const currentVersion = computed(
  () => versions.value.find((v) => v.versionNo === selected.value?.currentVersionNo) ?? null
)

function snapshotSummary(row: SpecRow): string {
  const vs = versionsBySpec[row.id] ?? []
  const cur = vs.find((v) => v.versionNo === row.currentVersionNo)
  return cur ? cur.config.systemPrompt : '（尚未发布任何版本）'
}
function enterDetail(row: SpecRow) {
  selected.value = row
  previewVersion.value = null
  view.value = 'detail'
}
function onReadOnly() {
  ElMessage.info('原型只读：此动作在实现工单中生效')
}
</script>
