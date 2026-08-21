<template>
  <ContentWrap>
    <!-- 搜索工作栏 -->
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="68px"
    >
      <el-form-item label="处理状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-240px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.AI_FEEDBACK_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="反馈内容" prop="content">
        <el-input
          v-model="queryParams.content"
          placeholder="请输入反馈内容"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm()" v-hasPermi="['ai:feedback:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 提交反馈
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="反馈内容" align="center" prop="content" min-width="240" show-overflow-tooltip />
      <el-table-column label="截图" align="center" width="80">
        <template #default="scope">
          <el-image
            v-if="(scope.row.screenshotUrls?.length ?? 0) > 0"
            :src="scope.row.screenshotUrls![0]"
            :preview-src-list="scope.row.screenshotUrls"
            preview-teleported
            fit="cover"
            class="h-30px w-30px"
          />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="关联会话" align="center" prop="sessionId" width="160" show-overflow-tooltip>
        <template #default="scope">
          <span>{{ scope.row.sessionId || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="处理状态" align="center" prop="status" width="100">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.AI_FEEDBACK_STATUS" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="提交人" align="center" prop="submitterId" width="90" />
      <el-table-column
        label="提交时间"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" align="center" width="140" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openDetail(scope.row.id)">
            详情
          </el-button>
          <el-button
            link
            type="warning"
            :disabled="getTransitionTargets(scope.row.status).length === 0"
            @click="openTransition(scope.row)"
            v-hasPermi="['ai:feedback:update']"
          >
            流转
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页 -->
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 提交反馈弹窗 -->
  <FeedbackForm ref="formRef" @success="getList" />

  <!-- 详情弹窗 -->
  <Dialog v-model="detailVisible" title="反馈详情" width="700">
    <el-descriptions :column="2" border v-if="detail">
      <el-descriptions-item label="编号">{{ detail.id }}</el-descriptions-item>
      <el-descriptions-item label="处理状态">
        <dict-tag :type="DICT_TYPE.AI_FEEDBACK_STATUS" :value="detail.status" />
      </el-descriptions-item>
      <el-descriptions-item label="提交人">{{ detail.submitterId }}</el-descriptions-item>
      <el-descriptions-item label="提交时间">
        {{ formatDate(detail.createTime) }}
      </el-descriptions-item>
      <el-descriptions-item label="关联会话" :span="2">
        {{ detail.sessionId || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="反馈内容" :span="2">
        {{ detail.content }}
      </el-descriptions-item>
      <el-descriptions-item label="截图" :span="2">
        <div v-if="detail.screenshotUrls?.length" class="flex gap-8px">
          <el-image
            v-for="url in detail.screenshotUrls"
            :key="url"
            :src="url"
            :preview-src-list="detail.screenshotUrls"
            preview-teleported
            fit="cover"
            class="h-80px w-80px"
          />
        </div>
        <span v-else>无</span>
      </el-descriptions-item>
    </el-descriptions>
  </Dialog>

  <!-- 流转弹窗 -->
  <Dialog v-model="transitionVisible" title="流转处理状态" width="420">
    <el-form label-width="90px">
      <el-form-item label="当前状态">
        <dict-tag v-if="transitionRow" :type="DICT_TYPE.AI_FEEDBACK_STATUS" :value="transitionRow.status" />
      </el-form-item>
      <el-form-item label="目标状态" required>
        <el-select v-model="transitionTarget" placeholder="请选择目标状态" class="!w-220px">
          <el-option
            v-for="dict in transitionTargetOptions"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :disabled="!transitionTarget" @click="submitTransition">确 定</el-button>
      <el-button @click="transitionVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { dateFormatter, formatDate } from '@/utils/formatTime'
import * as FeedbackApi from '@/api/ai/feedback'
import FeedbackForm from './FeedbackForm.vue'

defineOptions({ name: 'AiFeedback' })

const message = useMessage() // 消息弹窗

/** 状态流转矩阵（与后端 FeedbackStatus.canTransitionTo 一致）：已关闭为终态 */
const TRANSITION_MATRIX: Record<number, number[]> = {
  10: [20, 30, 40], // 待处理 → 处理中 / 已解决 / 已关闭
  20: [10, 30, 40], // 处理中 → 待处理 / 已解决 / 已关闭
  30: [10, 20, 40], // 已解决 → 待处理 / 处理中 / 已关闭
  40: [] // 已关闭 → 终态
}
const getTransitionTargets = (status: number): number[] => TRANSITION_MATRIX[status] ?? []

const loading = ref(true) // 列表的加载中
const total = ref(0) // 列表的总页数
const list = ref<FeedbackApi.FeedbackVO[]>([]) // 列表的数据
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  status: undefined as number | undefined,
  content: ''
})
const queryFormRef = ref() // 搜索的表单

/** 查询反馈列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await FeedbackApi.getFeedbackPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

/** 提交反馈操作（可携带会话标识预填） */
const formRef = ref()
const openForm = (sessionId?: string) => {
  formRef.value.open(sessionId)
}

/** 详情操作 */
const detailVisible = ref(false)
const detail = ref<FeedbackApi.FeedbackVO>()
const openDetail = async (id: number) => {
  detail.value = await FeedbackApi.getFeedback(id)
  detailVisible.value = true
}

/** 流转操作 */
const transitionVisible = ref(false)
const transitionRow = ref<FeedbackApi.FeedbackVO>()
const transitionTarget = ref<number>()
const transitionTargetOptions = computed(() =>
  getIntDictOptions(DICT_TYPE.AI_FEEDBACK_STATUS).filter((dict) =>
    transitionRow.value ? getTransitionTargets(transitionRow.value.status).includes(dict.value) : false
  )
)
const openTransition = (row: FeedbackApi.FeedbackVO) => {
  transitionRow.value = row
  transitionTarget.value = undefined
  transitionVisible.value = true
}
const submitTransition = async () => {
  if (!transitionRow.value || !transitionTarget.value) {
    return
  }
  await FeedbackApi.transitionFeedback({
    id: transitionRow.value.id!,
    targetStatus: transitionTarget.value
  })
  message.success('流转成功')
  transitionVisible.value = false
  await getList()
}

/** 初始化 **/
onMounted(() => {
  getList()
})
</script>
