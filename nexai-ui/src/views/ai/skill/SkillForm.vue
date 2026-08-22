<template>
  <Dialog
    v-model="dialogVisible"
    :title="formType === 'create' ? '创建技能' : '编辑技能'"
    width="860"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="110px"
      v-loading="formLoading"
    >
      <el-form-item label="SKILL.md" prop="skillMd">
        <div class="w-full">
          <el-input
            v-model="formData.skillMd"
            type="textarea"
            :rows="14"
            spellcheck="false"
            class="font-mono"
            placeholder="---&#10;name: pdf-report&#10;description: 生成 PDF 汇报文档&#10;---&#10;按模板生成汇报文档。"
          />
          <div class="mt-4px text-12px text-gray-400 leading-18px">
            YAML front matter 的 name（技能名，租户内唯一）与 description（技能描述）必填， front
            matter 之后为技能说明正文。技能名即运行时挂载寻址键。
          </div>
        </div>
      </el-form-item>

      <el-form-item label="资源文件">
        <div class="w-full">
          <div
            v-for="(resource, index) in resourceList"
            :key="index"
            class="w-full mb-8px flex items-start gap-8px"
          >
            <el-input
              v-model="resource.path"
              placeholder="相对路径，如 scripts/run.py"
              class="!w-240px shrink-0 font-mono"
            />
            <el-input
              v-model="resource.content"
              type="textarea"
              :rows="2"
              spellcheck="false"
              class="font-mono"
              placeholder="文件内容"
            />
            <el-button
              link
              type="danger"
              class="mt-4px shrink-0"
              @click="resourceList.splice(index, 1)"
            >
              删除
            </el-button>
          </div>
          <el-button class="mt-4px" @click="resourceList.push({ path: '', content: '' })">
            <Icon icon="ep:plus" class="mr-5px" /> 添加资源文件
          </el-button>
          <div class="mt-4px text-12px text-gray-400 leading-18px">
            附属脚本与数据文件，与 SKILL.md 一同被智能体装载；路径不能包含 .. 段与反斜杠。
          </div>
        </div>
      </el-form-item>

      <el-alert
        v-if="formType === 'update' && hasPublished"
        type="info"
        :closable="false"
        show-icon
        class="mb-10px"
        title="已发布的版本不可修改；本次保存将写入草稿，确认后再发布为新版本。发布后的技能才会对智能体运行时可见。"
      />
    </el-form>
    <template #footer>
      <el-button
        type="primary"
        :disabled="formLoading"
        v-hasPermi="['ai:skill:create', 'ai:skill:update']"
        @click="submitForm"
      >
        保 存
      </el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as SkillApi from '@/api/ai/skill'

defineOptions({ name: 'AiSkillForm' })

const message = useMessage() // 消息弹窗

/** 资源文件编辑行（path 为空串的行在提交时丢弃） */
interface ResourceRow {
  path: string
  content: string
}

const formType = ref<'create' | 'update'>('create') // 表单类型
const hasPublished = ref(false) // 编辑对象是否已发布过（提示草稿/版本语义）
const dialogVisible = ref(false) // 弹窗的是否展示
const formLoading = ref(false) // 表单的加载中
const formRef = ref() // 表单 Ref
const formData = ref<{ id?: number; skillMd: string }>({
  id: undefined,
  skillMd: ''
})
const resourceList = ref<ResourceRow[]>([]) // 资源文件编辑行
const formRules = reactive({
  skillMd: [{ required: true, message: 'SKILL.md 内容不能为空', trigger: 'blur' }]
})

/** 打开弹窗：传 id 为编辑（优先草稿、无草稿预填当前默认版本），否则为创建 */
const open = async (id?: number) => {
  dialogVisible.value = true
  formLoading.value = true
  try {
    if (id) {
      formType.value = 'update'
      const detail = await SkillApi.getSkill(id)
      hasPublished.value = detail.latestVersionNo > 0
      // 预填优先级：草稿 > 当前默认版本快照 > 空表单
      const content = detail.draft ?? detail.currentVersion?.content
      formData.value = { id: detail.id, skillMd: content?.skillMd ?? '' }
      resourceList.value = Object.entries(content?.resources ?? {}).map(([path, fileContent]) => ({
        path,
        content: fileContent
      }))
    } else {
      formType.value = 'create'
      hasPublished.value = false
      formData.value = { id: undefined, skillMd: '' }
      resourceList.value = []
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
  // 资源行 → path → content 映射（空白路径行丢弃；重复路径以最后一行覆盖）
  const resources: Record<string, string> = {}
  for (const row of resourceList.value) {
    const path = row.path.trim()
    if (path) {
      resources[path] = row.content
    }
  }
  formLoading.value = true
  try {
    const data: SkillApi.SkillSaveForm = {
      id: formData.value.id,
      skillMd: formData.value.skillMd,
      resources
    }
    if (formType.value === 'create') {
      await SkillApi.createSkill(data)
      message.success('创建成功，当前为草稿，发布后对智能体可见')
    } else {
      await SkillApi.updateSkill(data)
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
