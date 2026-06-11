CREATE TABLE IF NOT EXISTS salt_damage.salt_data (
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
COMMENT '盐离子监测数据表';

CREATE TABLE IF NOT EXISTS salt_damage.salt_data_hourly (
    `timestamp` DateTime64(3) COMMENT '时间窗口开始',
    `device_id` String COMMENT '设备ID',
    `tomb_id` String COMMENT '墓葬ID',
    `chamber_id` String COMMENT '墓室ID',
    `na_plus_avg` Float64 COMMENT 'Na⁺平均浓度',
    `na_plus_max` Float64 COMMENT 'Na⁺最大浓度',
    `ca2_plus_avg` Float64 COMMENT 'Ca²⁺平均浓度',
    `ca2_plus_max` Float64 COMMENT 'Ca²⁺最大浓度',
    `so42_minus_avg` Float64 COMMENT 'SO₄²⁻平均浓度',
    `so42_minus_max` Float64 COMMENT 'SO₄²⁻最大浓度',
    `cl_minus_avg` Float64 COMMENT 'Cl⁻平均浓度',
    `cl_minus_max` Float64 COMMENT 'Cl⁻最大浓度',
    `total_salt_avg` Float64 COMMENT '盐分平均总量',
    `total_salt_max` Float64 COMMENT '盐分最大总量',
    `sample_count` UInt32 COMMENT '采样数'
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (tomb_id, device_id, timestamp)
TTL timestamp + INTERVAL 2 YEAR
COMMENT '盐离子数据小时聚合表';

CREATE MATERIALIZED VIEW IF NOT EXISTS salt_damage.salt_data_hourly_mv
TO salt_damage.salt_data_hourly
AS SELECT
    toStartOfHour(timestamp) AS timestamp,
    device_id,
    tomb_id,
    chamber_id,
    avg(na_plus) AS na_plus_avg,
    max(na_plus) AS na_plus_max,
    avg(ca2_plus) AS ca2_plus_avg,
    max(ca2_plus) AS ca2_plus_max,
    avg(so42_minus) AS so42_minus_avg,
    max(so42_minus) AS so42_minus_max,
    avg(cl_minus) AS cl_minus_avg,
    max(cl_minus) AS cl_minus_max,
    avg(total_salt) AS total_salt_avg,
    max(total_salt) AS total_salt_max,
    count() AS sample_count
FROM salt_damage.salt_data
GROUP BY timestamp, device_id, tomb_id, chamber_id;
