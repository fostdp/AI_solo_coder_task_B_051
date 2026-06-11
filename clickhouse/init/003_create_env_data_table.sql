CREATE TABLE IF NOT EXISTS salt_damage.env_data (
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
COMMENT '微环境监测数据表';

CREATE TABLE IF NOT EXISTS salt_damage.env_data_hourly (
    `timestamp` DateTime64(3) COMMENT '时间窗口开始',
    `device_id` String COMMENT '设备ID',
    `tomb_id` String COMMENT '墓葬ID',
    `chamber_id` String COMMENT '墓室ID',
    `temperature_avg` Float64 COMMENT '平均温度',
    `temperature_max` Float64 COMMENT '最高温度',
    `temperature_min` Float64 COMMENT '最低温度',
    `humidity_avg` Float64 COMMENT '平均湿度',
    `humidity_max` Float64 COMMENT '最高湿度',
    `humidity_min` Float64 COMMENT '最低湿度',
    `wind_speed_avg` Float64 COMMENT '平均风速',
    `wind_speed_max` Float64 COMMENT '最大风速',
    `humidity_over_75_duration` Float64 COMMENT '湿度>75%持续时间(小时)',
    `sample_count` UInt32 COMMENT '采样数'
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (tomb_id, device_id, timestamp)
TTL timestamp + INTERVAL 2 YEAR
COMMENT '微环境数据小时聚合表';

CREATE MATERIALIZED VIEW IF NOT EXISTS salt_damage.env_data_hourly_mv
TO salt_damage.env_data_hourly
AS SELECT
    toStartOfHour(timestamp) AS timestamp,
    device_id,
    tomb_id,
    chamber_id,
    avg(temperature) AS temperature_avg,
    max(temperature) AS temperature_max,
    min(temperature) AS temperature_min,
    avg(humidity) AS humidity_avg,
    max(humidity) AS humidity_max,
    min(humidity) AS humidity_min,
    avg(wind_speed) AS wind_speed_avg,
    max(wind_speed) AS wind_speed_max,
    sumIf(1/6.0, humidity > 75) AS humidity_over_75_duration,
    count() AS sample_count
FROM salt_damage.env_data
GROUP BY timestamp, device_id, tomb_id, chamber_id;
