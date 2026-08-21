<template>
  <Dialog
    v-model="dialogVisible"
    :title="formType === 'create' ? '登记模型' : '编辑模型'"
    width="560"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="100px"
      v-loading="formLoading"
    >
      <el-form-item label="所属渠道" prop="channelId">
        <el-select
          v-model="formData.channelId"
          placeholder="请选择所属渠道"
          class="!w-full"
          :disabled="channelOptions.length === 0"
        >
          <el-option
            v-for="channel in channelOptions"
            :key="channel.id"
            :label="`${channel.name}（${getProviderLabel(channel.provider)}）`"
            :value="channel.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="模型标识" prop="modelId">
        <el-input
          v-model="formData.modelId"
          :maxlength="128"
          placeholder="请输入调用时传给提供商的 ID，如：gpt-4o / qwen-plus"
        />
      </el-form-item>
      <el-form-item label="显示名" prop="name">
        <el-input v-model="formData.name" :maxlength="64" placeholder="请输入显示名" />
      </el-form-item>
      <el-form-item label="上下文窗口" prop="contextWindow">
        <el-input-number
          v-model="formData.contextWindow"
          :min="0"
          :step="1000"
          :precision="0"
          placeholder="tokens，未知不填"
          class="!w-full"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item label="输入单价" prop="inputPrice">
        <el-input-number
          v-model="formData.inputPrice"
          :min="0"
          :precision="6"
          :step="0.1"
          placeholder="元 / 百万 tokens"
          class="!w-full"
          controls-position="right"
        />
        <span class="ml-8px text-12px text-gray-400">元 / 百万 tokens</span>
      </el-form-item>
      <el-form-item label="输出单价" prop="outputPrice">
        <el-input-number
          v-model="formData.outputPrice"
          :min="0"
          :precision="6"
          :step="0.1"
          placeholder="元 / 百万 tokens"
          class="!w-full"
          controls-position="right"
        />
        <span class="ml-8px text-12px text-gray-400">元 / 百万 tokens</span>
      </el-form-item>
      <el-form-item label="能力标签" prop="capabilities">
        <el-select
          v-model="formData.capabilities"
          multiple
          filterable
          allow-create
          default-first-option
          :reserve-keyword="false"
          placeholder="选择或输入标签，如：chat / vision / tools"
          class="!w-full"
        >
          <el-option v-for="tag in presetCapabilities" :key="tag" :label="tag" :value="tag" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button
        type="primary"
        :disabled="formLoading"
        v-hasPermi="['ai:model:create', 'ai:model:update']"
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
import * as ModelApi from '@/api/ai/model'

defineOptions({ name: 'AiModelForm' })

const message = useMessage() // 消息弹窗

/** 常用能力标签预设（可自由输入其他标签） */
const presetCapabilities = ['chat', 'vision', 'tools', 'embedding', 'reasoning', 'json']

const formType = ref<'create' | 'update'>('create') // 表单类型
const dialogVisible = ref(false) // 弹窗的是否展示
const formLoading = ref(false) // 表单的加载中
const formRef = ref() // 表单 Ref
const formData = ref<ModelApi.ModelSaveForm>({
  id: undefined,
  channelId: undefined as unknown as number,
  modelId: '',
  name: '',
  contextWindow: undefined,
  inputPrice: undefined,
  outputPrice: undefined,
  capabilities: []
})
const channelOptions = ref<ModelApi.ChannelVO[]>([]) // 启用渠道下拉选项
const formRules = reactive({
  channelId: [{ required: true, message: '所属渠道不能为空', trigger: 'change' }],
  modelId: [{ required: true, message: '模型标识不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '模型显示名不能为空', trigger: 'blur' }]
})

/** 提供商编码 → 字典显示名 */
const getProviderLabel = (provider: string) => {
  return (
    getStrDictOptions(DICT_TYPE.AI_CHANNEL_PROVIDER).find((d) => d.value === provider)?.label ??
    provider
  )
}

/** 打开弹窗：传 id 为编辑，否则为创建 */
const open = async (id?: number) => {
  dialogVisible.value = true
  formLoading.value = true
  try {
    channelOptions.value = await ModelApi.getEnabledChannelList()
    if (id) {
      formType.value = 'update'
      const model = await ModelApi.getModel(id)
      formData.value = {
        id: model.id,
        channelId: model.channelId,
        modelId: model.modelId,
        name: model.name,
        contextWindow: model.contextWindow ?? undefined,
        inputPrice: model.inputPrice ?? undefined,
        outputPrice: model.outputPrice ?? undefined,
        capabilities: model.capabilities ?? []
      }
    } else {
      formType.value = 'create'
      formData.value = {
        id: undefined,
        channelId: undefined as unknown as number,
        modelId: '',
        name: '',
        contextWindow: undefined,
        inputPrice: undefined,
        outputPrice: undefined,
        capabilities: []
      }
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
    if (formType.value === 'create') {
      await ModelApi.createModel(formData.value)
      message.success('登记成功')
    } else {
      await ModelApi.updateModel(formData.value)
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
