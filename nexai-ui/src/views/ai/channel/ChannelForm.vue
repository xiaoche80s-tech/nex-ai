<template>
  <Dialog
    v-model="dialogVisible"
    :title="
      formType === 'create'
        ? t('ai.channel.channelFormTitleCreate')
        : t('ai.channel.channelFormTitleUpdate')
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
      <el-form-item :label="t('ai.channel.formName')" prop="name">
        <el-input
          v-model="formData.name"
          :maxlength="64"
          :placeholder="t('ai.channel.formNamePlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('ai.channel.formProvider')" prop="provider">
        <el-select
          v-model="formData.provider"
          :placeholder="t('ai.channel.formProviderPlaceholder')"
          class="!w-full"
        >
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.AI_CHANNEL_PROVIDER)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('ai.channel.formBaseUrl')" prop="baseUrl">
        <el-input
          v-model="formData.baseUrl"
          :maxlength="512"
          :placeholder="t('ai.channel.formBaseUrlPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('ai.channel.formApiKey')" prop="apiKey">
        <el-input
          v-model="formData.apiKey"
          type="password"
          show-password
          :maxlength="1024"
          :placeholder="t('ai.channel.formApiKeyPlaceholder')"
        />
        <div
          v-if="formType === 'update' && apiKeyConfigured"
          class="text-12px text-gray-400 leading-20px mt-2px"
        >
          {{ t('ai.channel.formApiKeyKeepTip') }}
        </div>
      </el-form-item>

      <el-divider content-position="left">{{ t('ai.channel.testConnectivity') }}</el-divider>
      <el-form-item :label="t('ai.channel.testModelId')" prop="probeModelId">
        <div class="flex w-full gap-8px">
          <el-input
            v-model="probeModelId"
            :maxlength="128"
            :placeholder="t('ai.channel.testModelIdPlaceholder')"
            class="flex-1"
          />
          <el-button :loading="probing" @click="handleProbe">
            {{ t('ai.channel.testConnectivity') }}
          </el-button>
        </div>
        <el-alert
          v-if="probeResult"
          :type="probeResult.success ? 'success' : 'error'"
          :closable="false"
          show-icon
          class="mt-8px w-full"
        >
          <template #title>
            {{ probeResult.success ? t('ai.channel.testSuccess') : t('ai.channel.testFailed') }}
            （{{ t('ai.channel.testDuration') }} {{ probeResult.durationMs }}ms）
          </template>
          {{ probeResult.message }}
        </el-alert>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button
        type="primary"
        :disabled="formLoading"
        v-hasPermi="['ai:channel:create', 'ai:channel:update']"
        @click="submitForm"
      >
        {{ t('common.ok') }}
      </el-button>
      <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import * as ChannelApi from '@/api/ai/channel'

defineOptions({ name: 'AiChannelForm' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化

const formType = ref<'create' | 'update'>('create') // 表单类型
const apiKeyConfigured = ref(false) // 编辑对象是否已配置密钥（提示留空保留）
const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const formData = ref<ChannelApi.ChannelSaveForm>({
  id: undefined,
  name: '',
  provider: '',
  baseUrl: '',
  apiKey: ''
})
const formRules = reactive({
  name: [{ required: true, message: t('ai.channel.nameRequired'), trigger: 'blur' }],
  provider: [{ required: true, message: t('ai.channel.providerRequired'), trigger: 'change' }],
  baseUrl: [{ required: true, message: t('ai.channel.baseUrlRequired'), trigger: 'blur' }]
})

/** 打开弹窗：无 id 创建，有 id 编辑回显（密钥不回传明文） */
const open = async (id?: number) => {
  dialogVisible.value = true
  formLoading.value = true
  probeResult.value = null
  probeModelId.value = ''
  try {
    formType.value = id ? 'update' : 'create'
    formRef.value?.resetFields()
    if (id) {
      const channel = await ChannelApi.getChannel(id)
      formData.value = {
        id: channel.id,
        name: channel.name,
        provider: channel.provider,
        baseUrl: channel.baseUrl,
        apiKey: ''
      }
      apiKeyConfigured.value = channel.apiKeyConfigured
    } else {
      formData.value = { id: undefined, name: '', provider: '', baseUrl: '', apiKey: '' }
      apiKeyConfigured.value = false
    }
  } finally {
    formLoading.value = false
  }
}
defineExpose({ open })

// ==================== 连通性探测（表单即测，不落库） ====================
const probeModelId = ref('')
const probing = ref(false)
const probeResult = ref<ChannelApi.ConnectivityTestResult | null>(null)

const handleProbe = async () => {
  if (!probeModelId.value) {
    message.error(t('ai.channel.testModelIdRequired'))
    return
  }
  probing.value = true
  probeResult.value = null
  try {
    probeResult.value = await ChannelApi.testChannelConnectivity({
      channelId: formData.value.id,
      provider: formData.value.provider,
      baseUrl: formData.value.baseUrl,
      apiKey: formData.value.apiKey,
      modelId: probeModelId.value
    })
  } finally {
    probing.value = false
  }
}

// ==================== 提交 ====================
const emit = defineEmits(['success'])
const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await ChannelApi.createChannel(formData.value)
      message.success(t('ai.channel.created'))
    } else {
      await ChannelApi.updateChannel(formData.value)
      message.success(t('ai.channel.updated'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
