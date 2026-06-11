-- =============================================================================
-- 壁画颜料层起甲风险评估表
-- 结合盐结晶压力和颜料层附着力，逻辑回归预测起甲概率
-- =============================================================================

CREATE TABLE IF NOT EXISTS salt_damage.delamination_risk (
    `id` String COMMENT '记录ID',
    `tomb_id` String COMMENT '墓葬ID',
    `chamber_id` String COMMENT '墓室ID',
    `mural_id` Nullable(String) COMMENT '壁画ID',
    `pigment_type` String COMMENT '颜料类型: mineral/plant/synthetic',
    `mural_age` Int32 COMMENT '壁画年代 年',
    `crystallization_pressure` Float64 COMMENT '盐结晶压力 MPa',
    `adhesion_strength` Float64 COMMENT '颜料层附着力 MPa',
    `pressure_adhesion_ratio` Float64 COMMENT '压附比 = 结晶压力/附着力',
    `cycle_count_7d` Int32 COMMENT '近7天循环次数',
    `avg_daily_rh_fluctuation` Float64 COMMENT '日均RH波动幅度 %',
    `temperature_variation` Float64 COMMENT '温度变幅 ℃',
    `delamination_probability` Float64 COMMENT '起甲概率 0~1',
    `risk_level` String COMMENT '风险等级: LOW/MEDIUM/HIGH/CRITICAL',
    `feature_contributions` String COMMENT '各特征贡献度 JSON',
    `assessment_time` DateTime64(3) COMMENT '评估时间',
    `suggestion` String COMMENT '保护建议',
    `create_time` DateTime64(3) DEFAULT now64() COMMENT '创建时间'
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(assessment_time)
ORDER BY (tomb_id, chamber_id, risk_level, assessment_time)
TTL assessment_time + INTERVAL 3 YEAR
COMMENT '壁画颜料层起甲风险评估表';

-- =============================================================================
-- 起甲风险周聚合表
-- =============================================================================

CREATE TABLE IF NOT EXISTS salt_damage.delamination_risk_weekly (
    `week_start` Date COMMENT '周开始日期',
    `tomb_id` String COMMENT '墓葬ID',
    `chamber_id` String COMMENT '墓室ID',
    `risk_level` String COMMENT '风险等级',
    `assessment_count` UInt64 COMMENT '评估次数',
    `avg_probability` Float64 COMMENT '平均概率',
    `max_probability` Float64 COMMENT '最大概率',
    `min_probability` Float64 COMMENT '最小概率',
    `high_risk_count` UInt32 COMMENT '高风险次数(HIGH+CRITICAL)'
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(week_start)
ORDER BY (tomb_id, chamber_id, risk_level, week_start)
TTL week_start + INTERVAL 5 YEAR
COMMENT '起甲风险周聚合表';

-- =============================================================================
-- 物化视图：周聚合
-- =============================================================================

CREATE MATERIALIZED VIEW IF NOT EXISTS salt_damage.delamination_risk_weekly_mv
TO salt_damage.delamination_risk_weekly
AS SELECT
    toStartOfWeek(assessment_time) AS week_start,
    tomb_id,
    chamber_id,
    risk_level,
    count() AS assessment_count,
    avg(delamination_probability) AS avg_probability,
    max(delamination_probability) AS max_probability,
    min(delamination_probability) AS min_probability,
    countIf(risk_level IN ('HIGH', 'CRITICAL')) AS high_risk_count
FROM salt_damage.delamination_risk
GROUP BY week_start, tomb_id, chamber_id, risk_level;

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
WHERE database = 'salt_damage' AND table LIKE '%delamination%'
ORDER BY table;
