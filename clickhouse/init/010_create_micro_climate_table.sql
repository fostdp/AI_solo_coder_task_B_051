-- =============================================================================
-- 微环境控制记录表
-- 记录除湿机/加湿器控制操作及能耗数据
-- =============================================================================

CREATE TABLE IF NOT EXISTS salt_damage.micro_climate_control (
    `id` String COMMENT '记录ID',
    `tomb_id` String COMMENT '墓葬ID',
    `chamber_id` String COMMENT '墓室ID',
    `control_mode` String COMMENT '控制模式: MANUAL/AUTO_DQN/SCHEDULE',
    `current_rh` Float64 COMMENT '当前相对湿度 %',
    `target_rh` Float64 COMMENT '目标相对湿度 %',
    `dehumidifier_status` UInt8 COMMENT '除湿机状态: 0关/1开',
    `humidifier_status` UInt8 COMMENT '加湿器状态: 0关/1开',
    `energy_consumption` Float64 COMMENT '本次操作能耗 kWh',
    `reward_score` Float64 COMMENT 'DQN奖励分数',
    `action_taken` Int32 COMMENT '执行动作: 0待机/1除湿/2加湿/3同时开',
    `rh_before` Float64 COMMENT '操作前RH %',
    `rh_after` Float64 COMMENT '操作后RH %',
    `control_timestamp` DateTime64(3) COMMENT '控制时间',
    `create_time` DateTime64(3) DEFAULT now64() COMMENT '创建时间'
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(control_timestamp)
ORDER BY (tomb_id, chamber_id, control_timestamp)
TTL control_timestamp + INTERVAL 2 YEAR
COMMENT '微环境控制记录表';

-- =============================================================================
-- DQN模型训练记录表
-- =============================================================================

CREATE TABLE IF NOT EXISTS salt_damage.dqn_training_record (
    `id` String COMMENT '记录ID',
    `tomb_id` String COMMENT '墓葬ID',
    `chamber_id` String COMMENT '墓室ID',
    `episode` Int32 COMMENT '训练回合数',
    `total_episodes` Int32 COMMENT '总回合数',
    `final_epsilon` Float64 COMMENT '最终探索率',
    `average_reward` Float64 COMMENT '平均奖励',
    `max_reward` Float64 COMMENT '最大奖励',
    `loss` Float64 COMMENT '损失值',
    `training_duration_ms` Int64 COMMENT '训练时长 ms',
    `experience_count` Int32 COMMENT '经验样本数',
    `model_version` String COMMENT '模型版本',
    `training_time` DateTime64(3) COMMENT '训练时间',
    `create_time` DateTime64(3) DEFAULT now64() COMMENT '创建时间'
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(training_time)
ORDER BY (tomb_id, chamber_id, training_time)
TTL training_time + INTERVAL 1 YEAR
COMMENT 'DQN模型训练记录表';

-- =============================================================================
-- 能耗小时统计表（物化视图）
-- =============================================================================

CREATE MATERIALIZED VIEW IF NOT EXISTS salt_damage.micro_climate_energy_hourly_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(day_hour)
ORDER BY (tomb_id, chamber_id, day_hour)
AS SELECT
    toStartOfHour(control_timestamp) AS day_hour,
    tomb_id,
    chamber_id,
    sum(energy_consumption) AS total_energy,
    sum(dehumidifier_status) AS dehumidifier_runtime,
    sum(humidifier_status) AS humidifier_runtime,
    count() AS control_count
FROM salt_damage.micro_climate_control
GROUP BY day_hour, tomb_id, chamber_id;

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
WHERE database = 'salt_damage' AND table LIKE '%micro_climate%' OR table LIKE '%dqn%'
ORDER BY table;
