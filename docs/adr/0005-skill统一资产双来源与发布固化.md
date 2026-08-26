# Skill 资产统一管理：双来源（在线创建 + Git 同步导入）与发布固化

取代 ADR-0003 的双轨方案（PLATFORM 直连 Git 仓库源）。重审动因：源码核实 `GitSkillRepository` 不支持 commit/tag 固定（仅 branch 粒度），autoSync=true 时每次读都 ls-remote，而 core/harness 两套 skill middleware 每次 `call()` 都读仓库——直连意味着 agent 每轮对话背网络往返且内容复现性弱。同时产品决策去掉 PLATFORM 归属概念，Git 源下沉为租户级资产。

决策四条：

1. **统一 DB 资产**：所有 skill 落 `ai_skill` / `ai_skill_version` / `ai_skill_resources`（资源挂版本级，随版本不可变）；name 归属内唯一（owner_level + tenant_id + owner_user_id + name），**不全局唯一**——多租户各自建同名是真实需求。
2. **Git 是导入源不是运行时源**：`agentscope-extensions-skill-git-repository` 用于管理侧同步导入器；租户经 `ai_skill_git_source` 注册 Git 源（url/branch/skills_root）；同步为**删除重建**（按 git_source_id 定位软删后全量新建）；导入的 skill **只读**（不可编辑、不可追加版本、不可切换版本）；手动删除留屏蔽记录（git_source_id + name），后续同步跳过，防止复活。
3. **发布固化（钉版本）**：规格发布时把挂载技能的全量内容（markdown + resources）固化进 `agent_file` 表，草稿仅存 `skillIds` 轻引用；运行时只读固化副本、不回查技能资产——技能删除、同步重建、版本切换均不影响已发布规格。
4. **镜像导出**：skill 当前生效内容经 `agentscope-extensions-skill-postgresql-repository` 的 `PostgresSkillRepository.save` 镜像进本库 agentscope 官方表（事件驱动：创建/追加版本/切换版本/git 同步后，事务提交后异步执行）；agentscope 表 name 全库唯一与 NexAI 归属内唯一不对齐，由映射表记录 `ai_skill ↔ 实际导出名`（无冲突保真，冲突加 `-{skillId}` 后缀）。

## Considered Options

- PLATFORM 直连 GitSkillRepository（ADR-0003 原案）——**拒绝**：无 commit 固定 + 每轮 ls-remote，复现性与性能双输；PLATFORM 归属概念本身被废弃。
- Git 增量同步（同名追加新版本，保留版本链与 skillId）——**拒绝**：删除重建更简单，且发布固化后 skillId 变化对已发布规格无害；导入只读使本地无修改可被冲掉。
- skill name 全局唯一——**拒绝**：与多租户各自命名的真实需求冲突；agentscope 侧唯一冲突由导出映射表消解。
- 官方 PostgresSkillRepository 做权威存储——**拒绝**：无版本无租户（name 全库 UNIQUE、save 先删后插覆盖无历史），仅用作镜像导出通道。
