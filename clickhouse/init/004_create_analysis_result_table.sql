CREATE TABLE IF NOT EXISTS salt_damage.analysis_result (
    `timestamp` DateTime64(3) COMMENT '分析时间',
    `device_id` String COMMENT '设备ID',
    `tomb_id` String COMMENT '墓葬ID',
    `chamber_id` String COMMENT '墓室ID',
    `velocity_x` Float64 COMMENT '运移速度X分量 m/s',
    `velocity_y` Float64 COMMENT '运移速度Y分量 m/s',
    `velocity_z` Float64 COMMENT '运移速度Z分量 m/s',
    `velocity_magnitude` Float64 COMMENT '运移速度大小 m/s',
    `crystallization_pressure` Float64 COMMENT '结晶压力 MPa',
    `na2so4_saturation_index` Float64 COMMENT 'Na₂SO₄饱和指数',
    `risk_level` String COMMENT '风险等级: LOW/MEDIUM/HIGH/CRITICAL',
    `prediction_hours` Int32 COMMENT '预测小时数',
    `predicted_total_salt` Float64 COMMENT '预测盐分总量 mg/cm²',
    `predicted_crystallization_pressure` Float64 COMMENT '预测结晶压力 MPa',
    `position_x` Float64 COMMENT 'X坐标',
    `position_y` Float64 COMMENT 'Y坐标',
    `position_z` Float64 COMMENT 'Z坐标'
) ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(timestamp)
ORDER BY (tomb_id, device_id, timestamp)
TTL timestamp + INTERVAL 3 YEAR
COMMENT '盐害分析结果表';
