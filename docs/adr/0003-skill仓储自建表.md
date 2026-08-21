# skill 仓储自建 ai_skill 表实现官方接口

agentscope 官方 `PostgresSkillRepository` 的表结构（name 唯一、无 version/tenant/软删列）为单体本地场景设计，无法承载 SaaS 多租户与资产版本化。平台的 skill 管理面自建 `ai_skill` 表（租户、版本、审计、逻辑删除齐全），在 infrastructure 层实现官方 `AgentSkillRepository` 接口（9 个方法）喂给运行时，不直接复用官方表与其 `createIfNotExist` 自动建表。

## Considered Options

- 自建表实现 `AgentSkillRepository` 接口（选定）
- 沿用官方 `agentscope_skills` 表（租户隔离与版本化无处安放，放弃）
