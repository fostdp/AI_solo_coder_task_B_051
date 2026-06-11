CREATE TABLE IF NOT EXISTS salt_damage.alarm_history (
    `id` String COMMENT '告警ID',
    `type` String COMMENT '告警类型: SALT_EXCEED/HUMIDITY_EXCEED/DEVICE_OFFLINE',
    `level` String COMMENT '告警级别: WARNING/ERROR/CRITICAL',
    `message` String COMMENT '告警消息',
    `device_id` String COMMENT '设备ID',
    `tomb_id` String COMMENT '墓葬ID',
    `chamber_id` String COMMENT '墓室ID',
    `timestamp` DateTime64(3) COMMENT '告警时间',
    `status` String COMMENT '处理状态: PENDING/PROCESSING/RESOLVED',
    `value` Float64 COMMENT '实际值',
    `threshold` Float64 COMMENT '阈值',
    `duration_hours` Float64 COMMENT '持续时间(小时)',
    `process_user` String COMMENT '处理人',
    `process_time` DateTime64(3) COMMENT '处理时间',
    `process_note` String COMMENT '处理备注'
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (tomb_id, timestamp)
TTL timestamp + INTERVAL 5 YEAR
COMMENT '告警历史表';

CREATE TABLE IF NOT EXISTS salt_damage.alarm_stats_daily (
    `date` Date COMMENT '日期',
    `tomb_id` String COMMENT '墓葬ID',
    `type` String COMMENT '告警类型',
    `level` String COMMENT '告警级别',
    `total_count` UInt32 COMMENT '告警总数',
    `pending_count` UInt32 COMMENT '待处理数',
    `resolved_count` UInt32 COMMENT '已处理数'
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, tomb_id, type, level)
TTL date + INTERVAL 1 YEAR
COMMENT '告警日统计表';

CREATE MATERIALIZED VIEW IF NOT EXISTS salt_damage.alarm_stats_daily_mv
TO salt_damage.alarm_stats_daily
AS SELECT
    toDate(timestamp) AS date,
    tomb_id,
    type,
    level,
    count() AS total_count,
    countIf(status = 'PENDING') AS pending_count,
    countIf(status = 'RESOLVED') AS resolved_count
FROM salt_damage.alarm_history
GROUP BY date, tomb_id, type, level;
