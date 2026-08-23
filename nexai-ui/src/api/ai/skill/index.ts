import request from '@/config/axios'

/** Skill 资产列表项 */
export interface SkillVO {
  id: number
  name: string
  description: string
  ownerLevel: string
  ownerUserId: number | null
  currentVersionNo: number | null
  createTime: Date
}

/** Skill 版本列表项 */
export interface SkillVersionVO {
  id: number
  versionNo: number
  note: string | null
  current: boolean
  createTime: Date
}

/** 创建 Skill 命令 */
export interface SkillCreateForm {
  name: string
  description: string
  ownerLevel?: string
  markdown: string
  resources?: Record<string, string>
  note?: string
}

/** 登记版本命令 */
export interface SkillVersionForm {
  skillId: number
  markdown: string
  resources?: Record<string, string>
  note?: string
}

/** Skill 分页查询参数 */
export interface SkillPageParams extends PageParam {
  name?: string
}

// 创建 Skill
export const createSkill = (data: SkillCreateForm) => {
  return request.post({ url: '/ai/skill/create', data })
}

// 登记版本
export const addSkillVersion = (data: SkillVersionForm) => {
  return request.post({ url: '/ai/skill/version', data })
}

// 查询 Skill 分页
export const getSkillPage = (params: SkillPageParams) => {
  return request.get({ url: '/ai/skill/page', params })
}

// 查询版本列表
export const getSkillVersionPage = (skillId: number) => {
  return request.get<SkillVersionVO[]>({ url: '/ai/skill/version-page', params: { skillId } })
}

// 删除 Skill
export const deleteSkill = (id: number) => {
  return request.delete({ url: '/ai/skill/delete?id=' + id })
}
