<template>
  <ContentWrap>
    <!-- 搜索工作栏 -->
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="80px"
    >
      <el-form-item label="规格名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入规格名称"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"> <Icon icon="ep:refresh" class="mr-5px" /> 重置 </el-button>
        <el-button type="primary" plain @click="openForm()" v-hasPermi="['ai:spec:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 创建规格
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <!-- 规格列表 -->
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="业务编码" align="center" prop="specCode" width="160" show-overflow-tooltip />
      <el-table-column label="归属" align="center" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.ownerLevel === 'USER'" type="warning">用户级</el-tag>
          <el-tag v-else-if="row.ownerLevel === 'PLATFORM'" type="danger">平台级</el-tag>
          <el-tag v-else>租户级</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="规格名称"
        align="center"
        prop="name"
        min-width="150"
        show-overflow-tooltip
      >
        <template #default="scope">
          <div class="flex items-center justify-center gap-4px">
            <Icon v-if="scope.row.icon" :icon="scope.row.icon" />
            <span>{{ scope.row.name }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="描述"
        align="center"
        prop="description"
        min-width="180"
        show-overflow-tooltip
      >
        <template #default="scope">
          <span>{{ scope.row.description || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="引用模型"
        align="center"
        prop="modelName"
        min-width="140"
        show-overflow-tooltip
      >
        <template #default="scope">
          <span>{{ scope.row.modelName || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="默认版本" align="center" width="110">
        <template #default="scope">
          <el-tag v-if="scope.row.currentVersionNo" type="success" disable-transitions>
            v{{ scope.row.currentVersionNo }}
          </el-tag>
          <el-tag v-else type="info" disable-transitions>未发布</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="草稿" align="center" width="90">
        <template #default="scope">
          <el-tag v-if="scope.row.hasDraft" type="warning" disable-transitions>有草稿</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column
        label="创建时间"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" align="center" width="290" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="success"
            :disabled="!scope.row.hasDraft"
            :loading="scope.row.publishing"
            @click="handlePublish(scope.row)"
            v-hasPermi="['ai:spec:publish']"
          >
            发布
          </el-button>
          <el-button
            link
            type="primary"
            @click="openForm(scope.row.id)"
            v-hasPermi="['ai:spec:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="primary"
            @click="openVersionHistory(scope.row)"
            v-hasPermi="['ai:spec:query']"
          >
            版本历史
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['ai:spec:delete']"
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

  <!-- 规格编辑弹窗 -->
  <AgentSpecForm ref="formRef" @success="getList" />
  <!-- 版本历史抽屉 -->
  <VersionHistoryDrawer ref="versionHistoryRef" @switched="getList" />
</template>
<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import * as SpecApi from '@/api/ai/spec'
import AgentSpecForm from './AgentSpecForm.vue'
import VersionHistoryDrawer from './VersionHistoryDrawer.vue'

defineOptions({ name: 'AiAgentSpec' })

const message = useMessage() // 消息弹窗

const loading = ref(true) // 列表的加载中
const total = ref(0) // 列表的总条数
const list = ref<SpecApi.AgentSpecVO[]>([]) // 列表的数据
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: ''
})
const queryFormRef = ref() // 搜索的表单

/** 查询规格列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await SpecApi.getSpecPage(queryParams)
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

/** 新增/编辑规格操作 */
const formRef = ref()
const openForm = (id?: number) => {
  formRef.value.open(id)
}

/** 发布：草稿固化为不可变新版本（可填发布说明） */
const handlePublish = async (row: SpecApi.AgentSpecVO) => {
  const { value } = await ElMessageBox.prompt(
    `将「${row.name}」的当前草稿发布为不可变版本 v${row.latestVersionNo + 1}，发布后版本不可修改。`,
    '发布确认',
    {
      confirmButtonText: '发 布',
      cancelButtonText: '取 消',
      inputPlaceholder: '发布说明（可选），如：调整提示词与温度',
      inputPattern: /^.{0,255}$/,
      inputErrorMessage: '发布说明不能超过 255 个字符',
      type: 'warning'
    }
  ).catch(() => ({ value: undefined }) as never)
  if (value === undefined) {
    return
  }
  row.publishing = true
  try {
    const versionNo = await SpecApi.publishSpec({ id: row.id!, remark: value || undefined })
    message.success(`已发布 v${versionNo}，默认版本已切换`)
    await getList()
  } finally {
    row.publishing = false
  }
}

/** 版本历史抽屉 */
const versionHistoryRef = ref()
const openVersionHistory = (row: SpecApi.AgentSpecVO) => {
  versionHistoryRef.value.open(row)
}

/** 删除规格按钮操作（连同全部版本） */
const handleDelete = async (id: number) => {
  await message.delConfirm('删除将连同全部已发布版本一并移除，确认删除该规格吗？')
  await SpecApi.deleteSpec(id)
  message.success('删除成功')
  await getList()
}

/** 初始化 **/
onMounted(() => {
  getList()
})
</script>
