<template>
  <ContentWrap>
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="渠道" name="channel">
        <!-- 渠道搜索工作栏 -->
        <el-form
          v-show="activeTab === 'channel'"
          class="-mb-15px"
          :model="queryParams"
          ref="queryFormRef"
          :inline="true"
          label-width="68px"
        >
          <el-form-item label="渠道名称" prop="name">
            <el-input
              v-model="queryParams.name"
              placeholder="请输入渠道名称"
              clearable
              @keyup.enter="handleQuery"
              class="!w-240px"
            />
          </el-form-item>
          <el-form-item label="提供商" prop="provider">
            <el-select
              v-model="queryParams.provider"
              placeholder="请选择提供商"
              clearable
              class="!w-240px"
            >
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.AI_CHANNEL_PROVIDER)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" prop="enabled">
            <el-select
              v-model="queryParams.enabled"
              placeholder="请选择状态"
              clearable
              class="!w-240px"
            >
              <el-option label="已启用" :value="true" />
              <el-option label="已停用" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="handleQuery"
              ><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button
            >
            <el-button @click="resetQuery">
              <Icon icon="ep:refresh" class="mr-5px" /> 重置
            </el-button>
            <el-button type="primary" plain @click="openForm()" v-hasPermi="['ai:channel:create']">
              <Icon icon="ep:plus" class="mr-5px" /> 新增渠道
            </el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="模型" name="modelList">
        <!-- 模型搜索工作栏 -->
        <el-form
          v-show="activeTab === 'modelList'"
          class="-mb-15px"
          :model="modelQueryParams"
          ref="modelQueryFormRef"
          :inline="true"
          label-width="80px"
        >
          <el-form-item label="所属渠道" prop="channelId">
            <el-select
              v-model="modelQueryParams.channelId"
              placeholder="请选择所属渠道"
              clearable
              filterable
              class="!w-240px"
            >
              <el-option
                v-for="channel in channelOptions"
                :key="channel.id"
                :label="channel.name"
                :value="channel.id!"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="模型标识" prop="modelId">
            <el-input
              v-model="modelQueryParams.modelId"
              placeholder="请输入模型标识"
              clearable
              @keyup.enter="handleModelQuery"
              class="!w-200px"
            />
          </el-form-item>
          <el-form-item label="显示名" prop="name">
            <el-input
              v-model="modelQueryParams.name"
              placeholder="请输入显示名"
              clearable
              @keyup.enter="handleModelQuery"
              class="!w-160px"
            />
          </el-form-item>
          <el-form-item label="能力标签" prop="capability">
            <el-input
              v-model="modelQueryParams.capability"
              placeholder="如：vision"
              clearable
              @keyup.enter="handleModelQuery"
              class="!w-140px"
            />
          </el-form-item>
          <el-form-item label="状态" prop="enabled">
            <el-select
              v-model="modelQueryParams.enabled"
              placeholder="请选择状态"
              clearable
              class="!w-140px"
            >
              <el-option label="已启用" :value="true" />
              <el-option label="已停用" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="handleModelQuery"
              ><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button
            >
            <el-button @click="resetModelQuery">
              <Icon icon="ep:refresh" class="mr-5px" /> 重置
            </el-button>
            <el-button
              type="primary"
              plain
              @click="openModelForm()"
              v-hasPermi="['ai:model:create']"
            >
              <Icon icon="ep:plus" class="mr-5px" /> 登记模型
            </el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <!-- 渠道列表 -->
    <el-table v-show="activeTab === 'channel'" v-loading="loading" :data="list">
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column
        label="渠道名称"
        align="center"
        prop="name"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column label="提供商" align="center" prop="provider" width="120">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.AI_CHANNEL_PROVIDER" :value="scope.row.provider" />
        </template>
      </el-table-column>
      <el-table-column
        label="端点地址"
        align="center"
        prop="baseUrl"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column label="密钥" align="center" prop="apiKeyMasked" width="130">
        <template #default="scope">
          <span>{{ scope.row.apiKeyMasked || '未配置' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" align="center" width="90">
        <template #default="scope">
          <el-switch
            v-model="scope.row.enabled"
            :loading="scope.row.statusLoading"
            :disabled="!checkPermi(['ai:channel:update'])"
            @change="handleStatusChange(scope.row)"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="创建时间"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" align="center" width="140" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openForm(scope.row.id)"
            v-hasPermi="['ai:channel:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['ai:channel:delete']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 渠道分页 -->
    <Pagination
      v-show="activeTab === 'channel'"
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 模型列表 -->
    <el-table v-show="activeTab === 'modelList'" v-loading="modelLoading" :data="modelList">
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column
        label="所属渠道"
        align="center"
        prop="channelName"
        min-width="160"
        show-overflow-tooltip
      >
        <template #default="scope">
          <span>{{ scope.row.channelName || `#${scope.row.channelId}` }}</span>
        </template>
      </el-table-column>
      <el-table-column label="提供商" align="center" prop="channelProvider" width="110">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.AI_CHANNEL_PROVIDER" :value="scope.row.channelProvider" />
        </template>
      </el-table-column>
      <el-table-column
        label="模型标识"
        align="center"
        prop="modelId"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        label="显示名"
        align="center"
        prop="name"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column label="上下文窗口" align="center" prop="contextWindow" width="110">
        <template #default="scope">
          <span>{{
            scope.row.contextWindow ? formatTokens(scope.row.contextWindow) : '未知'
          }}</span>
        </template>
      </el-table-column>
      <el-table-column label="单价（元/百万）" align="center" width="150">
        <template #default="scope">
          <span>
            入 {{ scope.row.inputPrice ?? '—' }} / 出 {{ scope.row.outputPrice ?? '—' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="能力标签" align="center" prop="capabilities" min-width="160">
        <template #default="scope">
          <el-tag
            v-for="tag in scope.row.capabilities"
            :key="tag"
            size="small"
            class="mr-4px mb-2px"
            disable-transitions
          >
            {{ tag }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" align="center" width="90">
        <template #default="scope">
          <el-switch
            v-model="scope.row.enabled"
            :loading="scope.row.statusLoading"
            :disabled="!checkPermi(['ai:model:update'])"
            @change="handleModelStatusChange(scope.row)"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="创建时间"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" align="center" width="210" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="success"
            :loading="scope.row.testing"
            @click="handleTestConnectivity(scope.row)"
            v-hasPermi="['ai:model:update']"
          >
            测试
          </el-button>
          <el-button
            link
            type="primary"
            @click="openModelForm(scope.row.id)"
            v-hasPermi="['ai:model:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleModelDelete(scope.row.id)"
            v-hasPermi="['ai:model:delete']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 模型分页 -->
    <Pagination
      v-show="activeTab === 'modelList'"
      :total="modelTotal"
      v-model:page="modelQueryParams.pageNo"
      v-model:limit="modelQueryParams.pageSize"
      @pagination="getModelList"
    />
  </ContentWrap>

  <!-- 渠道表单弹窗 -->
  <ChannelForm ref="formRef" @success="refreshActiveTab" />
  <!-- 模型表单弹窗 -->
  <ModelForm ref="modelFormRef" @success="getModelList" />
</template>
<script lang="ts" setup>
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import { checkPermi } from '@/utils/permission'
import * as ModelApi from '@/api/ai/model'
import ChannelForm from './ChannelForm.vue'
import ModelForm from './ModelForm.vue'

defineOptions({ name: 'AiModel' })

const message = useMessage() // 消息弹窗

const activeTab = ref('channel') // 当前页签：channel 渠道 / modelList 模型

// ==================== 渠道页签 ====================
const loading = ref(true) // 列表的加载中
const total = ref(0) // 列表的总页数
const list = ref<ModelApi.ChannelVO[]>([]) // 列表的数据
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: '',
  provider: undefined as string | undefined,
  enabled: undefined as boolean | undefined
})
const queryFormRef = ref() // 搜索的表单

/** 查询渠道列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await ModelApi.getChannelPage(queryParams)
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

/** 新增/编辑渠道操作 */
const formRef = ref()
const openForm = (id?: number) => {
  formRef.value.open(id)
}

/** 启用/停用渠道 */
const handleStatusChange = async (row: ModelApi.ChannelVO) => {
  row.statusLoading = true
  try {
    await ModelApi.updateChannelStatus({ id: row.id!, enabled: row.enabled })
    message.success(row.enabled ? '已启用' : '已停用')
  } catch {
    // 失败回滚开关状态
    row.enabled = !row.enabled
  } finally {
    row.statusLoading = false
  }
}

/** 删除渠道按钮操作 */
const handleDelete = async (id: number) => {
  await message.delConfirm('删除后渠道下登记的模型将不可用，确认删除该渠道吗？')
  await ModelApi.deleteChannel(id)
  message.success('删除成功')
  await getList()
}

// ==================== 模型页签 ====================
const modelLoading = ref(false) // 模型列表的加载中
const modelTotal = ref(0) // 模型列表的总条数
const modelList = ref<ModelApi.ModelVO[]>([]) // 模型列表的数据
const modelQueryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  channelId: undefined as number | undefined,
  modelId: '',
  name: '',
  capability: '',
  enabled: undefined as boolean | undefined
})
const modelQueryFormRef = ref() // 模型搜索的表单
const channelOptions = ref<ModelApi.ChannelVO[]>([]) // 启用渠道下拉选项
let channelOptionsLoaded = false // 下拉选项懒加载标记

/** 查询模型列表 */
const getModelList = async () => {
  modelLoading.value = true
  try {
    const data = await ModelApi.getModelPage(modelQueryParams)
    modelList.value = data.list
    modelTotal.value = data.total
  } finally {
    modelLoading.value = false
  }
}

/** 加载启用渠道下拉选项（懒加载，渠道增删改后重取） */
const loadChannelOptions = async (force = false) => {
  if (channelOptionsLoaded && !force) {
    return
  }
  channelOptions.value = await ModelApi.getEnabledChannelList()
  channelOptionsLoaded = true
}

/** 模型搜索按钮操作 */
const handleModelQuery = () => {
  modelQueryParams.pageNo = 1
  getModelList()
}

/** 模型重置按钮操作 */
const resetModelQuery = () => {
  modelQueryFormRef.value.resetFields()
  handleModelQuery()
}

/** 新增/编辑模型操作 */
const modelFormRef = ref()
const openModelForm = async (id?: number) => {
  // 表单内的渠道下拉需要最新数据，每次打开都重取
  await loadChannelOptions(true)
  modelFormRef.value.open(id)
}

/** 启用/停用模型 */
const handleModelStatusChange = async (row: ModelApi.ModelVO) => {
  row.statusLoading = true
  try {
    await ModelApi.updateModelStatus({ id: row.id!, enabled: row.enabled })
    message.success(row.enabled ? '已启用' : '已停用')
  } catch {
    // 失败回滚开关状态
    row.enabled = !row.enabled
  } finally {
    row.statusLoading = false
  }
}

/** 删除模型按钮操作 */
const handleModelDelete = async (id: number) => {
  await message.delConfirm('确认删除该模型吗？')
  await ModelApi.deleteModel(id)
  message.success('删除成功')
  await getModelList()
}

/** 连通性测试：一次真实轻量调用，展示成败/耗时/错误信息 */
const handleTestConnectivity = async (row: ModelApi.ModelVO) => {
  row.testing = true
  try {
    const result = await ModelApi.testModelConnectivity(row.id!)
    if (result.success) {
      message.success(`「${row.name}」连通正常（${result.durationMs} ms）`)
    } else {
      // 失败原因可能较长（含提供商错误响应），弹窗纯文本完整展示（不拼 HTML，防注入）
      await ElMessageBox.alert(
        `${result.message}\n\n耗时：${result.durationMs} ms`,
        `「${row.name}」连通性测试失败`,
        {
          type: 'error',
          confirmButtonText: '知道了'
        }
      )
    }
  } finally {
    row.testing = false
  }
}

/** 上下文窗口数值格式化：128000 → 128K */
const formatTokens = (tokens: number) => {
  if (tokens >= 1000) {
    return `${Math.round(tokens / 1000)}K`
  }
  return `${tokens}`
}

/** 页签切换：首次进入模型页签时加载下拉与列表 */
const handleTabChange = async (tabName: string) => {
  if (tabName === 'modelList') {
    await loadChannelOptions()
    if (modelList.value.length === 0 && modelTotal.value === 0) {
      await getModelList()
    }
  }
}

/** 渠道增删改后：渠道列表刷新；若模型下拉已加载过则一并刷新 */
const refreshActiveTab = async () => {
  await getList()
  if (channelOptionsLoaded) {
    await loadChannelOptions(true)
  }
}

/** 初始化 **/
onMounted(() => {
  getList()
})
</script>
