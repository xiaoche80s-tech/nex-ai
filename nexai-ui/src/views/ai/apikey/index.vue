<template>
  <ContentWrap>
    <!-- 搜索栏 -->
    <el-form :inline="true" :model="queryParams" class="-mb-15px">
      <el-form-item :label="t('ai.apiKey.name')">
        <el-input
          v-model="queryParams.name"
          :placeholder="t('common.inputText', { field: t('ai.apiKey.name') })"
          clearable
          class="!w-240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"
          ><Icon icon="ep:search" class="mr-5px" /> {{ t('common.query') }}</el-button
        >
        <el-button @click="resetQuery">
          <Icon icon="ep:refresh" class="mr-5px" /> {{ t('common.reset') }}
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <!-- 列表 -->
    <el-button
      plain
      type="primary"
      class="mb-10px"
      v-hasPermi="['ai:api-key:create']"
      @click="openForm"
    >
      <Icon icon="ep:plus" class="mr-5px" /> {{ t('ai.apiKey.create') }}
    </el-button>
    <el-table v-loading="loading" :data="list">
      <el-table-column :label="t('ai.apiKey.name')" prop="name" min-width="140" />
      <el-table-column :label="t('ai.apiKey.keyPrefix')" prop="keyPrefix" width="160">
        <template #default="{ row }">
          <span class="font-mono">{{ row.keyPrefix }}…</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('ai.apiKey.status')" width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'danger'">
            {{ row.status === 'ENABLED' ? t('ai.apiKey.enabled') : t('ai.apiKey.revoked') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('ai.apiKey.specScope')" min-width="180">
        <template #default="{ row }">
          <template v-if="row.specCodes && row.specCodes.length">
            <el-tag v-for="code in row.specCodes" :key="code" size="small" class="mr-4px">
              {{ code }}
            </el-tag>
          </template>
          <span v-else class="text-gray-400">{{ t('ai.apiKey.allSpecs') }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('common.createTime')"
        prop="createTime"
        :formatter="dateFormatter"
        width="180"
      />
      <el-table-column :label="t('table.action')" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'ENABLED'"
            link
            type="danger"
            v-hasPermi="['ai:api-key:revoke']"
            @click="handleRevoke(row)"
          >
            {{ t('ai.apiKey.revoke') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 创建弹窗 -->
  <Dialog v-model="dialogVisible" :title="t('ai.apiKey.create')" width="520">
    <!-- 生成结果：明文仅此一次展示 -->
    <div v-if="createdKey">
      <el-alert
        :title="t('ai.apiKey.onceWarning')"
        type="warning"
        :closable="false"
        class="mb-12px"
      />
      <el-input :model-value="createdKey" readonly class="mb-12px">
        <template #append>
          <el-button @click="copyKey">{{ t('ai.apiKey.copy') }}</el-button>
        </template>
      </el-input>
    </div>
    <el-form v-else ref="formRef" :model="formData" :rules="formRules" label-width="100px">
      <el-form-item :label="t('ai.apiKey.name')" prop="name">
        <el-input
          v-model="formData.name"
          :maxlength="64"
          :placeholder="t('ai.apiKey.namePlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('ai.apiKey.specScope')">
        <el-select
          v-model="formData.specCodes"
          multiple
          filterable
          allow-create
          default-first-option
          :loading="specsLoading"
          :placeholder="t('ai.apiKey.specScopePlaceholder')"
          class="!w-full"
        >
          <el-option
            v-for="spec in specOptions"
            :key="spec.specCode"
            :label="spec.name"
            :value="spec.specCode"
          />
        </el-select>
        <div class="text-12px text-gray-400 leading-20px mt-2px">
          {{ t('ai.apiKey.specScopeTip') }}
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <template v-if="createdKey">
        <el-button type="primary" @click="dialogVisible = false">{{ t('common.ok') }}</el-button>
      </template>
      <template v-else>
        <el-button type="primary" :disabled="formLoading" @click="submitForm">
          {{ t('common.ok') }}
        </el-button>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
      </template>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as ApiKeyApi from '@/api/ai/apikey'
import * as SpecApi from '@/api/ai/spec'
import { dateFormatter } from '@/utils/formatTime'

defineOptions({ name: 'AiApiKey' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化

const loading = ref(false)
const list = ref<ApiKeyApi.TenantApiKeyVO[]>([])
const total = ref(0)
const queryParams = reactive<ApiKeyApi.ApiKeyPageParams>({
  pageNo: 1,
  pageSize: 10,
  name: undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await ApiKeyApi.getApiKeyPage(queryParams)
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
  handleQuery()
}

// ---------- 创建 ----------
const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const formData = ref<ApiKeyApi.ApiKeyCreateForm>({ name: '', specCodes: [] })
const createdKey = ref('') // 生成后的明文（仅此一次展示）
const specsLoading = ref(false)
const specOptions = ref<SpecApi.AgentSpecVO[]>([])

const formRules = reactive({
  name: [{ required: true, message: t('ai.apiKey.nameRequired'), trigger: 'blur' }]
})

const openForm = async () => {
  createdKey.value = ''
  formData.value = { name: '', specCodes: [] }
  dialogVisible.value = true
  formRef.value?.resetFields()
  // 规格候选（放行范围选择）
  specsLoading.value = true
  try {
    const page = await SpecApi.getSpecPage({ pageNo: 1, pageSize: 100 })
    specOptions.value = page.list
  } catch {
    specOptions.value = []
  } finally {
    specsLoading.value = false
  }
}

const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    const data = await ApiKeyApi.createApiKey(formData.value)
    createdKey.value = data.apiKey
    message.success(t('ai.apiKey.created'))
    getList()
  } finally {
    formLoading.value = false
  }
}

const copyKey = async () => {
  await navigator.clipboard.writeText(createdKey.value)
  message.success(t('ai.apiKey.copied'))
}

const handleRevoke = async (row: ApiKeyApi.TenantApiKeyVO) => {
  await message.delConfirm(t('ai.apiKey.revokeConfirm', { name: row.name }))
  await ApiKeyApi.revokeApiKey(row.id)
  message.success(t('common.success'))
  getList()
}

onMounted(() => {
  getList()
})
</script>
