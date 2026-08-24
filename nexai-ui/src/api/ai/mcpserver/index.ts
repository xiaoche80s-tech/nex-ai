import request from '@/config/axios'

/** 传输类型（agentscope McpClientBuilder 三传输直用） */
export type McpTransport = 'STDIO' | 'SSE' | 'STREAMABLE_HTTP'

/** MCP Server 列表项（认证头与 stdio 环境变量不出参） */
export interface McpServerVO {
  id: number
  name: string
  transport: McpTransport
  /** 端点地址（SSE / Streamable HTTP），stdio 传输为 null */
  endpoint: string | null
  /** 启动命令（stdio），HTTP 系传输为 null */
  command: string | null
  args: string[] | null
  /** 是否已配置认证头（只露布尔，不回传凭证） */
  headersConfigured: boolean
  /** 请求超时（秒），null = 运行时默认 */
  timeoutSeconds: number | null
  /** 工具白名单（server 级放行面），空 = 全部工具 */
  allowedTools: string[] | null
  /** 最近探测拉取的工具名清单（信息性缓存） */
  availableTools: string[] | null
  enabled: boolean
  ownerType: string
  createTime: Date
}

/** 探测结果工具条目 */
export interface McpToolVO {
  name: string
  description: string | null
}

/** 连通探测结果（成败 + 工具清单） */
export interface McpProbeResultVO {
  success: boolean
  elapsedMs: number
  message: string
  tools: McpToolVO[]
}

/** 创建/更新命令 */
export interface McpServerForm {
  id?: number
  name: string
  transport: McpTransport
  endpoint?: string
  command?: string
  args?: string[]
  env?: Record<string, string>
  /** null = 保留原认证头（编辑时不回传凭证） */
  headers?: Record<string, string> | null
  timeoutSeconds?: number
  allowedTools?: string[]
}

/** 分页查询参数 */
export interface McpServerPageParams extends PageParam {
  name?: string
  transport?: string
  enabled?: boolean
}

// 注册 MCP Server
export const createMcpServer = (data: McpServerForm) => {
  return request.post({ url: '/ai/mcp-server/create', data })
}

// 更新 MCP Server（headers 为 null 时保留原认证头）
export const updateMcpServer = (data: McpServerForm) => {
  return request.put({ url: '/ai/mcp-server/update', data })
}

// 启用/停用
export const updateMcpServerStatus = (id: number, enabled: boolean) => {
  return request.put({ url: '/ai/mcp-server/update-status', data: { id, enabled } })
}

// 删除
export const deleteMcpServer = (id: number) => {
  return request.delete({ url: '/ai/mcp-server/delete?id=' + id })
}

// 详情
export const getMcpServer = (id: number) => {
  return request.get<McpServerVO>({ url: '/ai/mcp-server/get', params: { id } })
}

// 分页
export const getMcpServerPage = (params: McpServerPageParams) => {
  return request.get<{ list: McpServerVO[]; total: number }>({
    url: '/ai/mcp-server/page',
    params
  })
}

// 连通探测 + 工具清单拉取（成功时回写清单缓存）
export const probeMcpServer = (id: number) => {
  return request.post<McpProbeResultVO>({ url: '/ai/mcp-server/probe', params: { id } })
}
