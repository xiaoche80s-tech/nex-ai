<template>
  <ContentWrap>
    <!-- 搜索工作栏 -->
    <el-form class="-mb-15px" :model="queryParams" :inline="true" label-width="80px">
      <el-form-item :label="t('ai.skill.searchName')" prop="name">
        <el-input
          v-model="queryParams.name"
          :placeholder="t('common.inputText')"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery">
          <Icon icon="ep:search" class="mr-5px" /> {{ t('common.query') }}
        </el-button>
        <el-button @click="resetQuery">
          <Icon icon="ep:refresh" class="mr-5px" /> {{ t('common.reset') }}
        </el-button>
        <el-button type="primary" plain @click="openForm()" v-hasPermi="['ai:skill:create']">
          <Icon icon="ep:plus" class="mr-5px" /> {{ t('ai.skill.create') }}
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column :label="t('ai.skill.tableId')" align="center" prop="id" width="80" />
      <el-table-column
        :label="t('ai.skill.tableName')"
        align="center"
        prop="name"
        min-width="150"
        show-overflow-tooltip
      />
      <el-table-column
        :label="t('ai.skill.tableDescription')"
        align="center"
        prop="description"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column :label="t('ai.skill.tableOwner')" align="center" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.ownerLevel === 'USER'" type="warning" disable-transitions>
            {{ t('ai.skill.ownerUser') }}
          </el-tag>
          <el-tag v-else disable-transitions>{{ t('ai.skill.ownerTenant') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('ai.skill.tableVersion')" align="center" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.currentVersionNo" type="success" disable-transitions>
            v{{ row.currentVersionNo }}
          </el-tag>
          <el-tag v-else type="warning" disable-transitions>{{ t('ai.skill.noVersion') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.skill.tableCreateTime')"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column :label="t('table.action')" align="center" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-hasPermi="['ai:skill:query']" @click="openVersions(row)">
            {{ t('ai.skill.versions') }}
          </el-button>
          <el-button
            link
            type="primary"
            v-hasPermi="['ai:skill:update']"
            @click="openVersionForm(row)"
          >
            {{ t('ai.skill.newVersion') }}
          </el-button>
          <el-button
            link
            type="danger"
            v-hasPermi="['ai:skill:delete']"
            @click="handleDelete(row.id)"
          >
            {{ t('table.del') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 创建 Skill 表单 -->
  <el-dialog v-model="formVisible" :title="t('ai.skill.formTitleCreate')" width="720px">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
      <el-form-item :label="t('ai.skill.formName')" prop="name">
        <el-input v-model="formData.name" :placeholder="t('ai.skill.formNamePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('ai.skill.formDescription')" prop="description">
        <el-input v-model="formData.description" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item :label="t('ai.skill.formOwnerLevel')" prop="ownerLevel">
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
        {{ t('common.confirm') }}
      </el-button>
      <el-button @click="formVisible = false">{{ t('common.cancel') }}</el-button>
    </template>
  </el-dialog>

  <!-- 版本列表 -->
  <el-dialog v-model="versionsVisible" :title="t('ai.skill.versionsDialog')" width="560px">
    <el-table :data="versionList" v-loading="versionsLoading">
      <el-table-column
        :label="t('ai.skill.versionNo')"
        align="center"
        prop="versionNo"
        width="100"
      />
      <el-table-column
        :label="t('ai.skill.versionNote')"
        align="center"
        prop="note"
        min-width="150"
        show-overflow-tooltip
      />
      <el-table-column :label="t('ai.skill.versionCurrent')" align="center" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.current" type="success" disable-transitions>{{
            t('ai.skill.current')
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.skill.versionTime')"
        align="center"
        prop="createTime"
        width="170"
        :formatter="dateFormatter"
      />
    </el-table>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dateFormatter } from '@/utils/formatTime'
import {
  createSkill,
  addSkillVersion,
  getSkillPage,
  getSkillVersionPage,
  deleteSkill,
  type SkillVO,
  type SkillVersionVO
} from '@/api/ai/skill'

defineOptions({ name: 'AiSkill' })

const { t } = useI18n()

// ---------- 列表（手写加载，与 spec 页同款） ----------
const loading = ref(true)
const list = ref<SkillVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined as string | undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getSkillPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryParams.name = undefined
  queryParams.pageNo = 1
  getList()
}

getList()

// ---------- 创建表单 ----------
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

const openForm = () => {
  currentSkill.value = undefined
  formData.name = ''
  formData.description = ''
  formData.ownerLevel = 'TENANT'
  formData.markdown = ''
  formData.note = ''
  formVisible.value = true
}

const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    if (currentSkill.value) {
      // 登记新版本
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

// ---------- 版本 ----------
const versionsVisible = ref(false)
const versionsLoading = ref(false)
const versionList = ref<SkillVersionVO[]>([])

const openVersions = async (row: SkillVO) => {
  currentSkill.value = row
  versionsVisible.value = true
  versionsLoading.value = true
  try {
    versionList.value = await getSkillVersionPage(row.id)
  } finally {
    versionsLoading.value = false
  }
}

// 登记新版本（复用创建表单，提交到 version 接口）
const openVersionForm = (row: SkillVO) => {
  currentSkill.value = row
  formData.name = row.name
  formData.description = row.description
  formData.ownerLevel = row.ownerLevel
  formData.markdown = ''
  formData.note = ''
  formVisible.value = true
}

// 删除 Skill（级联删除版本链）
const handleDelete = async (id: number) => {
  await ElMessageBox.confirm(t('ai.skill.deleteConfirm'), t('common.confirmTitle'), {
    type: 'warning'
  })
  await deleteSkill(id)
  ElMessage.success(t('ai.skill.deleted'))
  getList()
}
</script>
