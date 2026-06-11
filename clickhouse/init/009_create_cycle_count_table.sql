-- =============================================================================
-- 盐结晶潮解循环次数统计表
-- 使用雨流计数法(Rainflow Counting)统计RH波动循环，评估盐害疲劳损伤
-- =============================================================================

CREATE TABLE IF NOT EXISTS salt_damage.cycle_count (
    `id` String COMMENT '记录ID',
    `tomb_id` String COMMENT '墓葬ID',
    `chamber_id` String COMMENT '墓室ID',
    `device_id` Nullable(String) COMMENT '设备ID',
    `period_type` String COMMENT '统计周期: DAILY/WEEKLY/MONTHLY',
    `period_start` DateTime64(3) COMMENT '统计周期开始',
    `period_end` DateTime64(3) COMMENT '统计周期结束',
    `total_cycles` Int32 COMMENT '总循环次数',
    `full_cycles` Int32 COMMENT '完整循环次数',
    `partial_cycles` Int32 COMMENT '部分循环次数',
    `crossing_cycles` Int32 COMMENT '穿越潮解点(RH=75%)的循环次数',
    `average_range` Float64 COMMENT '平均循环幅度 %RH',
    `max_range` Float64 COMMENT '最大循环幅度 %RH',
    `min_range` Float64 COMMENT '最小循环幅度 %RH',
    `total_damage` Float64 COMMENT '总疲劳损伤值 (Miner准则)',
    `damage_level` String COMMENT '损伤等级: LOW/MEDIUM/HIGH/CRITICAL',
    `amplitude_histogram` String COMMENT '循环幅度分布直方图 JSON',
    `deliquescence_rh` Float64 DEFAULT 75.0 COMMENT '潮解点阈值 %RH',
    `sn_exponent` Float64 DEFAULT 3.0 COMMENT 'S-N曲线指数',
    `reference_cycles` Float64 DEFAULT 1000000 COMMENT '参考循环次数',
    `analysis_time` DateTime64(3) COMMENT '分析时间',
    `create_time` DateTime64(3) DEFAULT now64() COMMENT '创建时间'
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(analysis_time)
ORDER BY (tomb_id, chamber_id, period_type, analysis_time)
TTL analysis_time + INTERVAL 3 YEAR
COMMENT '盐结晶潮解循环次数统计表';

-- =============================================================================
-- 循环统计小时聚合表（物化视图源表）
-- =============================================================================

CREATE TABLE IF NOT EXISTS salt_damage.cycle_count_hourly (
    `timestamp` DateTime64(3) COMMENT '时间',
    `tomb_id` String COMMENT '墓葬ID',
    `chamber_id` String COMMENT '墓室ID',
    `total_cycles_sum` AggregateFunction(sum, Int32) COMMENT '总循环次数累加',
    `crossing_cycles_sum` AggregateFunction(sum, Int32) COMMENT '穿越循环次数累加',
    `total_damage_sum` AggregateFunction(sum, Float64) COMMENT '总损伤累加',
    `max_range_max` AggregateFunction(max, Float64) COMMENT '最大幅度',
    `sample_count` AggregateFunction(count) COMMENT '样本数'
) ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (tomb_id, chamber_id, timestamp)
TTL timestamp + INTERVAL 2 YEAR
COMMENT '循环统计小时聚合表';

-- =============================================================================
-- 物化视图：小时聚合
-- =============================================================================

CREATE MATERIALIZED VIEW IF NOT EXISTS salt_damage.cycle_count_hourly_mv
TO salt_damage.cycle_count_hourly
AS SELECT
    toStartOfHour(analysis_time) AS timestamp,
    tomb_id,
    chamber_id,
    sumState(total_cycles) AS total_cycles_sum,
    sumState(crossing_cycles) AS crossing_cycles_sum,
    sumState(total_damage) AS total_damage_sum,
    maxState(max_range) AS max_range_max,
    countState() AS sample_count
FROM salt_damage.cycle_count
GROUP BY timestamp, tomb_id, chamber_id;

-- =============================================================================
-- 验证表创建
-- =============================================================================

SELECT
    table,
    engine,
    partition_key,
    sorting_key,
    formatReadableSize(total_bytes) AS total_size,
    total_rows
FROM system.tables
WHERE database = 'salt_damage' AND table LIKE '%cycle%'
ORDER BY table;
