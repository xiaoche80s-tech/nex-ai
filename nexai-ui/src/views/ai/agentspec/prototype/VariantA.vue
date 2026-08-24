<template>
  <div class="p-4">
    <!-- 搜索与新增 -->
    <el-form inline class="mb-4">
      <el-form-item label="规格名称">
        <el-input v-model="keyword" placeholder="名称 / 业务编码" clearable class="!w-220px" />
      </el-form-item>
      <el-form-item>
        <el-button @click="filtered">搜索</el-button>
      </el-form-item>
      <el-form-item class="float-right">
        <el-button type="primary" @click="openCreate">＋ 新增规格</el-button>
      </el-form-item>
    </el-form>

    <!-- 列表：三态状态列（工单 23）；reactive mock，动作真实流转 -->
    <el-table :data="filteredRows" border stripe>
      <el-table-column label="业务编码" prop="specCode" width="150" />
      <el-table-column label="规格名称" min-width="190">
        <template #default="{ row }">
          <span class="mr-2">{{ row.icon }}</span>
          <span class="font-semibold">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="归属" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.ownerLevel === 'TENANT'" type="primary" size="small">租户级</el-tag>
          <el-tag v-else type="info" size="small">用户级 · {{ row.ownerUserName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="150">
        <template #default="{ row }">
          <el-tag :type="statusOf(row).type" size="small" effect="light">
            {{ statusOf(row).text }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="草稿" min-width="220">
        <template #default="{ row }">
          <span v-if="row.hasDraft" class="text-13px">{{ row.draftSummary }}</span>
          <span v-else class="text-13px text-#a1a1aa">（发布时已清空）</span>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" prop="updateTime" width="150" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openVersions(row)">版本</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增 / 编辑表单（ADR 0004：编辑回填来源徽章 + 保存才落新草稿） -->
    <SpecFormDialog ref="specFormRef" v-model="formVisible" :mode="formMode" :spec="formSpec" @saved="onSaved" />

    <!-- 版本弹窗：发布门禁（工单 22/23）+ 发布人列（工单 25）+ 预览入口（工单 24） -->
    <el-dialog v-model="dialogVisible" :title="`版本管理 · ${current?.name ?? ''}`" width="860px">
      <template v-if="current">
        <div class="flex items-center gap-3 mb-4">
          <el-tag :type="statusOf(current).type" size="small">{{ statusOf(current).text }}</el-tag>
          <span v-if="current.hasDraft" class="text-13px text-#71717a">
            草稿：{{ current.draftSummary }}
          </span>
          <span v-else class="text-13px text-#71717a">当前无草稿</span>
        </div>

        <!-- 发布区：无草稿 → 禁用 + tooltip（Q8 定案）；有草稿可真实发布（内存流转） -->
        <div class="flex items-center gap-2 mb-4 p-3 bg-#f5f7fa rd-2">
          <el-input v-model="publishNote" placeholder="发布备注（可空）" class="!w-260px" />
          <el-tooltip
            :disabled="current.hasDraft"
            content="已发布且无新草稿；编辑保存后方可发布新版本"
            placement="top"
          >
            <span>
              <el-button type="primary" :disabled="!current.hasDraft" @click="doPublish">
                🚀 发布新版本
              </el-button>
            </span>
          </el-tooltip>
          <span class="text-12px text-#a1a1aa ml-a">发布 = 固化快照 + 推进指针 + 清空草稿</span>
        </div>

        <el-table :data="reversedVersions" border size="small">
          <el-table-column label="版本" width="70">
            <template #default="{ row }">
              <b>v{{ row.versionNo }}</b>
            </template>
          </el-table-column>
          <el-table-column label="发布备注" prop="note" min-width="140" />
          <el-table-column label="发布人" prop="publisher" width="110" />
          <el-table-column label="发布时间" prop="createTime" width="150" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.versionNo === current.currentVersionNo" type="primary" size="small" effect="dark">
                ● 当前生效
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button link type="primary" @click="openPreview(row)">预览</el-button>
              <el-button
                link
                :type="row.versionNo === current.currentVersionNo ? 'info' : 'warning'"
                :disabled="row.versionNo === current.currentVersionNo"
                @click="doSwitch(row)"
              >
                {{ row.versionNo === current.currentVersionNo ? '已是当前' : '切换到此版' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="reversedVersions.length === 0" description="尚无版本快照（发布后产生 v1）" :image-size="60" />
      </template>
    </el-dialog>

    <!-- 版本预览抽屉（工单 24）：只读四层配置，不含 name/icon -->
    <el-drawer v-model="previewVisible" :title="`版本快照 · v${previewVersion?.versionNo ?? ''}`" size="620px">
      <template v-if="previewVersion">
        <el-descriptions :column="2" border size="small" class="mb-4">
          <el-descriptions-item label="发布备注" :span="2">{{ previewVersion.note || '—' }}</el-descriptions-item>
          <el-descriptions-item label="发布人">{{ previewVersion.publisher }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ previewVersion.createTime }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">模型与提示</el-divider>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="模型">{{ previewVersion.config.model }}</el-descriptions-item>
          <el-descriptions-item label="温度">{{ previewVersion.config.temperature }}</el-descriptions-item>
          <el-descriptions-item label="最大迭代">{{ previewVersion.config.maxIters }}</el-descriptions-item>
          <el-descriptions-item label="系统提示" :span="2">
            <div class="whitespace-pre-wrap text-13px">{{ previewVersion.config.systemPrompt }}</div>
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">挂载</el-divider>
        <div class="flex flex-col gap-1 text-13px">
          <div v-for="s in previewVersion.config.skills" :key="s">🎓 Skill：{{ s }}</div>
          <div v-for="t in previewVersion.config.tools" :key="t">🔧 工具：{{ t }}</div>
          <div v-for="f in previewVersion.config.folders" :key="f">📁 文件夹：{{ f }}</div>
        </div>

        <el-divider content-position="left">执行环境</el-divider>
        <div class="flex gap-2">
          <el-tag size="small" :type="previewVersion.config.workspace ? 'success' : 'info'">
            Workspace {{ previewVersion.config.workspace ? '开' : '关' }}
          </el-tag>
          <el-tag size="small" :type="previewVersion.config.sandbox ? 'success' : 'info'">
            沙箱 {{ previewVersion.config.sandbox ? '开' : '关' }}
          </el-tag>
          <el-tag v-for="c in previewVersion.config.capabilities" :key="c" size="small" type="warning">
            {{ c }}
          </el-tag>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script lang="ts" setup>
/**
 * 变体 A —— 弹窗基线（胜出方案，本轮迭代）：表格 + 新增/编辑表单 + 版本 Dialog + 预览 Drawer。
 * 本地内存流转（刷新重置）：新增→草稿态入列；编辑→保存才落新草稿（稳定态保存后转「编辑中」）；
 * 发布→固化快照+清空草稿；切换→只回退指针不动草稿（ADR 0004 全语义）。
 */
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import SpecFormDialog from './SpecFormDialog.vue'
import {
  allocSpecId, configOf, CURRENT_USER, specs, statusOf, versionsBySpec,
  type SpecRow, type VersionRow
} from './mock'

defineOptions({ name: 'VariantA' })

const keyword = ref('')
const filteredRows = computed(() =>
  specs.filter(
    (s) => !keyword.value || s.name.includes(keyword.value) || s.specCode.includes(keyword.value)
  )
)
function filtered() {
  // 搜索为本地即时过滤（computed），按钮仅作占位反馈
  ElMessage.info('列表已按输入即时过滤')
}

/* —— 新增 / 编辑 —— */
const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formSpec = ref<SpecRow | null>(null)
const specFormRef = ref()

function openCreate() {
  formMode.value = 'create'
  formSpec.value = null
  specFormRef.value?.initForm?.()
  formVisible.value = true
}
function openEdit(row: SpecRow) {
  formMode.value = 'edit'
  formSpec.value = row
  formVisible.value = true
}

function nowStr() {
  return new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
}

function onSaved(p: {
  name: string; specCode: string; icon: string
  ownerLevel: 'TENANT' | 'USER'; prompt: string
}) {
  const summary = p.prompt.length > 26 ? p.prompt.slice(0, 26) + '…' : p.prompt
  if (formMode.value === 'create') {
    specs.push({
      id: allocSpecId(), name: p.name, specCode: p.specCode, icon: p.icon || '🤖',
      ownerLevel: p.ownerLevel, ownerUserName: p.ownerLevel === 'USER' ? CURRENT_USER : '—',
      currentVersionNo: null, hasDraft: true,
      draftPrompt: p.prompt, draftSummary: summary,
      updateTime: nowStr()
    })
    ElMessage.success(`已创建「${p.name}」（草稿态，从未发布）`)
  } else if (formSpec.value) {
    const s = formSpec.value
    const wasStable = !s.hasDraft
    s.name = p.name
    s.icon = p.icon || '🤖'
    s.draftPrompt = p.prompt
    s.draftSummary = summary
    s.hasDraft = true
    s.updateTime = nowStr()
    ElMessage.success(
      wasStable
        ? `已保存新草稿（状态 → 「v${s.currentVersionNo} · 编辑中」，发布按钮恢复可用）`
        : '草稿已保存'
    )
  }
}

/* —— 版本弹窗：发布 / 切换（内存流转） —— */
const dialogVisible = ref(false)
const publishNote = ref('')
const current = ref<SpecRow | null>(null)
const reversedVersions = computed(() =>
  current.value ? [...(versionsBySpec[current.value.id] ?? [])].reverse() : []
)

const previewVisible = ref(false)
const previewVersion = ref<VersionRow | null>(null)

function openVersions(row: SpecRow) {
  current.value = row
  publishNote.value = ''
  dialogVisible.value = true
}
function doPublish() {
  const s = current.value
  if (!s || !s.hasDraft || s.draftPrompt == null) return
  const nextNo = (versionsBySpec[s.id] ?? []).reduce((m, v) => Math.max(m, v.versionNo), 0) + 1
  if (!versionsBySpec[s.id]) versionsBySpec[s.id] = []
  versionsBySpec[s.id].push({
    versionNo: nextNo, note: publishNote.value, publisher: CURRENT_USER,
    createTime: nowStr(), config: configOf(s.draftPrompt)
  })
  s.currentVersionNo = nextNo
  s.hasDraft = false
  s.draftPrompt = null
  s.draftSummary = null
  s.updateTime = nowStr()
  ElMessage.success(`已发布 v${nextNo}：快照固化、指针推进、草稿已清空（状态 → 「v${nextNo} 已发布」）`)
}
function doSwitch(row: VersionRow) {
  const s = current.value
  if (!s) return
  s.currentVersionNo = row.versionNo
  s.updateTime = nowStr()
  ElMessage.success(
    s.hasDraft
      ? `当前生效版本 → v${row.versionNo}（草稿原样保留，状态仍为「编辑中」）`
      : `当前生效版本 → v${row.versionNo}`
  )
}
function openPreview(row: VersionRow) {
  previewVersion.value = row
  previewVisible.value = true
}
</script>
