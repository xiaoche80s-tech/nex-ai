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
      <el-form-item :label="t('ai.spec.searchName')" prop="name">
        <el-input
          v-model="queryParams.name"
          :placeholder="t('common.inputText')"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item :label="t('ai.spec.searchSpecCode')" prop="specCode">
        <el-input
          v-model="queryParams.specCode"
          :placeholder="t('common.inputText')"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"
          ><Icon icon="ep:search" class="mr-5px" /> {{ t('common.query') }}</el-button
        >
        <el-button @click="resetQuery">
          <Icon icon="ep:refresh" class="mr-5px" /> {{ t('common.reset') }}
        </el-button>
        <el-button type="primary" plain @click="openForm()" v-hasPermi="['ai:spec:create']">
          <Icon icon="ep:plus" class="mr-5px" /> {{ t('ai.spec.create') }}
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <!-- 规格列表 -->
    <el-table v-loading="loading" :data="list">
      <el-table-column :label="t('ai.spec.tableId')" align="center" prop="id" width="80" />
      <el-table-column
        :label="t('ai.spec.tableSpecCode')"
        align="center"
        prop="specCode"
        width="180"
        show-overflow-tooltip
      />
      <el-table-column :label="t('ai.spec.tableOwner')" align="center" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.ownerLevel === 'USER'" type="warning" disable-transitions>
            {{ t('ai.spec.ownerUser') }}
          </el-tag>
          <el-tag v-else disable-transitions>{{ t('ai.spec.ownerTenant') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.spec.tableName')"
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
        :label="t('ai.spec.tableDescription')"
        align="center"
        prop="description"
        min-width="200"
        show-overflow-tooltip
      >
        <template #default="scope">
          <span>{{ scope.row.description || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('ai.spec.tableStatus')" align="center" width="130">
        <template #default="scope">
          <!-- 三态（工单 23）：从未发布=草稿；已发布且无草稿=vN 已发布（稳定）；已发布且有草稿=vN · 编辑中 -->
          <el-tag v-if="!scope.row.currentVersionNo" type="warning" disable-transitions>
            {{ t('ai.spec.statusDraft') }}
          </el-tag>
          <el-tag v-else-if="!scope.row.hasDraft" type="success" disable-transitions>
            {{ t('ai.spec.statusPublished') }} v{{ scope.row.currentVersionNo }}
          </el-tag>
          <el-tag v-else type="primary" disable-transitions>
            v{{ scope.row.currentVersionNo }} · {{ t('ai.spec.statusEditing') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.spec.tableCreateTime')"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column :label="t('table.action')" align="center" width="140" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            v-hasPermi="['ai:spec:update']"
            @click="openForm(scope.row.id)"
          >
            {{ t('ai.spec.edit') }}
          </el-button>
          <el-button
            link
            type="primary"
            v-hasPermi="['ai:spec:query']"
            @click="openVersion(scope.row)"
          >
            {{ t('ai.spec.version') }}
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

  <!-- 创建表单弹窗 -->
  <AgentSpecForm ref="formRef" @success="getList" />
  <!-- 版本管理弹窗（发布/列表/切换） -->
  <SpecVersion ref="versionRef" @success="getList" />
</template>
<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import * as SpecApi from '@/api/ai/spec'
import AgentSpecForm from './AgentSpecForm.vue'
import SpecVersion from './SpecVersion.vue'

defineOptions({ name: 'AiAgentSpec' })

const { t } = useI18n() // 国际化

const loading = ref(true) // 列表的加载中
const total = ref(0) // 列表的总条数
const list = ref<SpecApi.AgentSpecVO[]>([]) // 列表的数据
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined,
  specCode: undefined
})
const queryFormRef = ref() // 搜索的表单

/** 查询列表 */
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

/** 打开创建/编辑表单 */
const formRef = ref()
const openForm = (id?: number) => {
  formRef.value.open(id)
}

/** 打开版本管理弹窗（传整行：发布门禁需要 hasDraft 三态语义） */
const versionRef = ref()
const openVersion = (row: SpecApi.AgentSpecVO) => {
  versionRef.value.open(row)
}

/** 初始化 **/
onMounted(() => {
  getList()
})
</script>
