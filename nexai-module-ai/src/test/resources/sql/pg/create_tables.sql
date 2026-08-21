-- PG 测试库幂等建表（S1 接缝测试直连真实 PostgreSQL，工单 06 起）
-- 与 sql/postgresql/ruoyi-vue-pro.sql 的正式 DDL 同构，改为 IF NOT EXISTS 以便在已有表的共享库上重复执行

CREATE SEQUENCE IF NOT EXISTS ai_feedback_seq START 1;
CREATE TABLE IF NOT EXISTS ai_feedback (
    id int8 NOT NULL,
    content varchar(2048) NOT NULL,
    screenshot_urls varchar(4096) NULL DEFAULT NULL,
    session_id varchar(64) NULL DEFAULT NULL,
    status int2 NOT NULL DEFAULT 10,
    submitter_id int8 NOT NULL,
    creator varchar(64) NULL DEFAULT '',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) NULL DEFAULT '',
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted int2 NOT NULL DEFAULT 0,
    tenant_id int8 NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_feedback PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS ai_channel_seq START 1;
CREATE TABLE IF NOT EXISTS ai_channel (
    id int8 NOT NULL,
    name varchar(64) NOT NULL,
    provider varchar(32) NOT NULL,
    base_url varchar(512) NOT NULL,
    api_key varchar(1024) NULL DEFAULT NULL,
    enabled bool NOT NULL DEFAULT true,
    owner_type varchar(16) NOT NULL,
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
    capabilities varchar(1024) NULL DEFAULT NULL,
    enabled bool NOT NULL DEFAULT true,
    creator varchar(64) NULL DEFAULT '',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) NULL DEFAULT '',
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted int2 NOT NULL DEFAULT 0,
    tenant_id int8 NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_model PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS ai_agent_spec_seq START 1;
CREATE TABLE IF NOT EXISTS ai_agent_spec (
    id int8 NOT NULL,
    name varchar(64) NOT NULL,
    description varchar(512) NULL DEFAULT NULL,
    icon varchar(128) NULL DEFAULT NULL,
    latest_version_no int4 NOT NULL DEFAULT 0,
    current_version_no int4 NULL DEFAULT NULL,
    draft text NULL DEFAULT NULL,
    creator varchar(64) NULL DEFAULT '',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) NULL DEFAULT '',
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted int2 NOT NULL DEFAULT 0,
    tenant_id int8 NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_agent_spec PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS ai_agent_spec_version_seq START 1;
CREATE TABLE IF NOT EXISTS ai_agent_spec_version (
    id int8 NOT NULL,
    spec_id int8 NOT NULL,
    version_no int4 NOT NULL,
    snapshot text NOT NULL,
    remark varchar(255) NULL DEFAULT NULL,
    creator varchar(64) NULL DEFAULT '',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) NULL DEFAULT '',
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted int2 NOT NULL DEFAULT 0,
    tenant_id int8 NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_agent_spec_version PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_agent_spec_version ON ai_agent_spec_version (spec_id, version_no);

CREATE SEQUENCE IF NOT EXISTS ai_session_seq START 1;
CREATE TABLE IF NOT EXISTS ai_session (
    id int8 NOT NULL,
    session_key varchar(64) NOT NULL,
    type int2 NOT NULL,
    spec_id int8 NOT NULL,
    version_no int4 NOT NULL,
    title varchar(128) NULL DEFAULT NULL,
    message_rounds int4 NOT NULL DEFAULT 0,
    creator varchar(64) NULL DEFAULT '',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) NULL DEFAULT '',
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted int2 NOT NULL DEFAULT 0,
    tenant_id int8 NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_session PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_session_key ON ai_session (session_key);
