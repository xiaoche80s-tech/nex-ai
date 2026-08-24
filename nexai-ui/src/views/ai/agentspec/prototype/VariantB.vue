<template>
  <div class="flex h-full gap-3 p-3">
    <!-- 左：规格卡片列表（主从布局的主） -->
    <div class="w-300px flex flex-col gap-2 overflow-y-auto flex-none">
      <div
        v-for="row in specs"
        :key="row.id"
        class="p-3 bg-white rd-2 border border-solid cursor-pointer transition-colors"
        :class="row.id === selected?.id ? 'border-#4f46e5 bg-#eef2ff' : 'border-#e4e4e7 hover:border-#a5b4fc'"
        @click="selected = row"
      >
        <div class="flex items-center gap-2">
          <span class="text-20px">{{ row.icon }}</span>
          <span class="font-semibold">{{ row.name }}</span>
          <el-tag :type="statusOf(row).type" size="small" class="ml-a">
            {{ statusOf(row).text }}
          </el-tag>
        </div>
        <div class="text-12px text-#71717a mt-1">
          {{ row.specCode }} · {{ row.ownerLevel === 'TENANT' ? '租户级' : `用户级 · ${row.ownerUserName}` }}
        </div>
      </div>
    </div>

    <!-- 右：常驻详情面板（零弹层，版本时间线为一等公民） -->
    <div v-if="selected" class="flex-1 bg-white rd-2 border border-solid border-#e4e4e7 p-4 overflow-y-auto">
      <!-- 草稿横幅：有无草稿两态 -->
      <div
        class="p-3 rd-2 mb-4 border border-dashed"
        :class="selected.hasDraft ? 'border-#fbbf24 bg-#fffbeb' : 'border-#e4e4e7 bg-#f5f7fa'"
      >
        <template v-if="selected.hasDraft">
          <div class="flex items-center gap-2">
            <b>✏️ 当前草稿</b>
            <span class="text-13px text-#71717a">{{ selected.draftSummary }}</span>
            <el-input v-model="publishNote" placeholder="发布备注" size="small" class="!w-180px ml-a" />
            <el-button type="primary" size="small" @click="onReadOnly">🚀 发布新版本</el-button>
          </div>
          <div class="text-12px text-#a1a1aa mt-1">发布将固化快照、推进指针并清空草稿</div>
        </template>
        <template v-else>
          <div class="flex items-center gap-2">
            <b>🔒 稳定态（无草稿）</b>
            <el-button size="small" @click="onReadOnly">编辑（保存后产生新草稿）</el-button>
            <el-tooltip content="已发布且无新草稿；编辑保存后方可发布新版本" placement="top">
              <span class="ml-a">
                <el-button type="primary" size="small" disabled>🚀 发布新版本</el-button>
              </span>
            </el-tooltip>
          </div>
          <div class="text-12px text-#a1a1aa mt-1">编辑回填当前生效快照，保存才落草稿（打开不保存零副作用）</div>
        </template>
      </div>

      <!-- 版本时间线：发布人就地可见，配置折叠展开预览 -->
      <h3 class="mt-0 mb-3 text-15px font-semibold">版本历史</h3>
      <el-timeline v-if="versions.length">
        <el-timeline-item
          v-for="v in reversedVersions"
          :key="v.versionNo"
          :type="v.versionNo === selected.currentVersionNo ? 'primary' : undefined"
          :hollow="v.versionNo !== selected.currentVersionNo"
          :timestamp="`${v.createTime} · ${v.publisher} 发布`"
          placement="top"
        >
          <div class="flex items-center gap-2">
            <b>v{{ v.versionNo }}</b>
            <el-tag v-if="v.versionNo === selected.currentVersionNo" type="primary" size="small" effect="dark">
              ● 当前生效
            </el-tag>
            <span class="text-13px text-#71717a">{{ v.note }}</span>
            <el-button
              v-if="v.versionNo !== selected.currentVersionNo"
              link
              type="warning"
              size="small"
              class="ml-a"
              @click="onReadOnly"
            >
              切换到此版
            </el-button>
          </div>
          <el-collapse class="mt-1 border-none">
            <el-collapse-item title="查看四层配置" :name="`v${v.versionNo}`">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="模型">{{ v.config.model }}</el-descriptions-item>
                <el-descriptions-item label="温度 / 迭代">
                  {{ v.config.temperature }} / {{ v.config.maxIters }}
                </el-descriptions-item>
                <el-descriptions-item label="系统提示" :span="2">
                  <div class="whitespace-pre-wrap text-13px">{{ v.config.systemPrompt }}</div>
                </el-descriptions-item>
                <el-descriptions-item label="挂载" :span="2">
                  <div class="text-13px">
                    🎓 {{ v.config.skills.join('、') }}<br />
                    🔧 {{ v.config.tools.join('；') }}<br />
                    📁 {{ v.config.folders.join('；') }}
                  </div>
                </el-descriptions-item>
                <el-descriptions-item label="执行环境" :span="2">
                  Workspace {{ v.config.workspace ? '开' : '关' }} · 沙箱 {{ v.config.sandbox ? '开' : '关' }} ·
                  {{ v.config.capabilities.join('/') }}
                </el-descriptions-item>
              </el-descriptions>
            </el-collapse-item>
          </el-collapse>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="尚无版本快照（发布后产生 v1）" :image-size="60" />
    </div>
  </div>
</template>

<script lang="ts" setup>
/** 变体 B —— 时间线主从：左规格卡片 + 右常驻详情，版本垂直时间线、配置就地展开，全程零弹层 */
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import { specs, statusOf, versionsBySpec, type SpecRow } from './mock'

defineOptions({ name: 'VariantB' })

const selected = ref<SpecRow>(specs[0])
const publishNote = ref('')

const versions = computed(() => versionsBySpec[selected.value.id] ?? [])
const reversedVersions = computed(() => [...versions.value].reverse())

function onReadOnly() {
  ElMessage.info('原型只读：此动作在实现工单中生效')
}
</script>
