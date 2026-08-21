<template>
  <Dialog
    v-model="dialogVisible"
    :title="formType === 'create' ? '创建渠道' : '编辑渠道'"
    width="560"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="90px"
      v-loading="formLoading"
    >
      <el-form-item label="渠道名称" prop="name">
        <el-input
          v-model="formData.name"
          :maxlength="64"
          placeholder="请输入渠道名称，如：公司采购的 OpenAI 主渠道"
        />
      </el-form-item>
      <el-form-item label="提供商" prop="provider">
        <el-select v-model="formData.provider" placeholder="请选择提供商类型" class="!w-full">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.AI_CHANNEL_PROVIDER)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="端点地址" prop="baseUrl">
        <el-input
          v-model="formData.baseUrl"
          :maxlength="512"
          placeholder="请输入 http(s) 端点地址，如：https://api.openai.com/v1"
        />
      </el-form-item>
      <el-form-item label="API 密钥" prop="apiKey">
        <el-input
          v-model="formData.apiKey"
          type="password"
          show-password
          autocomplete="new-password"
          :maxlength="512"
          :placeholder="
            formType === 'create'
              ? '选填，Ollama 等本地服务无需密钥；保存后以密文落库'
              : `当前：${originalApiKeyMasked ?? '未配置'}；留空表示不修改`
          "
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button
        type="primary"
        :disabled="formLoading"
        v-hasPermi="['ai:channel:create', 'ai:channel:update']"
        @click="submitForm"
      >
        确 定
      </el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import * as ChannelApi from '@/api/ai/model'

defineOptions({ name: 'AiChannelForm' })

const message = useMessage() // 消息弹窗

const formType = ref<'create' | 'update'>('create') // 表单类型
const dialogVisible = ref(false) // 弹窗的是否展示
const formLoading = ref(false) // 表单的加载中
const formRef = ref() // 表单 Ref
const formData = ref<ChannelApi.ChannelSaveForm>({
  id: undefined,
  name: '',
  provider: '',
  baseUrl: '',
  apiKey: ''
})
/** 编辑时回显的脱敏密钥，提示保留原值 */
const originalApiKeyMasked = ref<string | null>(null)
const formRules = reactive({
  name: [{ required: true, message: '渠道名称不能为空', trigger: 'blur' }],
  provider: [{ required: true, message: '提供商类型不能为空', trigger: 'change' }],
  baseUrl: [
    { required: true, message: '端点地址不能为空', trigger: 'blur' },
    { pattern: /^https?:\/\/\S+$/, message: '端点地址必须是 http(s):// 开头', trigger: 'blur' }
  ]
})

/** 打开弹窗：传 id 为编辑，否则为创建 */
const open = async (id?: number) => {
  dialogVisible.value = true
  formLoading.value = true
  try {
    if (id) {
      formType.value = 'update'
      const channel = await ChannelApi.getChannel(id)
      // 详情不回传明文密钥，仅提示；留空提交即保留
      originalApiKeyMasked.value = channel.apiKeyMasked ?? null
      formData.value = {
        id: channel.id,
        name: channel.name,
        provider: channel.provider,
        baseUrl: channel.baseUrl,
        apiKey: ''
      }
    } else {
      formType.value = 'create'
      originalApiKeyMasked.value = null
      formData.value = { id: undefined, name: '', provider: '', baseUrl: '', apiKey: '' }
    }
  } finally {
    formLoading.value = false
  }
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  // 校验表单
  await formRef.value.validate()
  // 提交请求
  formLoading.value = true
  try {
    const data = { ...formData.value, apiKey: formData.value.apiKey || undefined }
    if (formType.value === 'create') {
      await ChannelApi.createChannel(data)
      message.success('创建成功')
    } else {
      await ChannelApi.updateChannel(data)
      message.success('更新成功')
    }
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
