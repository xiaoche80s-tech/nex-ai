<template>
  <Dialog
    v-model="dialogVisible"
    :title="formType === 'create' ? '创建智能体规格' : '编辑智能体规格'"
    width="640"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="110px"
      v-loading="formLoading"
    >
      <el-form-item label="规格名称" prop="name">
        <el-input v-model="formData.name" :maxlength="64" placeholder="请输入规格名称" />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input
          v-model="formData.description"
          :maxlength="512"
          type="textarea"
          :rows="2"
          placeholder="这个智能体做什么（可选）"
        />
      </el-form-item>
      <el-form-item label="图标" prop="icon">
        <el-input
          v-model="formData.icon"
          :maxlength="128"
          placeholder="图标标识，如 ep:service（可选）"
        />
      </el-form-item>
      <el-form-item label="引用模型" prop="modelId">
        <el-select
          v-model="formData.modelId"
          placeholder="请选择模型"
          class="!w-full"
          filterable
          :disabled="modelOptions.length === 0"
        >
          <el-option
            v-for="model in modelOptions"
            :key="model.id"
            :label="`${model.name}（${model.modelId}）`"
            :value="model.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="系统提示" prop="systemPrompt">
        <el-input
          v-model="formData.systemPrompt"
          :maxlength="16384"
          type="textarea"
          :rows="6"
          show-word-limit
          placeholder="告诉智能体它的角色、能力边界与回复风格"
        />
      </el-form-item>
      <el-form-item label="最大迭代轮数" prop="maxIters">
        <el-input-number
          v-model="formData.maxIters"
          :min="1"
          :precision="0"
          :step="1"
          placeholder="不填运行时取默认"
          class="!w-full"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item label="温度" prop="temperature">
        <el-input-number
          v-model="formData.temperature"
          :min="0"
          :max="2"
          :step="0.1"
          :precision="2"
          placeholder="0 ~ 2，不填运行时取默认"
          class="!w-full"
          controls-position="right"
        />
      </el-form-item>
      <el-alert
        v-if="formType === 'update' && hasPublished"
        type="info"
        :closable="false"
        show-icon
        class="mb-10px"
        title="已发布的版本不可修改；本次保存将写入草稿，确认后再发布为新版本。"
      />
    </el-form>
    <template #footer>
      <el-button
        type="primary"
        :disabled="formLoading"
        v-hasPermi="['ai:spec:create', 'ai:spec:update']"
        @click="submitForm"
      >
        保 存
      </el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as SpecApi from '@/api/ai/spec'
import * as ModelApi from '@/api/ai/model'

defineOptions({ name: 'AiAgentSpecForm' })

const message = useMessage() // 消息弹窗

const formType = ref<'create' | 'update'>('create') // 表单类型
const hasPublished = ref(false) // 编辑对象是否已发布过（提示草稿/版本语义）
const dialogVisible = ref(false) // 弹窗的是否展示
const formLoading = ref(false) // 表单的加载中
const formRef = ref() // 表单 Ref
const formData = ref<SpecApi.AgentSpecSaveForm & { systemPrompt?: string }>({
  id: undefined,
  name: '',
  description: '',
  icon: '',
  modelId: undefined,
  systemPrompt: '',
  maxIters: undefined,
  temperature: undefined
})
const modelOptions = ref<ModelApi.ModelVO[]>([]) // 启用模型下拉选项
const formRules = reactive({
  name: [{ required: true, message: '规格名称不能为空', trigger: 'blur' }],
  modelId: [{ required: true, message: '规格必须引用一个模型', trigger: 'change' }]
})

/** 打开弹窗：传 id 为编辑（优先草稿、无草稿预填当前默认版本），否则为创建 */
const open = async (id?: number) => {
  dialogVisible.value = true
  formLoading.value = true
  try {
    modelOptions.value = await ModelApi.getEnabledModelList()
    if (id) {
      formType.value = 'update'
      const detail = await SpecApi.getSpec(id)
      hasPublished.value = detail.latestVersionNo > 0
      // 预填优先级：草稿 > 当前默认版本快照 > 空表单
      const config = detail.draft ?? detail.currentVersion?.config
      formData.value = {
        id: detail.id,
        name: detail.name,
        description: detail.description ?? '',
        icon: detail.icon ?? '',
        modelId: config?.modelId,
        systemPrompt: config?.systemPrompt ?? '',
        maxIters: config?.maxIters ?? undefined,
        temperature: config?.temperature ?? undefined
      }
    } else {
      formType.value = 'create'
      hasPublished.value = false
      formData.value = {
        id: undefined,
        name: '',
        description: '',
        icon: '',
        modelId: undefined,
        systemPrompt: '',
        maxIters: undefined,
        temperature: undefined
      }
    }
  } finally {
    formLoading.value = false
  }
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单（写草稿） */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  // 校验表单
  await formRef.value.validate()
  // 提交请求
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await SpecApi.createSpec(formData.value)
      message.success('创建成功，当前为草稿，可随时发布')
    } else {
      await SpecApi.updateSpec(formData.value)
      message.success('已保存到草稿')
    }
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
