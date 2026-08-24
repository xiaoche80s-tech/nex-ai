<template>
  <div
    v-if="!isProd"
    class="fixed bottom-16px left-50% translate-x--50% z-9999 flex items-center gap-2 px-4 py-2 rd-full bg-#18181b text-white shadow-lg"
  >
    <button class="bg-transparent border-none text-white text-16px cursor-pointer px-1" @click="cycle(-1)">◀</button>
    <span class="text-13px font-semibold whitespace-nowrap">{{ label }}</span>
    <button class="bg-transparent border-none text-white text-16px cursor-pointer px-1" @click="cycle(1)">▶</button>
  </div>
</template>

<script lang="ts" setup>
/** 原型变体切换条（throwaway）：URL ?variant= 持久化 + 方向键循环切换；生产构建隐藏 */
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const props = defineProps<{
  variants: { key: string; name: string }[]
  current: string
}>()

const route = useRoute()
const router = useRouter()
const isProd = import.meta.env.PROD

const label = computed(() => {
  const v = props.variants.find((x) => x.key === props.current)
  return v ? `${v.key} — ${v.name}` : props.current
})

function cycle(dir: 1 | -1) {
  const i = props.variants.findIndex((x) => x.key === props.current)
  const next = props.variants[(i + dir + props.variants.length) % props.variants.length]
  router.replace({ query: { ...route.query, variant: next.key } })
}

function onKey(e: KeyboardEvent) {
  const tag = (e.target as HTMLElement)?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || (e.target as HTMLElement)?.isContentEditable) return
  if (e.key === 'ArrowLeft') cycle(-1)
  if (e.key === 'ArrowRight') cycle(1)
}
onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>
