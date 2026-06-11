-- =============================================================================
-- ClickHouse TTL 配置更新（保留3年）
-- =============================================================================
-- 原问题：原始建表脚本未设置TTL或TTL过长
-- 修复：所有数据表设置 3 年 TTL，超期自动合并到冷存储或删除
-- =============================================================================

-- 盐离子数据表 TTL（3年）
ALTER TABLE salt_damage.salt_data
MODIFY TTL timestamp + INTERVAL 3 YEAR
SETTINGS merge_with_ttl_timeout = 3600;

-- 环境数据表 TTL（3年）
ALTER TABLE salt_damage.env_data
MODIFY TTL timestamp + INTERVAL 3 YEAR
SETTINGS merge_with_ttl_timeout = 3600;

-- 分析结果表 TTL（3年）
ALTER TABLE salt_damage.analysis_results
MODIFY TTL timestamp + INTERVAL 3 YEAR
SETTINGS merge_with_ttl_timeout = 3600;

-- 告警历史表 TTL（5年，告警数据保留更久）
ALTER TABLE salt_damage.alarm_history
MODIFY TTL alarm_time + INTERVAL 5 YEAR
SETTINGS merge_with_ttl_timeout = 3600;

-- 小时聚合表 TTL（2年，明细数据保留短一些）
ALTER TABLE salt_damage.salt_data_hourly
MODIFY TTL timestamp + INTERVAL 2 YEAR
SETTINGS merge_with_ttl_timeout = 3600;

ALTER TABLE salt_damage.env_data_hourly
MODIFY TTL timestamp + INTERVAL 2 YEAR
SETTINGS merge_with_ttl_timeout = 3600;

-- 周聚合表 TTL（5年，趋势数据保留更久）
ALTER TABLE salt_damage.salt_data_weekly
MODIFY TTL week_start + INTERVAL 5 YEAR
SETTINGS merge_with_ttl_timeout = 3600;

ALTER TABLE salt_damage.env_data_weekly
MODIFY TTL week_start + INTERVAL 5 YEAR
SETTINGS merge_with_ttl_timeout = 3600;

-- 墓葬级周聚合表 TTL（10年，宏观趋势长期保留）
ALTER TABLE salt_damage.salt_data_tomb_weekly
MODIFY TTL week_start + INTERVAL 10 YEAR
SETTINGS merge_with_ttl_timeout = 3600;

-- =============================================================================
-- 验证 TTL 设置
-- =============================================================================
SELECT
    table,
    name,
    engine,
    partition_key,
    sorting_key,
    TTL_expression,
    formatReadableSize(total_bytes) AS total_size,
    total_rows
FROM system.tables
WHERE database = 'salt_damage'
ORDER BY table;
