<template>
  <Dialog v-model="dialogVisible" title="提交问题反馈" width="600">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="90px"
      v-loading="formLoading"
    >
      <el-form-item label="反馈内容" prop="content">
        <el-input
          v-model="formData.content"
          type="textarea"
          :rows="5"
          :maxlength="2048"
          show-word-limit
          placeholder="请描述遇到的问题（现象、复现步骤、期望结果）"
        />
      </el-form-item>
      <el-form-item label="截图" prop="screenshotUrls">
        <UploadFile
          v-model="formData.screenshotUrls"
          :file-type="['png', 'jpg', 'jpeg', 'gif', 'webp']"
          :file-size="10"
          :limit="5"
        />
      </el-form-item>
      <el-form-item label="会话标识" prop="sessionId">
        <el-input
          v-model="formData.sessionId"
          :maxlength="64"
          placeholder="选填，关联的调试会话标识"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button
        type="primary"
        :disabled="formLoading"
        v-hasPermi="['ai:feedback:create']"
        @click="submitForm"
      >
        确 定
      </el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as FeedbackApi from '@/api/ai/feedback'

defineOptions({ name: 'AiFeedbackForm' })

const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const formLoading = ref(false) // 表单的加载中
const formRef = ref() // 表单 Ref
const formData = ref({
  content: '',
  screenshotUrls: [] as string[],
  sessionId: ''
})
const formRules = reactive({
  content: [{ required: true, message: '反馈内容不能为空', trigger: 'blur' }]
})

/** 打开弹窗（可携带会话标识，供调试台等入口预填） */
const open = async (sessionId?: string) => {
  dialogVisible.value = true
  formData.value = {
    content: '',
    screenshotUrls: [],
    sessionId: sessionId ?? ''
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
    await FeedbackApi.createFeedback({
      content: formData.value.content,
      screenshotUrls: formData.value.screenshotUrls,
      sessionId: formData.value.sessionId || undefined
    })
    message.success('提交成功')
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
