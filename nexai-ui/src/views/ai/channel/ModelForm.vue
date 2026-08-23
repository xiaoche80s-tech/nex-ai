<template>
  <Dialog
    v-model="dialogVisible"
    :title="
      formType === 'create'
        ? t('ai.channel.modelFormTitleCreate')
        : t('ai.channel.modelFormTitleUpdate')
    "
    width="560"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="100px"
      v-loading="formLoading"
    >
      <el-form-item :label="t('ai.channel.formChannel')" prop="channelId">
        <el-select
          v-model="formData.channelId"
          :placeholder="t('common.selectText')"
          class="!w-full"
          filterable
        >
          <el-option
            v-for="channel in channelOptions"
            :key="channel.id"
            :label="channel.name"
            :value="channel.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('ai.channel.formModelId')" prop="modelId">
        <el-input
          v-model="formData.modelId"
          :maxlength="128"
          :placeholder="t('ai.channel.formModelIdPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('ai.channel.formModelName')" prop="name">
        <el-input
          v-model="formData.name"
          :maxlength="64"
          :placeholder="t('ai.channel.formModelNamePlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('ai.channel.formContextWindow')" prop="contextWindow">
        <el-input-number
          v-model="formData.contextWindow"
          :min="1"
          :precision="0"
          :step="1000"
          :placeholder="t('ai.channel.formContextWindowPlaceholder')"
          class="!w-full"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item :label="t('ai.channel.formInputPrice')" prop="inputPrice">
        <el-input-number
          v-model="formData.inputPrice"
          :min="0"
          :precision="6"
          :step="0.1"
          :placeholder="t('ai.channel.formPricePlaceholder')"
          class="!w-full"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item :label="t('ai.channel.formOutputPrice')" prop="outputPrice">
        <el-input-number
          v-model="formData.outputPrice"
          :min="0"
          :precision="6"
          :step="0.1"
          :placeholder="t('ai.channel.formPricePlaceholder')"
          class="!w-full"
          controls-position="right"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button
        type="primary"
        :disabled="formLoading"
        v-hasPermi="['ai:model:create', 'ai:model:update']"
        @click="submitForm"
      >
        {{ t('common.ok') }}
      </el-button>
      <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as ChannelApi from '@/api/ai/channel'

defineOptions({ name: 'AiModelForm' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化

const formType = ref<'create' | 'update'>('create')
const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const channelOptions = ref<ChannelApi.ChannelVO[]>([])
const formData = ref<ChannelApi.ModelSaveForm>({
  id: undefined,
  channelId: undefined,
  modelId: '',
  name: '',
  contextWindow: undefined,
  inputPrice: undefined,
  outputPrice: undefined
})
const formRules = reactive({
  channelId: [{ required: true, message: t('ai.channel.formChannelRequired'), trigger: 'change' }],
  modelId: [{ required: true, message: t('ai.channel.formModelIdRequired'), trigger: 'blur' }],
  name: [{ required: true, message: t('ai.channel.formModelNameRequired'), trigger: 'blur' }]
})

/** 打开弹窗：无 id 登记，有 id 编辑回显 */
const open = async (id?: number) => {
  dialogVisible.value = true
  formLoading.value = true
  try {
    formType.value = id ? 'update' : 'create'
    formRef.value?.resetFields()
    channelOptions.value = await ChannelApi.getEnabledChannelList()
    if (id) {
      const model = await ChannelApi.getModel(id)
      formData.value = {
        id: model.id,
        channelId: model.channelId,
        modelId: model.modelId,
        name: model.name,
        contextWindow: model.contextWindow ?? undefined,
        inputPrice: model.inputPrice ?? undefined,
        outputPrice: model.outputPrice ?? undefined
      }
    } else {
      formData.value = {
        id: undefined,
        channelId: undefined,
        modelId: '',
        name: '',
        contextWindow: undefined,
        inputPrice: undefined,
        outputPrice: undefined
      }
    }
  } finally {
    formLoading.value = false
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await ChannelApi.createModel(formData.value)
      message.success(t('ai.channel.created'))
    } else {
      await ChannelApi.updateModel(formData.value)
      message.success(t('ai.channel.updated'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
