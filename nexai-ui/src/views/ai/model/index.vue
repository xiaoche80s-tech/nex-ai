<template>
  <ContentWrap>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="渠道" name="channel">
        <!-- 搜索工作栏 -->
        <el-form
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
    </el-tabs>

    <!-- 渠道列表 -->
    <el-table v-loading="loading" :data="list">
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
    <!-- 分页 -->
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 渠道表单弹窗 -->
  <ChannelForm ref="formRef" @success="getList" />
</template>
<script lang="ts" setup>
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import { checkPermi } from '@/utils/permission'
import * as ChannelApi from '@/api/ai/model'
import ChannelForm from './ChannelForm.vue'

defineOptions({ name: 'AiModel' })

const message = useMessage() // 消息弹窗

const activeTab = ref('channel') // 当前页签（模型页签随工单 04 加入）

const loading = ref(true) // 列表的加载中
const total = ref(0) // 列表的总页数
const list = ref<ChannelApi.ChannelVO[]>([]) // 列表的数据
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
    const data = await ChannelApi.getChannelPage(queryParams)
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
const handleStatusChange = async (row: ChannelApi.ChannelVO) => {
  row.statusLoading = true
  try {
    await ChannelApi.updateChannelStatus({ id: row.id!, enabled: row.enabled })
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
  await ChannelApi.deleteChannel(id)
  message.success('删除成功')
  await getList()
}

/** 初始化 **/
onMounted(() => {
  getList()
})
</script>
