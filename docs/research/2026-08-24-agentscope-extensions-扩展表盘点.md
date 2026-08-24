# agentscope-java v2 agentscope-extensions 各扩展模块持久化存储（表/键/桶）盘点

> 调研日期：2026-08-24
> 调研对象：本机 `~/agent-project/agentscope-java/agentscope-extensions/` 全部 18 个子模块（含 rag/skills/mem/sandbox/channel/model/protocol/nacos/scheduler/spring-boot-starters 的全部孙模块）源码；被扩展实现的核心接口回溯 `agentscope-core`、`agentscope-harness`。
> 调研纪律：**全部结论以源码为准**（`grep CREATE TABLE` 全量 + 逐文件读完整 DDL）；`.qoder/repowiki` 未作为论据。搜索均排除 `target/`。
> 引用约定：`~/agent-project/agentscope-java/agentscope-extensions/` 简写为 `<EXT>`，`~/agent-project/agentscope-java/` 简写为 `<AS>`；行号以当前工作区文件为准。

---

## TL;DR

- 扩展层的持久化收敛为 **5 张核心表名**（`agentscope_skills`、`agentscope_skill_resources`、`agentscope_sessions`、`agentscope_store`、`agentscope_snapshots`）+ **pgvector 向量表（表名自定）**；按「模块 × 数据库方言」展开共 **11 张物理表 DDL**（见 §1-§4）。全部 DDL 均为**代码内嵌的 `CREATE TABLE IF NOT EXISTS`**，**没有任何 .sql 迁移脚本**（`find <EXT> -name "*.sql"` 无结果），也没有 flyway/liquibase。
- 表的语义只有四类：**skill 仓库**（skills 扩展，实现 core 的 `AgentSkillRepository`）、**会话状态**（mysql/postgresql/redis/cos/oss 的 `*AgentStateStore`，实现 core 的 `AgentStateStore`）、**工作区文件 KV**（`JdbcStore`/`PostgresBaseStore`/`RedisStore`/`CosBaseStore`/`OssBaseStore`，实现 harness 的 `BaseStore`）、**沙箱快照**（`*RemoteSnapshotClient`，实现 harness 的 `RemoteSnapshotClient`）。后三类由各模块的 `*DistributedStore` 聚合（harness 的 `DistributedStore` 接口）一次接线到 `HarnessAgent`。
- 最关键的表：`agentscope_sessions`（会话/记忆状态，每条消息一行、增量追加）与 `agentscope_store`（远程工作区文件的内容与版本，CAS 乐观并发）；对 NexAI 的 AI 平台模块最有直接参考价值的是 **skills 的 PG 双表**（skill + 资源子表、外键级联删除）与 **PgVectorStore**（pgvector + JSONB payload + HNSW 索引的 RAG 文档表）。
- 非表存储：redis 扩展用 4 组 key 前缀（`agentscope:session:` / `agentscope:store:` / `agentscope:sandbox:snapshots:` / `agentscope:sandbox:lock:`）；cos/oss 扩展用桶内对象 key（`agentscope/store/`、`agentscope/state/`、快照 `.tar`）。
- **无任何持久化的模块**：aistio、channel（5 子模块）、higress、mem（3 子模块）、model（6 子模块）、nacos（3 子模块）、protocol（4 子模块）、sandbox（4 子模块）、scheduler（2 子模块）、skills-git-repository、studio、training、spring-boot-starters（详见 §8）。

---

## 0. 背景：扩展实现的核心接口契约（表的读写方来源）

扩展的表都是这几个接口的实现产物，理解契约即理解读写时机：

| 接口 | 所在 | 主要方法 | 实现方（扩展） |
|---|---|---|---|
| `AgentStateStore` | `<AS>/agentscope-core/.../state/AgentStateStore.java` | `save(userId, sessionId, key, value)`（单值全量覆盖）、`save(..., List<State>)`（列表增量追加，哈希检测变化）、`get`/`getList`、`getVersioned`/`saveIfVersion`（CAS 乐观并发，`UNVERSIONED=-1`，`expectedVersion==0` 表示不存在才创建）、`exists`、`delete`、`listSessionIds(userId)` | `MysqlAgentStateStore`、`PostgresAgentStateStore`、`RedisAgentStateStore`、`CosAgentStateStore`、`OssAgentStateStore` |
| `AgentSkillRepository` | `<AS>/agentscope-core/.../skill/repository/AgentSkillRepository.java` | `getSkill(name)`、`getAllSkillNames`、`getAllSkills`、`save(List<AgentSkill>, force)`、`delete`、`skillExists`、`getRepositoryInfo`、`getSource` | `PostgresSkillRepository`、`MysqlSkillRepository`、`GitSkillRepository`、`NacosSkillRepository` |
| `BaseStore` | `<AS>/agentscope-harness/.../filesystem/remote/store/BaseStore.java` | `get(namespace, key)`、`put`、`putIfVersion`（CAS；`expectedVersion==0` = create-if-absent）、`search(namespace, limit, offset)`（前缀分页）、`delete`；namespace 为层级路径 | `JdbcStore`、`PostgresBaseStore`、`RedisStore`、`CosBaseStore`、`OssBaseStore` |
| `RemoteSnapshotClient` | `<AS>/agentscope-harness/.../sandbox/snapshot/RemoteSnapshotClient.java` | `upload(snapshotId, InputStream)`、`download`、`exists`（存沙箱 workspace 的 tar 归档） | `JdbcRemoteSnapshotClient`、`PostgresRemoteSnapshotClient`、`RedisRemoteSnapshotClient`、`Cos/OssRemoteSnapshotClient` |
| `SandboxExecutionGuard` | harness | `tryEnter` 互斥进入沙箱隔离槽 | `JdbcSandboxExecutionGuard`（MySQL `GET_LOCK`）、`PostgresSandboxExecutionGuard`（`pg_advisory_lock`）、`RedisSandboxExecutionGuard`（`SET NX PX`）——三者均**不建表** |
| `DistributedStore` | `<AS>/agentscope-harness/.../agent/DistributedStore.java` | 聚合工厂：`agentStateStore()` + `baseStore()` + `sandboxSnapshotSpec()` + `sandboxExecutionGuard()`，一次传给 `HarnessAgent.Builder#distributedStore()` | `MysqlDistributedStore`、`PostgresDistributedStore`、`RedisDistributedStore`、`CosDistributedStore`、`OssDistributedStore` |

`AgentStateStore` 契约要点（`AgentStateStore.java:L30-55`）：`(userId, sessionId)` 二元组寻址，`userId == null` 表示匿名/单租户，实现自行决定合并为存储键（SQL 里拼成 `userId:sessionId` 一列；Redis/OSS 里拼成 `userId/sessionId` 路径段），调用方不得自行拼接。

---

## 1. skills 扩展：skill 仓库双表（PG / MySQL 各一套）

### 1.1 PostgreSQL —— `PostgresSkillRepository`

文件：`<EXT>/agentscope-extensions-skills/agentscope-extensions-skill-postgresql-repository/src/main/java/io/agentscope/core/skill/repository/postgresql/PostgresSkillRepository.java`

默认建 **schema `agentscope`**（`CREATE SCHEMA IF NOT EXISTS`，:286；数据库本身由 JDBC URL 选定，不建库）。

**表 1：`agentscope.agentscope_skills`**（DDL :311-318，表名/Schema 可经 Builder 自定义）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | 自增主键，资源表外键 |
| name | VARCHAR(255) NOT NULL **UNIQUE** | skill 名（业务主键，查询入口） |
| description | TEXT NOT NULL | 描述 |
| skill_content | TEXT NOT NULL | SKILL.md 正文 |
| source | VARCHAR(255) NOT NULL | 来源标识 |
| metadata_json | TEXT NULL | 完整元数据树 JSON（新表才有；旧表运行时检测 `INFORMATION_SCHEMA.COLUMNS` 降级，仅往返 name/description，:444-488） |
| created_at / updated_at | TIMESTAMP DEFAULT CURRENT_TIMESTAMP | |

**表 2：`agentscope.agentscope_skill_resources`**（DDL :322-330）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT NOT NULL | → `agentscope_skills(id)`，**FOREIGN KEY ON DELETE CASCADE**（删 skill 自动清资源，:922-931） |
| resource_path | VARCHAR(500) NOT NULL | 资源相对路径 |
| resource_content | TEXT NOT NULL | 资源内容 |
| created_at / updated_at | TIMESTAMP | |
| | **PRIMARY KEY (id, resource_path)** | 复合主键 |

读写方（全在本类）：
- 写：`save()`（:656-729）事务内先删旧再 `insertSkill` + `insertResources`（**JDBC 批插**，每批 1000 条防超 PG 绑定参数上限，:791-834）；`delete()`/`clearAllSkills()` 靠级联。
- 读：`getSkill()`（name → id → 资源两段查，:497-555）、`getAllSkills()`（两查询内存缝合，:580-653）、`getAllSkillNames()`、`skillExists()`。
- 构造时机：`createIfNotExist=true`（Builder 默认 true，:1305）时建 schema/表；false 则仅校验存在性。

### 1.2 MySQL —— `MysqlSkillRepository`

文件：`<EXT>/agentscope-extensions-skills/agentscope-extensions-skill-mysql-repository/src/main/java/io/agentscope/core/skill/repository/mysql/MysqlSkillRepository.java`

默认建 **database `agentscope`**（`CREATE DATABASE IF NOT EXISTS ... utf8mb4_unicode_ci`，:276-279），表结构同 PG 版：

- **表 3：`agentscope_skills`**（DDL :298-307）：`id BIGINT AUTO_INCREMENT PK`、`name VARCHAR(255) UNIQUE`、`description TEXT`、`skill_content LONGTEXT`、`source VARCHAR(255)`、`metadata_json LONGTEXT NULL`、`created_at/updated_at TIMESTAMP`（`ON UPDATE CURRENT_TIMESTAMP`）。
- **表 4：`agentscope_skill_resources`**（DDL :310-320）：同 PG 版（`resource_content LONGTEXT`），PK `(id, resource_path)` + FK 级联。

读写流程与 PG 版完全同构（save 事务、批插、级联删除、metadata_json 兼容检测）。

---

## 2. mysql 扩展：三件套（sessions / store / snapshots）

模块：`<EXT>/agentscope-extensions-mysql/`（JdbcStore 经方言同时支持 MySQL/PG/SQLite/H2）。

### 2.1 `agentscope_sessions` —— `MysqlAgentStateStore`（会话状态表）

文件：`<EXT>/agentscope-extensions-mysql/src/main/java/io/agentscope/extensions/mysql/state/MysqlAgentStateStore.java`

- 建库 `agentscope`（utf8mb4，:213-216）；**表 5 DDL :234-241**：

| 列 | 类型 | 说明 |
|---|---|---|
| session_id | VARCHAR(255) NOT NULL | 实际存 `userId:sessionId`（匿名用户 `__anon__:`，slotId 拼接 :849-854） |
| state_key | VARCHAR(255) NOT NULL | 状态键（如 `agent_state`、`memory_messages`）；列表哈希存为 `{key}:_hash` 的行（:75、:526） |
| item_index | INT NOT NULL DEFAULT 0 | 单值恒 0；列表按 0,1,2... 逐行存 |
| state_data | LONGTEXT NOT NULL | 状态对象 JSON |
| version | BIGINT NOT NULL DEFAULT 0 | 乐观锁版本；**新表自带，旧表由 `ensureVersionColumn()` 运行时 `ALTER TABLE ADD COLUMN version` 补齐（:181-203）** |
| created_at / updated_at | DATETIME（updated_at `ON UPDATE CURRENT_TIMESTAMP`） | |
| | **PRIMARY KEY (session_id, state_key, item_index)** | |

读写方：
- 单值：`save()` 用 `INSERT ... ON DUPLICATE KEY UPDATE state_data=..., version=version+1`（:362-368）；`saveIfVersion()` CAS——`expectedVersion==0` 走 `insertIfAbsent`（捕 1062/23000 冲突，:452-475），否则 `UPDATE ... WHERE version=?`（:477-497）。
- 列表：`save(list)`（:517-552）——`ListHashUtil.computeHash` 与存量 `: _hash` 行比对，变化/缩短→全删重写，纯追加→只 INSERT 新增段；`getList()` 按 `item_index` 排序返回。
- 会话级：`exists()`/`delete(userId, sessionId)`（按 session_id 前缀）/`listSessionIds(userId)`（`LIKE 'user:%'` 前缀枚举）。

### 2.2 `agentscope_store` —— `JdbcStore`（工作区文件 KV 表，4 方言）

文件：`<EXT>/agentscope-extensions-mysql/src/main/java/io/agentscope/extensions/mysql/store/JdbcStore.java`（默认表名常量 :86，DDL 经方言注入 `initializeSchema()` :124-133）

**表 6 逻辑结构**（Javadoc :44-53；namespace 分段用 ASCII 单元分隔符 `0x1F` 连接并带尾分隔符，`search` 用 `LIKE 'a\x1Fb\x1F%' ESCAPE '!'` 匹配子命名空间，:58-65、:289-316）：

| 列 | MySQL（MysqlJdbcStoreDialect.java:24-33） | PostgreSQL（PostgresJdbcStoreDialect.java:23-32） | SQLite（SqliteJdbcStoreDialect.java:26-35） | H2（H2JdbcStoreDialect.java:28-37） |
|---|---|---|---|---|
| namespace_path | VARCHAR(**512**)（压在 InnoDB utf8mb4 3072 字节主键限内） | VARCHAR(2048) | TEXT | VARCHAR(2048) |
| item_key | VARCHAR(255) | VARCHAR(255) | TEXT | VARCHAR(255) |
| value_json | LONGTEXT | TEXT | TEXT | CLOB |
| version | BIGINT（insert=1，upsert +1） | BIGINT | INTEGER | BIGINT |
| updated_at | BIGINT（epoch millis） | BIGINT | INTEGER | BIGINT |
| 约束 | **PRIMARY KEY (namespace_path, item_key)**；MySQL 加 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` | 同 | 同 | 同 |

各方言提供 select/upsert/insert/casUpdate/delete/search 六类 SQL 模板（接口 `JdbcStoreDialect.java:51-100`；upsert 语法差异：MySQL `ON DUPLICATE KEY` / PG·SQLite `ON CONFLICT DO UPDATE` / H2 `MERGE INTO`）。

读写方：实现 harness `BaseStore`——`put`（upsert 版本自增）、`putIfVersion`（CAS：version=0 走 insert 撞主键判失败 :188-206；>0 走条件 UPDATE :208-218）、`search`（前缀 LIKE + LIMIT/OFFSET）、`get`/`delete`。多 JVM 共库安全（无额外锁）。**由 `MysqlDistributedStore.baseStore()` 以 `initializeSchema(true)` 装配**（MysqlDistributedStore.java:78-80）。

### 2.3 `agentscope_snapshots` —— `JdbcRemoteSnapshotClient`（沙箱快照表）

文件：`<EXT>/agentscope-extensions-mysql/src/main/java/io/agentscope/extensions/mysql/snapshot/JdbcRemoteSnapshotClient.java`

**表 7 DDL :62-69**：

| 列 | 类型 |
|---|---|
| snapshot_id | VARCHAR(512) **PRIMARY KEY** |
| data | **LONGBLOB** NOT NULL（沙箱 workspace 的 tar 归档字节） |
| created_at | TIMESTAMP DEFAULT CURRENT_TIMESTAMP |

读写：`upload`（`INSERT ... ON DUPLICATE KEY UPDATE data=...` :81-93）、`download`（:96-109，未找到抛 `FileNotFoundException`）、`exists`。构造时 `initializeSchema=true` 才建表。`JdbcSnapshotSpec` 是把它包成 harness `SandboxSnapshotSpec` 的组合类。

### 2.4 无表的配套组件

- `JdbcSandboxExecutionGuard`：MySQL 命名锁 `SELECT GET_LOCK(?, ?)` / `RELEASE_LOCK(?)`（>64 字符名先哈希），持锁连接即租约，**无表**（JdbcSandboxExecutionGuard.java:32-45、:72、:122）。

---

## 3. postgresql 扩展：同构三件套（PG 版）

模块：`<EXT>/agentscope-extensions-postgresql/`。

### 3.1 `agentscope.agentscope_sessions` —— `PostgresAgentStateStore`

文件：`<EXT>/agentscope-extensions-postgresql/src/main/java/io/agentscope/extensions/postgresql/state/PostgresAgentStateStore.java`

- `CREATE SCHEMA IF NOT EXISTS "agentscope"`（:150-158）；**表 8 DDL :163-172**：`session_id/state_key VARCHAR(255)`、`item_index INT`、`state_data TEXT`、`version BIGINT NOT NULL DEFAULT 0`（旧表由 `ensureVersionColumn()` 的 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS version` 补齐，:137-148）、`created_at/updated_at TIMESTAMP`，**PK (session_id, state_key, item_index)**。
- 与 MySQL 版逐方法同构，仅 SQL 方言不同：单值 upsert 用 `ON CONFLICT ... DO UPDATE ... version = %s.version + 1`（:268-277）；CAS 插入用 `ON CONFLICT ... DO NOTHING`（:357-376）、条件更新 :378-400；列表哈希/增量追加逻辑相同（:403-438）。session_id 同样拼 `userId:sessionId`（:668-673）。

### 3.2 `agentscope_store` —— `PostgresBaseStore`

文件：`<EXT>/agentscope-extensions-postgresql/src/main/java/io/agentscope/extensions/postgresql/store/PostgresBaseStore.java`

- **表 9 DDL（CREATE_TABLE_SQL :89-99）**：`namespace_path VARCHAR(2048)`、`item_key VARCHAR(255)`、`value_json TEXT`、`version BIGINT`、`updated_at BIGINT`，**PK (namespace_path, item_key)**；默认**不带 schema**（即落 `public`），Builder 可选 `schemaName`（会先 `CREATE SCHEMA IF NOT EXISTS`，:175-188）。
- 与 `JdbcStore` 的 PG 方言等价（namespace 0x1F 编码、CAS、search `LIKE ... ESCAPE '!'`），是独立的 PG 专用实现。测试 `PostgresBaseStoreTest.java:165` 另用自定义表名验证 DDL 模板。

### 3.3 `agentscope_snapshots` —— `PostgresRemoteSnapshotClient`

文件：`<EXT>/agentscope-extensions-postgresql/src/main/java/io/agentscope/extensions/postgresql/snapshot/PostgresRemoteSnapshotClient.java`

- **表 10 DDL :62-71**：`snapshot_id VARCHAR(512) PK`、`data BYTEA NOT NULL`（PG 字节数组，对应 MySQL 版的 LONGBLOB）、`created_at TIMESTAMP`。upload 用 `ON CONFLICT (snapshot_id) DO UPDATE`（:85-98）。
- `PostgresSnapshotSpec` 组合类（:27-34）。

### 3.4 无表配套

- `PostgresSandboxExecutionGuard`：`pg_try_advisory_lock / pg_advisory_unlock`（键经 MurmurHash3 折成 int4），**无表**（PostgresSandboxExecutionGuard.java:37-48、:79、:147）。
- `PostgresDistributedStore`：聚合以上三件 + guard。

---

## 4. rag 扩展：pgvector 表 + 外部向量库结构

### 4.1 `agentscope-extensions-rag-simple` 的 `PgVectorStore`（**表 11**，表名由使用方指定）

文件：`<EXT>/agentscope-extensions-rag/agentscope-extensions-rag-simple/src/main/java/io/agentscope/core/rag/store/PgVectorStore.java`

初始化（构造即建，`ensureTable` :430-469）：
1. `CREATE EXTENSION IF NOT EXISTS vector`（:434，要求 PG11+ 装 pgvector）；
2. 非 `public` schema 时 `CREATE SCHEMA IF NOT EXISTS`（:440；默认 schema=`public`，:118）；
3. **建表 DDL :481-498**（列名常量 :111-116）：

| 列 | 类型 | 说明 |
|---|---|---|
| id | VARCHAR(64) **PRIMARY KEY** | chunk 唯一 ID |
| embedding | **vector(dimensions)** | 向量，维度建表时定死（Builder 必填） |
| doc_id | VARCHAR(256) | 文档 ID（删除/过滤入口） |
| chunk_id | VARCHAR(256) | 分块 ID |
| content | TEXT | 原文分块 |
| payload | **JSONB** | 自定义元数据（`?::jsonb` 写入 :539） |

4. 两个索引（:504-524）：`idx_{schema}_{table}_doc_id`（B-tree，按 doc_id 过滤/整文档删除）、`idx_{schema}_{table}_vector` **USING hnsw**（操作类随距离类型：`vector_l2_ops`/`vector_ip_ops`/`vector_cosine_ops`，枚举 :141-158）。

读写方：实现同包 `VDBStoreBase` 接口——`add()` upsert（`ON CONFLICT (id) DO UPDATE`，维度校验 :285-291）、`search()` 按 `ORDER BY embedding <=> ?`（L2 `<->`、IP `<#>`）+ 可选分数阈值、`delete(docId)` 整文档删除；Reactor `Mono` 包装在 boundedElastic 上执行。

### 4.2 rag-simple 其余向量存储（非关系表，建 collection/index）

| 类 | 存储结构 | 出处 |
|---|---|---|
| `MilvusStore` | Milvus collection：`id`(VarChar64, PK)、`vector`(FloatVector(dim))、`doc_id`(VarChar256)、`chunk_id`、`content`(VarChar65535)、`payload`(JSON)，开 dynamic field；向量 AUTOINDEX + metricType；必要时先建 database | MilvusStore.java:417-490（字段常量 :108-113） |
| `ElasticsearchStore` | ES 索引 mapping：`id`/`doc_id`/`chunk_id`(keyword)、`content`(text, index=false)、`vector`(**dense_vector**，dims + similarity)、`payload`；启动 `ensureIndex()` 自动建 | ElasticsearchStore.java:93-98、:331-375 |
| `QdrantStore` | Qdrant collection：`VectorParams(size=dimensions, Distance.Cosine)`；point ID 为由 doc_id/chunk_id/content 生成的确定性 UUID；元数据全放 payload | QdrantStore.java:391-397（createCollection）、:66、:409-411 |
| `InMemoryStore`（rag-simple） | 进程内 Map，无持久化 | InMemoryStore.java |

### 4.3 rag 其余子模块

`rag-bailian`、`rag-dify`、`rag-haystack`、`rag-ragflow`：均为对应外部 RAG 平台的 HTTP API 客户端（如 DifyKnowledge/DifyRAGConfig），**知识库存储在平台侧，本仓库无表**。

---

## 5. redis 扩展：无表，4 组 key 前缀 + 数据结构

模块：`<EXT>/agentscope-extensions-redis/`。`RedisDistributedStore`（总前缀默认 `agentscope:`）聚合以下组件。

### 5.1 `RedisAgentStateStore`（实现 `AgentStateStore`；key 布局 Javadoc :42-50）

默认前缀 **`agentscope:session:`**（:180）。slotId = `{userId}/{sessionId}`（匿名 `__anon__/`，:662-673）：

| key | Redis 结构 | 内容 |
|---|---|---|
| `{prefix}{user}/{session}:{stateKey}` | String | 单值状态 JSON |
| 同上 + `:ver` | String | 乐观锁版本（`VERSION_SUFFIX=":ver"`，RedisStateVersionSupport.java:27、:77-79） |
| `{prefix}{user}/{session}:{stateKey}:list` | List | 列表状态逐项 JSON（RPUSH 增量追加） |
| 同上 + `:_hash` | String | 列表内容哈希（变更检测） |
| `{prefix}{user}/{session}:_keys` | Set | 该会话全部 state key 登记（delete 按它反查清删 :618-636；exists 判空 :601-616） |

CAS 由 Lua 脚本 `SAVE_SCRIPT` 原子完成（payload/version/keys 三 key，RedisStateVersionSupport.java:92-94）；支持 Jedis/Lettuce/Redisson 三客户端适配。`listSessionIds` 用 `SCAN` 模式 `{prefix}{user}/*:_keys`（:639-660）。

### 5.2 `RedisStore`（实现 `BaseStore`；前缀 `agentscope:store:`，:60）

- item：`{prefix}item:{ns\x1F...}{key}` → **Hash**，字段 `value`（JSON）+ `version`（:235、toItem :200-213）。
- 索引：`{prefix}idx:{ns}` → **ZSet**（member=item key，`ZRANGEBYLEX` 字典序分页，search :167-186）。
- put/putIfVersion/delete 由 Lua（`PUT_SCRIPT`/`PUT_IF_VERSION_SCRIPT`/`DELETE_SCRIPT`）保证 item 与索引原子一致（:140-197）。

### 5.3 `RedisRemoteSnapshotClient`（实现 `RemoteSnapshotClient`）

默认前缀 **`agentscope:sandbox:snapshots:`**（:86），key = `{prefix}{snapshotId}.tar`（:81），String 存 tar 字节，可选 TTL。

### 5.4 `RedisSandboxExecutionGuard`（实现 `SandboxExecutionGuard`）

默认前缀 **`agentscope:sandbox:lock:`**（:194），key 形如 `agentscope:sandbox:lock:agent:{agentId}` / `...:global:__global__}`（Javadoc 示例 :49-53）；`SET NX PX <ttlMs>` 写唯一 token 获租约，释放时 Lua 校验 token 一致才删（防误删他人锁），是 `SandboxExecutionGuard` 的参考实现。

---

## 6. cos / oss 扩展：对象存储桶内 key 约定（无表）

两模块（腾讯 COS / 阿里 OSS）是镜像实现，各含 5 个类（StateStore/BaseStore/RemoteSnapshotClient/SnapshotSpec/DistributedStore）：

| 组件 | 对象 key 模式（默认前缀） | 内容 | 出处 |
|---|---|---|---|
| `CosAgentStateStore` / `OssAgentStateStore` | `agentscope/state/{userId}/{sessionId}/{stateKey}.json`（`JSON_SUFFIX=".json"`；列表为目录下多对象或按实现展开） | 状态 JSON 对象 | CosAgentStateStore.java:73-75、:241-243、:252-256；OssAgentStateStore.java:68、:255-259 |
| `CosBaseStore` / `OssBaseStore` | 数据：`agentscope/store/{ns0}/{ns1}/.../{key}.json`；版本计数：同路径 `{key}.version` | `Map<String,Object>` JSON；`.version` 为单独小对象存版本号（CAS 靠「读版本→写数据→写版本」） | CosBaseStore.java:49、:70-72、:209-214 |
| `Cos/OssRemoteSnapshotClient` | `{keyPrefix}{snapshotId}.tar`（前缀可配，未配时仅归一化） | 快照 tar 字节 | CosRemoteSnapshotClient.java:96-100；OssRemoteSnapshotClient.java:70-74 |

namespace 用 `/` 分段（CosBaseStore.java:228-232），search 靠对象存储 list-objects 前缀枚举 + 后缀过滤（:144-186）。

---

## 7. 表 × 模块 × 读写方总表

| # | 表名（默认） | 数据库/Schema | 所属扩展 | DDL 出处 | 写入时机 / 读取场景 |
|---|---|---|---|---|---|
| 1 | `agentscope.agentscope_skills` | PG / schema `agentscope` | skill-postgresql-repository | PostgresSkillRepository.java:311 | `AgentSkillRepository.save/delete`；skill 加载（agent 启动装配 toolkit） |
| 2 | `agentscope.agentscope_skill_resources` | 同上 | 同上 | 同文件 :322 | 随 skill 行级联写删；`getSkill/getAllSkills` 二段查 |
| 3 | `agentscope.agentscope_skills` | MySQL / 库 `agentscope` | skill-mysql-repository | MysqlSkillRepository.java:299 | 同 1（MySQL 版） |
| 4 | `agentscope.agentscope_skill_resources` | 同上 | 同上 | 同文件 :311 | 同 2 |
| 5 | `agentscope.agentscope_sessions` | MySQL / 库 `agentscope` | mysql | MysqlAgentStateStore.java:234 | 每轮对话后持久化会话状态/记忆（列表增量）；恢复会话读回；CAS 版本防并发覆盖 |
| 6 | `agentscope.agentscope_sessions` | PG / schema `agentscope` | postgresql | PostgresAgentStateStore.java:163 | 同 5（PG 版） |
| 7 | `agentscope_store` | MySQL（VARCHAR512）/ PG / SQLite / H2 | mysql（JdbcStore 多方言） | Mysql/Postgres/Sqlite/H2JdbcStoreDialect.java:24/23/26/28 | 远程工作区文件 KV：写文件→put，读→get，目录枚举→search；CAS 防多实例丢失更新 |
| 8 | `agentscope_store` | PG（默认 public，可配 schema） | postgresql | PostgresBaseStore.java:91 | 同 7（PG 专用实现） |
| 9 | `agentscope_snapshots` | MySQL（LONGBLOB） | mysql | JdbcRemoteSnapshotClient.java:63 | 沙箱暂停/迁移时 upload tar；恢复时 download；exists 探测 |
| 10 | `agentscope_snapshots` | PG（BYTEA） | postgresql | PostgresRemoteSnapshotClient.java:65 | 同 9（PG 版） |
| 11 | （表名自定，默认 public） | PG + pgvector 扩展 | rag-simple | PgVectorStore.java:483（索引 :509/:519，EXTENSION :434） | RAG 文档入库 add（upsert chunk）；检索 search（hnsw + 距离算子）；delete(docId) |

---

## 8. 无持久化表的模块清单及原因

| 模块（子模块） | 原因（源码依据） |
|---|---|
| `aistio` | 控制面/数据面桥接：HTTP ContractProvider、gRPC 传输、SessionBridge（SessionBridge.java 等），纯网络转发，无存储 |
| `channel`（dingtalk/feishu/github/gitlab/wecom） | IM 渠道适配：各子模块仅 StreamClient/OutboundClient/InboundMapper 等 webhook 与 API 客户端 |
| `cos` / `oss` | 有持久化但**非表**——对象存储桶内 key（见 §6） |
| `higress` | Higress MCP 网关客户端封装（HigressMcpClientWrapper 等 4 类） |
| `mem`（mem0/reme/memory-bailian） | 长期记忆托管给外部平台：`Mem0Client`/`ReMeClient`/`BailianMemoryClient` 均为 OkHttp HTTP 客户端（Mem0Client.java:24-26），记忆数据存平台侧 |
| `model`（openai/dashscope/anthropic/gemini/ollama + e2e-tests） | 模型 API 客户端，无本地状态 |
| `nacos`（a2a/prompt/skill） | `NacosSkillRepository` 经 Nacos `AiService` 下载 skill ZIP（只读，save/delete 为 no-op，NacosSkillRepository.java:41-52）；a2a 注册与 prompt 监听均落在 Nacos 配置中心侧 |
| `protocol`（a2a/agent-protocol/agui/chat-completions-web） | 协议适配层（A2A/AG-UI/OpenAI chat-completions），会话态在内存 |
| `redis` | 有持久化但**非表**——Redis key 结构（见 §5） |
| `sandbox`（agentrun/daytona/e2b/kubernetes） | 远程沙箱创建/编排客户端（如 KubernetesSandboxClient 操作 Pod），沙箱内文件在容器侧 |
| `scheduler`（common/quartz/xxl-job） | Quartz 用 `new StdSchedulerFactory().getScheduler()` 默认 **RAMJobStore**（QuartzAgentScheduler.java:676；JDBCJobStore 仅在注释中留给集群部署，代码不建 QRTZ_ 表）；xxl-job 是执行器（XxlJobExecutor），任务表在 XXL-Job admin 服务侧 |
| `skills` 之 `skill-git-repository` | `GitSkillRepository` clone 远端仓库到本地目录读 SKILL.md（GitSkillRepository.java:42、:101-113），无库表 |
| `studio` | Studio WebSocket 客户端 + 消息 hook + OpenTelemetry tracing（StudioWebSocketClient/TelemetryTracer 等），无存储 |
| `training` | 训练 runner + Trinity 后端 HTTP 客户端 + 进程内 RunRegistry/TaskExecutionRegistry（内存注册表），无库表 |
| `spring-boot-starters`（含 admin/multitenancy 等 12 个） | 装配层。admin starter 的 `InMemoryAgentRegistry`/`SnapshotStore`/`MetricsRecorder` 均内存（目录内 grep 无 jdbc/redis/DataSource）；multitenancy starter 仅有 .flattened-pom.xml，无源码 |

---

## 9. 对 NexAI 的参考要点

1. **DDL 全部代码自管**（`CREATE TABLE IF NOT EXISTS`，无迁移脚本）：与 NexAI 「PG 专属能力直连真实 PostgreSQL」的测试策略兼容——扩展模块用 testcontainers/真实 PG 即可验证；但 NexAI 落地时建议改为 sql 脚本/Flyway 管理，代码内建表仅留给测试。
2. **`agentscope_sessions` 的三键复合主键 + item_index 行转列**是消息列表增量持久化的关键设计（纯追加只 INSERT 新段，`: _hash` 行检测中途修改），`version` 列支持会话级 CAS。NexAI 的会话调试/记忆功能可复用该表结构，但需叠加租户列（框架原生无多租户概念，slotId 只有 userId/sessionId 两段）。
3. **skills 双表**（主表 + 资源子表、外键级联、`metadata_json` 兼容列）与 **PgVectorStore**（id/embedding/doc_id/chunk_id/content/payload JSONB + hnsw）分别给出了 NexAI「skill 仓库」与「知识库向量存储」的现成 schema 模板，均为 DDD 之外的基础设施适配器风格（贫血 SQL 包装）。
4. **逻辑删除/时间戳惯例不同**：扩展表没有 `deleted` 软删列，删除即物理 DELETE（skill 靠 FK CASCADE）；接入 NexAI 多租户体系时这两个差异必须显式处理。
