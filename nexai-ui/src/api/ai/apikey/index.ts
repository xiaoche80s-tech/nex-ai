import request from '@/config/axios'

/** 租户 API Key 列表项（不含明文/哈希——密文不回传） */
export interface TenantApiKeyVO {
  id: number
  /** Key 名称（展示用） */
  name: string
  /** 识别前缀（展示面，如 nexai-abc123…） */
  keyPrefix: string
  /** 状态（ENABLED/REVOKED，吊销即泄漏止损） */
  status: 'ENABLED' | 'REVOKED'
  /** 规格范围（specCode 白名单，空 = 本租户全部规格） */
  specCodes: string[]
  createTime: Date
}

/** 创建结果（明文 Key 仅此一次返回） */
export interface ApiKeyCreatedVO {
  id: number
  /** 明文 Key（仅此一次返回，请妥善保存） */
  apiKey: string
  keyPrefix: string
}

/** 创建表单 */
export interface ApiKeyCreateForm {
  name: string
  /** 规格范围白名单，空 = 全部 */
  specCodes?: string[]
}

/** 分页查询参数 */
export interface ApiKeyPageParams extends PageParam {
  name?: string
}

// 生成 API Key（明文仅返回一次；密文落库）
export const createApiKey = (data: ApiKeyCreateForm) => {
  return request.post({ url: '/ai/api-key/create', data })
}

// 查询 API Key 分页
export const getApiKeyPage = (params: ApiKeyPageParams) => {
  return request.get({ url: '/ai/api-key/page', params })
}

// 吊销 API Key（泄漏止损，幂等）
export const revokeApiKey = (id: number) => {
  return request.delete({ url: '/ai/api-key/revoke', params: { id } })
}
