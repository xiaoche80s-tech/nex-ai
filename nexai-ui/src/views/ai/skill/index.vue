<template>
  <ContentWrap>
    <!-- 顶部：tab 筛选 + 搜索 + 主操作 -->
    <div class="flex items-center justify-between mb-4 flex-wrap gap-2">
      <el-tabs v-model="activeTab" class="!flex-1" @tab-change="handleTabChange">
        <el-tab-pane name="all" :label="t('ai.skill.tabAll')" />
        <el-tab-pane name="published" :label="t('ai.skill.tabPublished')" />
        <el-tab-pane name="created" :label="t('ai.skill.tabCreated')" />
        <el-tab-pane name="git" :label="t('ai.skill.tabGit')" />
        <el-tab-pane name="gitSource" :label="t('ai.skill.tabGitSource')" />
      </el-tabs>
      <div class="flex items-center gap-2">
        <el-input
          v-model="queryParams.name"
          :placeholder="t('ai.skill.searchName')"
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
        <el-button @click="handleQuery">
          <Icon icon="ep:search" class="mr-5px" /> {{ t('common.query') }}
        </el-button>
        <el-button type="primary" plain v-hasPermi="['ai:skill:create']" @click="openForm()">
          <Icon icon="ep:plus" class="mr-5px" /> {{ t('ai.skill.create') }}
        </el-button>
      </div>
    </div>

    <!-- Git 源管理：工单 29 占位 -->
    <div v-if="activeTab === 'gitSource'">
      <el-empty :description="t('ai.skill.gitSourcePlaceholder')">
        <template #image>
          <Icon icon="ep:connection" :size="48" class="text-gray-300" />
        </template>
      </el-empty>
    </div>

    <!-- 技能卡片网格 -->
    <div v-else>
      <div class="grid grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-4">
        <div
          v-for="row in list"
          :key="row.id"
          class="relative border border-gray-200 dark:border-gray-700 rounded-xl p-4 cursor-pointer
                 hover:shadow-md transition-shadow bg-white dark:bg-transparent"
          @click="openDetail(row)"
        >
          <span
            v-if="row.published === 1"
            class="absolute -top-2 -right-2 bg-green-500 text-white text-xs px-2 py-0.5 rounded-full"
          >
            {{ t('ai.skill.publishedBadge') }}
          </span>
          <div class="flex items-center gap-2 mb-1">
            <span class="font-semibold truncate">{{ row.name }}</span>
            <el-tag v-if="row.gitSourceId" type="info" size="small" disable-transitions>Git</el-tag>
            <el-tag v-if="row.ownerLevel === 'USER'" type="warning" size="small" disable-transitions>
              {{ t('ai.skill.ownerUser') }}
            </el-tag>
          </div>
          <p class="text-xs text-gray-500 dark:text-gray-400 h-8 overflow-hidden">
            {{ row.description }}
          </p>
          <div class="mt-3 flex items-center justify-between">
            <el-tag
              v-if="row.currentVersionNo"
              type="success"
              size="small"
              disable-transitions
            >
              v{{ row.currentVersionNo }}
            </el-tag>
            <el-tag v-else type="warning" size="small" disable-transitions>
              {{ t('ai.skill.noVersion') }}
            </el-tag>
            <el-switch
              v-model="row.published"
              :active-value="1"
              :inactive-value="0"
              size="small"
              :loading="publishingMap[row.id]"
              v-hasPermi="['ai:skill:update']"
              @click.stop
              @change="handlePublishChange(row)"
            />
          </div>
        </div>
      </div>
      <el-empty v-if="!loading && list.length === 0" :description="t('common.noData')" />
      <Pagination
        class="mt-4"
        :total="total"
        v-model:page="queryParams.pageNo"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </div>
  </ContentWrap>

  <!-- 详情大抽屉：版本 stepper + 内容预览 -->
  <el-drawer v-model="detailVisible" size="62%" :with-header="false">
    <div v-if="activeSkill" class="p-2">
      <header class="flex items-start justify-between border-b border-gray-100 dark:border-gray-700 pb-4">
        <div>
          <div class="flex items-center gap-2 flex-wrap">
            <h2 class="text-xl font-semibold">{{ activeSkill.name }}</h2>
            <el-tag v-if="activeSkill.gitSourceId" type="info" disable-transitions>
              {{ t('ai.skill.sourceGitReadonly') }}
            </el-tag>
            <el-tag v-else disable-transitions>{{ t('ai.skill.sourceCreated') }}</el-tag>
            <el-tag v-if="activeSkill.published === 1" type="success" disable-transitions>
              {{ t('ai.skill.publishedBadge') }}
            </el-tag>
            <el-tag v-if="activeSkill.ownerLevel === 'USER'" type="warning" disable-transitions>
              {{ t('ai.skill.ownerUser') }}
            </el-tag>
          </div>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">{{ activeSkill.description }}</p>
        </div>
        <div class="flex gap-2 flex-shrink-0">
          <el-button
            type="primary"
            :disabled="!!activeSkill.gitSourceId"
            v-hasPermi="['ai:skill:update']"
            @click="openVersionForm(activeSkill)"
          >
            {{ t('ai.skill.newVersion') }}
          </el-button>
          <el-button
            :type="activeSkill.published === 1 ? 'warning' : 'success'"
            plain
            v-hasPermi="['ai:skill:update']"
            @click="handlePublishButton"
          >
            {{ activeSkill.published === 1 ? t('ai.skill.unpublish') : t('ai.skill.publish') }}
          </el-button>
          <el-button type="danger" plain v-hasPermi="['ai:skill:delete']" @click="handleDelete(activeSkill)">
            {{ t('action.del') }}
          </el-button>
        </div>
      </header>

      <el-alert
        v-if="activeSkill.gitSourceId"
        type="info"
        :closable="false"
        class="mt-3"
        :title="t('ai.skill.gitReadonlyTip')"
      />

      <!-- 版本横向 stepper -->
      <div v-loading="versionsLoading" class="py-5">
        <el-steps v-if="versions.length" :active="stepIndex" align-center>
          <el-step
            v-for="v in versions"
            :key="v.versionNo"
            :title="`v${v.versionNo}`"
            :description="v.note ?? ''"
            class="cursor-pointer"
            @click="loadVersionContent(v.versionNo)"
          />
        </el-steps>
        <el-empty v-else :description="t('ai.skill.noVersion')" :image-size="60" />
      </div>

      <!-- 内容预览 -->
      <div v-if="viewing" v-loading="contentLoading">
        <el-tabs>
          <el-tab-pane label="SKILL.md">
            <pre class="bg-gray-50 dark:bg-gray-800 p-4 rounded text-xs whitespace-pre-wrap">{{ viewing.markdown }}</pre>
          </el-tab-pane>
          <el-tab-pane :label="t('ai.skill.resources') + `（${viewing.resourcePaths.length}）`">
            <el-empty v-if="!viewing.resourcePaths.length" :description="t('common.noData')" :image-size="60" />
            <div
              v-for="path in viewing.resourcePaths"
              :key="path"
              class="py-2 text-sm border-b border-gray-100 dark:border-gray-700 font-mono"
            >
              {{ path }}
            </div>
          </el-tab-pane>
        </el-tabs>
        <div class="mt-3 flex items-center justify-between">
          <span class="text-xs text-gray-400">
            {{ t('ai.skill.versionMeta', { no: viewing.versionNo, time: formatDate(viewing.createTime) }) }}
            ·
            {{ viewing.versionNo === activeSkill.currentVersionNo ? t('ai.skill.current') : t('ai.skill.historyVersion') }}
          </span>
          <el-tooltip
            v-if="viewing.versionNo !== activeSkill.currentVersionNo"
            :content="t('ai.skill.switchVersionTip')"
            placement="top"
          >
            <el-button size="small" type="warning" plain disabled>
              {{ t('ai.skill.setAsCurrent') }}
            </el-button>
          </el-tooltip>
        </div>
      </div>
    </div>
  </el-drawer>

  <!-- 创建 / 登记版本表单（沿用原形态） -->
  <el-dialog v-model="formVisible" :title="formTitle" width="720px">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
      <el-form-item :label="t('ai.skill.formName')" prop="name">
        <el-input v-model="formData.name" :placeholder="t('ai.skill.formNamePlaceholder')" :disabled="!!currentSkill" />
      </el-form-item>
      <el-form-item :label="t('ai.skill.formDescription')" prop="description">
        <el-input v-model="formData.description" type="textarea" :rows="2" :disabled="!!currentSkill" />
      </el-form-item>
      <el-form-item v-if="!currentSkill" :label="t('ai.skill.formOwnerLevel')" prop="ownerLevel">
        <el-radio-group v-model="formData.ownerLevel">
          <el-radio value="TENANT">{{ t('ai.skill.ownerTenant') }}</el-radio>
          <el-radio value="USER">{{ t('ai.skill.ownerUser') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="t('ai.skill.formMarkdown')" prop="markdown">
        <el-input
          v-model="formData.markdown"
          type="textarea"
          :rows="10"
          :placeholder="t('ai.skill.formMarkdownPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('ai.skill.formNote')" prop="note">
        <el-input v-model="formData.note" :placeholder="t('ai.skill.formNotePlaceholder')" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">
        {{ t('common.ok') }}
      </el-button>
      <el-button @click="formVisible = false">{{ t('common.cancel') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDate } from '@/utils/formatTime'
import {
  createSkill,
  addSkillVersion,
  getSkillPage,
  getSkillVersionPage,
  getSkillVersionContent,
  deleteSkill,
  publishSkill,
  unpublishSkill,
  type SkillVO,
  type SkillVersionVO,
  type SkillVersionContentVO
} from '@/api/ai/skill'

defineOptions({ name: 'AiSkill' })

const { t } = useI18n()

// ---------- 卡片库列表（tab 筛选 + 分页） ----------
type TabKey = 'all' | 'published' | 'created' | 'git' | 'gitSource'
const activeTab = ref<TabKey>('all')
const loading = ref(true)
const list = ref<SkillVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 12,
  name: undefined as string | undefined
})

const getList = async () => {
  if (activeTab.value === 'gitSource') return
  loading.value = true
  try {
    const params: Record<string, unknown> = { ...queryParams }
    if (activeTab.value === 'published') params.published = 1
    if (activeTab.value === 'created') params.sourceType = 'created'
    if (activeTab.value === 'git') params.sourceType = 'git'
    const data = await getSkillPage(params)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleTabChange = () => {
  queryParams.pageNo = 1
  getList()
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

getList()

// ---------- 上架 / 下架 ----------
const publishingMap = reactive<Record<number, boolean>>({})

const handlePublishChange = async (row: SkillVO) => {
  publishingMap[row.id] = true
  try {
    if (row.published === 1) {
      await publishSkill(row.id)
    } else {
      await unpublishSkill(row.id)
    }
    ElMessage.success(row.published === 1 ? t('ai.skill.publishedOk') : t('ai.skill.unpublishedOk'))
  } catch {
    row.published = row.published === 1 ? 0 : 1 // 失败回滚开关
  } finally {
    publishingMap[row.id] = false
  }
}

// ---------- 详情抽屉：版本 stepper + 预览 ----------
const detailVisible = ref(false)
const versionsLoading = ref(false)
const contentLoading = ref(false)
const activeSkill = ref<SkillVO>()
const versions = ref<SkillVersionVO[]>([])
const viewingNo = ref<number>()
const viewing = ref<SkillVersionContentVO>()

const stepIndex = computed(() =>
  versions.value.findIndex((v) => v.versionNo === viewingNo.value)
)

const openDetail = async (row: SkillVO) => {
  activeSkill.value = row
  viewing.value = undefined
  viewingNo.value = row.currentVersionNo ?? undefined
  detailVisible.value = true
  versionsLoading.value = true
  try {
    versions.value = await getSkillVersionPage(row.id)
    if (viewingNo.value) {
      await loadVersionContent(viewingNo.value)
    }
  } finally {
    versionsLoading.value = false
  }
}

const loadVersionContent = async (versionNo: number) => {
  if (!activeSkill.value) return
  viewingNo.value = versionNo
  contentLoading.value = true
  try {
    viewing.value = await getSkillVersionContent(activeSkill.value.id, versionNo)
  } finally {
    contentLoading.value = false
  }
}

const handlePublishButton = async () => {
  if (!activeSkill.value) return
  activeSkill.value.published = activeSkill.value.published === 1 ? 0 : 1
  await handlePublishChange(activeSkill.value)
}

// ---------- 创建 / 登记版本表单 ----------
const formVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const formData = reactive({
  name: '',
  description: '',
  ownerLevel: 'TENANT',
  markdown: '',
  note: ''
})
const formRules = {
  name: [{ required: true, message: t('ai.skill.nameRequired'), trigger: 'blur' }],
  description: [{ required: true, message: t('ai.skill.descriptionRequired'), trigger: 'blur' }],
  markdown: [{ required: true, message: t('ai.skill.markdownRequired'), trigger: 'blur' }]
}
/** 当前编辑的 Skill（有值 = 登记新版本；无值 = 创建） */
const currentSkill = ref<SkillVO>()

const formTitle = computed(() =>
  currentSkill.value ? t('ai.skill.formTitleVersion') : t('ai.skill.formTitleCreate')
)

const openForm = () => {
  currentSkill.value = undefined
  formData.name = ''
  formData.description = ''
  formData.ownerLevel = 'TENANT'
  formData.markdown = ''
  formData.note = ''
  formVisible.value = true
}

const openVersionForm = (row: SkillVO) => {
  currentSkill.value = row
  formData.name = row.name
  formData.description = row.description
  formData.ownerLevel = row.ownerLevel
  formData.markdown = ''
  formData.note = ''
  detailVisible.value = false
  formVisible.value = true
}

const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    if (currentSkill.value) {
      await addSkillVersion({
        skillId: currentSkill.value.id,
        markdown: formData.markdown,
        note: formData.note
      })
      ElMessage.success(t('ai.skill.versionAdded'))
    } else {
      await createSkill({ ...formData })
      ElMessage.success(t('ai.skill.created'))
    }
    formVisible.value = false
    getList()
  } finally {
    formLoading.value = false
  }
}

// ---------- 删除 ----------
const handleDelete = async (row: SkillVO) => {
  await ElMessageBox.confirm(t('ai.skill.deleteConfirm'), t('common.confirmTitle'), {
    type: 'warning'
  })
  await deleteSkill(row.id)
  ElMessage.success(t('ai.skill.deleted'))
  detailVisible.value = false
  getList()
}
</script>
