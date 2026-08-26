# AgentScope Java v2 Harness 层 Skill 加载链路调研：DynamicSkillMiddleware 与双途径（Git / PostgreSQL）接线

> 调研日期：2026-08-25
> 调研对象：`/Users/jerry/agent-project/agentscope-java/`（AgentScope Java v2 源码）
> 调研方法：只依据一手源码逐文件精读；所有关键论断附文件路径与行号。
> 定位：本篇是 `2026-08-25-agentscope-skill-管理三种存储与同步机制.md`（下称"前篇"）的补篇。前篇覆盖 SPI / 六实现 / core 基本链路总览；本篇聚焦 **harness 层（HarnessAgent）的组装细节、Git 与 PG 双途径的真实接线方式、每轮加载时序**，不重复前篇内容。

---

## 0. 结论速览（TL;DR）

1. **HarnessAgent 不用 core 的 `DynamicSkillMiddleware`**：`HarnessAgent.Builder.build()` 合成完仓库列表后调用 `inner.dynamicSkillsEnabled(false)` 显式关闭 core 路径，改装 harness 原生的 `HarnessSkillMiddleware`（`HarnessAgent.java:2762-2781`，注释原话 "Harness owns both the live and frozen repository paths"）。两套 middleware 行为相似但**不等价**：core 版有内容签名短路，harness 版**没有**——每轮全量读仓库 + 全量物化（靠文件级 SHA-256 去重兜底写盘）。
2. **四层合成**（低 → 高优先级，同名后者覆盖）：① `projectGlobalSkillsDir` → `FileSystemSkillRepository`；② builder 传入的任意仓库（**Git/PG/Nacos/MySQL/Classpath 全在这层**，按注册顺序叠加）；③ `workspace/skills/` → `FileSystemSkillRepository`；④ per-user 文件系统视图 → `WorkspaceSkillRepository`（`HarnessAgentBuilderSupport.java:803-852`）。
3. **per-user 隔离的实现**是"`RuntimeContextSkillRepository.getAllSkills(ctx)` 用请求级上下文 + 文件系统实现的命名空间路由"两级配合：`HarnessSkillMiddleware` 对实现 `RuntimeContextSkillRepository` 的仓库改调 `getAllSkills(ctx)`（`HarnessSkillMiddleware.java:311-314`）；`WorkspaceSkillRepository` 把 `ctx` 透传给 `AbstractFilesystem`，由实现按 `rc.getUserId()` 解析命名空间（如 `agents/<agentId>/users/<uid>`，`RemoteFilesystemSpec.java:298-316`）。并发会话隔离有专门测试佐证。
4. **Git 途径有三个接线入口**：纯代码 `builder.skillRepository(new GitSkillRepository(...))`；agentscope-service 的 `agentscope.json` 声明式配置（`SkillRepositorySupport` 反射构造，仅 `type: git|filesystem`）；paw/codingagent/dataagent 三个 example 的 bootstrap（各自拷贝了同一份 `SkillRepositorySupport`）。**clone 惰性发生在第一次读操作**；此后每次读先 `ls-remote` 比对远程 HEAD，变了才 pull。
5. **PG 途径没有任何声明式或框架级接线入口**：全仓 grep 确认 `PostgresSkillRepository` 在 harness/service/examples 中零引用，声明式配置 switch 只认 `filesystem|git`。**PG 仓库只能纯代码 new 出来挂到 `builder.skillRepository()`（Layer 2）**。service 生态中 PG 的角色是 BaseStore/StateStore/DistributedStore（`agentscope-extensions-postgresql`），不是 skill 仓库；service 自己的 skill 存储走 `BaseStoreDefinitionStore`（KV 里存 `skills/<name>/SKILL.md` 文本），与 `PostgresSkillRepository` 无关。
6. **双途径同挂**：都在 Layer 2，按 `skillRepository()` 调用顺序决定同名覆盖（后注册的高）；harness 层每轮对每个仓库都做一次 `getAllSkills()`（Git = ls-remote 网络往返，PG = 全量 SELECT），然后统一物化到 `<wsRoot>/.skills-cache/<source>/<name>/`——source 字段（`git-owner/repo@branch`、`postgresql_<schema>_<table>`）直接成为物化目录名，两途径天然分区互不干扰。
7. **对 NexAI 最关键的事实**：若用 HarnessAgent 做底座，可见性过滤要走 harness 的 `SkillVisibilityFilter` 构造参数（而非继承 core 的 `filterVisible`）；PG 仓库必须由 NexAI 的 Spring 装配层自己构造（这正好契合按租户实例化的需求）；harness 每轮全量读意味着 repository 适配器内部自建"租户级版本号缓存"是必要的性能手段。

---

## 1. core 层：DynamicSkillMiddleware 的加载机制（基础）

### 1.1 安装时机与 dynamicSkillsEnabled 开关

`ReActAgent.Builder` 持有 `List<AgentSkillRepository> skillRepositories`（可重复 `skillRepository(repo)` 追加，`ReActAgent.java:4937-4951`，Javadoc 原话 "Multiple calls append in order from low to high priority — when two repositories expose a skill with the same name, the later (higher priority) entry wins"；`skillRepositories(List)` 则整体替换，:4957-4967）。

`build()` 时安装（`ReActAgent.java:5174-5216`）：

```java
if (!skillRepositories.isEmpty() && dynamicSkillsEnabled) {
    middlewares.add(new DynamicSkillMiddleware(
            List.copyOf(skillRepositories),
            agentToolkit,                          // 注意：toolkit.copy() 之后的新实例
            skillFilter != null ? skillFilter : SkillFilter.all(),
            skillCodeExecutionEnabled,
            skillWorkDir));
}
```

（`ReActAgent.java:5208-5216`）

- `dynamicSkillsEnabled` 默认 `true`（字段声明 :4468）；置 `false` 的 Javadoc 明确是为"外部编排器（如 `HarnessAgent`）想装自己的 subclass 或退回静态 SkillBox"预留（:4978-4984）——**HarnessAgent 正是走这条路**（见第 2 节）。
- 注意安装发生在 `Toolkit agentToolkit = this.toolkit.copy()`（:5176）之后，middleware 直接持有 copy 后的 toolkit；早于 copy 注册的外部 middleware 若实现 `ToolkitAware` 会被 `rebindToolkit` 重绑（:5180-5184）。
- 若用户走 deprecated 的 `skillBox(...)`（:4931），则走 `configureSkillBox` 静态装配（:5159-5166），与 repository 路径互斥并存（两者都设时 middleware 追加在后）。

### 1.2 每轮加载时序（逐方法）

```
agent.call(msg)
 └─ middleware 链 → DynamicSkillMiddleware#onSystemPrompt(agent, ctx, currentPrompt)
     │  agentscope-core/.../skill/DynamicSkillMiddleware.java:129-143
     ├─ reloadSkills(rc)                                    // :131 → :166-256
     │   ├─ repositories 为空 → 清空 currentSkillBox/lastSignature，return    // :167-171
     │   ├─ for repo : repositories                          // :173
     │   │    try { skills = repo.getAllSkills(); }           // :176 ← 每仓库调用点
     │   │    catch (Exception e) { log.warn(...); continue; } // :177-183 ← 单仓库失败仅 WARN 跳过
     │   │    skills == null → continue                       // :184-186
     │   ├─ 合并：skillsByName.put(skill.getName(), skill)     // :187-192（LinkedHashMap，同名后者覆盖）
     │   ├─ 合并结果为空 → 清空 return                         // :194-198
     │   ├─ visible = filterVisible(merged, ctx)              // :201-211（钩子异常 → pass-through）
     │   ├─ visible 为空 → 清空 return                         // :212-216
     │   ├─ signature = computeSignature(visible)             // :222
     │   │    与 lastSignature 相同且 currentSkillBox != null → return  // :223-225（短路）
     │   └─ new SkillBox(toolkit) → setWorkDir(稳定目录) → 逐个 registerSkill
     │        → bindToolkit + registerSkillLoadTool（异常 WARN，:234-241）
     │        → uploadSkillFiles（若 autoUpload，:242-248）
     │        → currentSkillBox = box; lastSignature = signature  // :254-255
     └─ currentSkillBox.getSkillPrompt(resolveFilter(rc)) 追加到 system prompt   // :135-142
```

`resolveFilter`：builder 时 filter 与 `RuntimeContext` 上的 `SkillFilter` overlay 合并（:161-164）。稳定工作目录 `ensureStableWorkDir` 首次 mkdtemp（`agentscope-skill-workdir-`）并注册 shutdown hook 清理（:263-293）。

**单仓库失败语义**：`getAllSkills()` 抛任何 `Exception` 只 WARN 后 continue——一个仓库挂掉不阻塞其他仓库与整轮对话，但该轮该仓库的 skill 全部不可见（下一轮 call 会重试）。

### 1.3 合并与去重：后注册覆盖

`DynamicSkillMiddleware.java:187-192`：

```java
for (AgentSkill skill : skills) {
    if (skill == null || skill.getName() == null) continue;
    skillsByName.put(skill.getName(), skill);   // LinkedHashMap：同名 key，后 put 覆盖
}
```

repository 列表顺序 = builder 注册顺序 = 优先级从低到高（构造器 Javadoc :93 "compose-ordered list (low-to-high priority); merged by skill name"）。类 Javadoc 同样声明（:46-47）。**没有任何告警或冲突计数——同名覆盖完全静默**。

### 1.4 内容签名短路：computeSignature

`DynamicSkillMiddleware.java:314-357`。SHA-256 的哈希输入，按 skill name 排序（`TreeSet`，屏蔽 map 迭代序）后逐 skill 依次 update：

- `name`（UTF-8 字节）+ 分隔字节 `0x00`；
- `skillContent` 全文（SKILL.md 正文）+ `0x00`；
- 排序后的每个 resource `key` + `value` 全文 + `0x00`（:335-343）；
- `originDir` 的字符串形式（若有）+ 结束字节 `0x01`（:344-346）。

短路条件：`signature.equals(lastSignature) && currentSkillBox != null`（:223-225）。**跳过的**：`SkillBox` 重建、逐 skill `registerSkill`、`bindToolkit`、`registerSkillLoadTool`、`uploadSkillFiles`（含文件上传 IO）。**不跳过的**：所有仓库的 `getAllSkills()` 本身——它在短路判断**之前**已经执行完（:173-193）。也就是说：

- 对 `GitSkillRepository`：每轮 call 仍会走 `ensureAutoSynced()` 的 `ls-remote` 网络往返（见 4.1）；
- 对 `PostgresSkillRepository`：每轮 call 仍是全量 `SELECT`。

签名短路省的是"重建与上传"成本，不是"读取"成本。注释原话（:218-221）："if the (sorted) merged view hashes to the same value as the previous call AND we already have a SkillBox, skip the entire rebuild + upload."

### 1.5 filterVisible 钩子

`DynamicSkillMiddleware.java:157-159`：

```java
protected List<AgentSkill> filterVisible(List<AgentSkill> raw, RuntimeContext ctx) {
    return raw;
}
```

- 默认行为：原样返回（Javadoc :150 "Default implementation returns raw unchanged"）。
- 子类能做：按 `RuntimeContext` 做**金丝雀发布、白名单、环境门禁**等请求级可见性裁剪（Javadoc :146-149 明确列举）；入参是"已合并去重、低到高优先序"的列表，返回空列表则本轮不更新 skill prompt（:212-216）。
- 调用时机：每 call 一次、合并之后、签名计算与 SkillBox 重建之前；钩子自身抛异常按 pass-through 处理（:205-211）。
- **注意**：HarnessAgent 走 harness 原生 middleware，这个钩子在 harness 路径上不会被调用——harness 对应物是 `SkillVisibilityFilter`（见 2.2）。harness 侧另有现成实现 `CanaryFilter` / `AllowListFilter` / `EnvironmentFilter`（`agentscope-harness/.../skill/curator/`），前篇 8.4 节已列。

---

## 2. HarnessAgent 的 skill 仓库组装（重点）

### 2.1 四层合成 composeSkillRepositories

调用点：`HarnessAgent.Builder.build()` 内（`HarnessAgent.java:2622-2632`）：

```java
Supplier<RuntimeContext> currentRcSupplier = () -> {
    ReActAgent self = selfRef.get();
    RuntimeContext rc = self != null ? self.getRuntimeContext() : null;
    return rc != null ? rc : RuntimeContext.empty();
};
List<AgentSkillRepository> orderedSkillRepos =
        HarnessAgentBuilderSupport.composeSkillRepositories(this, wsManager, filesystem, currentRcSupplier);
```

合成实现（`HarnessAgentBuilderSupport.java:803-852`），逐层：

| 层 | 来源 | 构造的 repository | 行号 |
| --- | --- | --- | --- |
| 1（最低） | `b.projectGlobalSkillsDir`（须已存在目录） | `new FileSystemSkillRepository(b.projectGlobalSkillsDir)` | :810-820 |
| 2 | `b.skillRepositories`（builder 上 `skillRepository(...)` 传入的任意仓库，**Git/PG/Nacos/MySQL/Classpath 全在这层**，按注册顺序原样 `addAll`） | 原样追加 | :822-823 |
| 3 | `wsManager.getSkillsDir()`（`workspace/skills/`，须已存在目录） | `new FileSystemSkillRepository(workspaceSkillsDir)` | :825-836 |
| 4（最高） | `filesystem != null` 且未 `disableDefaultWorkspaceSkills()` | `new WorkspaceSkillRepository(filesystem, "skills", currentRcSupplier, "workspace-namespaced", false)`（只读） | :838-849 |

Layer 1/3 构造失败（目录不存在/IO 异常）只 WARN 跳过，不阻断构建。Layer 2 与其余层叠加的语义就是"同名时 Layer 2 覆盖 Layer 1、被 Layer 3/4 覆盖"——优先级只由列表顺序决定，与仓库类型无关。

builder 侧对应方法（`HarnessAgent.java`）：`skillRepository(AgentSkillRepository)` 追加（:1662-1667，Javadoc "e.g. GitSkillRepository"，可重复）、`skillRepositories(List)` 替换（:1672-1682）、`projectGlobalSkillsDir(Path)`（:1688-1691，Javadoc "layered below marketplace and workspace skills (lowest precedence)"）、`disableDynamicSkills()`（:1973）、`disableDefaultWorkspaceSkills()`（:1984）、`skillFilter(...)`（:2067）。

**skill_manage（自学习）开启时的增补**（`HarnessAgent.java:2639-2676`）：从列表尾部找第一个只读的 `WorkspaceSkillRepository` 替换为 writable 实例（`smConfig.mainDir()`，source `"workspace-writable"`，:2651-2660）；找不到就追加（:2662-2670）；再追加 drafts 仓库（`smConfig.draftsDir()`，source `"workspace-drafts"`，:2671-2676）。

### 2.2 HarnessSkillMiddleware 的安装：harness 关闭 core 路径

`HarnessAgent.java:2725-2792`：

```java
HarnessSkillMiddleware skillMiddleware =
        disableDynamicSkills
                ? HarnessSkillMiddleware.frozen(orderedSkillRepos, agentToolkit, skillFilter,
                        visibilityFilter, stager, shellPolicy)     // 构建时快照仓库视图
                : new HarnessSkillMiddleware(orderedSkillRepos, agentToolkit, skillFilter,
                        visibilityFilter, stager, shellPolicy);     // 每轮实时拉取
inner.middleware(skillMiddleware);

// Harness owns both the live and frozen repository paths.
inner.dynamicSkillsEnabled(false);                                   // ← 关闭 core 的 DynamicSkillMiddleware

// Wire pre-start staging so sandbox projection picks up .skills-cache content
// that MarketplaceStager materialises from database-backed repositories.
if (sandboxLifecycleMw != null && stager != null) {
    sandboxLifecycleMw.setBeforeStartCallback(skillMiddleware::prestageMarketplaceSkills);
}
```

（`HarnessAgent.java:2762-2788`）

要点：

- **`stager` 仅在 `resolvedWorkspace != null` 时创建**（`new MarketplaceStager(resolvedWorkspace)`，:2726-2730），否则 null——即物化完全跳过、`<files-root>` 不渲染（`MarketplaceStager.stage` 的 null workspaceRoot 分支，`MarketplaceStager.java:97-109`）。
- `ShellPathPolicy` 按 filesystem 类型四分支分派（:2732-2760）：`disableShellTool` → `noShell()`；`LocalFilesystemWithShell`（或 Overlay 的 upper 是它）→ `localWithShell(resolvedWorkspace)`；`SandboxBackedFilesystem`/`RoutedSandboxFilesystem`（primary 为 sandbox）→ `sandbox(wsPrefix)`（wsPrefix 取沙箱 clientOptions 的 workspaceRoot，默认 `/workspace`）；其余 → `noShell()`。
- `prestageMarketplaceSkills` 挂为沙箱启动前回调（:2785-2788），保证同一次 `start()` 的 workspace projection 就能带上 `.skills-cache`（注释明确点名 "database-backed repositories"——**这正是 PG 途径 skill 进沙箱的路径**）。
- **frozen 模式**（`disableDynamicSkills()`）：`HarnessSkillMiddleware.frozen(...)` 在构造时用 `RuntimeContext.empty()` 快照一次合并视图（`HarnessSkillMiddleware.java:141-156、176-180`），此后每轮不再读仓库；filter 与懒资源仍按当轮 ctx。适合"会话开始锁定 skill 集"的场景。

### 2.3 WorkspaceSkillRepository 与 per-user 路由

`WorkspaceSkillRepository`（`agentscope-harness/.../skill/WorkspaceSkillRepository.java`）实现 harness 的 `RuntimeContextSkillRepository`（接口定义 `RuntimeContextSkillRepository.java:24-41`，核心是 `List<AgentSkill> getAllSkills(RuntimeContext context)`）+ `LazyResourceCapable`。

**每轮读取如何路由到 per-user 目录**——两级配合：

第一级，`HarnessSkillMiddleware.mergeRepositories`（`HarnessSkillMiddleware.java:306-333`）对实现 `RuntimeContextSkillRepository` 的仓库改用**请求级 ctx**：

```java
skills = repo instanceof RuntimeContextSkillRepository contextRepository
        ? contextRepository.getAllSkills(ctx)     // ← 用当轮 call 的 ctx，而非共享 supplier
        : repo.getAllSkills();
```

第二级，`WorkspaceSkillRepository.getAllSkills(ctx)` 把 ctx 透传给 `AbstractFilesystem`（:169-177）：

```java
glob = filesystem.glob(ctx, SKILL_FILE, skillsRelativeDir);   // skillsRelativeDir = "skills"
// 命中每个 SKILL.md 后：只读正文，resources 刻意留空（懒加载）
skills.add(SkillUtil.createFrom(rr.fileData().content(), null, source));   // :199
```

`AbstractFilesystem` 是接口，**每个操作都带 `RuntimeContext`**（`AbstractFilesystem.java:40-42`："Every operation accepts a RuntimeContext so stores can scope work to the current session, user, or sandbox"）。实际 per-user 前缀由文件系统实现/配置决定：

- **remote（共享 KV 存储）**：`RemoteFilesystemSpec.storeNamespace`（`RemoteFilesystemSpec.java:298-316`）按 `IsolationScope` 出命名空间元组——`SESSION → agents/<agentId>/sessions/<sid>`、**`USER → agents/<agentId>/users/<uid>`（uid 取 `rc.getUserId()`，空则 `anonymousUserId`）**、`AGENT → agents/<agentId>/shared`、`GLOBAL → global`；每层经 `NamespaceFactory` 在**每次 store 操作时**重新求值（`NamespaceFactory.java:22-34`），而非构造时固化。
- **local**：`LocalFilesystem` 构造可带 `NamespaceFactory`（`LocalFilesystem.java:86、142-198`），不传则无命名空间。
- **sandbox**：宿主侧用户目录经 workspace projection 注入容器 `/workspace`（文档 `docs/v2/zh/docs/harness/skill.md:172-179`）。

**并发会话隔离的实证**：`HarnessSkillMiddlewareContextIsolationTest`（`agentscope-harness/src/test/java/.../HarnessSkillMiddlewareContextIsolationTest.java:39-69`）构造 alice/bob 两个 ctx（tenant 不同、LocalFilesystem 按 `tenants/<tenant>/` 命名空间），并把共享 `contextSupplier`（模拟 `ReActAgent.activeRc`）指向 bob；断言 alice 的 prompt/catalog 只含 alice 的 skill——**因为 mergeRepositories 用的是请求级 ctx**，共享 activeRc 被并发覆盖也不串扰。

其他细节：删除非破坏（移入 `.archive/<name>-<ts>/`，:453-459）；`hasMetadataAncestor` 过滤 `_drafts/.archive/.audit/.backups/.skills-cache` 等元数据子树（:496-554，含 base 为 `"."`/`""` 时对 `.skills-cache` 的特判）；写路径 `save/delete/writeSkillFile` 等供 `SkillManageTool` 使用（:249-391）。

### 2.4 子 agent 继承

两类 subagent 工厂都做同样的传递（`HarnessAgentBuilderSupport.java`）：

- **general-purpose 子 agent**（`buildGeneralPurposeFactory`，:290-387）：构建时快照 `capturedSkillRepos = List.copyOf(b.skillRepositories)`（:305）与 `capturedProjectGlobalSkillsDir`（:306）；spawn 时 `sub.skillRepositories(capturedSkillRepos)`（:362）+ `sub.projectGlobalSkillsDir(capturedProjectGlobalSkillsDir)`（:363-365）。子 agent 与父共享 workspace（:342 `workspace(workspace)`）。
- **声明式子 agent**（`buildDeclaredFactory`，:392-522）：同样传递（:427-428、:509-512）；另支持声明里的 skills allowlist → `sub.skillFilter(SkillFilter.only(...))`（:514-517）。

继承语义：**传的是 Layer 1（全局目录配置）与 Layer 2（市场仓库列表）**；子 agent 自己 build 时会重跑 `composeSkillRepositories`，Layer 3/4 基于子 agent 的 workspace/filesystem 重建。注意这也意味着**每 spawn 一个子 agent，市场仓库（Git/PG）会被独立地再读一遍**。

### 2.5 MarketplaceStager 物化

（`agentscope-harness/.../skill/runtime/MarketplaceStager.java`）

- **触发时机**（两处）：
  1. 每轮 `onSystemPrompt` 里 `stager.stage(enabled, sourceNamespaces)`（`HarnessSkillMiddleware.java:241-242`）；
  2. 沙箱启动前 `prestageMarketplaceSkills(ctx)`（:202-218）经 `beforeStartCallback` 调用（`HarnessAgent.java:2785-2788`）——内部同样走"合并 → 可见性过滤 → skillFilter → stage"，幂等（内容哈希护栏，:196-199 Javadoc）。
- **目录布局**：`<wsRoot>/.skills-cache/<source-ns>/<skill-name>/`（`CACHE_DIR = ".skills-cache"` :67；路径拼接 :111、135）。**`source-ns` 直接用 `repo.getSource()`**（空则 `"_global"` :68；:127-133）——Git 仓库的 `git-owner/repo@branch`、PG 仓库的 `postgresql_<schema>_<table>` 原样成为目录名，双途径天然分区。
- **source 冲突去重**：`resolveSourceNamespaces`（:372-407）发现两个仓库 `getSource()` 相同时，第二个起解析为 `<source>_<idx>` 并 WARN——物化路径与 skill-id 不会撞。
- **SHA-256 去重**：`writeIfChanged`（:201-214）——目标文件存在时读回现有字节，`sha256(existing) == sha256(bytes)` 则跳过写；否则写并恢复执行位。注意比对要**读回整个现有文件**，省的是写与后续投影 hydrate，不是读。
- **孤儿清理**：两层——skill 目录内不再发布的文件被 `removeUnexpected` 删除并剪空目录（:269-298）；整轮结束后 `garbageCollectOrphans` 删掉白名单外（下架 skill / 被移除仓库）的 `<ns>/<skill>` 目录与空 ns 目录（:300-328）。白名单每轮重建，stager 自身无跨轮状态（类 Javadoc :53-55）。
- **+x 执行位恢复**：`maybeMarkExecutable`（:220-248）——shebang（`#!` 起始）或已知脚本后缀（`.sh/.bash/.zsh/.ksh/.py/.rb/.pl/.js/.mjs`，:251-252）触发；按 `chmod +x` 语义只给已有读权限的位加执行（640→750）；Windows 非 POSIX 静默 no-op。原因：入库时资源被转成字符串，POSIX 权限丢失（:210-212 注释）。
- **WorkspaceNative 跳过**：来源是 `WorkspaceSkillRepository` 的 skill 不物化（本就在 `skills/` 下，:121-124）；路径安全拒绝绝对路径与 `..`（:183-191）；`base64:` 前缀解码（:344-349）。

### 2.6 SkillRuntime / SkillCatalog / SkillLoadTool

- **SkillRuntime**（`SkillRuntime.java:36-154`）：聚合单例 `SkillLoadTool` + `SkillPromptBuilder`。`install(catalog, ctx, toolkit)`（:125-130）把 catalog `context.put(SkillCatalog.class, catalog)` 绑到**当轮 RuntimeContext** 并幂等补注册 load 工具。Javadoc :113-119 原话："The catalog is deliberately stored on context, not on this shared runtime. **This is the authorization boundary** used by SkillLoadTool; a skill omitted from the call's filtered catalog cannot be loaded even while another session exposes it."——**并发会话隔离即靠此实现**。`prepareToolkit`（:94-110）在 middleware 构造时（早于 ReActAgent copy toolkit）把 load 工具注册为 **ungrouped**，保证持久化会话（active-group 列表为空/旧）也能看到它。legacy context-free catalog 引用仅在工具调用完全没有 RuntimeContext 时兜底（:187-193，2.2.0 起 deprecated）。
- **SkillCatalog**（`SkillCatalog.java:30-73`）：不可变目录，`of(List<HarnessSkillEntry>)` / `get(skillId)` / `all()` / `ids()`。`HarnessSkillEntry` 是 record `(AgentSkill skill, SkillResources lazyResources, String filesRoot)`（`HarnessSkillEntry.java:37`）。
- **harness 的 `load_skill_through_path` vs core 的实现差异**（问题 10）：
  - harness 版 `SkillLoadTool`（`SkillLoadTool.java:57` 同名工具）：查找顺序 = `path=="SKILL.md"` 特判（:198-201）→ 内存 `resources` map（:203-207）→ `lazyResources.read(path)` 懒读（`LazyResourceCapable`，即 `WorkspaceSkillRepository` 的按需文件系统读取，:209-215）→ 未找到返回去重后的可用路径清单（:217-218、277-312）。`SKILL.md` 返回**完整文档**（frontmatter + 正文，经 `MarkdownSkillParser.generate` 重组，:242-254，注释说明是为了让 `skill_manage(action=patch)` 的 old_string 对得上）；输出带 `filesRoot`（:239-241）。**没有工具组激活语义**。
  - core 版 `SkillToolFactory#createSkillAccessToolAgentTool`（`SkillToolFactory.java:79`，描述 "Load **and activate** a skill"）：每次成功加载都 `activateSkill(skillId)`（:177、184、196）→ 激活 skill 绑定的 `SkillToolGroup`（:370-387）；资源查找顺序为内存 → `originDir` 磁盘回退（前篇 6.3 已详）。也**没有** catalog/RuntimeContext 授权边界（harness 独有）。

---

## 3. 双途径的真实接线方式（本次核心）

### 3.1 Git 途径：三个入口 + clone/pull 时机

**入口 ① 纯代码**（最直接）：`GitSkillRepositoryExample`（`agentscope-examples/documentation/.../documentation2/skill/GitSkillRepositoryExample.java:91-108`）`new GitSkillRepository(remoteUrl, branch, localPath, source, autoSync)` → `ReActAgent.builder().skillRepository(repo)`。六参构造器签名（`GitSkillRepository.java:239-245`）见前篇 3.2。

**入口 ② agentscope-service 声明式配置**：`agentscope.json` 的 `skillRepositories` 数组 → `SkillRepositoryConfigEntry`（`agentscope-service/service-common/.../config/SkillRepositoryConfigEntry.java:31-72`，字段 `type/path/remoteUrl/branch/skillsRoot/localPath/source/autoSync`）→ `SkillRepositorySupport.createAll/create`（`SkillRepositorySupport.java:43-79`）：

```java
String kind = entry.getType().trim().toLowerCase();
return switch (kind) {
    case TYPE_FILESYSTEM -> createFilesystem(cwd, entry);   // "filesystem"
    case TYPE_GIT -> createGit(cwd, entry);                  // "git"
    default -> { log.warn("Unknown skillRepository type '{}' ..."); yield null; }  // ← 其余类型一律 WARN 丢弃
};
```

`createGit`（:99-138）用**反射**构造：`Class.forName("io.agentscope.core.skill.repository.GitSkillRepository")` 拿六参构造器 `(String, String, Path, String, boolean, String)` 即 `(remoteUrl, branch, localPath, source, autoSync, skillsRoot)` 后 `newInstance`；`localPath` 相对 bootstrap `cwd` 解析（:105-108）；`autoSync` 缺省 true（:109）；classpath 缺 jar 时 `ClassNotFoundException` → WARN "add dependency agentscope-extensions-skill-git-repository" + 返回 null（:129-133）。**支持的 type 只有 `filesystem` 与 `git` 两个**（:32-33）。

**入口 ③ examples 配置驱动装配**（三处，均为 service 版 `SkillRepositorySupport` 的逐字拷贝，同样只支持 `filesystem|git`）：

| example | 装配点 | 形态 |
| --- | --- | --- |
| agentscope-paw | `ClawBootstrap.java:436-438`：`SkillRepositorySupport.create(clawHome, entry)` → `b.skillRepository(repo)` | 单 entry |
| agentscope-codingagent | `CodingBootstrap.java:337-342`：同上（`cwd`） | 单 entry |
| agentscope-dataagent | `DataAgentBootstrap.java:395-397`：`SkillRepositorySupport.createAll(cwd, e.effectiveSkillRepositories())` → `b.skillRepositories(repos)` | 列表 |

（各自的 `SkillRepositorySupport.java` 位于 `claw2/runtime/config/`、`harness/coding/config/`、`dataagent/runtime/config/`，`TYPE_FILESYSTEM/TYPE_GIT` 常量与反射逻辑与 service 版一致。）

**clone 与读时同步**（`GitSkillRepository.java`）：

- 构造函数**不 clone**；clone 惰性发生在**第一次读操作**——`getSkill/getAllSkillNames/getAllSkills/skillExists` 都先 `ensureAutoSynced()`（:279-297）。
- `ensureRepositorySynced()`（`synchronized`，:450-528）：本地目录空 → `cloneRepository()`（:569-587，`CloneCommand.setBranch(branch)`，clone 后记 `lastRemoteRef`）；本地已是 git 仓库 → `hasRemoteUpdates()` 为真才 `pullRepository()`（:467-473）。
- `hasRemoteUpdates()`（:711-720）：`resolveRemoteHead()` 用 `Git.lsRemoteRepository().setHeads(true)` 拉远程分支 ref，取目标分支（branch 为空匹配第一个）的 ObjectId（:722-739），与缓存的 `lastRemoteRef` 比对；**ls-remote 失败返回 null → 视为有更新 → 执行 pull**（:712-715）。pull 成功后 `lastRemoteRef = resolveLocalHead()`（:672-675）。
- 即：**首次读 = clone（重），后续每次读 = 一次 ls-remote（网络 RTT）+ 条件 pull**。`ensureRepositorySynced` 是 `synchronized`——同一 repository 实例的并发读串行化。
- 同步首次还会解析 `skillsPath`（:487-522）：显式 `skillsRoot`（校验拒绝越出仓库，:489-505）或约定 `<repo>/skills/` 存在则用之否则仓库根（:506-518）。

### 3.2 PostgreSQL 途径：结论——只能纯代码接线

**排查过程与证据**：

1. 全仓 grep `PostgresSkillRepository`：命中仅其自身模块的实现（`agentscope-extensions-skill-postgresql-repository/.../PostgresSkillRepository.java`）与单测（同模块 `PostgresSkillRepositoryTest.java`）。**harness、service、examples 中零引用。**
2. 声明式配置：`SkillRepositorySupport` 的 switch 只认 `filesystem|git`（service 版 :67-78；三个 example 拷贝同）——**`type: "postgresql"` 不被支持**，落入 default 分支 WARN "Unknown skillRepository type" 后丢弃。
3. examples：三个 agent 示例的 bootstrap 均不构造 PG 仓库（3.1 表格已穷尽其 skill 装配点）。
4. service 生态中 PG 的实际形态是**存储/状态设施而非 skill 仓库**：`agentscope-extensions-postgresql` 模块提供 `PostgresBaseStore`、`PostgresDistributedStore`、`PostgresAgentStateStore`、`PostgresRemoteSnapshotClient`、`PostgresSandboxExecutionGuard`（模块文件列表）。service 自己的 skill 存储走 `BaseStoreDefinitionStore`（`agentscope-service/service-common/.../catalog/BaseStoreDefinitionStore.java:36-43`，`DefinitionStore` 后端，KV namespace `definitions/{ownerId}/{agentId}`，skill 以 `skills/<name>/SKILL.md` **文本文件形态**存 KV；读取侧配套 `DefinitionStoreSkillRepository`，source `"definition-store"`，`DefinitionStoreSkillRepository.java:37-67`）。即便 service 的 BaseStore 配了 PG 后端，skill 走的也是 DefinitionStore 这条路，**与 `PostgresSkillRepository` 的双表 schema 无关**。

**结论**：`PostgresSkillRepository` 的唯一接线方式是应用代码 `new PostgresSkillRepository(dataSource, ...)`（或其 builder）后挂到 `builder.skillRepository(...)`（Layer 2）——**没有声明式入口、没有框架自动装配、没有官方示例**。这意味着：

- 想用 PG 途径的宿主（如 NexAI）必须**自己写装配代码**，在构造 agent 时注入；
- 反过来说，PG 仓库实例的构造参数（DataSource、schema/表名、writeable）完全由宿主控制——**按租户/按环境构造不同实例天然可行**，这正是 Git 声明式配置做不到的（agentscope.json 是静态的）；
- 不存在"框架替你管理 PG 仓库生命周期"这回事——`AutoCloseable.close()` 由宿主负责调用。

### 3.3 双途径挂在同一个 HarnessAgent 上时

- **加载顺序与覆盖**：两仓库都在 Layer 2，按 `skillRepository()` 调用顺序进入列表；每轮 `HarnessSkillMiddleware.mergeRepositories`（:306-333）按列表序 `merged.put(skill.getName(), new RepoBound(skill, repo))`——**后注册者覆盖先注册者的同名 skill**（与 core 版语义一致）。典型挂法：`builder.skillRepository(gitRepo).skillRepository(pgRepo)` → PG 覆盖 Git 同名。
- **每轮成本**（harness 路径**无签名短路**，`HarnessSkillMiddleware` 全文无 `computeSignature`）：
  - Git 仓库每轮：`ls-remote`（网络往返）+ HEAD 变化时 pull + 扫描本地目录装载全部 skill；
  - PG 仓库每轮：全量 `SELECT`（skills 表 + 资源表，前篇 4.4）；
  - 之后统一走 `stager.stage`：两途径的 skill 都会参与文件级 SHA-256 比对（读回 `.skills-cache` 现有文件），内容不变则实际写盘为零；孤儿清理每轮执行。
  - 对比 core 路径：`DynamicSkillMiddleware` 的签名短路至少省掉 SkillBox 重建与 uploadSkillFiles；harness 路径连这层也没有（但有 stager 的文件级去重与投影的整体哈希兜底）。
- **物化分区**：`.skills-cache/git-owner/repo@branch/<name>/` 与 `.skills-cache/postgresql_<schema>_<table>/<name>/`（source 原样作目录名，:127-135）；`<files-root>` 前缀按 ShellPathPolicy 模式渲染（`/workspace/.skills-cache/<ns>/<name>` 或 `<wsRoot>/.skills-cache/<ns>/<name>`，`ShellPathPolicy.java:100-120` + 文档 `harness/skill.md:316-323`）。
- **单仓库失败**：与 core 相同，`mergeRepositories` 里 catch `Exception` → WARN + continue（:315-321）——Git 网络抖动只丢该轮 Git skill，不阻塞 PG。

---

## 4. 沙箱与执行（简要）

- **投影根**：`SandboxFilesystemSpec.DEFAULT_WORKSPACE_PROJECTION_ROOTS = List.of("AGENTS.md", "skills", "subagents", "knowledge", ".skills-cache")`（`SandboxFilesystemSpec.java:41-42`，字段 :48 可经 `workspaceProjectionRoots(List)` 覆盖、`workspaceProjectionEnabled(false)` 关闭）。沙箱 `start()` 时把宿主工作区这些根打成 tar hydrate 进容器 `/workspace`；投影按内容算整体 SHA-256，内容不变跳过重复注入（文档 `harness/skill.md:355-361`）。
- **时序闭环**：`prestageMarketplaceSkills` 挂在沙箱 `beforeStartCallback`（`HarnessAgent.java:2785-2788`），保证"物化 → 投影 → 容器内执行"在同一轮 start 内完成；每轮 `onSystemPrompt` 的 stage 负责后续内容变化的增量更新。
- **`<files-root>` 执行**：`ShellPathPolicy.resolve`（`ShellPathPolicy.java:100-111`）——`NO_SHELL` → null（prompt 不渲染 files-root 与代码执行块）；`WorkspaceNative` → `<prefix>/skills/<name>`；`Cached` → `<prefix>/.skills-cache/<ns>/<name>`。agent 发 `execute_shell_command("python3 <files-root>/scripts/foo.py")`，命令在容器里跑、读的就是投影进来的文件（文档 `harness/skill.md:325-339、372-385`）。
- **读与执行分离**：读 SKILL.md/资源走内存或懒文件系统读取（`SkillLoadTool`），不需要沙箱；只有 shell 跑脚本才依赖投影（文档 `harness/skill.md:399-403`）。

---

## 5. 对 NexAI 的启示（事实性）

1. **底座选择决定接线点**：若 NexAI 用 `HarnessAgent`，skill 每轮加载走 `HarnessSkillMiddleware`（core 的 `DynamicSkillMiddleware` 被显式关闭，`HarnessAgent.java:2781`）；自定义可见性过滤（金丝雀/白名单）要用 harness 的 `SkillVisibilityFilter` 构造参数（`HarnessAgent.java:2774、2768`），而不是继承 core 的 `filterVisible` 钩子。若只用 `ReActAgent`，则相反。
2. **PG 途径只能代码装配——这对 NexAI 反而是优势**：官方无声明式入口意味着 PG 仓库实例由 NexAI 的 Spring 服务层构造，天然可以按**租户参数**实例化（每租户一个实例/一个 schema，或自建带 tenant 列的表）；`agentscope.json` 式静态配置做不到这一点。
3. **多租户映射的现成语义**：Layer 2 内多仓库"后注册覆盖同名"（`HarnessSkillMiddleware.java:325-330`）可直接表达"平台公共 skill（低）→ 租户覆盖 skill（高）"；per-user 隔离参考 Layer 4 的 `RuntimeContext.userId` + `NamespaceFactory` 模式（`RemoteFilesystemSpec.java:298-316`）。官方 `PostgresSkillRepository` 无租户概念（隔离靠 schema），单表多租户需 NexAI 自建 repository 实现。
4. **性能特征要认账**：harness 路径**每轮每仓库全量 `getAllSkills()`，无签名短路**——Git = 每轮 ls-remote（网络 RTT，synchronized 串行）；PG = 每轮全量 SELECT。NexAI 的 PG repository 适配器内部应自建"租户级版本号/更新时间戳"缓存（类比 `GitSkillRepository` 的 HEAD 比对思路，`GitSkillRepository.java:711-720`），在 repo 内部短路即可，不必动 harness。每 spawn 子 agent 还会独立再读一遍市场仓库（2.4 节），缓存收益翻倍。
5. **指纹与复现性**：core 的 `computeSignature`（内容 SHA-256）与 `MarketplaceStager` 的文件级 SHA-256 都以**内容**为键——内容不变则 prompt/物化稳定；但 skill 无版本号语义（PG 表只有 `updated_at`），审计/回滚/灰度对比需 NexAI 管理端自行记录版本历史。
6. **物化目录即缓存指纹**：`.skills-cache/<source>/<name>/` 的 source（如 `postgresql_nexai_skills`）直接成为目录名；NexAI 若多套 PG 配置（多 schema），source 天然区分；同 source 的多实例会被加 `_idx` 后缀（`MarketplaceStager.java:372-407`）——给不同租户的 repository 配不同 source 标识可避免混淆。
7. **frozen 开关是现成的"会话内锁定"**：`disableDynamicSkills()` 让 harness 构建时快照仓库视图（`HarnessSkillMiddleware.frozen`，`HarnessSkillMiddleware.java:141-156`）——若业务要求"会话开始后 skill 集不变"，这是零成本的官方机制；默认动态模式则"管理端改完下一轮 call 生效"。
8. **沙箱执行的前提**：要跑 skill 脚本需要 ① workspace 存在（否则 stager 为 null，`HarnessAgent.java:2726-2730`）② shell 工具启用（否则 `ShellPathPolicy.noShell()`，files-root 不渲染）③ 沙箱投影默认已含 `.skills-cache` 与 `skills`（`SandboxFilesystemSpec.java:41-42`）。NexAI 若只需"读 SKILL.md/参考资料"级别能力，可不开沙箱，`load_skill_through_path` 纯内存/懒读即可工作。

---

## 6. 来源清单

源码（相对 `/Users/jerry/agent-project/agentscope-java/`）：

1. `agentscope-core/src/main/java/io/agentscope/core/skill/DynamicSkillMiddleware.java`（全文精读：:60-113 构造、:129-143 onSystemPrompt、:157-159 filterVisible、:166-256 reloadSkills、:222-225 签名短路、:314-357 computeSignature、:263-293 稳定 workDir）
2. `agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java`（:4458-4479 builder 字段、:4931-4967 skillRepository/skillRepositories、:4978-4984 dynamicSkillsEnabled、:5159-5166 configureSkillBox、:5174-5225 build 安装）
3. `agentscope-core/src/main/java/io/agentscope/core/skill/SkillToolFactory.java`（:79-199 core 版 load_skill_through_path、:370-387 activateSkill/工具组）
4. `agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java`（:1662-1691 builder 方法、:2622-2632 合成调用与 currentRcSupplier、:2639-2676 skill_manage 增补、:2725-2792 HarnessSkillMiddleware 安装与 dynamicSkillsEnabled(false)、beforeStartCallback）
5. `agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgentBuilderSupport.java`（:290-387 general-purpose 子 agent、:392-522 声明式子 agent、:803-852 composeSkillRepositories）
6. `agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/HarnessSkillMiddleware.java`（全文精读：:141-156 frozen、:158-182 构造、:202-218 prestage、:220-272 onSystemPrompt、:278-333 mergeRepositories/请求级 ctx、:335-367 可见性过滤）
7. `agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/WorkspaceSkillRepository.java`（:90-130 构造、:169-205 getAllSkills(ctx)/懒资源、:237-243 resourcesFor、:453-459 归档删除、:496-554 hasMetadataAncestor）
8. `agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/RuntimeContextSkillRepository.java`（:24-41 接口）
9. `agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/runtime/MarketplaceStager.java`（:63-74 常量、:94-148 stage、:151-163 invalidateAll、:169-199 物化、:201-267 SHA-256 去重与 +x、:269-328 孤儿清理、:372-417 source 命名空间）
10. `agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/runtime/SkillRuntime.java`（:36-153，install/prepareToolkit/授权边界）
11. `agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/runtime/SkillLoadTool.java`（:57-219 工具与查找顺序、:232-256 全文返回）
12. `agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/runtime/SkillCatalog.java`（:30-73）、`HarnessSkillEntry.java`（:37）
13. `agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/runtime/ShellPathPolicy.java`（:38-120 三模式与 resolve）
14. `agentscope-harness/src/main/java/io/agentscope/harness/agent/filesystem/AbstractFilesystem.java`（:40-44 接口契约）
15. `agentscope-harness/src/main/java/io/agentscope/harness/agent/filesystem/spec/RemoteFilesystemSpec.java`（:298-316 storeNamespace/isolationScope）
16. `agentscope-harness/src/main/java/io/agentscope/harness/agent/filesystem/remote/store/NamespaceFactory.java`（:22-47）
17. `agentscope-harness/src/main/java/io/agentscope/harness/agent/filesystem/local/LocalFilesystem.java`（:86-198 NamespaceFactory 构造）
18. `agentscope-harness/src/main/java/io/agentscope/harness/agent/filesystem/spec/SandboxFilesystemSpec.java`（:41-48 DEFAULT_WORKSPACE_PROJECTION_ROOTS）
19. `agentscope-harness/src/test/java/io/agentscope/harness/agent/middleware/HarnessSkillMiddlewareContextIsolationTest.java`（:39-79 并发隔离实证）
20. `agentscope-extensions/agentscope-extensions-skills/agentscope-extensions-skill-git-repository/src/main/java/io/agentscope/core/skill/repository/GitSkillRepository.java`（:279-297 读时同步入口、:450-539 ensureRepositorySynced、:711-757 ls-remote/HEAD 比对）
21. `agentscope-extensions/agentscope-extensions-skills/agentscope-extensions-skill-postgresql-repository/`（全仓唯一引用范围：自身实现+单测）
22. `agentscope-extensions/agentscope-extensions-postgresql/`（模块文件列表：BaseStore/DistributedStore/StateStore/SnapshotClient/ExecutionGuard，PG 在 service 生态的真实角色）
23. `agentscope-service/service-common/src/main/java/io/agentscope/builder/runtime/config/SkillRepositoryConfigEntry.java`（:31-137 字段）
24. `agentscope-service/service-common/src/main/java/io/agentscope/builder/runtime/config/SkillRepositorySupport.java`（:43-138 createAll/create/createGit 反射）
25. `agentscope-service/service-common/src/main/java/io/agentscope/builder/web/catalog/BaseStoreDefinitionStore.java`、`DefinitionStoreSkillRepository.java`（service 的 skill 存储实际形态）
26. `agentscope-service/service-dataplane/src/main/java/io/agentscope/builder/web/managed/selfhosted/SkillsBundleService.java`（存在性核对，非 PG 仓库接线）
27. `agentscope-examples/agents/agentscope-paw/src/main/java/io/agentscope/claw2/runtime/ClawBootstrap.java`（:436-438）及其 `runtime/config/SkillRepositorySupport.java`
28. `agentscope-examples/agents/agentscope-codingagent/src/main/java/io/agentscope/harness/coding/CodingBootstrap.java`（:337-342）及其 `config/SkillRepositorySupport.java`
29. `agentscope-examples/agents/agentscope-dataagent/src/main/java/io/agentscope/dataagent/runtime/DataAgentBootstrap.java`（:395-397）及其 `runtime/config/SkillRepositorySupport.java`
30. `agentscope-examples/documentation/src/main/java/io/agentscope/examples/documentation2/skill/GitSkillRepositoryExample.java`（:91-108 纯代码入口）

官方文档：

31. `docs/v2/zh/docs/harness/skill.md`（:160-194 四层优先级、:290-312 path 解析表、:314-397 files-root/物化/投影/容器内执行）

关联前篇：

32. `docs/research/2026-08-25-agentscope-skill-管理三种存储与同步机制.md`（SPI/六实现/PG 表结构/Nacos 总览，本篇不重复）
