<template>
  <ContentWrap>
    <!-- 搜索工作栏 -->
    <el-form class="-mb-15px" :model="queryParams" :inline="true" label-width="80px">
      <el-form-item :label="t('ai.mcp.searchName')" prop="name">
        <el-input
          v-model="queryParams.name"
          :placeholder="t('common.inputText')"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item :label="t('ai.mcp.searchTransport')" prop="transport">
        <el-select
          v-model="queryParams.transport"
          :placeholder="t('common.selectText')"
          clearable
          class="!w-180px"
        >
          <el-option label="STDIO" value="STDIO" />
          <el-option label="SSE" value="SSE" />
          <el-option label="Streamable HTTP" value="STREAMABLE_HTTP" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery">
          <Icon icon="ep:search" class="mr-5px" /> {{ t('common.query') }}
        </el-button>
        <el-button @click="resetQuery">
          <Icon icon="ep:refresh" class="mr-5px" /> {{ t('common.reset') }}
        </el-button>
        <el-button type="primary" plain @click="openForm()" v-hasPermi="['ai:mcp-server:create']">
          <Icon icon="ep:plus" class="mr-5px" /> {{ t('ai.mcp.create') }}
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column :label="t('ai.mcp.tableId')" align="center" prop="id" width="80" />
      <el-table-column
        :label="t('ai.mcp.tableName')"
        align="center"
        prop="name"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column :label="t('ai.mcp.tableTransport')" align="center" width="130">
        <template #default="{ row }">
          <el-tag disable-transitions>{{ transportLabel(row.transport) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.mcp.tableEndpoint')"
        align="center"
        min-width="200"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span v-if="row.endpoint">{{ row.endpoint }}</span>
          <span v-else class="font-mono">{{ row.command }} {{ (row.args || []).join(' ') }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('ai.mcp.tableAuth')" align="center" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.headersConfigured" type="success" disable-transitions>
            {{ t('ai.mcp.authConfigured') }}
          </el-tag>
          <el-tag v-else type="info" disable-transitions>{{ t('ai.mcp.authNone') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('ai.mcp.tableWhitelist')" align="center" width="110">
        <template #default="{ row }">
          <span v-if="row.allowedTools && row.allowedTools.length">
            {{ row.allowedTools.length }} {{ t('ai.mcp.toolCountUnit') }}
          </span>
          <span v-else>{{ t('ai.mcp.whitelistAll') }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('ai.mcp.tableAvailable')" align="center" width="110">
        <template #default="{ row }">
          <span v-if="row.availableTools && row.availableTools.length">
            {{ row.availableTools.length }} {{ t('ai.mcp.toolCountUnit') }}
          </span>
          <span v-else class="text-gray-400">--</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('ai.mcp.tableStatus')" align="center" width="90">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled"
            v-hasPermi="['ai:mcp-server:update']"
            @change="(enabled: string | number | boolean) => handleStatusChange(row, !!enabled)"
          />
        </template>
      </el-table-column>
      <el-table-column :label="t('table.action')" align="center" width="230" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            v-hasPermi="['ai:mcp-server:probe']"
            @click="handleProbe(row)"
          >
            {{ t('ai.mcp.probe') }}
          </el-button>
          <el-button
            link
            type="primary"
            v-hasPermi="['ai:mcp-server:update']"
            @click="openForm(row)"
          >
            {{ t('action.edit') }}
          </el-button>
          <el-button
            link
            type="danger"
            v-hasPermi="['ai:mcp-server:delete']"
            @click="handleDelete(row.id)"
          >
            {{ t('action.del') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 注册/编辑表单 -->
  <el-dialog v-model="formVisible" :title="formTitle" width="680px">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
      <el-form-item :label="t('ai.mcp.formName')" prop="name">
        <el-input v-model="formData.name" :placeholder="t('ai.mcp.formNamePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('ai.mcp.formTransport')" prop="transport">
        <el-radio-group v-model="formData.transport" @change="onTransportChange">
          <el-radio value="STREAMABLE_HTTP">Streamable HTTP</el-radio>
          <el-radio value="SSE">SSE</el-radio>
          <el-radio value="STDIO">stdio</el-radio>
        </el-radio-group>
      </el-form-item>
      <template v-if="formData.transport === 'STDIO'">
        <el-form-item :label="t('ai.mcp.formCommand')" prop="command">
          <el-input v-model="formData.command" :placeholder="t('ai.mcp.formCommandPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('ai.mcp.formArgs')">
          <el-select
            v-model="formData.args"
            multiple
            filterable
            allow-create
            default-first-option
            :placeholder="t('ai.mcp.formArgsPlaceholder')"
            class="!w-full"
          />
        </el-form-item>
        <el-form-item :label="t('ai.mcp.formEnv')">
          <el-input
            v-model="envText"
            type="textarea"
            :rows="3"
            :placeholder="t('ai.mcp.formEnvPlaceholder')"
          />
          <div class="text-12px text-gray-400 leading-20px mt-2px">
            {{ t('ai.mcp.formEnvTip') }}
          </div>
        </el-form-item>
      </template>
      <template v-else>
        <el-form-item :label="t('ai.mcp.formEndpoint')" prop="endpoint">
          <el-input
            v-model="formData.endpoint"
            :placeholder="t('ai.mcp.formEndpointPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('ai.mcp.formHeaders')">
          <el-input
            v-model="headersText"
            type="textarea"
            :rows="2"
            :disabled="editing && headersPreserved"
            :placeholder="t('ai.mcp.formHeadersPlaceholder')"
          />
          <div v-if="editing" class="text-12px text-gray-400 leading-20px mt-2px">
            {{
              headersPreserved
                ? t('ai.mcp.formHeadersPreserved')
                : t('ai.mcp.formHeadersReplaceTip')
            }}
            <el-button
              v-if="headersPreserved"
              link
              type="primary"
              @click="headersPreserved = false"
            >
              {{ t('ai.mcp.formHeadersReplace') }}
            </el-button>
          </div>
        </el-form-item>
      </template>
      <el-form-item :label="t('ai.mcp.formTimeout')">
        <el-input-number
          v-model="formData.timeoutSeconds"
          :min="1"
          :precision="0"
          :placeholder="t('ai.mcp.formTimeoutPlaceholder')"
          class="!w-full"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item :label="t('ai.mcp.formWhitelist')">
        <el-select
          v-model="formData.allowedTools"
          multiple
          filterable
          allow-create
          default-first-option
          :placeholder="t('ai.mcp.formWhitelistPlaceholder')"
          class="!w-full"
        >
          <el-option v-for="tool in availableToolOptions" :key="tool" :label="tool" :value="tool" />
        </el-select>
        <div class="text-12px text-gray-400 leading-20px mt-2px">
          {{ t('ai.mcp.formWhitelistTip') }}
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">
        {{ t('common.ok') }}
      </el-button>
      <el-button @click="formVisible = false">{{ t('common.cancel') }}</el-button>
    </template>
  </el-dialog>

  <!-- 探测结果 -->
  <el-dialog v-model="probeVisible" :title="t('ai.mcp.probeResultTitle')" width="640px">
    <el-result
      v-if="probeResult"
      :icon="probeResult.success ? 'success' : 'error'"
      :title="probeResult.message"
      :sub-title="t('ai.mcp.probeElapsed', { ms: probeResult.elapsedMs })"
    />
    <el-table
      v-if="probeResult && probeResult.tools.length"
      :data="probeResult.tools"
      max-height="360"
    >
      <el-table-column
        :label="t('ai.mcp.toolName')"
        prop="name"
        width="200"
        show-overflow-tooltip
      />
      <el-table-column
        :label="t('ai.mcp.toolDescription')"
        prop="description"
        min-width="300"
        show-overflow-tooltip
      />
    </el-table>
    <template #footer>
      <el-button @click="probeVisible = false">{{ t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createMcpServer,
  updateMcpServer,
  updateMcpServerStatus,
  deleteMcpServer,
  getMcpServerPage,
  probeMcpServer,
  type McpServerVO,
  type McpServerForm,
  type McpTransport,
  type McpProbeResultVO
} from '@/api/ai/mcpserver'

defineOptions({ name: 'AiMcpServer' })

const { t } = useI18n()

const transportLabel = (transport: McpTransport) =>
  transport === 'STREAMABLE_HTTP' ? 'Streamable HTTP' : transport

// ---------- 列表 ----------
const loading = ref(true)
const list = ref<McpServerVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined as string | undefined,
  transport: undefined as string | undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getMcpServerPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryParams.name = undefined
  queryParams.transport = undefined
  queryParams.pageNo = 1
  getList()
}

getList()

// ---------- 表单 ----------
const formVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const editing = ref(false)
/** 编辑态认证头保留标志：true = 不回传（保留原值） */
const headersPreserved = ref(false)
/** 认证头 JSON 文本（{ "Authorization": "Bearer xxx" }） */
const headersText = ref('')
/** stdio 环境变量 JSON 文本 */
const envText = ref('')
/** 编辑行的探测缓存工具名（白名单候选） */
const editingAvailableTools = ref<string[]>([])
const formData = reactive<McpServerForm>({
  name: '',
  transport: 'STREAMABLE_HTTP',
  endpoint: '',
  command: '',
  args: [],
  timeoutSeconds: undefined,
  allowedTools: []
})

const formTitle = computed(() =>
  editing.value ? t('ai.mcp.formTitleEdit') : t('ai.mcp.formTitleCreate')
)
const availableToolOptions = computed(() => editingAvailableTools.value)

const formRules = {
  name: [{ required: true, message: t('ai.mcp.nameRequired'), trigger: 'blur' }],
  transport: [{ required: true, message: t('ai.mcp.transportRequired'), trigger: 'change' }],
  endpoint: [
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (formData.transport !== 'STDIO' && !value) {
          callback(new Error(t('ai.mcp.endpointRequired')))
          return
        }
        if (value && !/^https?:\/\//.test(value)) {
          callback(new Error(t('ai.mcp.endpointPattern')))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  command: [
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (formData.transport === 'STDIO' && !value) {
          callback(new Error(t('ai.mcp.commandRequired')))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const openForm = async (row?: McpServerVO) => {
  editing.value = !!row
  headersPreserved.value = !!row?.headersConfigured
  headersText.value = ''
  envText.value = ''
  editingAvailableTools.value = row?.availableTools || []
  Object.assign(formData, {
    id: row?.id,
    name: row?.name || '',
    transport: row?.transport || 'STREAMABLE_HTTP',
    endpoint: row?.endpoint || '',
    command: row?.command || '',
    args: row?.args ? [...row.args] : [],
    timeoutSeconds: row?.timeoutSeconds ?? undefined,
    allowedTools: row?.allowedTools ? [...row.allowedTools] : []
  })
  formVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

/** 传输类型切换：互斥字段清空 */
const onTransportChange = () => {
  if (formData.transport === 'STDIO') {
    formData.endpoint = ''
    headersText.value = ''
  } else {
    formData.command = ''
    formData.args = []
    envText.value = ''
  }
}

/** JSON 文本安全解析（Map<string,string>），非法时抛可读错误；空文本返回空对象 */
const parseJsonMap = (text: string, field: string): Record<string, string> => {
  if (!text.trim()) {
    return {}
  }
  try {
    const parsed = JSON.parse(text)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, string>
    }
  } catch {
    // fallthrough
  }
  throw new Error(t('ai.mcp.jsonMapInvalid', { field }))
}

const submitForm = async () => {
  await formRef.value.validate()
  // JSON 文本先行解析（非法时就地提示并中止，不进入提交）
  let headers: Record<string, string> | null | undefined
  let env: Record<string, string> | undefined
  try {
    headers =
      formData.transport === 'STDIO'
        ? undefined
        : headersPreserved.value && editing.value
          ? null // null = 保留原认证头
          : parseJsonMap(headersText.value, t('ai.mcp.formHeaders'))
    env =
      formData.transport === 'STDIO' ? parseJsonMap(envText.value, t('ai.mcp.formEnv')) : undefined
  } catch (ex) {
    ElMessage.error((ex as Error).message)
    return
  }
  formLoading.value = true
  try {
    const data: McpServerForm = {
      ...formData,
      endpoint: formData.endpoint || undefined,
      command: formData.command || undefined,
      headers,
      env
    }
    if (editing.value) {
      await updateMcpServer(data)
      ElMessage.success(t('ai.mcp.updated'))
    } else {
      await createMcpServer(data)
      ElMessage.success(t('ai.mcp.created'))
    }
    formVisible.value = false
    getList()
  } finally {
    formLoading.value = false
  }
}

// ---------- 启停/删除 ----------
const handleStatusChange = async (row: McpServerVO, enabled: boolean) => {
  await updateMcpServerStatus(row.id, enabled)
  row.enabled = enabled
  ElMessage.success(enabled ? t('ai.mcp.enabled') : t('ai.mcp.disabled'))
}

const handleDelete = async (id: number) => {
  await ElMessageBox.confirm(t('ai.mcp.deleteConfirm'), t('common.confirmTitle'), {
    type: 'warning'
  })
  await deleteMcpServer(id)
  ElMessage.success(t('ai.mcp.deleted'))
  getList()
}

// ---------- 探测 ----------
const probeVisible = ref(false)
const probeLoading = ref(false)
const probeResult = ref<McpProbeResultVO>()

const handleProbe = async (row: McpServerVO) => {
  probeLoading.value = true
  probeVisible.value = true
  probeResult.value = undefined
  try {
    probeResult.value = await probeMcpServer(row.id)
    await getList()
  } finally {
    probeLoading.value = false
  }
}
</script>
