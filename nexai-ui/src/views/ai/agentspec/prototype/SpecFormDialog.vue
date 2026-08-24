<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'create' ? '新增智能体规格' : `编辑 · ${form.name}`"
    width="680px"
    @update:model-value="emit('update:modelValue', $event)"
    @open="initForm"
  >
    <!-- 编辑态：回填来源徽章（工单 22 —— ADR 0004 交互核心） -->
    <el-alert v-if="mode === 'edit' && spec" :closable="false" class="!mb-3" type="info">
      <template #title>
        <el-tag size="small" type="primary" effect="plain">{{ fillSource.label }}</el-tag>
        <span class="text-12px text-#71717a ml-2">
          {{ spec.hasDraft ? '草稿进行中，回填草稿内容' : '稳定态（无草稿），回填当前生效快照 —— 保存才会产生新草稿' }}
        </span>
      </template>
    </el-alert>

    <el-form :model="form" label-width="92px">
      <el-divider content-position="left">基础信息</el-divider>
      <el-form-item label="规格名称" required>
        <el-input v-model="form.name" placeholder="如：电商客服助手" :maxlength="64" />
      </el-form-item>
      <el-form-item label="业务编码" required>
        <el-input
          v-model="form.specCode"
          :disabled="mode === 'edit'"
          placeholder="小写字母开头，如 ecom-cs-bot"
        />
        <span v-if="mode === 'edit'" class="text-12px text-#a1a1aa">创建后不可变</span>
      </el-form-item>
      <el-form-item label="归属层级">
        <el-radio-group v-model="form.ownerLevel" :disabled="mode === 'edit'">
          <el-radio value="TENANT">租户级</el-radio>
          <el-radio value="USER">用户级（我）</el-radio>
        </el-radio-group>
        <span v-if="mode === 'edit'" class="text-12px text-#a1a1aa ml-2">创建后不可变</span>
      </el-form-item>
      <el-form-item label="图标">
        <el-input v-model="form.icon" placeholder="emoji，如 🤖" class="!w-120px" />
      </el-form-item>

      <el-divider content-position="left">模型与提示</el-divider>
      <el-form-item label="模型">
        <el-select v-model="form.model" class="!w-320px">
          <el-option label="glm-4.7（智谱 · 主力渠道）" value="glm-4.7（智谱 · 主力渠道）" />
          <el-option label="glm-4.7-air（智谱 · 轻量）" value="glm-4.7-air（智谱 · 轻量）" />
          <el-option label="deepseek-v4（深度求索）" value="deepseek-v4（深度求索）" />
        </el-select>
      </el-form-item>
      <el-form-item label="系统提示">
        <el-input v-model="form.prompt" type="textarea" :rows="4" placeholder="定义智能体的角色与行为边界" />
      </el-form-item>
      <el-form-item label="温度">
        <el-slider v-model="form.temperature" :min="0" :max="1" :step="0.1" class="!w-320px" />
      </el-form-item>

      <el-divider content-position="left">挂载与执行环境</el-divider>
      <div class="text-13px text-#71717a pl-92px">
        （原型简化：技能 / 工具挂载 / 私有文件夹 / 沙箱等分区沿用现有表单结构，此处略）
      </div>
    </el-form>

    <template #footer>
      <div class="flex items-center">
        <span v-if="mode === 'edit' && spec && !spec.hasDraft" class="text-12px text-#b45309 mr-a">
          保存将产生新草稿，规格进入「v{{ spec.currentVersionNo }} · 编辑中」
        </span>
        <span v-else class="mr-a"></span>
        <el-button @click="emit('update:modelValue', false)">取消（不保存，零副作用）</el-button>
        <el-button type="primary" :disabled="!form.name || !form.specCode || !form.prompt" @click="save">
          {{ mode === 'create' ? '创建（草稿态）' : '保存草稿' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
/** 新增/编辑规格表单（throwaway）：与现有 AgentSpecForm 同构简化；编辑态显式呈现回填来源（ADR 0004） */
import { reactive, ref, watch } from 'vue'
import { fillSourceOf, type SpecRow } from './mock'

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  spec: SpecRow | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved', payload: { name: string; specCode: string; icon: string; ownerLevel: 'TENANT' | 'USER'; model: string; prompt: string; temperature: number }): void
}>()

const form = reactive({
  name: '',
  specCode: '',
  icon: '🤖',
  ownerLevel: 'TENANT' as 'TENANT' | 'USER',
  model: 'glm-4.7（智谱 · 主力渠道）',
  prompt: '',
  temperature: 0.3
})
const fillSource = ref({ label: '', prompt: '' })

function initForm() {
  if (props.mode === 'edit' && props.spec) {
    const src = fillSourceOf(props.spec)
    fillSource.value = src
    form.name = props.spec.name
    form.specCode = props.spec.specCode
    form.icon = props.spec.icon
    form.ownerLevel = props.spec.ownerLevel
    form.prompt = src.prompt
    form.temperature = 0.3
  } else {
    form.name = ''
    form.specCode = ''
    form.icon = '🤖'
    form.ownerLevel = 'TENANT'
    form.prompt = ''
    form.temperature = 0.3
  }
}
// modelValue 打开时也走一次 initForm（@open 在首次挂载前可能不触发）
watch(() => props.modelValue, (v) => v && initForm(), { immediate: true })

function save() {
  emit('saved', { ...form })
  emit('update:modelValue', false)
}
</script>
