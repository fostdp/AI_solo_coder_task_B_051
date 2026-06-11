-- =============================================================================
-- 存储层优化：ClickHouse 分区改造 + 周均盐分物化视图
-- 版本: v2.0.0
-- 问题: 按天分区导致跨季度查询慢（90+分区扫描）
-- 修复: 改用按月分区 + 新增周聚合物化视图
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Part 1: 主表分区改造（从按天 → 按月）
-- -----------------------------------------------------------------------------

-- 由于 ClickHouse 不支持直接修改 PARTITION BY，
-- 需要通过新建表 → 数据迁移 → 交换表名的方式完成。
-- 以下为完整迁移步骤。

-- Step 1.1: 新建按月分区的盐离子数据表（新版）
CREATE TABLE IF NOT EXISTS salt_damage.salt_data_v2 (
    `timestamp` DateTime64(3) COMMENT '采集时间',
    `device_id` String COMMENT '设备ID',
    `tomb_id` String COMMENT '墓葬ID',
    `chamber_id` String COMMENT '墓室ID',
    `na_plus` Float64 COMMENT 'Na⁺浓度 mg/cm²',
    `ca2_plus` Float64 COMMENT 'Ca²⁺浓度 mg/cm²',
    `so42_minus` Float64 COMMENT 'SO₄²⁻浓度 mg/cm²',
    `cl_minus` Float64 COMMENT 'Cl⁻浓度 mg/cm²',
    `total_salt` Float64 COMMENT '盐分总量 mg/cm²',
    `position_x` Float64 COMMENT 'X坐标',
    `position_y` Float64 COMMENT 'Y坐标',
    `position_z` Float64 COMMENT 'Z坐标'
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (tomb_id, device_id, timestamp)
TTL timestamp + INTERVAL 5 YEAR
COMMENT '盐离子监测数据表（按月分区优化版）';

-- Step 1.2: 迁移历史数据（按分区分批执行，避免一次性迁移过大）
-- 建议按月份分批 INSERT INTO ... SELECT
-- 示例（手动替换 YYYYMM）:
-- INSERT INTO salt_damage.salt_data_v2
-- SELECT * FROM salt_damage.salt_data
-- WHERE toYYYYMM(timestamp) = 202401;

-- Step 1.3: 数据迁移完成后，交换表名（需要短暂停写）
-- RENAME TABLE salt_damage.salt_data TO salt_damage.salt_data_old;
-- RENAME TABLE salt_damage.salt_data_v2 TO salt_damage.salt_data;

-- Step 1.4: 验证数据一致性后删除旧表
-- DROP TABLE IF EXISTS salt_damage.salt_data_old;

-- -----------------------------------------------------------------------------
-- Part 1.5: 微环境数据表同样改造（按月分区）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS salt_damage.env_data_v2 (
    `timestamp` DateTime64(3) COMMENT '采集时间',
    `device_id` String COMMENT '设备ID',
    `tomb_id` String COMMENT '墓葬ID',
    `chamber_id` String COMMENT '墓室ID',
    `temperature` Float64 COMMENT '温度 ℃',
    `humidity` Float64 COMMENT '相对湿度 %',
    `wind_speed` Float64 COMMENT '风速 m/s',
    `position_x` Float64 COMMENT 'X坐标',
    `position_y` Float64 COMMENT 'Y坐标',
    `position_z` Float64 COMMENT 'Z坐标'
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (tomb_id, device_id, timestamp)
TTL timestamp + INTERVAL 5 YEAR
COMMENT '微环境监测数据表（按月分区优化版）';

-- -----------------------------------------------------------------------------
-- Part 2: 新增周聚合表 + 物化视图（核心优化）
-- -----------------------------------------------------------------------------

-- 设计目标:
-- - 跨季度趋势查询直接走周聚合表，数据量降低约 84 倍（168 条/周 → 1 条/周）
-- - 盐害分析页默认展示周均数据，明细数据按需加载

-- Step 2.1: 盐离子周聚合表
CREATE TABLE IF NOT EXISTS salt_damage.salt_data_weekly (
    `week_start` DateTime64(3) COMMENT '周起始时间（周一零点）',
    `device_id` String COMMENT '设备ID',
    `tomb_id` String COMMENT '墓葬ID',
    `chamber_id` String COMMENT '墓室ID',
    `na_plus_avg` Float64 COMMENT 'Na⁺周均浓度',
    `na_plus_max` Float64 COMMENT 'Na⁺周最大浓度',
    `na_plus_min` Float64 COMMENT 'Na⁺周最小浓度',
    `na_plus_trend` Float64 COMMENT 'Na⁺周变化率(本周-上周)/上周*100%',
    `ca2_plus_avg` Float64 COMMENT 'Ca²⁺周均浓度',
    `ca2_plus_max` Float64 COMMENT 'Ca²⁺周最大浓度',
    `ca2_plus_min` Float64 COMMENT 'Ca²⁺周最小浓度',
    `ca2_plus_trend` Float64 COMMENT 'Ca²⁺周变化率',
    `so42_minus_avg` Float64 COMMENT 'SO₄²⁻周均浓度',
    `so42_minus_max` Float64 COMMENT 'SO₄²⁻周最大浓度',
    `so42_minus_min` Float64 COMMENT 'SO₄²⁻周最小浓度',
    `so42_minus_trend` Float64 COMMENT 'SO₄²⁻周变化率',
    `cl_minus_avg` Float64 COMMENT 'Cl⁻周均浓度',
    `cl_minus_max` Float64 COMMENT 'Cl⁻周最大浓度',
    `cl_minus_min` Float64 COMMENT 'Cl⁻周最小浓度',
    `cl_minus_trend` Float64 COMMENT 'Cl⁻周变化率',
    `total_salt_avg` Float64 COMMENT '盐分周均总量',
    `total_salt_max` Float64 COMMENT '盐分周最大总量',
    `total_salt_min` Float64 COMMENT '盐分周最小总量',
    `total_salt_trend` Float64 COMMENT '盐分总量周变化率',
    `risk_days` UInt32 COMMENT '本周盐分超标天数 (>5mg/cm²)',
    `sample_count` UInt32 COMMENT '本周采样数'
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(week_start)
ORDER BY (tomb_id, device_id, week_start)
TTL week_start + INTERVAL 10 YEAR
COMMENT '盐离子数据周聚合表（趋势分析用）';

-- Step 2.2: 盐离子周聚合物化视图
-- 从小时聚合表进一步聚合到周粒度（比直接从明细表聚合高效）
CREATE MATERIALIZED VIEW IF NOT EXISTS salt_damage.salt_data_weekly_mv
TO salt_damage.salt_data_weekly
AS SELECT
    toStartOfWeek(timestamp, 1) AS week_start,  -- 1 = 周一为一周起始
    device_id,
    tomb_id,
    chamber_id,
    avg(na_plus_avg) AS na_plus_avg,
    max(na_plus_max) AS na_plus_max,
    min(na_plus_avg) AS na_plus_min,
    0 AS na_plus_trend,  -- 趋势通过查询时计算或后续脚本更新
    avg(ca2_plus_avg) AS ca2_plus_avg,
    max(ca2_plus_max) AS ca2_plus_max,
    min(ca2_plus_avg) AS ca2_plus_min,
    0 AS ca2_plus_trend,
    avg(so42_minus_avg) AS so42_minus_avg,
    max(so42_minus_max) AS so42_minus_max,
    min(so42_minus_avg) AS so42_minus_min,
    0 AS so42_minus_trend,
    avg(cl_minus_avg) AS cl_minus_avg,
    max(cl_minus_max) AS cl_minus_max,
    min(cl_minus_avg) AS cl_minus_min,
    0 AS cl_minus_trend,
    avg(total_salt_avg) AS total_salt_avg,
    max(total_salt_max) AS total_salt_max,
    min(total_salt_avg) AS total_salt_min,
    0 AS total_salt_trend,
    countIf(total_salt_max > 5) AS risk_days,  -- 当日最大超标即计入风险
    sum(sample_count) AS sample_count
FROM salt_damage.salt_data_hourly
GROUP BY week_start, device_id, tomb_id, chamber_id;

-- Step 2.3: 微环境周聚合表
CREATE TABLE IF NOT EXISTS salt_damage.env_data_weekly (
    `week_start` DateTime64(3) COMMENT '周起始时间',
    `device_id` String COMMENT '设备ID',
    `tomb_id` String COMMENT '墓葬ID',
    `chamber_id` String COMMENT '墓室ID',
    `temperature_avg` Float64 COMMENT '周平均温度',
    `temperature_max` Float64 COMMENT '周最高温度',
    `temperature_min` Float64 COMMENT '周最低温度',
    `temperature_trend` Float64 COMMENT '温度周变化率',
    `humidity_avg` Float64 COMMENT '周平均湿度',
    `humidity_max` Float64 COMMENT '周最高湿度',
    `humidity_min` Float64 COMMENT '周最低湿度',
    `humidity_trend` Float64 COMMENT '湿度周变化率',
    `humidity_over_75_hours` Float64 COMMENT '本周湿度>75%累计小时数',
    `humidity_over_75_days` UInt32 COMMENT '本周湿度>75%天数',
    `wind_speed_avg` Float64 COMMENT '周平均风速',
    `wind_speed_max` Float64 COMMENT '周最大风速',
    `sample_count` UInt32 COMMENT '本周采样数'
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(week_start)
ORDER BY (tomb_id, device_id, week_start)
TTL week_start + INTERVAL 10 YEAR
COMMENT '微环境数据周聚合表（趋势分析用）';

-- Step 2.4: 微环境周聚合物化视图
CREATE MATERIALIZED VIEW IF NOT EXISTS salt_damage.env_data_weekly_mv
TO salt_damage.env_data_weekly
AS SELECT
    toStartOfWeek(timestamp, 1) AS week_start,
    device_id,
    tomb_id,
    chamber_id,
    avg(temperature_avg) AS temperature_avg,
    max(temperature_max) AS temperature_max,
    min(temperature_min) AS temperature_min,
    0 AS temperature_trend,
    avg(humidity_avg) AS humidity_avg,
    max(humidity_max) AS humidity_max,
    min(humidity_min) AS humidity_min,
    0 AS humidity_trend,
    sum(humidity_over_75_duration) AS humidity_over_75_hours,
    countIf(humidity_avg > 75) AS humidity_over_75_days,
    avg(wind_speed_avg) AS wind_speed_avg,
    max(wind_speed_max) AS wind_speed_max,
    sum(sample_count) AS sample_count
FROM salt_damage.env_data_hourly
GROUP BY week_start, device_id, tomb_id, chamber_id;

-- -----------------------------------------------------------------------------
-- Part 3: 性能优化辅助视图（按墓葬聚合的周视图）
-- -----------------------------------------------------------------------------

-- 盐离子墓葬级周视图（用于墓葬间横向对比）
CREATE TABLE IF NOT EXISTS salt_damage.salt_data_tomb_weekly (
    `week_start` DateTime64(3) COMMENT '周起始时间',
    `tomb_id` String COMMENT '墓葬ID',
    `chamber_count` UInt32 COMMENT '监测墓室数',
    `device_count` UInt32 COMMENT '传感器数量',
    `total_salt_avg` Float64 COMMENT '全墓周均盐分',
    `total_salt_max` Float64 COMMENT '全墓周最大盐分',
    `high_risk_points` UInt32 COMMENT '高风险监测点数量(>5mg/cm²)',
    `risk_level` String COMMENT '综合风险等级(LOW/MEDIUM/HIGH/CRITICAL)'
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(week_start)
ORDER BY (tomb_id, week_start)
TTL week_start + INTERVAL 10 YEAR
COMMENT '墓葬级盐离子周聚合表';

CREATE MATERIALIZED VIEW IF NOT EXISTS salt_damage.salt_data_tomb_weekly_mv
TO salt_damage.salt_data_tomb_weekly
AS SELECT
    week_start,
    tomb_id,
    uniq(chamber_id) AS chamber_count,
    uniq(device_id) AS device_count,
    avg(total_salt_avg) AS total_salt_avg,
    max(total_salt_max) AS total_salt_max,
    countIf(total_salt_avg > 5) AS high_risk_points,
    multiIf(
        max(total_salt_max) > 8, 'CRITICAL',
        max(total_salt_max) > 5, 'HIGH',
        max(total_salt_max) > 3, 'MEDIUM',
        'LOW'
    ) AS risk_level
FROM salt_damage.salt_data_weekly
GROUP BY week_start, tomb_id;

-- -----------------------------------------------------------------------------
-- Part 4: 查询优化建议
-- -----------------------------------------------------------------------------

-- 优化前: 跨一年趋势查询需扫描 365 个分区
-- SELECT * FROM salt_damage.salt_data WHERE timestamp >= '2024-01-01' AND timestamp < '2025-01-01';
-- 扫描分区数: 365

-- 优化后: 跨一年趋势查询仅需扫描 12 个分区 + 走周聚合表
-- SELECT * FROM salt_damage.salt_data_weekly WHERE week_start >= '2024-01-01' AND week_start < '2025-01-01';
-- 扫描分区数: 12，数据量降低 ~84 倍

-- 分区裁剪验证:
-- EXPLAIN SELECT count() FROM salt_damage.salt_data
-- WHERE timestamp >= '2024-03-01' AND timestamp < '2024-06-01';
-- 预期仅扫描 3 个分区 (202403, 202404, 202405)
