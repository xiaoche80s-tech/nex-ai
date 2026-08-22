import request from '@/config/axios'

/** 技能内容（SKILL.md 全文 + 附属资源文件集，对应后端 SkillContentDTO） */
export interface SkillContent {
  skillMd: string
  /** 附属资源文件集（相对路径 → 文件内容） */
  resources: Record<string, string>
}

/** 技能版本（不可变快照，对应后端 SkillVersionDTO） */
export interface SkillVersionVO {
  id: number
  skillId: number
  versionNo: number
  remark: string | null
  content: SkillContent
  createTime: Date
}

/** 技能列表项（对应后端 SkillDTO） */
export interface SkillVO {
  id: number | undefined
  /** 技能名（SKILL.md front matter 的 name，运行时挂载寻址键） */
  name: string
  description: string
  /** 已发布的最新版本号，从未发布为 0 */
  latestVersionNo: number
  /** 当前默认版本号（运行时读取的版本），从未发布为 null */
  currentVersionNo: number | null
  hasDraft: boolean
  createTime: Date
  /** 前端行内状态：发布按钮的加载中 */
  publishing?: boolean
}

/** 技能详情（对应后端 SkillDetailDTO） */
export interface SkillDetailVO extends SkillVO {
  /** 草稿内容，无草稿为 null（编辑表单优先按此预填） */
  draft: SkillContent | null
  /** 当前默认版本快照，从未发布为 null（无草稿时编辑表单按此预填） */
  currentVersion: SkillVersionVO | null
}

/** 技能分页查询参数 */
export interface SkillPageParams extends PageParam {
  name?: string
}

/** 技能创建/编辑表单（SKILL.md 全文 + 资源文件集；name/description 由服务端解析校验） */
export interface SkillSaveForm {
  id?: number
  skillMd: string
  resources?: Record<string, string>
}

// 创建技能（携带首个草稿）
export const createSkill = (data: SkillSaveForm) => {
  return request.post({ url: '/ai/skill/create', data })
}

// 编辑技能（SKILL.md 全文覆盖草稿；发布后再编辑即生成新草稿）
export const updateSkill = (data: SkillSaveForm) => {
  return request.put({ url: '/ai/skill/update', data })
}

// 发布技能：草稿固化为不可变新版本，返回新版本号
export const publishSkill = (data: { id: number; remark?: string }) => {
  return request.post<number>({ url: '/ai/skill/publish', data })
}

// 切换默认版本（回滚/迭代入口，运行时读取随之切换）
export const switchDefaultVersion = (data: { id: number; versionNo: number }) => {
  return request.put({ url: '/ai/skill/switch-default-version', data })
}

// 删除技能及其全部版本
export const deleteSkill = (id: number) => {
  return request.delete({ url: '/ai/skill/delete?id=' + id })
}

// 查询技能分页
export const getSkillPage = (params: SkillPageParams) => {
  return request.get({ url: '/ai/skill/page', params })
}

// 查询技能详情（含草稿与当前默认版本快照）
export const getSkill = (id: number) => {
  return request.get<SkillDetailVO>({ url: '/ai/skill/get?id=' + id })
}

// 查询版本历史（按版本号倒序，含各版本 SKILL.md 与资源快照）
export const getVersionList = (skillId: number) => {
  return request.get<SkillVersionVO[]>({ url: '/ai/skill/version/list?skillId=' + skillId })
}
