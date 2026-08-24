/**
 * 原型共享 mock 数据（throwaway —— 工单 22/23/24/25 的 UI 定案验证）。
 * 本轮升级为 reactive 内存态：新增/编辑/发布/切换在页面内真实流转（刷新即重置，不落库）。
 * 覆盖三态：草稿（从未发布）/ vN 已发布（稳定）/ vN · 编辑中（有未发布草稿）。
 */
import { reactive } from 'vue'

export interface SpecConfig {
  model: string
  temperature: number
  maxIters: number
  systemPrompt: string
  skills: string[]
  tools: string[]
  folders: string[]
  workspace: boolean
  sandbox: boolean
  capabilities: string[]
}

export interface SpecRow {
  id: number
  name: string
  specCode: string
  icon: string
  ownerLevel: 'TENANT' | 'USER'
  ownerUserName: string
  currentVersionNo: number | null
  hasDraft: boolean
  /** 草稿的系统提示（编辑表单改的就是它；无草稿为 null） */
  draftPrompt: string | null
  draftSummary: string | null
  updateTime: string
}

export interface VersionRow {
  versionNo: number
  note: string
  publisher: string
  createTime: string
  config: SpecConfig
}

/** 当前登录者（发布人署名用） */
export const CURRENT_USER = '王管理员'

const configOf = (prompt: string): SpecConfig => ({
  model: 'glm-4.7（智谱 · 主力渠道）',
  temperature: 0.3,
  maxIters: 10,
  systemPrompt: prompt,
  skills: ['faq 检索问答', '退换货政策查询'],
  tools: ['MCP · 知识库检索（放行全部）', 'MCP · 工单系统（敏感：创建工单）'],
  folders: ['ASSET · faq/（3 个文件）'],
  workspace: true,
  sandbox: true,
  capabilities: ['SHELL', 'PYTHON']
})

/** 列表：四种形态各一（reactive：动作真实流转） */
export const specs = reactive<SpecRow[]>([
  {
    id: 1, name: '电商客服助手', specCode: 'ecom-cs-bot', icon: '🤖',
    ownerLevel: 'TENANT', ownerUserName: '—',
    currentVersionNo: 2, hasDraft: true,
    draftPrompt: '你是电商客服助手，擅长退换货与物流查询，正在补会员积分能力（半篇）。',
    draftSummary: '在 v2 基础上增加会员积分查询能力（半篇）',
    updateTime: '2026-08-24 14:32'
  },
  {
    id: 2, name: '数据分析师', specCode: 'data-analyst', icon: '📊',
    ownerLevel: 'TENANT', ownerUserName: '—',
    currentVersionNo: 1, hasDraft: false,
    draftPrompt: null, draftSummary: null,
    updateTime: '2026-08-23 09:10'
  },
  {
    id: 3, name: '文档摘要器', specCode: 'doc-summarizer', icon: '📄',
    ownerLevel: 'USER', ownerUserName: '张三',
    currentVersionNo: null, hasDraft: true,
    draftPrompt: '万字长文提炼三句要点，保留关键数字。',
    draftSummary: '初始草稿：万字长文提炼三句要点',
    updateTime: '2026-08-24 11:05'
  },
  {
    id: 4, name: '代码审查员', specCode: 'code-reviewer', icon: '🔍',
    ownerLevel: 'TENANT', ownerUserName: '—',
    currentVersionNo: 3, hasDraft: true,
    draftPrompt: '你是代码审查员，聚焦安全、性能与架构规范，高危操作须审批。（草稿：调整敏感审批面）',
    draftSummary: '调整敏感工具审批面',
    updateTime: '2026-08-22 18:47'
  }
])

/** 版本历史（含发布人 —— 工单 25；reactive：发布真实追加） */
export const versionsBySpec = reactive<Record<number, VersionRow[]>>({
  1: [
    { versionNo: 1, note: '首版发布', publisher: '王管理员', createTime: '2026-08-20 10:00',
      config: configOf('你是电商客服助手，擅长退换货问题。') },
    { versionNo: 2, note: '扩能力：物流查询', publisher: '王管理员', createTime: '2026-08-22 15:30',
      config: configOf('你是电商客服助手，擅长退换货与物流查询。') }
  ],
  2: [
    { versionNo: 1, note: '首版发布', publisher: '李运营', createTime: '2026-08-23 09:10',
      config: configOf('你是数据分析师，输出带图表建议的分析报告。') }
  ],
  3: [],
  4: [
    { versionNo: 1, note: '首版', publisher: '王管理员', createTime: '2026-08-18 09:00',
      config: configOf('你是代码审查员，聚焦安全与性能。') },
    { versionNo: 2, note: '加 skill：架构规范库', publisher: '赵开发', createTime: '2026-08-20 16:20',
      config: configOf('你是代码审查员，聚焦安全、性能与架构规范。') },
    { versionNo: 3, note: '敏感工具收紧', publisher: '王管理员', createTime: '2026-08-22 18:40',
      config: configOf('你是代码审查员，聚焦安全、性能与架构规范，高危操作须审批。') }
  ]
})

let nextId = 5
export function allocSpecId(): number {
  return nextId++
}

export { configOf }

/** 三态徽章（工单 23） */
export function statusOf(spec: SpecRow): { text: string; type: 'info' | 'success' | 'warning' } {
  if (spec.currentVersionNo == null) return { text: '草稿', type: 'info' }
  if (!spec.hasDraft) return { text: `v${spec.currentVersionNo} 已发布`, type: 'success' }
  return { text: `v${spec.currentVersionNo} · 编辑中`, type: 'warning' }
}

/** 编辑回填来源（工单 22 语义）：有草稿回填草稿，无草稿（稳定态）回填当前生效快照 */
export function fillSourceOf(spec: SpecRow): { label: string; prompt: string } {
  if (spec.hasDraft && spec.draftPrompt != null) {
    return { label: '回填自：草稿', prompt: spec.draftPrompt }
  }
  const cur = (versionsBySpec[spec.id] ?? []).find((v) => v.versionNo === spec.currentVersionNo)
  return { label: `回填自：v${spec.currentVersionNo} 快照`, prompt: cur?.config.systemPrompt ?? '' }
}
