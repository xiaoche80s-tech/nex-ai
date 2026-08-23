-- H2 内存库清理脚本（每个测试结束后执行；内存库本即随测试销毁，追加表后在此按表清理）
-- 注：Spring ScriptUtils 不能执行纯注释脚本，故保留一条无害语句占位
SELECT 1;

DELETE FROM "ai_agent_spec_version";
DELETE FROM "ai_agent_spec";
DELETE FROM "ai_channel";
DELETE FROM "ai_model";
