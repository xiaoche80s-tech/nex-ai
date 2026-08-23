-- PG 测试按测试租户精确清理（专用租户 999999 与 local 手动验收数据互不影响）
-- 注：Spring ScriptUtils 不能执行纯注释脚本，故保留一条无害语句占位
SELECT 1;

DELETE FROM ai_agent_spec_version WHERE tenant_id = 999999;
DELETE FROM ai_agent_spec WHERE tenant_id = 999999;
DELETE FROM ai_model WHERE tenant_id = 999999;
DELETE FROM ai_channel WHERE tenant_id = 999999;

DELETE FROM ai_session WHERE tenant_id = 999999;

DELETE FROM ai_skill_version WHERE tenant_id = 999999;
DELETE FROM ai_skill WHERE tenant_id = 999999;
