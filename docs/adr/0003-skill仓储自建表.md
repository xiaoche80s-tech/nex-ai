# skill 仓储自建 ai_skill 表实现官方接口

agentscope 官方 `PostgresSkillRepository` 的表结构（name 唯一、无 version/tenant/软删列）为单体本地场景设计，无法承载 SaaS 多租户与资产版本化。平台的 skill 管理面自建 `ai_skill` 表族（租户、版本、审计、逻辑删除齐全），在 infrastructure 层实现官方 `AgentSkillRepository` 接口（9 个方法）喂给运行时，不直接复用官方表与其 `createIfNotExist` 自动建表。

## 表结构：内容独立成表 + 资源文件行级子表（2026-08-22 修订）

skill content（SKILL.md 全文 + 附属资源文件）**不内嵌任何调用方表的列**：内容本体单独一张表，资源文件按官方 `agentscope_skill_resources` 惯例行级化。

```
ai_skill             主表：name、description（front matter 解析出的冗余列，列表/重名校验/按名寻址用）、
                     latest_version_no、current_version_no、draft_content_id（NULL = 无草稿）
ai_skill_content     内容表：skill_md（一行 = 一份完整 SKILL.md）；内容行不可变，编辑草稿 = 写新行
ai_skill_resource    资源子表：content_id + path + content（一行 = 一个附属文件），(content_id, path) 唯一
ai_skill_version     版本表：skill_id + version_no + content_id + remark（纯指针，不存内容）
```

草稿与版本共用内容表：草稿经主表 `draft_content_id` 引用；**发布 = 引用转正（零复制）**——同一内容行改由 `ai_skill_version.content_id` 引用，从此不可变。四表均带租户/审计/逻辑删除全套列（TenantBaseDO 同构；资源子表亦然——租户插件对全部非忽略表注入 tenant_id 条件，缺列即 SQL 报错），聚合删除时级联逻辑删，不用物理外键。

## Considered Options

- 自建表实现 `AgentSkillRepository` 接口（选定）
- 沿用官方 `agentscope_skills` 表（租户隔离与版本化无处安放，放弃）
- 内容列内嵌主表/版本表（`draft_skill_md` 双 text 列形态，工单 10 初版——主表被大字段拖累、内容两种存储形态并存，部署前废弃）
- 单一内容表 + 资源 JSON 列（无按文件查询诉求，但既然独立成表则资源行级化对齐官方惯例，弃用 JSON 列）
- 发布时复制内容行给版本（双份相同数据 + 草稿行失效规则复杂，放弃——引用转正零复制）
