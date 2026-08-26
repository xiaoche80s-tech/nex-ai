# AgentScope Java v2 Skill 管理调研：三种存储与同步机制

> 调研日期：2026-08-25
> 调研对象：`/Users/jerry/agent-project/agentscope-java/`（AgentScope Java v2 源码，含 agentscope-examples 与 .qoder/repowiki）
> 调研方法：只依据源码、官方示例、官方文档（docs/v2）与 repowiki 知识库等一手材料；所有关键论断附文件路径与行号。

---

## 0. 结论速览（TL;DR）

1. **统一 SPI 确实存在**：`io.agentscope.core.skill.repository.AgentSkillRepository`（位于 agentscope-core），一个 `AutoCloseable` 接口，定义 `getSkill / getAllSkillNames / getAllSkills / save / delete / skillExists / getRepositoryInfo / getSource / setWriteable / isWriteable / close` 十一个方法（`agentscope-core/src/main/java/io/agentscope/core/skill/repository/AgentSkillRepository.java:35-133`）。
2. **实际是"内置 2 种 + 扩展 4 种"共 6 个实现**，用户关注的"三种"全部存在且各有独立 Maven 模块：
   - **Git 分支存储**：`io.agentscope.core.skill.repository.GitSkillRepository`（`agentscope-extensions/agentscope-extensions-skills/agentscope-extensions-skill-git-repository`）——只读，JGit clone 到本地目录，读时轻量检查远程 HEAD、变了才 pull；
   - **PostgreSQL 存储**：`io.agentscope.core.skill.repository.postgresql.PostgresSkillRepository`（`agentscope-extensions/agentscope-extensions-skills/agentscope-extensions-skill-postgresql-repository`）——完整 CRUD，两张表 `agentscope.agentscope_skills` / `agentscope_skill_resources`，管理后台改完即生效；
   - **Nacos 存储**：`io.agentscope.core.nacos.skill.NacosSkillRepository`（`agentscope-extensions/agentscope-extensions-nacos/agentscope-extensions-nacos-skill`）——只读，按 skill 名从 Nacos AI 模块下载 skill ZIP 包并解析。
   - 此外还有同构的 **MySQL 实现**（`MysqlSkillRepository`）与 core 内置的 **FileSystem / Classpath** 两个实现（`docs/v2/zh/integration/skill/overview.md:5-13`）。
3. **skill 的统一数据模型**是「一份 `SKILL.md`（YAML frontmatter 必含 `name`/`description`）+ 若干附属资源文件」，内存中对应 `AgentSkill`（metadata Map + 正文 + resources Map + source + originDir）（`agentscope-core/.../skill/AgentSkill.java:68-80`）。
4. **没有"同步到项目"的独立步骤**：三种仓库都不产生编译期产物。运行时接线是 `ReActAgent.builder().skillRepository(repo)`（可重复调用、后者优先级高），build 时安装 `DynamicSkillMiddleware`，**每次 `call()` 前从仓库实时拉取合并**并重建 system prompt 中的 `<available_skills>` 块；LLM 再通过内置工具 `load_skill_through_path(skillId, path)` 按需加载 SKILL.md 正文或资源文件（`agentscope-core/.../skill/DynamicSkillMiddleware.java:129-143`）。
5. **对 NexAI 最直接的结论**：NexAI（Spring Boot 4.1 + PostgreSQL 管理后台）做 skill 管理端，首选 **PostgresSkillRepository**——管理后台对 `agentscope_skills` 表做 CRUD，agent 侧用 `writeable=false` 的只读实例，天然"改完即生效"，零同步任务。详见第 8 节。

---

## 1. 统一抽象：AgentSkillRepository SPI

### 1.1 接口定义

```java
public interface AgentSkillRepository extends AutoCloseable {
    AgentSkill getSkill(String name);
    List<String> getAllSkillNames();
    List<AgentSkill> getAllSkills();
    boolean save(List<AgentSkill> skills, boolean force);
    boolean delete(String skillName);
    boolean skillExists(String skillName);
    AgentSkillRepositoryInfo getRepositoryInfo();
    String getSource();
    void setWriteable(boolean writeable);
    boolean isWriteable();
    default void close() { }
}
```

（`agentscope-core/src/main/java/io/agentscope/core/skill/repository/AgentSkillRepository.java:35-133`）

接口 Javadoc 明确说明这是 Repository Pattern / 依赖倒置的端口："allowing different storage stores (filesystem, database, remote APIs, etc.) to be used interchangeably"（同文件 21-27 行）。skill 的全局唯一标识格式为 `name_source`（同文件 49、103 行）。

`AgentSkillRepositoryInfo` 携带三元组 `(type, location, writeable)` 描述仓库元信息（`agentscope-core/.../skill/repository/AgentSkillRepositoryInfo.java`）。

### 1.2 全部实现一览（6 个）

| 实现 | 全名 | 模块 | 读写 |
| --- | --- | --- | --- |
| FileSystem | `io.agentscope.core.skill.repository.FileSystemSkillRepository` | agentscope-core | 可读写 |
| Classpath | `io.agentscope.core.skill.repository.ClasspathSkillRepository` | agentscope-core | 只读 |
| **Git** | `io.agentscope.core.skill.repository.GitSkillRepository` | `agentscope-extensions/agentscope-extensions-skills/agentscope-extensions-skill-git-repository` | **只读** |
| MySQL | `io.agentscope.core.skill.repository.mysql.MysqlSkillRepository` | `agentscope-extensions/agentscope-extensions-skills/agentscope-extensions-skill-mysql-repository` | 可读写 |
| **PostgreSQL** | `io.agentscope.core.skill.repository.postgresql.PostgresSkillRepository` | `agentscope-extensions/agentscope-extensions-skills/agentscope-extensions-skill-postgresql-repository` | 可读写 |
| **Nacos** | `io.agentscope.core.nacos.skill.NacosSkillRepository` | `agentscope-extensions/agentscope-extensions-nacos/agentscope-extensions-nacos-skill` | **只读** |

（实现类路径由 grep 全仓 `skill` 关键字核实；官方文档 `docs/v2/zh/integration/skill/overview.md:5-13` 列出 Git/MySQL/PostgreSQL 三扩展并注明 "Nacos 也提供了一个 AgentSkillRepository 实现：见 Nacos"）

注意包名惯例：Git 实现直接放在 `io.agentscope.core.skill.repository` 包下（与接口同包，便于 `SkillRepositorySupport` 反射加载），PG/MySQL/Nacos 各自在子包（`postgresql` / `mysql` / `io.agentscope.core.nacos.skill`）。

### 1.3 插拔方式：纯代码 Builder，无 Spring 自动装配

- **没有 skill 相关的 Spring Boot starter / AutoConfiguration / @ConfigurationProperties**。`agentscope-extensions/agentscope-spring-boot-starters/` 下 grep "skill" 仅命中 a2a starter 中与 skill 无关的类。插拔完全通过构造器/Builder new 出实例，再交给 `ReActAgent.builder().skillRepository(repo)`。
- 唯一的"声明式配置"通道在 agentscope-service（官方托管服务模块）：`agentscope.json` 中的 `skillRepositories` 数组，类型仅支持 `filesystem` 与 `git` 两种（`agentscope-service/service-common/src/main/java/io/agentscope/builder/runtime/config/SkillRepositoryConfigEntry.java:23-38`；`SkillRepositorySupport.java:62-79` 的 `switch (kind)`）。Git 类型通过 `Class.forName("io.agentscope.core.skill.repository.GitSkillRepository")` 反射构造，缺依赖时降级为 WARN 跳过（`SkillRepositorySupport.java:99-138`）。**PG / Nacos / MySQL 不在声明式配置支持范围内**，只能代码接线。

---

## 2. Skill 的数据模型

### 2.1 内存模型：AgentSkill

`io.agentscope.core.skill.AgentSkill`（`agentscope-core/src/main/java/io/agentscope/core/skill/AgentSkill.java:68-80`）：

- `metadata: Map<String, Object>`——**必含非空 `name` 与 `description`**，其余任意（如 `version`）；构造时校验缺失即抛 `IllegalArgumentException`（同文件 158-162、523-535 行）；
- `skillContent: String`——SKILL.md 的正文（指令内容），不可为空；
- `resources: Map<String, String>`——附属资源，key 为相对路径（如 `scripts/run.py`），value 为文本或 `"base64:"` 前缀的二进制（落盘时解码，`agentscope-core/.../skill/util/SkillFileSystemHelper.java:239-245`）；
- `source: String`——来源标识（如 `git-owner/repo@branch`、`postgresql_agentscope_agentscope_skills`、`nacos:namespace`）；
- `originDir: Path`（可选）——文件系统来源的 skill 磁盘目录，用于 prompt 输出 `<files-root>` 与 load 工具的磁盘回退读取（`AgentSkill.java:74-80、272-280`）；
- `getSkillId()` 返回 `name + "_" + source`（同文件 267-269 行）。

### 2.2 文件形态：SKILL.md + 目录

磁盘上一个 skill 是一个目录，**必须包含 `SKILL.md`**：

```
code-reviewer/
├── SKILL.md           # 必需：YAML frontmatter（name + description）+ 给 agent 看的指令
├── references/        # 可选：长篇参考资料
│   └── style-guide.md
└── scripts/           # 可选：agent 可通过 shell 调用的脚本
    └── run-checks.sh
```

（`docs/v2/zh/docs/harness/skill.md:17-24`；加载逻辑见 `SkillFileSystemHelper.loadSkillFromDirectory`，`agentscope-core/.../skill/util/SkillFileSystemHelper.java:96-121`——读 `SKILL.md` 解析 frontmatter，可选拷贝全树资源并回填 `originDir`）

`SKILL.md` 格式（YAML frontmatter 用 snakeyaml 安全解析，正文保持原样）：

```markdown
---
name: code-reviewer
description: 当用户需要代码评审、风格反馈或 PR 审核时使用。
version: 1.0.0        # 可选
---
# Code Reviewer
（给 agent 的指令正文）
```

（frontmatter 语法与解析见 `agentscope-core/.../skill/util/MarkdownSkillParser.java:45-54、74-76、99-118`；`SkillUtil.createFrom` 强制 name/description/正文非空，`agentscope-core/.../skill/util/SkillUtil.java:90-113`）

**版本信息**：frontmatter 中可写 `version` 之类的自定义键（进 metadata），但框架本身**没有基于版本的比对/回滚逻辑**——唯一内置的版本语义在 Nacos 下载（按 version/label 选包）与 `SkillBox` 注册快照注释（`agentscope-core/.../skill/SkillBox.java:222-229`）。PG 表里也只有 `created_at/updated_at` 时间戳。

### 2.3 ZIP 形态（Nacos / 分发用）

`SkillUtil.createFromZip(byte[]|Path, source, charset)`：ZIP 包内必须有一个且仅一个 `<skillName>/SKILL.md` 条目（一级目录 + SKILL.md），其余条目按相对路径进 resources；`.skill` 与 `.zip` 扩展名等价（`agentscope-core/.../skill/util/SkillUtil.java:116-150`；条目校验 `NacosSkillRepository.java:84-85` 的 `ROOT_SKILL_MD` 正则 `^([^/]+)/SKILL\.md$`）。

---

## 3. 方式一：Git 分支存储（GitSkillRepository）

### 3.1 类与依赖

- 全名：`io.agentscope.core.skill.repository.GitSkillRepository`
- 路径：`agentscope-extensions/agentscope-extensions-skills/agentscope-extensions-skill-git-repository/src/main/java/io/agentscope/core/skill/repository/GitSkillRepository.java`
- Maven 坐标：`io.agentscope:agentscope-extensions-skill-git-repository`（`docs/v2/zh/integration/skill/git-repository.md:13-19`）；底层用 Eclipse JGit（import 见 GitSkillRepository.java:28-35）。

### 3.2 配置（构造器，无 yaml）

最全构造器（`GitSkillRepository.java:239-245`）：

```java
new GitSkillRepository(
    remoteUrl,    // HTTPS 或 SSH URL（系统级 git 凭证）
    branch,       // 分支，null = 远程默认分支
    localPath,    // 本地 clone 目录，null = 临时目录
    source,       // 自定义 source 标识，null = "git-owner/repo[@branch]"
    autoSync,     // true（默认）= 每次读操作自动检查更新
    skillsRoot    // 仓库内技能根目录；null = 自动探测（见下）
);
```

官方文档示例（`docs/v2/zh/integration/skill/git-repository.md:44-52`）：

```java
GitSkillRepository repo = new GitSkillRepository(
    "https://github.com/agentscope/skills.git",
    "develop",                   // 分支
    Path.of("/var/skills/repo"), // 本地路径（null = 临时目录）
    "agentscope-public",         // source 标识
    true                         // autoSync
);
```

`agentscope.json` 声明式等价配置（仅 service 模块消费，`SkillRepositoryConfigEntry.java:39-72`）：

```json
{ "skillRepositories": [ {
    "type": "git",
    "remoteUrl": "https://github.com/your-org/team-skills.git",
    "branch": "main",
    "localPath": "var/skills-repo",
    "skillsRoot": "skills",
    "source": "team",
    "autoSync": true
} ] }
```

### 3.3 仓库中的目录布局

克隆后按 `skillsPath` 扫描：`skillsRoot` 显式配置则用之（校验拒绝绝对路径与 `..` 防穿越，`GitSkillRepository.java:422-442`）；未配置走约定——**`<repo>/skills/` 子目录存在则用之，否则仓库根**（`GitSkillRepository.java:507-518`）。每个 skill 一个子目录，内含 `SKILL.md`。`skillsPath` 下每个含 SKILL.md 的目录即一个 skill（`SkillFileSystemHelper.getAllSkills`，`SkillFileSystemHelper.java:166-193`）。

### 3.4 「分支」的作用

分支是**整仓的版本/环境切面**，不是"每个 skill 一个分支"：clone 时 `CloneCommand.setBranch(branch)` 选定分支，之后该仓库实例的所有 skill 都来自这个分支（`GitSkillRepository.java:579-582`）；source 标识带分支后缀 `git-owner/repo@branch`（同文件 367-376 行）。要切环境/版本，就换分支参数建一个新的 `GitSkillRepository` 实例。默认 source 从 URL 提取 `owner/repo`（SSH/HTTPS/local 均支持，同文件 391-413 行）。

### 3.5 同步机制（读时同步 + 远端 HEAD 比对的增量更新）

- **触发时机**：所有读操作（`getSkill/getAllSkillNames/getAllSkills/skillExists`）先 `ensureAutoSynced()`（`GitSkillRepository.java:277-299、530-539`）。
- **增量逻辑**：本地空 → `cloneRepository()`；本地是 git 仓库 → `hasRemoteUpdates()` 用 `ls-remote` 解析远程分支 HEAD 的 ObjectId，与上次记录的 `lastRemoteRef` 比较，**不同才 pull**，相同跳过（同文件 450-528、711-747 行）。类 Javadoc 原话："Each read performs a lightweight remote reference check; a pull is only executed when the remote HEAD changes"（同文件 47-48 行）。
- **autoSync=false**：完全不自动拉，需要时手动 `repo.sync()`（文档建议配合定时任务，`docs/v2/zh/integration/skill/git-repository.md:70-79`）。
- **本地缓存**：clone 到 `Files.createTempDirectory("agentscope-git-skills-")`（或指定 `localPath`），临时目录注册 JVM shutdown hook 自动清理（`GitSkillRepository.java:262-274`）。
- **只读**：`save/delete/setWriteable` 一律 WARN 忽略，`isWriteable()` 恒 false（同文件 308-328 行）。
- **鉴权**：不管理凭证，复用系统 git 配置（HTTPS credential helper / SSH agent，同文件 64-70 行；文档 `git-repository.md:54-68`）。

### 3.6 同步到"运行时"的方式

Git 仓库本身只是 `AgentSkillRepository` 数据源；进入 agent 的路径见第 6 节（`DynamicSkillMiddleware` 每次 call 拉取 + harness 层物化到 `.skills-cache`）。

---

## 4. 方式二：PostgreSQL 存储（PostgresSkillRepository）

### 4.1 类与依赖

- 全名：`io.agentscope.core.skill.repository.postgresql.PostgresSkillRepository`
- 路径：`agentscope-extensions/agentscope-extensions-skills/agentscope-extensions-skill-postgresql-repository/src/main/java/io/agentscope/core/skill/repository/postgresql/PostgresSkillRepository.java`
- Maven 坐标：`io.agentscope:agentscope-extensions-skill-postgresql-repository`（`docs/v2/zh/integration/skill/postgresql-repository.md:11-19`）
- 纯 JDBC（`javax.sql.DataSource`），不依赖 JPA/MyBatis——与宿主应用的 ORM 无耦合。

### 4.2 表结构（自动建表 DDL）

`createIfNotExist=true` 时自动 `CREATE SCHEMA IF NOT EXISTS "agentscope"` 并建两张表（DDL 见 `PostgresSkillRepository.java:52-75、308-353`；文档 `postgresql-repository.md:50-78`）：

```sql
CREATE TABLE IF NOT EXISTS "agentscope"."agentscope_skills" (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    skill_content TEXT NOT NULL,      -- SKILL.md 全文（frontmatter + 正文）
    source VARCHAR(255) NOT NULL,
    metadata_json TEXT NULL,          -- 完整 metadata 树（JSON）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "agentscope"."agentscope_skill_resources" (
    id BIGINT NOT NULL,               -- 外键 → agentscope_skills.id
    resource_path VARCHAR(500) NOT NULL,
    resource_content TEXT NOT NULL,   -- 文本或 "base64:" 前缀二进制
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, resource_path),
    FOREIGN KEY (id) REFERENCES "agentscope"."agentscope_skills"(id) ON DELETE CASCADE
);
```

要点：`name` 全局唯一（同名 upsert 语义）；`metadata_json` 存完整 frontmatter 树，读取时反序列化后用 SQL 列的 name/description 覆盖（`PostgresSkillRepository.java:1042-1080`）；**旧表没有该列时自动降级**为只往返 name+description，不主动 `ALTER TABLE`（兼容检测在构造时一次完成并缓存，同文件 148-149、444-495 行）。写入侧：save 走事务，先删后插实现覆盖，资源批量插入每批 1000 行（同文件 656-729、791-879 行）。

### 4.3 配置（构造器 / Builder，无 yaml）

```java
// 简单构造：默认 schema=agentscope，两张默认表名
PostgresSkillRepository repo = new PostgresSkillRepository(dataSource, /*createIfNotExist*/ true, /*writeable*/ true);

// Builder：自定义 schema / 表名
PostgresSkillRepository repo = PostgresSkillRepository.builder(ds)
    .schemaName("my_schema")
    .skillsTableName("my_skills")
    .resourcesTableName("my_resources")
    .createIfNotExist(true)
    .writeable(true)
    .build();
```

（`PostgresSkillRepository.java:99-121、1288-1396`；文档 `postgresql-repository.md:21-46、103-111`）

source 标识为 `postgresql_<schema>_<skillsTable>`（同文件 972-975 行）；PG 用 **schema**（而非 database）做命名空间隔离，库由 JDBC URL 决定（文档 `postgresql-repository.md:78`）。

### 4.4 同步机制：没有同步，直读即生效

- 每次 `getSkill/getAllSkills` 都是即时 SQL 查询，无本地缓存、无定时任务、无版本比对——**管理后台 save 之后 agent 下一次 call() 立即可见**（文档定位："在控制台/业务系统里编辑保存，Agent 这边立即可读"，`postgresql-repository.md:3`；"改完即生效"，`overview.md:34`）。
- 变更生效的节流不在这层，而在 `DynamicSkillMiddleware` 的内容签名短路（见 6.2）与 harness 的 SHA-256 物化去重（见 6.4）。
- MySQL 实现（`MysqlSkillRepository`）与 PG 完全同构，差异仅是 database 代替 schema、`LONGTEXT` 代替 `TEXT`、`AUTO_INCREMENT` 代替 `BIGSERIAL`（`docs/v2/zh/integration/skill/mysql-repository.md:30-60`）。

---

## 5. 方式三：Nacos 存储（NacosSkillRepository）

### 5.1 类与依赖

- 全名：`io.agentscope.core.nacos.skill.NacosSkillRepository`
- 路径：`agentscope-extensions/agentscope-extensions-nacos/agentscope-extensions-nacos-skill/src/main/java/io/agentscope/core/nacos/skill/NacosSkillRepository.java`
- Maven 坐标：`io.agentscope:agentscope-extensions-nacos-skill`（`docs/v2/zh/integration/infrastructure/nacos.md:81-89`）
- 依赖 Nacos 客户端的 **AI 模块 API**：`com.alibaba.nacos.api.ai.AiService`（`NacosSkillRepository.java:18、102`）。

### 5.2 Nacos 侧的存储形态

不是传统 config 的 dataId/group，而是 Nacos AI 模块的"skill 包"：**每个 skill 一个 ZIP 包，按 skill 名（+ 可选 version / label）寻址**。ZIP 内布局与通用 skill 包一致：`<skillName>/SKILL.md` + 附属文件（`ROOT_SKILL_MD` 正则校验，`NacosSkillRepository.java:84-85`）。命名空间（namespaceId）做租户隔离，source 标识 `nacos:<namespaceId>`（同文件 146-148 行）。

### 5.3 配置（构造器 + Properties/JVM/env 三级解析）

```java
Properties props = new Properties();
props.setProperty(NacosSkillRepository.SKILL_VERSION_PATH, "1.2.0");  // agentscope.nacos.skill.version
// 或 props.setProperty(NacosSkillRepository.SKILL_LABEL_PATH, "stable"); // agentscope.nacos.skill.label

NacosSkillRepository repo = new NacosSkillRepository(aiService, "default-namespace", props);
AgentSkill skill = repo.getSkill("calculator");
```

（文档 `docs/v2/zh/integration/infrastructure/nacos.md:91-102`；常量定义 `NacosSkillRepository.java:91-100`：`SKILL_VERSION_PATH="agentscope.nacos.skill.version"`、`SKILL_LABEL_PATH="agentscope.nacos.skill.label"`、环境变量 `AGENTSCOPE_NACOS_SKILL_VERSION` / `AGENTSCOPE_NACOS_SKILL_LABEL`）

版本/标签解析顺序：构造 Properties → JVM 系统属性 → 环境变量；**同时设置时版本优先，标签不参与下载**（类 Javadoc `NacosSkillRepository.java:49-52、68-72`）。

### 5.4 读取与「同步」机制：运行时按需下载，无缓存

- 下载 API 三选一：设了 version 用 `aiService.downloadSkillZipByVersion(name, version)`；只有 label 用 `downloadSkillZipByLabel`；都没有用 `downloadSkillZip`（`NacosSkillRepository.java:295-304`）。
- 下载的 ZIP 先做 **frontmatter 规范化**：Nacos 导出的 SKILL.md 常用缩进续行（松散 YAML），而 AgentScope 的解析器只认扁平 `key: value`，因此解包→折叠续行→重打包再交给 `SkillUtil.createFromZip`（`NacosSkillRepository.java:306-361、464`）。
- **无任何本地缓存**：每次 `getSkill` 都是一次网络下载（同文件 170-188 行）；**无 list API**：Nacos AI Service 不提供"列出所有 skill"，不传 `knownSkillNames` 构造参数时 `getAllSkillNames()/getAllSkills()` 返回空并告警；传了则逐个 `getSkill` 枚举（每 skill 一次网络调用，Javadoc 明确建议大目录用 MySQL 实现，同文件 60-66、229-258 行）。
- 只读：`save/delete` 忽略、`isWriteable()` 恒 false（同文件 217-269 行）。

### 5.5 小结（与另两种对比）

| 维度 | Git | PostgreSQL | Nacos |
| --- | --- | --- | --- |
| 读写 | 只读 | 完整 CRUD | 只读 |
| 本地缓存 | 本地 clone 目录 | 无（直查 DB） | 无（每次下载） |
| 变更检测 | ls-remote 比 HEAD，变了才 pull | 不需要（读即最新） | 不需要（读即最新） |
| 枚举全部 | 扫描本地目录 | `SELECT name` | 无 API，需外部注入 knownSkillNames |
| 二进制资源 | 文件（保留权限） | `resource_content` 文本/base64 | ZIP 条目 |
| 部署形态 | 需要 git 凭证 | 需要 DataSource | 需要 Nacos AI 模块 |

---

## 6. 运行时如何使用：从仓库到 agent 的完整调用链

### 6.1 接线（Builder）

```java
ReActAgent agent = ReActAgent.builder()
    .name("SkillAgent")
    .model(model)
    .toolkit(new Toolkit())
    .skillRepository(skillRepo)   // 可重复调用，多次追加；后注册的优先级更高
    .build();
```

（示例 `agentscope-examples/documentation/src/main/java/io/agentscope/examples/documentation2/skill/GitSkillRepositoryExample.java:91-108`；Builder 方法 `ReActAgent.java:4937-4963`——Javadoc："Multiple calls append in order from low to high priority — when two repositories expose a skill with the same name, the later (higher) wins"）

`build()` 时：若 `skillRepositories` 非空且 `dynamicSkillsEnabled`（默认 true），安装 `DynamicSkillMiddleware`（`ReActAgent.java:5208-5216`）；`dynamicSkillsEnabled(false)` 可改为 build 时合并一次（静态视图）。

> 注意一个**文档与源码不一致**：`docs/v2/zh/integration/skill/overview.md:17-29` 给的接入示例是 `skills.forEach(toolkit::registerSkill)`，但当前 `Toolkit` 类中**不存在** `registerSkill(AgentSkill)` 方法（全仓 grep 仅 `SkillBox.registerSkill`、`SkillRegistry.registerSkill`）。以源码为准：2.0 的正道是 `builder().skillRepository(...)`；`SkillBox` 已标注 "Since 2.0.0, SkillBox is intended for internal use only; prefer skill repositories"（`SkillBox.java:40-43`）。

### 6.2 每轮合成：DynamicSkillMiddleware（core 层）

调用链（`agentscope-core/src/main/java/io/agentscope/core/skill/DynamicSkillMiddleware.java`）：

```
agent.call(msg)
 └─ DynamicSkillMiddleware#onSystemPrompt(agent, ctx, currentPrompt)   // :129
     └─ reloadSkills(ctx)                                              // :166
         ├─ for repo : repositories → repo.getAllSkills()               // :173-176（读失败仅 WARN 跳过）
         ├─ 按 skill.getName() 合并去重：后注册（高优先）覆盖先注册      // :187-192
         ├─ filterVisible(merged, ctx)   // 子类钩子：金丝雀/白名单/环境门禁 // :157-159, 202-211
         ├─ computeSignature(visible)    // 内容哈希                     // :222-225
         │    └─ 与上次相同且有 SkillBox → 直接复用，跳过重建（缓存/增量）
         └─ new SkillBox(toolkit) → box.registerSkill(skill)...          // :227-233
 └─ currentSkillBox.getSkillPrompt(filter) 追加到 system prompt           // :136-142
```

设计动机（类 Javadoc 40-57 行）：**每次 call 重建是有意为之**——支持按用户命名空间的仓库在同一个 skill 名下随 `RuntimeContext` 切换内容；内容签名短路让"内容没变"的常见情形零开销。

### 6.3 Prompt 与工具：渐进式披露（progressive disclosure）

- system prompt 中注入 `<available_skills>` XML 块，每个 skill 只带最少元数据（name/description/其余 frontmatter/skill-id/files-root），**不含正文**（`AgentSkillPromptProvider.DEFAULT_AGENT_SKILL_INSTRUCTION`，`agentscope-core/.../skill/AgentSkillPromptProvider.java:48-74、173-209`；效果示例 `docs/v2/zh/docs/harness/skill.md:274-288`）。
- LLM 判断相关后调用内置工具 **`load_skill_through_path(skillId, path)`**（`SkillToolFactory#createSkillAccessToolAgentTool`，`agentscope-core/.../skill/SkillToolFactory.java:79-162`；注册进 toolkit 的 `skill-build-in-tools` 组，`SkillBox#registerSkillLoadTool`，`SkillBox.java:636-661`）：
  - `path="SKILL.md"` → 激活 skill 并返回 markdown 正文（激活 = 启用该 skill 绑定的工具组 `SkillToolGroup`，`SkillBox#syncToolGroupStates`，`SkillBox.java:162-200`；`Toolkit#createSkillToolGroup`，`agentscope-core/.../tool/Toolkit.java:605-618`）；
  - 其他 path → 资源内容；查找顺序：**内存 resources → originDir 磁盘回退 → 返回可用路径清单**（`SkillToolFactory.java:172-199`）。
- `SkillWithToolGroupExample`（`agentscope-examples/documentation/src/main/java/io/agentscope/examples/documentation2/skill/SkillWithToolGroupExample.java`）演示 skill 与自定义工具组绑定、激活才启用的玩法。

### 6.4 Harness 层（HarnessAgent）：四层合成 + 物化 + 沙箱

HarnessAgent 在 core 之上叠加（`docs/v2/zh/docs/harness/skill.md` 整篇 + 源码 `agentscope-harness/.../HarnessAgentBuilderSupport.java`）：

1. **四层仓库合成**（低 → 高优先级，同名覆盖）（`HarnessAgentBuilderSupport#composeSkillRepositories`，`HarnessAgentBuilderSupport.java:803-852`）：
   - Layer 1 项目全局目录：`projectGlobalSkillsDir(Path)` → `FileSystemSkillRepository`；
   - Layer 2 市场：builder 上 `skillRepository(...)` 传入的任意仓库（**Git/PG/Nacos/MySQL/Classpath 都在这一层**）；
   - Layer 3 工作区共用：`workspace/skills/` → `FileSystemSkillRepository`；
   - Layer 4 用户隔离：`<userId>/skills/` → `WorkspaceSkillRepository`（实现 harness 的 `RuntimeContextSkillRepository` 接口，按 `RuntimeContext` 路由 `AbstractFilesystem`，SKILL.md 预载、其余按需读；`agentscope-harness/.../skill/WorkspaceSkillRepository.java:47-68`）。子 agent 自动继承市场列表与全局目录（`HarnessAgentBuilderSupport.java:305-364`）。
2. **市场 skill 物化**：`MarketplaceStager` 每轮把非工作区 skill 的资源写到 `<wsRoot>/.skills-cache/<source>/<name>/`，文件级 SHA-256 去重（内容没变不重写）、孤儿目录清理、脚本恢复 `+x` 执行位（shebang/扩展名启发式）（`agentscope-harness/.../skill/runtime/MarketplaceStager.java:41-63`；文档 `harness/skill.md:320-353`）。
3. **请求级目录**：`SkillRuntime` 从 `RuntimeContext` 解析 `SkillCatalog`，并发会话互不串扰；harness 原生的 `load_skill_through_path` 由 `SkillLoadTool` 承载（`agentscope-harness/.../skill/runtime/SkillRuntime.java:30-80`；`SkillLoadTool.java:55-57`）。
4. **沙箱执行**：`.skills-cache` 与 `skills/` 都在默认 workspace projection roots 里，沙箱启动时 hydrate 进容器 `/workspace`，agent 用 `<files-root>` 绝对路径 `execute_shell_command` 跑脚本（文档 `harness/skill.md:332-397`）。
5. **自学习闭环**（可选）：`propose_skill` / `skill_manage` 工具让 agent 起草/编辑 skill 到 `skills/_drafts/`，经 `LocalApprovalGate` 等审核闸门晋升，`SkillCurator` 后台周期整理（stale/归档到 `skills/.archive/`），使用计数落 `skills/.usage.json`（`agentscope-harness/.../tool/ProposeSkillTool.java:44`、`SkillManageTool.java:41`；文档 `harness/skill.md:209-270`）。这部分写在 workspace 文件系统上，与三种远程存储正交。

---

## 7. 官方示例与文档索引

### 7.1 示例（agentscope-examples/documentation）

| 示例 | 内容 |
| --- | --- |
| `.../documentation2/skill/AgentSkillExample.java` | AgentSkill 基本创建/注册 |
| `.../documentation2/skill/GitSkillRepositoryExample.java` | 公开仓库 `https://github.com/agentscope-ai/skills` clone、列技能、`ReActAgent.builder().skillRepository(repo)` 交互对话（`GitSkillRepositoryExample.java:51、68、106`） |
| `.../documentation2/skill/SkillWithToolGroupExample.java` | skill + 工具组绑定、激活启用 |
| `.../documentation2/harness/skill/SkillCompositionExample.java` | HarnessAgent 多层合成演示：临时 workspace 下手写 `skills/code-reviewer/SKILL.md`（Layer 3）+ `ClasspathSkillRepository`（Layer 2），运行观察覆盖关系 |

### 7.2 官方文档（docs/v2/zh）

| 文档 | 要点 |
| --- | --- |
| `docs/v2/zh/integration/skill/overview.md` | SPI 总览、三扩展选型表、Nacos 指路（第 5-13 行）；选型建议：Git PR 流程管控 → 数据库/Nacos 在线改即时生效（第 31-35 行） |
| `docs/v2/zh/integration/skill/git-repository.md` | Git 用法、分支/本地路径/autoSync、鉴权、同步策略（70-79 行：autoSync=false + 手动 sync + 定时调度）、工程实践（83-85 行：Spring 单例持有、close 清理、多实例无锁竞争） |
| `docs/v2/zh/integration/skill/postgresql-repository.md` | PG 表结构 DDL、Builder 参数表、旧表兼容（ALTER 升级 metadata_json）、CRUD 事务说明 |
| `docs/v2/zh/integration/skill/mysql-repository.md` | MySQL 同构实现 |
| `docs/v2/zh/integration/infrastructure/nacos.md` | Nacos 三子模块（a2a/prompt/skill）；skill 部分 79-107 行：依赖、版本/标签配置与优先级、可与其他仓库并存 |
| `docs/v2/zh/docs/harness/skill.md` | **最全面的一篇**：四层合成、同名优先级表（181-194 行）、`load_skill_through_path` 各层解析方式表（299-306 行）、`<files-root>` 与沙箱投影、MarketplaceStager 物化、自学习闭环、写作建议（SKILL.md 控制在 2k tokens、description 决定触发、只用相对路径） |

### 7.3 repowiki 知识库（.qoder/repowiki）

- `knowledge/zh/.../AgentSkill 仓库扩展（Git_MySQL_PostgreSQL）/架构设计.md`：确认三扩展均实现 core 的 `AgentSkillRepository`，Git 只读+HEAD 比对、MySQL/PG 双表+metadata_json 兼容检测、PG schema 隔离等，与源码核对一致。
- `knowledge/zh/.../Agent Skill 系统（注册、仓库与动态加载）/`：core 层 SkillBox/SkillRegistry/动态加载体系的知识条目。

---

## 8. 对 NexAI 集成的参考建议

背景：NexAI 管理后台（Spring Boot 4.1 + PostgreSQL + MyBatis Plus，多租户）正在重建 `nexai-module-ai`，需要做 skill 的管理端 + agent 运行时消费。

1. **存储选型：直接采用 PostgresSkillRepository 模式，但建议自建聚合而非引 jar 硬依赖**。
   - 业务上 NexAI 就是"管理后台在线运营 skill、改完即生效"，官方文档给这个场景的推荐正是 MySQL/PostgreSQL/Nacos（`overview.md:31-35`）；NexAI 已有 PG，无 Nacos 设施，**PG 是零新增基础设施的选择**。
   - 表结构可直接借鉴 `agentscope.agentscope_skills` / `agentscope_skill_resources` 双表设计（name 唯一 + skill_content 全文 + metadata_json + 资源子表 ON DELETE CASCADE，`PostgresSkillRepository.java:308-353`）：它把「frontmatter 树」与「查询列」分离的思路，正好兼容管理后台列表页（只查列）与详情页（读全文）两种负载。
   - 引入官方 jar（纯 JDBC，与 MyBatis Plus 无冲突）可以最快落地；但按 NexAI 的 DDD 约定（domain 零框架依赖、DO 与领域模型分离），更自然的做法是：`skill` 聚合自建 `domain/model/Skill` + `domain/repository/SkillRepository` 端口，infrastructure 层的 PG 适配器可参照 `PostgresSkillRepository` 的 SQL/事务/批量写法。二进制资源沿用 `"base64:"` 前缀约定，方便与 AgentScope 生态的 skill 包互导。
2. **同步触发：不需要任何同步任务**。三种官方实现的共同启示是"读时合成"——`DynamicSkillMiddleware` 每次 call 拉取合并、内容签名短路（`DynamicSkillMiddleware.java:222-225`）。NexAI 的 agent 服务侧对 PG 只读查询即可，管理端 save 提交后下一轮对话自动可见。若担心每次 call 全量 `SELECT` 的压力，可在应用服务层加"租户级版本号/更新时间戳"缓存（对比 `GitSkillRepository` 的 HEAD 比对思路，`GitSkillRepository.java:711-720`），这是纯增量优化，不影响架构。
3. **多租户映射**：官方 PG 实现没有租户概念（隔离边界是 schema）。NexAI 已启用 `nexai.tenant` 行级过滤，skill 表若走 MyBatis Plus `TenantBaseDO` 天然获得租户隔离；agent 运行时构造 repository 时按租户参数实例化即可。也可参考 harness Layer 4 的"按用户命名空间覆盖共用版"模式（`harness/skill.md:160-179`）做「平台公共 skill + 租户覆盖 skill」两级合成——这正是 `DynamicSkillMiddleware` 多仓库按 name 后者覆盖前者的现成语义（`ReActAgent.java:4937-4944`）。
4. **管理端功能映射**（管理后台 CRUD ↔ 数据模型）：
   - 列表/搜索 → `name/description/metadata_json`；
   - 编辑器 → SKILL.md（frontmatter + 正文）文本编辑 + 资源文件上传（`resource_path/resource_content`）；
   - 校验 → 复用 `SkillUtil.createFrom` 的 name/description/正文非空校验（`SkillUtil.java:102-110`）与 `MarkdownSkillParser` 解析；
   - 可选进阶：把 harness 的"使用计数 + 灰度可见性"（`CanaryFilter`/`AllowListFilter`/`EnvironmentFilter`，`agentscope-harness/.../skill/curator/`）作为管理端的发布控制位设计参考。
5. **Git 仓库可作为补充源**：若后续要接入社区/集团级 skill 仓库（如 `https://github.com/agentscope-ai/skills`），`GitSkillRepository` 以只读市场身份挂在低优先级层即可（`skillRepository(communityGitRepo).skillRepository(nexaiPgRepo)`，后注册的 PG 覆盖 Git 同名）。Nacos 在 NexAI 无设施的前提下不建议引入。
6. **运行时对接提醒**：agent 侧对 skill 的消费完全通过 `ReActAgent.builder().skillRepository(...)` + `DynamicSkillMiddleware`（core 已内置），NexAI 的 AI 模块只需把自建的 repository 适配成 `AgentSkillRepository`（或直接使用官方接口）传给 agent builder；不要走已 deprecated 的 `SkillBox` 直连路线（`ReActAgent.java:4924-4932`）。若 agent 需要执行 skill 脚本，关注 harness 的 `MarketplaceStager` 物化 + `<files-root>` 模式（`MarketplaceStager.java:41-63`），NexAI 可先只做"读 SKILL.md/参考资料"级别的能力。

---

## 9. 来源清单

源码（相对 agentscope-java 根）：

1. `agentscope-core/src/main/java/io/agentscope/core/skill/repository/AgentSkillRepository.java`（SPI 接口）
2. `agentscope-core/src/main/java/io/agentscope/core/skill/repository/AgentSkillRepositoryInfo.java`
3. `agentscope-core/src/main/java/io/agentscope/core/skill/repository/FileSystemSkillRepository.java`（:83-124 构造器）
4. `agentscope-core/src/main/java/io/agentscope/core/skill/repository/ClasspathSkillRepository.java`（:78-111）
5. `agentscope-core/src/main/java/io/agentscope/core/skill/AgentSkill.java`（数据模型）
6. `agentscope-core/src/main/java/io/agentscope/core/skill/SkillBox.java`（:40-43 deprecated 说明、:162-200 工具组同步、:222-256 注册、:636-661 load 工具注册）
7. `agentscope-core/src/main/java/io/agentscope/core/skill/SkillRegistry.java`（:54）
8. `agentscope-core/src/main/java/io/agentscope/core/skill/SkillToolFactory.java`（:79-199 load_skill_through_path）
9. `agentscope-core/src/main/java/io/agentscope/core/skill/AgentSkillPromptProvider.java`（:48-209 prompt 模板）
10. `agentscope-core/src/main/java/io/agentscope/core/skill/DynamicSkillMiddleware.java`（:129-233 每轮合成）
11. `agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java`（:4460-4476 builder 字段、:4937-5014 skillRepository 系列方法、:5208-5216 middleware 安装）
12. `agentscope-core/src/main/java/io/agentscope/core/skill/util/SkillUtil.java`（:90-150 createFrom/createFromZip）
13. `agentscope-core/src/main/java/io/agentscope/core/skill/util/MarkdownSkillParser.java`（:45-118 frontmatter 解析）
14. `agentscope-core/src/main/java/io/agentscope/core/skill/util/SkillFileSystemHelper.java`（:62-249 目录布局与落盘）
15. `agentscope-core/src/main/java/io/agentscope/core/tool/Toolkit.java`（:605-618 SkillToolGroup）
16. `agentscope-extensions/agentscope-extensions-skills/agentscope-extensions-skill-git-repository/src/main/java/io/agentscope/core/skill/repository/GitSkillRepository.java`（全文）
17. `agentscope-extensions/agentscope-extensions-skills/agentscope-extensions-skill-postgresql-repository/src/main/java/io/agentscope/core/skill/repository/postgresql/PostgresSkillRepository.java`（全文）
18. `agentscope-extensions/agentscope-extensions-skills/agentscope-extensions-skill-mysql-repository/src/main/java/io/agentscope/core/skill/repository/mysql/MysqlSkillRepository.java`（存在性核对）
19. `agentscope-extensions/agentscope-extensions-nacos/agentscope-extensions-nacos-skill/src/main/java/io/agentscope/core/nacos/skill/NacosSkillRepository.java`（全文）
20. `agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgentBuilderSupport.java`（:803-852 四层合成）
21. `agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/WorkspaceSkillRepository.java`（:47-130）
22. `agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/RuntimeContextSkillRepository.java`
23. `agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/runtime/MarketplaceStager.java`（:41-90 物化）
24. `agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/runtime/SkillRuntime.java`、`SkillLoadTool.java`
25. `agentscope-harness/src/main/java/io/agentscope/harness/agent/tool/ProposeSkillTool.java`、`SkillManageTool.java`（自学习工具名）
26. `agentscope-service/service-common/src/main/java/io/agentscope/builder/runtime/config/SkillRepositoryConfigEntry.java`（agentscope.json 声明式配置）
27. `agentscope-service/service-common/src/main/java/io/agentscope/builder/runtime/config/SkillRepositorySupport.java`（反射装配，仅 filesystem/git）
28. `agentscope-service/service-common/src/main/java/io/agentscope/builder/runtime/config/AgentConfigEntry.java`（:86-87 skillRepositories 字段）

官方文档（docs/v2/zh）：

29. `docs/v2/zh/integration/skill/overview.md`
30. `docs/v2/zh/integration/skill/git-repository.md`
31. `docs/v2/zh/integration/skill/postgresql-repository.md`
32. `docs/v2/zh/integration/skill/mysql-repository.md`
33. `docs/v2/zh/integration/infrastructure/nacos.md`
34. `docs/v2/zh/docs/harness/skill.md`

示例：

35. `agentscope-examples/documentation/src/main/java/io/agentscope/examples/documentation2/skill/AgentSkillExample.java`
36. `agentscope-examples/documentation/src/main/java/io/agentscope/examples/documentation2/skill/GitSkillRepositoryExample.java`
37. `agentscope-examples/documentation/src/main/java/io/agentscope/examples/documentation2/skill/SkillWithToolGroupExample.java`
38. `agentscope-examples/documentation/src/main/java/io/agentscope/examples/documentation2/harness/skill/SkillCompositionExample.java`

repowiki 知识库：

39. `.qoder/repowiki/knowledge/zh/AgentScope Java 多模块聚合根/AgentScope 扩展聚合根（extensions）/AgentSkill 仓库扩展（Git_MySQL_PostgreSQL）/架构设计.md`
40. `.qoder/repowiki/knowledge/zh/AgentScope Java 多模块聚合根/AgentScope Java 核心运行时/Agent Skill 系统（注册、仓库与动态加载）/`
