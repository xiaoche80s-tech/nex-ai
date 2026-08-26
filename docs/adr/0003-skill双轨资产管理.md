# Skill 资产双轨管理：租户/用户级走 DB 版本链，平台级走 Git 仓库源

> **状态：已被 [ADR-0005](./0005-skill统一资产双来源与发布固化.md) 取代（2026-08-25）**——PLATFORM 归属概念废弃，Git 源下沉为租户级同步导入源（删除重建、导入只读），全部 skill 统一走 DB 版本链资产管理。

agentscope 的 skill 是 Markdown+YAML 能力包，仓库源仅支持文件系统/Classpath/Git——没有"租户自助管理 skill 资产"的能力。决定双轨：**TENANT/USER 级 skill 以 DB 版本链表管理资产**（管理页上传/编辑/版本/挂载），运行时物化为文件目录供 agentscope 文件仓库源读取；**PLATFORM 级官方 skill 库直连 Git 仓库源**（GitSkillRepository），天然版本化免运维。DB 侧只管资产元数据与版本链，物化层是对 agentscope 接口的适配，不构成重复造轮子。

## Considered Options

- 全走 Git 仓库源——**拒绝**：租户用户不会用 Git，"管理页上传编辑 skill"是对外产品的基本体验。
- 全走 DB+物化——**拒绝**：平台官方 skill 库用 DB 托管是多养一套资产流程，Git 现成且对开发者协作者友好。
