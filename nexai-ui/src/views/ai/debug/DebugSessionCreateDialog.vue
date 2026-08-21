<template>
  <Dialog v-model="dialogVisible" title="发起调试会话" width="520px">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
      <el-form-item label="智能体规格" prop="specId">
        <el-select
          v-model="formData.specId"
          placeholder="选择已发布的规格"
          filterable
          class="!w-full"
          @change="handleSpecChange"
        >
          <el-option
            v-for="spec in publishedSpecs"
            :key="spec.id"
            :label="`${spec.name}（默认 v${spec.currentVersionNo}）`"
            :value="spec.id!"
            :disabled="!spec.currentVersionNo"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="绑定版本" prop="versionNo">
        <el-select
          v-model="formData.versionNo"
          placeholder="选择规格版本"
          class="!w-full"
          :disabled="!formData.specId"
        >
          <el-option
            v-for="version in versions"
            :key="version.versionNo"
            :label="`v${version.versionNo}${version.remark ? ' — ' + version.remark : ''}`"
            :value="version.versionNo"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="会话标题" prop="title">
        <el-input
          v-model="formData.title"
          :placeholder="`缺省为「${selectedSpecName || '规格'} v${formData.versionNo ?? ''}」`"
          maxlength="128"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="sending" @click="dialogVisible = false">取 消</el-button>
      <el-button type="primary" :loading="sending" @click="submitForm">开始调试</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as SpecApi from '@/api/ai/spec'
import * as SessionApi from '@/api/ai/session'

defineOptions({ name: 'DebugSessionCreateDialog' })

const message = useMessage()

const dialogVisible = ref(false)
const sending = ref(false)
const formRef = ref()
const formData = ref({
  specId: undefined as number | undefined,
  versionNo: undefined as number | undefined,
  title: ''
})
const formRules = {
  specId: [{ required: true, message: '请选择规格', trigger: 'change' }],
  versionNo: [{ required: true, message: '请选择版本', trigger: 'change' }]
}

/** 已发布规格（无版本不可调试）与其版本列表 */
const publishedSpecs = ref<SpecApi.AgentSpecVO[]>([])
const versions = ref<SpecApi.AgentSpecVersionVO[]>([])

const selectedSpecName = computed(
  () => publishedSpecs.value.find((spec) => spec.id === formData.value.specId)?.name
)

const open = async () => {
  dialogVisible.value = true
  formData.value = { specId: undefined, versionNo: undefined, title: '' }
  versions.value = []
  const data = await SpecApi.getSpecPage({ pageNo: 1, pageSize: 100 })
  publishedSpecs.value = data.list.filter((spec) => spec.currentVersionNo)
}
defineExpose({ open })

/** 选定规格后加载版本列表并默认选中其当前默认版本 */
const handleSpecChange = async (specId: number) => {
  formData.value.versionNo = undefined
  versions.value = await SpecApi.getVersionList(specId)
  const spec = publishedSpecs.value.find((item) => item.id === specId)
  if (spec?.currentVersionNo) {
    formData.value.versionNo = spec.currentVersionNo
  }
}

/** 提交创建：标题缺省以「规格名 v版本」落库，会话列表无需再拼映射；成功后带会话编号通知父级 */
const emit = defineEmits<{ created: [sessionId: number] }>()
const submitForm = async () => {
  await formRef.value.validate()
  const specName = selectedSpecName.value ?? '规格'
  const versionNo = formData.value.versionNo!
  sending.value = true
  try {
    const sessionId = await SessionApi.createDebugSession({
      specId: formData.value.specId!,
      versionNo,
      title: formData.value.title?.trim() || `${specName} v${versionNo}`
    })
    message.success('调试会话已创建')
    dialogVisible.value = false
    emit('created', sessionId)
  } finally {
    sending.value = false
  }
}
</script>
