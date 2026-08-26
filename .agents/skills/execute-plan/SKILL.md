---
name: execute-plan
description: 执行 docs/plans/ 下的实施计划（writing-plans 产物）：executing-plans 的过程编排 + implement 的质量纪律，并在完成时自动维护 .scratch 工单文件状态（Status: resolved + 验收 checkbox 全勾 + Comments 实现评论）。用户说「执行计划」「按计划开工」「跑工单 NN 的计划」「执行实施计划」时使用——即使没提到工单状态也要触发。
---

# Execute Plan（计划执行 × 质量纪律 × 工单联动）

执行一份已写好的实施计划。本 skill 是三个来源的整合：

- **过程编排**（来自 executing-plans）：加载计划 → 批判性评审 → 逐任务逐步骤严格执行 → 卡住即停；
- **质量纪律**（来自 implement）：TDD 优先、单文件测试常跑、类型检查定期跑、完成后 code-review、逐任务 commit；
- **工单联动**（本仓库约定，见 docs/agents/issue-tracker.md）：计划通常对应 `.scratch/<feature>/issues/NN-*.md` 一张工单，完成时更新其状态。

开始时宣布：「我正在使用 execute-plan skill 执行这份实施计划。」

## 第一步：定位计划与工单

1. 读计划文件（用户给路径，或从 `docs/plans/` 按工单号/主题匹配）。
2. 定位关联工单：优先取计划头部的 `**Ticket:**` 字段；没有则从计划正文中找工单文件引用（`.scratch/.../issues/NN-*.md`）；再没有就问用户「这次执行对应哪张工单」。
3. 同时读计划 `**Spec:**` 字段指向的设计文档——计划从设计出发，两者都要读。

## 第二步：加载与批判性评审

1. 通读计划全部任务与步骤，对照设计文档与仓库现状找矛盾、缺口、过时引用。
2. 有疑虑：先向用户提出，达成一致再开工（必要时回 writing-plans 修订计划）。
3. 无疑虑：为每个计划任务建 todo，开始执行。

## 第三步：逐任务执行

对每个任务：

1. todo 置 in_progress。
2. **严格按计划步骤执行**——步骤是 bite-sized 的（写失败测试 → 跑确认失败 → 最小实现 → 跑通过 → commit），不跳步、不合并、不即兴发挥。计划里给了代码就照写；发现计划代码与仓库现状冲突时停下问，不自行改道。
3. 质量纪律叠加在步骤节奏内：
   - 测试先行：计划步骤本身已是 TDD 节奏，保持不变形；
   - 单文件测试常跑（`mvn -pl <module> test -Dtest=XxxTest`），别攒到最后；
   - 前端改动跑 `pnpm ts:check`；
   - 每任务末尾按计划给的 commit 步骤提交。
4. 任务完成：todo 置 completed，并**勾选计划文件中该任务的步骤 checkbox**（`- [ ]` → `- [x]`，用 Edit 改计划文件，留执行痕迹）。

**卡停规则**（立即停下向用户求助，不猜）：缺依赖、测试反复失败、指令看不懂、验证连续失败两次以上。卡停时往工单 `## Comments` 追加卡停记录（日期、卡在哪、已完成到哪个任务），`Status:` 保持不动，便于续接。

## 第四步：收尾（全部任务完成后，按序做四件事）

1. **全量验证**：后端 `mvn -pl <module> test` 全绿；前端 `pnpm ts:check && pnpm lint:eslint`；再执行计划最后的验证任务（如 server clean 构建或真库重放）。
2. **code-review**：调用 `/code-review` skill 审查本次全部改动，修复发现的问题后重跑验证。
3. **更新工单状态**——对关联工单文件一次做完三件套：
   - `**Status:** ready-for-agent` 改为 `**Status:** resolved`（本仓库完成态惯例，对齐工单 23/24/25）；
   - 验收 checkbox 全部勾选（原验收项文字不动，只把 `[ ]` 改 `[x]`）；
   - `## Comments` 下追加实现评论：

     ```markdown
     2026-08-XX 实现完成：
     - （关键决策与落点，2-6 条，从计划任务的 commit 与设计决策表提炼）
     - （验证结果：H2 全绿 / PG 集成 / 构建产物——如实记录，跑不了的写明未跑）
     ```
4. **分支收尾**：宣布使用 superpowers:finishing-a-development-branch skill 并遵循它（验证测试、给出选项、执行选择）。

## 原则

- **计划是契约**：严格执行；要改计划先回 writing-plans 修订，不在执行中悄悄偏离。
- **工单验收项是终态断言**：只在全部完成并验证通过后统一勾选，不在执行中途逐项勾——过程痕迹记在计划文件的步骤 checkbox。
- **工单只碰三处**：Status 行、验收 checkbox、Comments 追加；其余内容（What to build / Blocked by 等）是历史，不改。
