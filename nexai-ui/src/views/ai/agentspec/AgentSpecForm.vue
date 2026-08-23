<template>
  <Dialog v-model="dialogVisible" :title="t('ai.spec.formTitle')" width="680">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="110px"
      v-loading="formLoading"
    >
      <el-divider content-position="left">{{ t('ai.spec.sectionBasic') }}</el-divider>
      <el-form-item :label="t('ai.spec.formName')" prop="name">
        <el-input
          v-model="formData.name"
          :maxlength="64"
          :placeholder="t('ai.spec.formNamePlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('ai.spec.formSpecCode')" prop="specCode">
        <el-input
          v-model="formData.specCode"
          :maxlength="64"
          :placeholder="t('ai.spec.formSpecCodePlaceholder')"
        />
        <div class="text-12px text-gray-400 leading-20px mt-2px">{{
          t('ai.spec.formSpecCodeTip')
        }}</div>
      </el-form-item>
      <el-form-item :label="t('ai.spec.formOwnerLevel')" prop="ownerLevel">
        <el-radio-group v-model="formData.ownerLevel">
          <el-radio value="TENANT">{{ t('ai.spec.formOwnerTenant') }}</el-radio>
          <el-radio value="USER">{{ t('ai.spec.formOwnerUser') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="t('ai.spec.formDescription')" prop="description">
        <el-input
          v-model="formData.description"
          :maxlength="1024"
          type="textarea"
          :rows="2"
          show-word-limit
          :placeholder="t('ai.spec.formDescriptionPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('ai.spec.formIcon')" prop="icon">
        <el-input
          v-model="formData.icon"
          :maxlength="128"
          :placeholder="t('ai.spec.formIconPlaceholder')"
        />
      </el-form-item>

      <el-divider content-position="left">{{ t('ai.spec.sectionModel') }}</el-divider>
      <el-form-item :label="t('ai.spec.formSystemPrompt')" prop="systemPrompt">
        <el-input
          v-model="formData.systemPrompt"
          :maxlength="16384"
          type="textarea"
          :rows="6"
          show-word-limit
          :placeholder="t('ai.spec.formSystemPromptPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('ai.spec.formMaxIters')" prop="maxIters">
        <el-input-number
          v-model="formData.maxIters"
          :min="1"
          :precision="0"
          :step="1"
          :placeholder="t('ai.spec.formMaxItersPlaceholder')"
          class="!w-full"
          controls-position="right"
        />
      </el-form-item>

      <el-collapse class="border-none">
        <el-collapse-item name="generateOptions">
          <template #title>
            <span class="text-14px">{{ t('ai.spec.generateOptions') }}</span>
          </template>
          <el-form-item :label="t('ai.spec.formTemperature')" prop="temperature">
            <el-input-number
              v-model="formData.temperature"
              :min="0"
              :max="2"
              :step="0.1"
              :precision="2"
              :placeholder="t('ai.spec.formTemperaturePlaceholder')"
              class="!w-full"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item :label="t('ai.spec.formTopP')" prop="topP">
            <el-input-number
              v-model="formData.topP"
              :min="0"
              :max="1"
              :step="0.05"
              :precision="2"
              :placeholder="t('ai.spec.formTopPPlaceholder')"
              class="!w-full"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item :label="t('ai.spec.formMaxTokens')" prop="maxTokens">
            <el-input-number
              v-model="formData.maxTokens"
              :min="1"
              :step="256"
              :precision="0"
              :placeholder="t('ai.spec.formMaxTokensPlaceholder')"
              class="!w-full"
              controls-position="right"
            />
          </el-form-item>
        </el-collapse-item>
      </el-collapse>

      <el-divider content-position="left">{{ t('ai.spec.sectionEnv') }}</el-divider>
      <el-form-item :label="t('ai.spec.formWorkspace')">
        <el-switch v-model="formData.workspaceEnabled" @change="onWorkspaceToggle" />
        <div class="text-12px text-gray-400 leading-20px mt-2px">
          {{ t('ai.spec.formWorkspaceTip') }}
        </div>
      </el-form-item>
      <template v-if="formData.workspaceEnabled">
        <el-form-item :label="t('ai.spec.formSandbox')">
          <el-switch v-model="formData.sandboxEnabled" @change="onSandboxToggle" />
          <div class="text-12px text-gray-400 leading-20px mt-2px">
            {{ t('ai.spec.formSandboxTip') }}
          </div>
        </el-form-item>
        <el-form-item v-if="formData.sandboxEnabled" :label="t('ai.spec.formCapabilities')">
          <el-checkbox-group v-model="formData.capabilities">
            <el-checkbox value="SHELL">{{ t('ai.spec.capabilityShell') }}</el-checkbox>
            <el-checkbox value="PYTHON">{{ t('ai.spec.capabilityPython') }}</el-checkbox>
            <el-checkbox value="NODE">{{ t('ai.spec.capabilityNode') }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button
        type="primary"
        :disabled="formLoading"
        v-hasPermi="['ai:spec:create']"
        @click="submitForm"
      >
        {{ t('common.ok') }}
      </el-button>
      <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as SpecApi from '@/api/ai/spec'

defineOptions({ name: 'AiAgentSpecForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const formLoading = ref(false) // 表单的加载中
const formRef = ref() // 表单 Ref
const formData = ref<SpecApi.AgentSpecCreateForm>({
  name: '',
  specCode: '',
  ownerLevel: 'TENANT',
  description: '',
  icon: '',
  systemPrompt: '',
  maxIters: undefined,
  temperature: undefined,
  topP: undefined,
  maxTokens: undefined,
  workspaceEnabled: false,
  sandboxEnabled: false,
  capabilities: []
})
const formRules = reactive({
  name: [{ required: true, message: t('ai.spec.nameRequired'), trigger: 'blur' }],
  specCode: [
    { required: true, message: t('ai.spec.specCodeRequired'), trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9-]{1,63}$/, message: t('ai.spec.specCodePattern'), trigger: 'blur' }
  ],
  description: [{ max: 1024, message: t('ai.spec.descriptionMax'), trigger: 'blur' }]
})

/** 打开弹窗（仅创建） */
const open = () => {
  dialogVisible.value = true
  formRef.value?.resetFields()
}
defineExpose({ open })

/** 执行环境联动：关工作区 → 沙箱与能力一并清空；关沙箱 → 能力清空（非沙箱禁执行能力） */
const onWorkspaceToggle = (enabled: boolean | string | number) => {
  if (!enabled) {
    formData.value.sandboxEnabled = false
    formData.value.capabilities = []
  }
}
const onSandboxToggle = (enabled: boolean | string | number) => {
  if (!enabled) {
    formData.value.capabilities = []
  }
}

/** 提交表单 */
const emit = defineEmits(['success']) // 成功回调
const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    await SpecApi.createSpec(formData.value)
    dialogVisible.value = false
    message.success(t('ai.spec.created'))
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
