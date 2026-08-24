import request from '@/config/axios'

/** 平台工具库条目（@Tool 注册的业务工具，规格挂载编辑面候选列表） */
export interface PlatformToolVO {
  /** 稳定注册编号（挂载引用 ToolMount.sourceId 的语义） */
  id: number
  code: string
  name: string
  description: string | null
  /** 条目提供的工具名集合（挂载白名单候选面） */
  toolNames: string[]
}

// 获得平台工具库条目列表
export const getPlatformToolList = () => {
  return request.get<PlatformToolVO[]>({ url: '/ai/platform-tool/list' })
}
