<template>
  <ContentWrap>
    <div class="text-16px font-bold mb-12px">{{ t('ai.channel.tabChannel') }}</div>
    <div>
      <el-form
        class="-mb-15px"
        :model="channelQuery"
        ref="channelQueryFormRef"
        :inline="true"
        label-width="80px"
      >
        <el-form-item :label="t('ai.channel.searchName')" prop="name">
          <el-input
            v-model="channelQuery.name"
            :placeholder="t('common.inputText')"
            clearable
            @keyup.enter="handleChannelQuery"
            class="!w-240px"
          />
        </el-form-item>
        <el-form-item :label="t('ai.channel.searchProvider')" prop="provider">
          <el-select
            v-model="channelQuery.provider"
            :placeholder="t('common.selectText')"
            clearable
            class="!w-240px"
          >
            <el-option
              v-for="dict in getStrDictOptions(DICT_TYPE.AI_CHANNEL_PROVIDER)"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('ai.channel.searchEnabled')" prop="enabled">
          <el-select
            v-model="channelQuery.enabled"
            :placeholder="t('common.selectText')"
            clearable
            class="!w-240px"
          >
            <el-option :label="t('ai.channel.enabled')" :value="true" />
            <el-option :label="t('ai.channel.disabled')" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="handleChannelQuery">
            <Icon icon="ep:search" class="mr-5px" /> {{ t('common.query') }}
          </el-button>
          <el-button @click="resetChannelQuery">
            <Icon icon="ep:refresh" class="mr-5px" /> {{ t('common.reset') }}
          </el-button>
          <el-button
            type="primary"
            plain
            @click="openChannelForm()"
            v-hasPermi="['ai:channel:create']"
          >
            <Icon icon="ep:plus" class="mr-5px" /> {{ t('ai.channel.createChannel') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table v-loading="channelLoading" :data="channelList">
      <el-table-column :label="t('ai.channel.tableId')" align="center" prop="id" width="80" />
      <el-table-column
        :label="t('ai.channel.tableName')"
        align="center"
        prop="name"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column :label="t('ai.channel.tableProvider')" align="center" width="130">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.AI_CHANNEL_PROVIDER" :value="scope.row.provider" />
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.channel.tableBaseUrl')"
        align="center"
        prop="baseUrl"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column :label="t('ai.channel.tableApiKey')" align="center" width="150">
        <template #default="scope">
          <span>{{ scope.row.apiKeyMasked || t('ai.channel.notConfigured') }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('ai.channel.tableEnabled')" align="center" width="90">
        <template #default="scope">
          <el-switch
            v-model="scope.row.enabled"
            @change="handleChannelStatusChange(scope.row)"
            :loading="scope.row.statusLoading"
          />
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.channel.tableCreateTime')"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column :label="t('table.action')" align="center" width="140" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openChannelForm(scope.row.id)"
            v-hasPermi="['ai:channel:update']"
          >
            {{ t('action.edit') }}
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleChannelDelete(scope.row.id)"
            v-hasPermi="['ai:channel:delete']"
          >
            {{ t('action.del') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="channelTotal"
      v-model:page="channelQuery.pageNo"
      v-model:limit="channelQuery.pageSize"
      @pagination="getChannelList"
    />
  </ContentWrap>

  <ContentWrap>
    <div class="text-16px font-bold mb-12px">{{ t('ai.channel.tabModel') }}</div>
    <div>
      <el-form
        class="-mb-15px"
        :model="modelQuery"
        ref="modelQueryFormRef"
        :inline="true"
        label-width="80px"
      >
        <el-form-item :label="t('ai.channel.modelSearchChannel')" prop="channelId">
          <el-select
            v-model="modelQuery.channelId"
            :placeholder="t('common.selectText')"
            clearable
            filterable
            class="!w-240px"
          >
            <el-option
              v-for="channel in channelOptions"
              :key="channel.id"
              :label="channel.name"
              :value="channel.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('ai.channel.modelSearchModelId')" prop="modelId">
          <el-input
            v-model="modelQuery.modelId"
            :placeholder="t('common.inputText')"
            clearable
            @keyup.enter="handleModelQuery"
            class="!w-240px"
          />
        </el-form-item>
        <el-form-item :label="t('ai.channel.modelSearchName')" prop="name">
          <el-input
            v-model="modelQuery.name"
            :placeholder="t('common.inputText')"
            clearable
            @keyup.enter="handleModelQuery"
            class="!w-240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button @click="handleModelQuery">
            <Icon icon="ep:search" class="mr-5px" /> {{ t('common.query') }}
          </el-button>
          <el-button @click="resetModelQuery">
            <Icon icon="ep:refresh" class="mr-5px" /> {{ t('common.reset') }}
          </el-button>
          <el-button type="primary" plain @click="openModelForm()" v-hasPermi="['ai:model:create']">
            <Icon icon="ep:plus" class="mr-5px" /> {{ t('ai.channel.createModel') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table v-loading="modelLoading" :data="modelList">
      <el-table-column :label="t('ai.channel.modelTableId')" align="center" prop="id" width="80" />
      <el-table-column
        :label="t('ai.channel.modelTableChannel')"
        align="center"
        prop="channelName"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column
        :label="t('ai.channel.modelTableModelId')"
        align="center"
        prop="modelId"
        min-width="150"
        show-overflow-tooltip
      />
      <el-table-column
        :label="t('ai.channel.modelTableName')"
        align="center"
        prop="name"
        min-width="130"
        show-overflow-tooltip
      />
      <el-table-column :label="t('ai.channel.modelTableContext')" align="center" width="120">
        <template #default="scope">
          <span>{{
            scope.row.contextWindow ? scope.row.contextWindow.toLocaleString() : '—'
          }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('ai.channel.modelTablePrice')" align="center" width="160">
        <template #default="scope">
          <span>{{ formatPrice(scope.row.inputPrice, scope.row.outputPrice) }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('ai.channel.modelTableEnabled')" align="center" width="90">
        <template #default="scope">
          <el-switch v-model="scope.row.enabled" @change="handleModelStatusChange(scope.row)" />
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.channel.modelTableCreateTime')"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column :label="t('table.action')" align="center" width="140" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openModelForm(scope.row.id)"
            v-hasPermi="['ai:model:update']"
          >
            {{ t('action.edit') }}
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleModelDelete(scope.row.id)"
            v-hasPermi="['ai:model:delete']"
          >
            {{ t('action.del') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="modelTotal"
      v-model:page="modelQuery.pageNo"
      v-model:limit="modelQuery.pageSize"
      @pagination="getModelList"
    />
  </ContentWrap>

  <ChannelForm ref="channelFormRef" @success="reloadAll" />
  <ModelForm ref="modelFormRef" @success="getModelList" />
</template>
<script lang="ts" setup>
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import * as ChannelApi from '@/api/ai/channel'
import ChannelForm from './ChannelForm.vue'
import ModelForm from './ModelForm.vue'

defineOptions({ name: 'AiChannel' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化

// ==================== 渠道 ====================
const channelLoading = ref(false)
const channelList = ref<ChannelApi.ChannelVO[]>([])
const channelTotal = ref(0)
const channelQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined,
  provider: undefined,
  enabled: undefined
})
const channelQueryFormRef = ref()

const getChannelList = async () => {
  channelLoading.value = true
  try {
    const data = await ChannelApi.getChannelPage(channelQuery)
    channelList.value = data.list
    channelTotal.value = data.total
  } finally {
    channelLoading.value = false
  }
}

/** 启停渠道（失败回滚开关状态） */
const handleChannelStatusChange = async (row: ChannelApi.ChannelVO) => {
  const enabled = row.enabled
  try {
    await ChannelApi.updateChannelStatus({ id: row.id, enabled })
    message.success(enabled ? t('ai.channel.enableSuccess') : t('ai.channel.disableSuccess'))
  } catch {
    row.enabled = !enabled
  }
}

const handleChannelDelete = async (id: number) => {
  await message.delConfirm(t('ai.channel.deleteChannelConfirm'))
  await ChannelApi.deleteChannel(id)
  message.success(t('ai.channel.deleted'))
  await getChannelList()
}

const channelFormRef = ref()
const openChannelForm = (id?: number) => {
  channelFormRef.value.open(id)
}

const handleChannelQuery = () => {
  channelQuery.pageNo = 1
  getChannelList()
}
const resetChannelQuery = () => {
  channelQueryFormRef.value.resetFields()
  handleChannelQuery()
}

// ==================== 模型 ====================
const modelLoading = ref(false)
const modelList = ref<ChannelApi.ModelVO[]>([])
const modelTotal = ref(0)
const modelQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  channelId: undefined,
  modelId: undefined,
  name: undefined,
  enabled: undefined
})
const modelQueryFormRef = ref()
/** 模型表单与筛选下拉的渠道选项（含停用渠道，便于筛选已停用渠道下的模型） */
const channelOptions = ref<ChannelApi.ChannelVO[]>([])

const getChannelOptions = async () => {
  channelOptions.value = await ChannelApi.getEnabledChannelList()
}

const getModelList = async () => {
  modelLoading.value = true
  try {
    const data = await ChannelApi.getModelPage(modelQuery)
    modelList.value = data.list
    modelTotal.value = data.total
  } finally {
    modelLoading.value = false
  }
}

const handleModelStatusChange = async (row: ChannelApi.ModelVO) => {
  const enabled = row.enabled
  try {
    await ChannelApi.updateModelStatus({ id: row.id, enabled })
    message.success(enabled ? t('ai.channel.enableSuccess') : t('ai.channel.disableSuccess'))
  } catch {
    row.enabled = !enabled
  }
}

const handleModelDelete = async (id: number) => {
  await message.delConfirm()
  await ChannelApi.deleteModel(id)
  message.success(t('ai.channel.deleted'))
  await getModelList()
}

const modelFormRef = ref()
const openModelForm = (id?: number) => {
  modelFormRef.value.open(id)
}

const handleModelQuery = () => {
  modelQuery.pageNo = 1
  getModelList()
}
const resetModelQuery = () => {
  modelQueryFormRef.value.resetFields()
  handleModelQuery()
}

const reloadAll = () => {
  getChannelList()
  getChannelOptions()
}

/** 单价列展示：输入 / 输出，缺一显示单边，均无为 — */
const formatPrice = (input: number | null, output: number | null): string => {
  const inputText = input != null ? String(input) : ''
  const outputText = output != null ? String(output) : ''
  if (inputText && outputText) {
    return `${inputText} / ${outputText}`
  }
  return inputText || outputText || '—'
}

onMounted(() => {
  getChannelList()
  getModelList()
  getChannelOptions()
})
</script>
