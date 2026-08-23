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
    current_version_no int4 NULL DEFAULT NULL,
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

CREATE SEQUENCE IF NOT EXISTS ai_agent_spec_version_seq START 1;
CREATE TABLE IF NOT EXISTS ai_agent_spec_version (
    id int8 NOT NULL,
    spec_id int8 NOT NULL,
    version_no int4 NOT NULL,
    config text NOT NULL,
    note varchar(255) NULL DEFAULT NULL,
    creator varchar(64) NULL DEFAULT '',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) NULL DEFAULT '',
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted int2 NOT NULL DEFAULT 0,
    tenant_id int8 NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_agent_spec_version PRIMARY KEY (id)
);
-- 同一规格内版本号唯一（并发发布兜底）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_agent_spec_version ON ai_agent_spec_version (
    tenant_id, spec_id, version_no) WHERE deleted = 0;

CREATE SEQUENCE IF NOT EXISTS ai_channel_seq START 1;
CREATE TABLE IF NOT EXISTS ai_channel (
    id int8 NOT NULL,
    name varchar(64) NOT NULL,
    provider varchar(32) NOT NULL,
    base_url varchar(512) NOT NULL,
    api_key varchar(1024) NULL DEFAULT NULL,
    enabled bool NOT NULL DEFAULT true,
    owner_type varchar(16) NOT NULL DEFAULT 'tenant',
    creator varchar(64) NULL DEFAULT '',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) NULL DEFAULT '',
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted int2 NOT NULL DEFAULT 0,
    tenant_id int8 NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_channel PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS ai_model_seq START 1;
CREATE TABLE IF NOT EXISTS ai_model (
    id int8 NOT NULL,
    channel_id int8 NOT NULL,
    model_id varchar(128) NOT NULL,
    name varchar(64) NOT NULL,
    context_window int4 NULL DEFAULT NULL,
    input_price numeric(12, 6) NULL DEFAULT NULL,
    output_price numeric(12, 6) NULL DEFAULT NULL,
    enabled bool NOT NULL DEFAULT true,
    creator varchar(64) NULL DEFAULT '',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) NULL DEFAULT '',
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted int2 NOT NULL DEFAULT 0,
    tenant_id int8 NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_model PRIMARY KEY (id)
);
-- 同渠道下模型标识唯一（仅存活行；应用层预校验 + 此索引兜底并发）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_model_channel_model ON ai_model (channel_id, model_id) WHERE deleted = 0;

CREATE SEQUENCE IF NOT EXISTS ai_session_seq START 1;
CREATE TABLE IF NOT EXISTS ai_session (
    id int8 NOT NULL,
    session_key varchar(64) NOT NULL,
    title varchar(128) NOT NULL,
    type varchar(16) NOT NULL DEFAULT 'DEBUG',
    user_id int8 NULL DEFAULT NULL,
    spec_id int8 NOT NULL,
    version_no int4 NULL DEFAULT NULL,
    status varchar(16) NOT NULL DEFAULT 'READY',
    rounds text NOT NULL DEFAULT '[]',
    pending_confirmations text NULL DEFAULT NULL,
    creator varchar(64) NULL DEFAULT '',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) NULL DEFAULT '',
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted int2 NOT NULL DEFAULT 0,
    tenant_id int8 NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_session PRIMARY KEY (id)
);
-- 会话业务键全局唯一（仅存活行）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_session_key ON ai_session (tenant_id, session_key) WHERE deleted = 0;

CREATE SEQUENCE IF NOT EXISTS ai_skill_seq START 1;
CREATE TABLE IF NOT EXISTS ai_skill (
    id int8 NOT NULL,
    name varchar(64) NOT NULL,
    description varchar(512) NOT NULL,
    owner_level varchar(16) NOT NULL DEFAULT 'TENANT',
    owner_user_id int8 NULL DEFAULT NULL,
    current_version_no int4 NULL DEFAULT NULL,
    creator varchar(64) NULL DEFAULT '',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) NULL DEFAULT '',
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted int2 NOT NULL DEFAULT 0,
    tenant_id int8 NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_skill PRIMARY KEY (id)
);
-- 同归属下技能名称唯一（部分唯一索引：仅存活行）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_skill_name ON ai_skill (
    owner_level, tenant_id, COALESCE(owner_user_id, 0), name) WHERE deleted = 0;

CREATE SEQUENCE IF NOT EXISTS ai_skill_version_seq START 1;
CREATE TABLE IF NOT EXISTS ai_skill_version (
    id int8 NOT NULL,
    skill_id int8 NOT NULL,
    version_no int4 NOT NULL,
    content text NOT NULL,
    note varchar(255) NULL DEFAULT NULL,
    creator varchar(64) NULL DEFAULT '',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) NULL DEFAULT '',
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted int2 NOT NULL DEFAULT 0,
    tenant_id int8 NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_skill_version PRIMARY KEY (id)
);
-- 同一 skill 内版本号唯一（并发兜底）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_skill_version ON ai_skill_version (
    tenant_id, skill_id, version_no) WHERE deleted = 0;
