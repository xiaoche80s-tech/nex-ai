<template>
  <Dialog v-model="dialogVisible" title="克隆会话重跑" width="520px">
    <el-alert
      type="info"
      :closable="false"
      class="mb-12px"
      :title="`将复制「${source?.title || '会话 #' + source?.id}」的对话历史为新调试会话`"
      description="克隆会话绑定同一规格版本并携带完整上下文，可在其上微调推理参数对照重跑"
      show-icon
    />
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
      <el-form-item label="新会话标题" prop="title">
        <el-input v-model="formData.title" placeholder="缺省为「源标题（克隆）」" maxlength="128" />
      </el-form-item>
      <el-form-item label="maxIters" prop="maxIters">
        <el-input-number
          v-model="formData.maxIters"
          :min="1"
          :max="100"
          :placeholder="currentMaxItersLabel"
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
          :placeholder="currentTemperatureLabel"
          class="!w-full"
          controls-position="right"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="sending" @click="dialogVisible = false">取 消</el-button>
      <el-button type="primary" :loading="sending" @click="submitForm">克隆并打开</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as SessionApi from '@/api/ai/session'

defineOptions({ name: 'DebugSessionCloneDialog' })

const message = useMessage()

const dialogVisible = ref(false)
const sending = ref(false)
const formRef = ref()
const source = ref<SessionApi.SessionVO | null>(null)
const formData = ref({
  title: '',
  maxIters: undefined as number | undefined,
  temperature: undefined as number | undefined
})
/** 仅拦截非法值（范围由输入控件约束），不强制填写——留空沿用源会话当前值 */
const formRules = {
  title: [{ max: 128, message: '标题不能超过 128 个字符', trigger: 'blur' }]
}

/** 占位提示展示源会话当前生效值（覆盖优先于版本快照，克隆后沿用） */
const currentMaxItersLabel = computed(() =>
  source.value?.overrideMaxIters != null ? `当前 ${source.value.overrideMaxIters}` : '沿用版本快照'
)
const currentTemperatureLabel = computed(() =>
  source.value?.overrideTemperature != null
    ? `当前 ${source.value.overrideTemperature}`
    : '沿用版本快照'
)

const open = (session: SessionApi.SessionVO) => {
  source.value = session
  formData.value = { title: '', maxIters: undefined, temperature: undefined }
  dialogVisible.value = true
}
defineExpose({ open })

const emit = defineEmits<{ cloned: [sessionId: number] }>()
const submitForm = async () => {
  await formRef.value.validate()
  sending.value = true
  try {
    const sessionId = await SessionApi.cloneSession(source.value!.id, {
      title: formData.value.title?.trim() || undefined,
      maxIters: formData.value.maxIters ?? undefined,
      temperature: formData.value.temperature ?? undefined
    })
    message.success('已克隆为新调试会话')
    dialogVisible.value = false
    emit('cloned', sessionId)
  } finally {
    sending.value = false
  }
}
</script>
