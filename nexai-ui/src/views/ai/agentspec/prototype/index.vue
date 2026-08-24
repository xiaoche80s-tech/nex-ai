<template>
  <div class="h-screen flex flex-col bg-#f4f4f5">
    <!-- 原型身份说明（throwaway，定案后整目录移除） -->
    <div class="flex-none px-4 py-2 bg-#18181b text-white text-12px flex items-center gap-3">
      <b>PROTOTYPE</b>
      <span>agentspec 管理页 · 3 个结构迥异的变体（?variant=A/B/C 或用底部切换条 / ←→ 方向键）</span>
      <span class="text-#a1a1aa ml-a">只读 mock · 定案后随 throwaway 分支归档</span>
    </div>

    <div class="flex-1 overflow-hidden">
      <VariantA v-if="variant === 'A'" />
      <VariantB v-else-if="variant === 'B'" />
      <VariantC v-else />
    </div>

    <PrototypeSwitcher :variants="variants" :current="variant" />
  </div>
</template>

<script lang="ts" setup>
/** 原型宿主（throwaway）：工单 22-25 的 UI 定案验证。变体只读，mock 数据见 ./mock.ts */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PrototypeSwitcher from './PrototypeSwitcher.vue'
import VariantA from './VariantA.vue'
import VariantB from './VariantB.vue'
import VariantC from './VariantC.vue'

defineOptions({ name: 'PrototypeAgentspec' })

const route = useRoute()
const variants = [
  { key: 'A', name: '弹窗基线（现状演进）' },
  { key: 'B', name: '时间线主从（版本一等公民）' },
  { key: 'C', name: '详情页式（快照 vs 草稿对照）' }
]
const variant = computed(() => {
  const v = route.query.variant
  return typeof v === 'string' && ['A', 'B', 'C'].includes(v) ? v : 'A'
})
</script>
