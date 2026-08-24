<template>
  <Dialog v-model="dialogVisible" :title="formData.id ? t('ai.spec.formTitleEdit') : t('ai.spec.formTitle')" width="680">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="110px"
      v-loading="formLoading"
    >
      <el-divider content-position="left">{{ t('ai.spec.sectionBasic') }}</el-divider>
      <el-form-item :label="t('ai.spec.formName')" prop="name">
        <el-input
          v-model="formData.name"
          :maxlength="64"
          :placeholder="t('ai.spec.formNamePlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('ai.spec.formSpecCode')" prop="specCode">
        <el-input
          v-model="formData.specCode"
          :maxlength="64"
          :disabled="!!formData.id"
          :placeholder="t('ai.spec.formSpecCodePlaceholder')"
        />
        <div class="text-12px text-gray-400 leading-20px mt-2px">{{
          t('ai.spec.formSpecCodeTip')
        }}</div>
      </el-form-item>
      <el-form-item :label="t('ai.spec.formOwnerLevel')" prop="ownerLevel">
        <el-radio-group v-model="formData.ownerLevel" :disabled="!!formData.id">
          <el-radio value="TENANT">{{ t('ai.spec.formOwnerTenant') }}</el-radio>
          <el-radio value="USER">{{ t('ai.spec.formOwnerUser') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="t('ai.spec.formDescription')" prop="description">
        <el-input
          v-model="formData.description"
          :maxlength="1024"
          type="textarea"
          :rows="2"
          show-word-limit
          :placeholder="t('ai.spec.formDescriptionPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('ai.spec.formIcon')" prop="icon">
        <el-input
          v-model="formData.icon"
          :maxlength="128"
          :placeholder="t('ai.spec.formIconPlaceholder')"
        />
      </el-form-item>

      <el-divider content-position="left">{{ t('ai.spec.sectionModel') }}</el-divider>
      <el-form-item :label="t('ai.spec.formModel')" prop="modelId">
        <el-select
          v-model="formData.modelId"
          filterable
          clearable
          :loading="modelsLoading"
          :placeholder="t('ai.spec.formModelPlaceholder')"
          class="!w-full"
        >
          <el-option
            v-for="model in modelOptions"
            :key="model.id"
            :label="model.name"
            :value="model.id"
          >
            <span class="mr-8px">{{ model.name }}</span>
            <span class="text-12px text-gray-400">{{ model.modelId }}</span>
          </el-option>
        </el-select>
        <div class="text-12px text-gray-400 leading-20px mt-2px">{{
          t('ai.spec.formModelTip')
        }}</div>
      </el-form-item>
      <el-form-item :label="t('ai.spec.formSystemPrompt')" prop="systemPrompt">
        <el-input
          v-model="formData.systemPrompt"
          :maxlength="16384"
          type="textarea"
          :rows="6"
          show-word-limit
          :placeholder="t('ai.spec.formSystemPromptPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('ai.spec.formMaxIters')" prop="maxIters">
        <el-input-number
          v-model="formData.maxIters"
          :min="1"
          :precision="0"
          :step="1"
          :placeholder="t('ai.spec.formMaxItersPlaceholder')"
          class="!w-full"
          controls-position="right"
        />
      </el-form-item>

      <el-collapse class="border-none">
        <el-collapse-item name="generateOptions">
          <template #title>
            <span class="text-14px">{{ t('ai.spec.generateOptions') }}</span>
          </template>
          <el-form-item :label="t('ai.spec.formTemperature')" prop="temperature">
            <el-input-number
              v-model="formData.temperature"
              :min="0"
              :max="2"
              :step="0.1"
              :precision="2"
              :placeholder="t('ai.spec.formTemperaturePlaceholder')"
              class="!w-full"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item :label="t('ai.spec.formTopP')" prop="topP">
            <el-input-number
              v-model="formData.topP"
              :min="0"
              :max="1"
              :step="0.05"
              :precision="2"
              :placeholder="t('ai.spec.formTopPPlaceholder')"
              class="!w-full"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item :label="t('ai.spec.formMaxTokens')" prop="maxTokens">
            <el-input-number
              v-model="formData.maxTokens"
              :min="1"
              :step="256"
              :precision="0"
              :placeholder="t('ai.spec.formMaxTokensPlaceholder')"
              class="!w-full"
              controls-position="right"
            />
          </el-form-item>
        </el-collapse-item>
      </el-collapse>

      <el-divider content-position="left">{{ t('ai.spec.sectionMount') }}</el-divider>
      <el-form-item :label="t('ai.spec.formSkills')">
        <el-select
          v-model="formData.skillIds"
          multiple
          filterable
          :loading="skillsLoading"
          :placeholder="t('ai.spec.formSkillsPlaceholder')"
          class="!w-full"
        >
          <el-option
            v-for="skill in skillOptions"
            :key="skill.id"
            :label="skill.name"
            :value="skill.id"
          >
            <span class="mr-8px">{{ skill.name }}</span>
            <span class="text-12px text-gray-400">{{ skill.description }}</span>
          </el-option>
        </el-select>
        <div class="text-12px text-gray-400 leading-20px mt-2px">
          {{ t('ai.spec.formSkillsTip') }}
        </div>
      </el-form-item>
      <el-form-item :label="t('ai.spec.formTools')">
        <div class="w-full">
          <el-button plain type="primary" size="small" class="mb-8px" @click="addToolMount">
            <Icon icon="ep:plus" class="mr-5px" /> {{ t('ai.spec.addToolMount') }}
          </el-button>
          <el-table v-if="(formData.tools || []).length" :data="formData.tools" size="small">
            <el-table-column :label="t('ai.spec.mountSource')" width="150">
              <template #default="{ row }">
                <el-select v-model="row.source" @change="onMountSourceChange(row)">
                  <el-option label="MCP Server" value="MCP" />
                  <el-option :label="t('ai.spec.mountSourcePlatform')" value="PLATFORM" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('ai.spec.mountTarget')" min-width="160">
              <template #default="{ row }">
                <el-select v-model="row.sourceId" filterable :placeholder="t('common.selectText')">
                  <el-option
                    v-for="item in mountTargetOptions(row.source)"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('ai.spec.mountWhitelist')" min-width="180">
              <template #default="{ row }">
                <el-select
                  v-model="row.allowedTools"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  :placeholder="t('ai.spec.mountWhitelistPlaceholder')"
                >
                  <el-option
                    v-for="tool in mountToolOptions(row)"
                    :key="tool"
                    :label="tool"
                    :value="tool"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('ai.spec.mountSensitive')" min-width="180">
              <template #default="{ row }">
                <el-select
                  v-model="row.sensitiveTools"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  :placeholder="t('ai.spec.mountSensitivePlaceholder')"
                >
                  <el-option
                    v-for="tool in row.allowedTools || []"
                    :key="tool"
                    :label="tool"
                    :value="tool"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('table.action')" width="70" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" @click="formData.tools!.splice($index, 1)">
                  {{ t('table.del') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="text-12px text-gray-400 leading-20px mt-2px">
            {{ t('ai.spec.formToolsTip') }}
          </div>
        </div>
      </el-form-item>

      <el-form-item :label="t('ai.spec.formFolders')">
        <div class="w-full">
          <template v-if="formData.workspaceEnabled">
            <el-button
              plain
              type="primary"
              size="small"
              class="mb-8px mr-8px"
              @click="addFolder('ASSET')"
            >
              <Icon icon="ep:folder-add" class="mr-5px" /> {{ t('ai.spec.addAssetFolder') }}
            </el-button>
            <el-button
              plain
              type="primary"
              size="small"
              class="mb-8px"
              @click="addFolder('TOOLSET')"
            >
              <Icon icon="ep:folder-opened" class="mr-5px" /> {{ t('ai.spec.addToolsetFolder') }}
            </el-button>
            <div
              v-for="(folder, folderIndex) in formData.folders || []"
              :key="folderIndex"
              class="border border-gray-200 rounded-4px p-8px mb-8px"
            >
              <div class="flex items-center mb-4px">
                <el-tag
                  size="small"
                  class="mr-8px"
                  :type="folder.type === 'ASSET' ? 'success' : 'warning'"
                >
                  {{
                    folder.type === 'ASSET'
                      ? t('ai.spec.folderTypeAsset')
                      : t('ai.spec.folderTypeToolset')
                  }}
                </el-tag>
                <el-input
                  v-model="folder.name"
                  size="small"
                  class="!w-220px mr-8px"
                  :placeholder="t('ai.spec.folderNamePlaceholder')"
                />
                <el-upload
                  :show-file-list="false"
                  multiple
                  :http-request="(options: any) => uploadFolderFile(folder, options)"
                >
                  <el-button plain size="small" type="primary">
                    <Icon icon="ep:upload" class="mr-5px" /> {{ t('ai.spec.folderUpload') }}
                  </el-button>
                </el-upload>
                <el-button
                  link
                  type="danger"
                  size="small"
                  class="ml-auto"
                  @click="removeFolder(folderIndex)"
                >
                  {{ t('table.del') }}
                </el-button>
              </div>
              <el-table v-if="folder.files.length" :data="folder.files" size="small">
                <el-table-column :label="t('ai.spec.folderFilePath')" min-width="180">
                  <template #default="{ row }">
                    <el-input
                      v-model="row.path"
                      size="small"
                      :placeholder="t('ai.spec.folderFilePath')"
                    />
                  </template>
                </el-table-column>
                <el-table-column prop="size" :label="t('ai.spec.folderFileSize')" width="110">
                  <template #default="{ row }">{{ formatSize(row.size) }}</template>
                </el-table-column>
                <el-table-column :label="t('ai.spec.folderFileHash')" min-width="140">
                  <template #default="{ row }">
                    <span class="text-12px text-gray-400 font-mono"
                      >{{ row.contentHash.slice(0, 12) }}…</span
                    >
                  </template>
                </el-table-column>
                <el-table-column :label="t('table.action')" width="70" align="center">
                  <template #default="{ $index }">
                    <el-button link type="danger" @click="folder.files.splice($index, 1)">
                      {{ t('table.del') }}
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div class="text-12px text-gray-400 leading-20px mt-2px">
              {{ t('ai.spec.formFoldersTip') }}
            </div>
          </template>
          <div v-else class="text-12px text-gray-400 leading-20px">
            {{ t('ai.spec.formFoldersDisabled') }}
          </div>
        </div>
      </el-form-item>

      <el-divider content-position="left">{{ t('ai.spec.sectionEnv') }}</el-divider>
      <el-form-item :label="t('ai.spec.formWorkspace')">
        <el-switch v-model="formData.workspaceEnabled" @change="onWorkspaceToggle" />
        <div class="text-12px text-gray-400 leading-20px mt-2px">
          {{ t('ai.spec.formWorkspaceTip') }}
        </div>
      </el-form-item>
      <template v-if="formData.workspaceEnabled">
        <el-form-item :label="t('ai.spec.formSandbox')">
          <el-switch v-model="formData.sandboxEnabled" @change="onSandboxToggle" />
          <div class="text-12px text-gray-400 leading-20px mt-2px">
            {{ t('ai.spec.formSandboxTip') }}
          </div>
        </el-form-item>
        <el-form-item v-if="formData.sandboxEnabled" :label="t('ai.spec.formCapabilities')">
          <el-checkbox-group v-model="formData.capabilities">
            <el-checkbox value="SHELL">{{ t('ai.spec.capabilityShell') }}</el-checkbox>
            <el-checkbox value="PYTHON">{{ t('ai.spec.capabilityPython') }}</el-checkbox>
            <el-checkbox value="NODE">{{ t('ai.spec.capabilityNode') }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button
        type="primary"
        :disabled="formLoading"
        v-hasPermi="['ai:spec:create']"
        @click="submitForm"
      >
        {{ t('common.ok') }}
      </el-button>
      <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as SpecApi from '@/api/ai/spec'
import * as SkillApi from '@/api/ai/skill'
import * as McpServerApi from '@/api/ai/mcpserver'
import * as PlatformToolApi from '@/api/ai/platformTool'
import * as ChannelApi from '@/api/ai/channel'

defineOptions({ name: 'AiAgentSpecForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const formLoading = ref(false) // 表单的加载中
const formRef = ref() // 表单 Ref

/** 表单初始值（创建）；id 存在即编辑模式 */
const defaultForm = (): SpecApi.AgentSpecCreateForm & { id?: number } => ({
  id: undefined,
  name: '',
  specCode: '',
  ownerLevel: 'TENANT',
  description: '',
  icon: '',
  modelId: undefined,
  systemPrompt: '',
  maxIters: undefined,
  temperature: undefined,
  topP: undefined,
  maxTokens: undefined,
  skillIds: [],
  tools: [],
  folders: [],
  workspaceEnabled: false,
  sandboxEnabled: false,
  capabilities: []
})
const formData = ref(defaultForm())
const formRules = reactive({
  name: [{ required: true, message: t('ai.spec.nameRequired'), trigger: 'blur' }],
  specCode: [
    { required: true, message: t('ai.spec.specCodeRequired'), trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9-]{1,63}$/, message: t('ai.spec.specCodePattern'), trigger: 'blur' }
  ],
  description: [{ max: 1024, message: t('ai.spec.descriptionMax'), trigger: 'blur' }]
})

// ---------- 挂载区候选数据（打开弹窗时加载） ----------
const skillsLoading = ref(false)
const skillOptions = ref<SkillApi.SkillVO[]>([])
const mcpServerOptions = ref<McpServerApi.McpServerVO[]>([])
const platformToolOptions = ref<PlatformToolApi.PlatformToolVO[]>([])
const modelsLoading = ref(false)
const modelOptions = ref<ChannelApi.ModelVO[]>([])

/** 挂载目标候选项（按来源） */
const mountTargetOptions = (source: string): { id: number; name: string }[] =>
  source === 'MCP'
    ? mcpServerOptions.value
        .filter((server) => server.enabled)
        .map((server) => ({ id: server.id, name: server.name }))
    : platformToolOptions.value.map((tool) => ({
        id: tool.id,
        name: `${tool.name}（${tool.code}）`
      }))

/** 白名单候选工具名（MCP = server 探测缓存；平台工具 = 条目工具名） */
const mountToolOptions = (row: SpecApi.ToolMount): string[] => {
  if (row.source === 'MCP') {
    return mcpServerOptions.value.find((server) => server.id === row.sourceId)?.availableTools || []
  }
  return platformToolOptions.value.find((tool) => tool.id === row.sourceId)?.toolNames || []
}

/** 来源切换：互斥字段清空（sourceId 归 0 哨兵，提交时过滤） */
const onMountSourceChange = (row: SpecApi.ToolMount) => {
  row.sourceId = 0
  row.allowedTools = []
  row.sensitiveTools = []
}

const addToolMount = () => {
  formData.value.tools = formData.value.tools || []
  formData.value.tools.push({ source: 'MCP', sourceId: 0, allowedTools: [], sensitiveTools: [] })
}

// ---------- 私有文件夹挂载（工单 18：ASSET 资料 / TOOLSET 工具集） ----------

const addFolder = (type: SpecApi.FolderType) => {
  formData.value.folders = formData.value.folders || []
  formData.value.folders.push({ type, name: '', files: [] })
}

const removeFolder = (index: number) => {
  formData.value.folders?.splice(index, 1)
}

/** 上传文件夹内单个文件：服务端签发凭证（url + 哈希 + 字节数），path 默认取原文件名 */
const uploadFolderFile = async (folder: SpecApi.FolderMount, options: any) => {
  const result = (await SpecApi.uploadFolderFile({
    file: options.file
  })) as SpecApi.FolderFileUploadResult
  folder.files.push({
    path: options.file.name,
    url: result.url,
    contentHash: result.contentHash,
    size: result.size
  })
  message.success(t('ai.spec.folderUploaded'))
}

/** 字节数人类可读化 */
const formatSize = (size: number): string => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

/** 打开弹窗（创建或编辑：编辑时拉详情平铺回填） */
const open = async (id?: number) => {
  formData.value = defaultForm()
  dialogVisible.value = true
  formRef.value?.resetFields()
  loadMountOptions()
  if (id != null) {
    const detail = await SpecApi.getSpec(id)
    formData.value = {
      id: detail.id,
      name: detail.name,
      specCode: detail.specCode,
      ownerLevel: detail.ownerLevel as SpecApi.OwnerLevel,
      description: detail.description ?? '',
      icon: detail.icon ?? '',
      modelId: detail.modelId ?? undefined,
      systemPrompt: detail.systemPrompt ?? '',
      maxIters: detail.maxIters ?? undefined,
      temperature: detail.temperature ?? undefined,
      topP: detail.topP ?? undefined,
      maxTokens: detail.maxTokens ?? undefined,
      skillIds: detail.skillIds ?? [],
      tools: (detail.tools ?? []).map((tool) => ({ ...tool })),
      folders: (detail.folders ?? []).map((folder) => ({ ...folder, files: [...folder.files] })),
      workspaceEnabled: detail.workspaceEnabled ?? false,
      sandboxEnabled: detail.sandboxEnabled ?? false,
      capabilities: detail.capabilities ?? []
    }
  }
}
defineExpose({ open })

/** 挂载区候选加载（技能分页 + MCP Server 分页 + 平台工具列表 + 启用模型分页）。
 *  任一失败时降级为空候选（表单其余部分照常可用），不冒泡为全局错误 */
const loadMountOptions = async () => {
  skillsLoading.value = true
  modelsLoading.value = true
  try {
    const [skillPage, mcpPage, platformTools, modelPage] = await Promise.all([
      SkillApi.getSkillPage({ pageNo: 1, pageSize: 100 }),
      McpServerApi.getMcpServerPage({ pageNo: 1, pageSize: 100 }),
      PlatformToolApi.getPlatformToolList(),
      ChannelApi.getModelPage({ pageNo: 1, pageSize: 100, enabled: true })
    ])
    skillOptions.value = skillPage.list
    mcpServerOptions.value = mcpPage.list
    platformToolOptions.value = platformTools
    modelOptions.value = modelPage.list
  } catch {
    skillOptions.value = []
    mcpServerOptions.value = []
    platformToolOptions.value = []
    modelOptions.value = []
  } finally {
    skillsLoading.value = false
    modelsLoading.value = false
  }
}

/** 执行环境联动：关工作区 → 沙箱、能力与文件夹挂载一并清空（文件夹须物化落 workspace） */
const onWorkspaceToggle = (enabled: boolean | string | number) => {
  if (!enabled) {
    formData.value.sandboxEnabled = false
    formData.value.capabilities = []
    formData.value.folders = []
  }
}
const onSandboxToggle = (enabled: boolean | string | number) => {
  if (!enabled) {
    formData.value.capabilities = []
  }
}

/** 提交表单 */
const emit = defineEmits(['success']) // 成功回调
const submitForm = async () => {
  await formRef.value.validate()
  // 挂载清洗：未选择目标的行（sourceId=0 哨兵）与空白名单语义（空数组 = 全部）保持原样提交
  const tools = (formData.value.tools || []).filter((tool) => tool.sourceId > 0)
  // 文件夹清洗：未命名或无文件的文件夹不提交；目录名合法段校验与后端领域校验同口径
  const folders = (formData.value.folders || []).filter(
    (folder) => folder.name && folder.files.length > 0
  )
  if (folders.some((folder) => !/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(folder.name))) {
    message.error(t('ai.spec.folderNamePattern'))
    return
  }
  formLoading.value = true
  try {
    if (formData.value.id) {
      await SpecApi.updateSpec({ ...formData.value, id: formData.value.id, tools, folders })
      message.success(t('ai.spec.updated'))
    } else {
      await SpecApi.createSpec({ ...formData.value, tools, folders })
      message.success(t('ai.spec.created'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
