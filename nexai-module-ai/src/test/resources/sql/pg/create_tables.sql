-- PG 测试建表脚本（pg-test profile，直连真实 PostgreSQL）
-- 占位：各聚合工单在此追加幂等 PG 方言 DDL（CREATE IF NOT EXISTS——共享库已有表则跳过，缺表则补齐）
-- 注：Spring ScriptUtils 不能执行纯注释脚本，故保留一条无害语句占位
SELECT 1;
