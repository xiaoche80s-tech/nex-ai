-- PG 测试建表脚本（pg-test profile，直连真实 PostgreSQL）
-- 与 sql/postgresql/ruoyi-vue-pro.sql 的正式 DDL 同构，改为 IF NOT EXISTS 以便在已有表的共享库上重复执行
-- 注：Spring ScriptUtils 不能执行纯注释脚本，故保留一条无害语句占位
SELECT 1;

CREATE SEQUENCE IF NOT EXISTS ai_agent_spec_seq START 1;
CREATE TABLE IF NOT EXISTS ai_agent_spec (
    id int8 NOT NULL,
    name varchar(64) NOT NULL,
    spec_code varchar(64) NOT NULL,
    icon varchar(128) NULL DEFAULT NULL,
    owner_level varchar(16) NOT NULL DEFAULT 'TENANT',
    owner_user_id int8 NULL DEFAULT NULL,
    draft text NULL DEFAULT NULL,
    creator varchar(64) NULL DEFAULT '',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) NULL DEFAULT '',
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted int2 NOT NULL DEFAULT 0,
    tenant_id int8 NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_agent_spec PRIMARY KEY (id)
);
-- spec_code 唯一性按归属层级（部分唯一索引：仅存活行；COALESCE 使非用户级 owner_user_id=NULL 也参与判重）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_agent_spec_code ON ai_agent_spec (
    owner_level, tenant_id, COALESCE(owner_user_id, 0), spec_code) WHERE deleted = 0;
