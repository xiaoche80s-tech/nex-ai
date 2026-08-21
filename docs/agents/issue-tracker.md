# Issue 跟踪器：本地 Markdown

本仓库的工单与 spec 以 markdown 文件形式存放在 `.scratch/` 下（该目录已被 `.gitignore` 排除，不会推送到远程）。

## 约定

- 一个功能一个目录：`.scratch/<feature-slug>/`
- spec 文件：`.scratch/<feature-slug>/spec.md`
- 实施工单每张一个文件：`.scratch/<feature-slug>/issues/<NN>-<slug>.md`，从 `01` 开始编号——绝不合并成单个工单文件
- Triage 状态记录在工单文件顶部的 `Status:` 行（角色字符串见 `triage-labels.md`）
- 评论与讨论历史追加到文件底部 `## Comments` 标题之下

## 当 skill 说"发布到 issue tracker"

在 `.scratch/<feature-slug>/` 下新建文件（目录不存在则创建）。

## 当 skill 说"获取相关工单"

读取所指路径的文件。用户通常会直接给出路径或工单编号。

## Wayfinder 操作（`/wayfinder` 使用）

地图是一个文件，每张工单一个子文件。

- **地图（Map）**：`.scratch/<effort>/map.md` —— Notes / Decisions-so-far / 正文雾区。
- **子工单**：`.scratch/<effort>/issues/NN-<slug>.md`，从 `01` 编号，正文写问题。`Type:` 行记录工单类型（`research`/`prototype`/`grilling`/`task`）；`Status:` 行记录 `claimed`/`resolved`。
- **阻塞（Blocking）**：顶部 `Blocked by: NN, NN` 行。所列文件全部 `resolved` 后本工单解除阻塞。
- **前沿（Frontier）**：扫描 `.scratch/<effort>/issues/` 中开放、未阻塞、未认领的文件，编号最小者优先。
- **认领（Claim）**：动手前先置 `Status: claimed` 并保存。
- **解决（Resolve）**：在 `## Answer` 标题下追加答案，置 `Status: resolved`，并把上下文指针（要点 + 链接）追加到 `map.md` 的 Decisions-so-far。
