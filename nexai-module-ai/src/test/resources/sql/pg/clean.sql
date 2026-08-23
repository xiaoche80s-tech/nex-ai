-- PG 测试按测试租户精确清理（专用租户 999999 与 local 手动验收数据互不影响）
-- 占位：各聚合工单在此追加按租户清理的 DELETE 语句（WHERE tenant_id = 999999）
-- 注：Spring ScriptUtils 不能执行纯注释脚本，故保留一条无害语句占位
SELECT 1;
