-- MySQL业务数据库初始化脚本
-- 创建数据库
CREATE DATABASE IF NOT EXISTS salt_damage_biz DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE salt_damage_biz;

-- 墓葬表
CREATE TABLE IF NOT EXISTS tomb (
    id VARCHAR(32) PRIMARY KEY COMMENT '主键',
    name VARCHAR(100) NOT NULL COMMENT '墓葬名称',
    code VARCHAR(50) UNIQUE NOT NULL COMMENT '墓葬编码',
    dynasty VARCHAR(50) COMMENT '朝代',
    description TEXT COMMENT '描述',
    longitude DECIMAL(10,6) COMMENT '经度',
    latitude DECIMAL(10,6) COMMENT '纬度',
    address VARCHAR(255) COMMENT '地址',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='墓葬信息表';

-- 墓室表
CREATE TABLE IF NOT EXISTS chamber (
    id VARCHAR(32) PRIMARY KEY COMMENT '主键',
    tomb_id VARCHAR(32) NOT NULL COMMENT '墓葬ID',
    name VARCHAR(100) NOT NULL COMMENT '墓室名称',
    code VARCHAR(50) NOT NULL COMMENT '墓室编码',
    description TEXT COMMENT '描述',
    structure_info JSON COMMENT '结构信息',
    width DECIMAL(10,3) COMMENT '墓室宽度(m)',
    height DECIMAL(10,3) COMMENT '墓室高度(m)',
    length DECIMAL(10,3) COMMENT '墓室长度(m)',
    UNIQUE KEY uk_tomb_code (tomb_id, code),
    FOREIGN KEY (tomb_id) REFERENCES tomb(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='墓室信息表';

-- 设备表
CREATE TABLE IF NOT EXISTS device (
    id VARCHAR(32) PRIMARY KEY COMMENT '主键',
    tomb_id VARCHAR(32) NOT NULL COMMENT '墓葬ID',
    chamber_id VARCHAR(32) COMMENT '墓室ID',
    code VARCHAR(50) UNIQUE NOT NULL COMMENT '设备编码',
    name VARCHAR(100) NOT NULL COMMENT '设备名称',
    type VARCHAR(20) NOT NULL COMMENT '设备类型: SALT-盐离子, ENV-微环境',
    model VARCHAR(50) COMMENT '设备型号',
    status VARCHAR(20) DEFAULT 'ONLINE' COMMENT '状态: ONLINE-在线, OFFLINE-离线, MAINTENANCE-维护',
    position_x DECIMAL(10,3) COMMENT 'X坐标',
    position_y DECIMAL(10,3) COMMENT 'Y坐标',
    position_z DECIMAL(10,3) COMMENT 'Z坐标',
    install_time DATETIME COMMENT '安装时间',
    last_online_time DATETIME COMMENT '最后在线时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (tomb_id) REFERENCES tomb(id),
    FOREIGN KEY (chamber_id) REFERENCES chamber(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备信息表';

-- 告警配置表
CREATE TABLE IF NOT EXISTS alarm_config (
    id VARCHAR(32) PRIMARY KEY COMMENT '主键',
    tomb_id VARCHAR(32) COMMENT '墓葬ID',
    type VARCHAR(50) NOT NULL COMMENT '告警类型',
    threshold_value DECIMAL(10,3) NOT NULL COMMENT '阈值',
    duration_hours INT DEFAULT 0 COMMENT '持续时间(小时)',
    level VARCHAR(20) NOT NULL COMMENT '告警级别',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    notify_dingtalk TINYINT DEFAULT 1 COMMENT '是否钉钉通知',
    notify_websocket TINYINT DEFAULT 1 COMMENT '是否WebSocket通知',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (tomb_id) REFERENCES tomb(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警配置表';

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id VARCHAR(32) PRIMARY KEY COMMENT '主键',
    username VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    real_name VARCHAR(50) COMMENT '真实姓名',
    role VARCHAR(20) NOT NULL COMMENT '角色: ADMIN, EXPERT, OPERATOR',
    email VARCHAR(100) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    dingtalk_userid VARCHAR(50) COMMENT '钉钉UserID',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 钉钉配置表
CREATE TABLE IF NOT EXISTS dingtalk_config (
    id VARCHAR(32) PRIMARY KEY COMMENT '主键',
    app_key VARCHAR(100) COMMENT '应用Key',
    app_secret VARCHAR(200) COMMENT '应用Secret',
    webhook_url VARCHAR(500) COMMENT '机器人Webhook地址',
    secret VARCHAR(200) COMMENT '签名密钥',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钉钉配置表';

-- 初始化数据
INSERT INTO tomb (id, name, code, dynasty, description, longitude, latitude, address) VALUES
('T001', '懿德太子墓', 'YDTZ', '唐代', '懿德太子李重润墓，乾陵陪葬墓之一', 108.2175, 34.5703, '陕西省咸阳市乾县'),
('T002', '永泰公主墓', 'YTGZ', '唐代', '永泰公主李仙蕙墓，乾陵陪葬墓之一', 108.2180, 34.5698, '陕西省咸阳市乾县')
ON DUPLICATE KEY UPDATE update_time = CURRENT_TIMESTAMP;

INSERT INTO chamber (id, tomb_id, name, code, description, width, height, length) VALUES
('C001', 'T001', '墓道', 'YD-MD', '懿德太子墓墓道，南北走向，绘有仪仗图', 3.8, 2.5, 26.3),
('C002', 'T001', '前室', 'YD-QS', '懿德太子墓前室，绘有宫女图', 4.5, 5.0, 4.8),
('C003', 'T001', '后室', 'YD-HS', '懿德太子墓后室，放置石椁，绘有宫廷生活图', 5.3, 5.5, 5.0),
('C004', 'T002', '墓道', 'YT-MD', '永泰公主墓墓道，绘有青龙、白虎、阙楼图', 3.9, 2.4, 27.5),
('C005', 'T002', '前室', 'YT-QS', '永泰公主墓前室，绘有宫女群像', 4.6, 5.2, 4.9),
('C006', 'T002', '后室', 'YT-HS', '永泰公主墓后室，放置石椁，绘有宴饮图', 5.4, 5.6, 5.1)
ON DUPLICATE KEY UPDATE update_time = CURRENT_TIMESTAMP;

-- 初始化40台盐离子传感器
INSERT INTO device (id, tomb_id, chamber_id, code, name, type, model, status, position_x, position_y, position_z, install_time) VALUES
-- 懿德太子墓 - 墓道 (20台盐离子传感器
('D001', 'T001', 'C001', 'YD-MD-S01', '墓道东壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 0.5, 1.2, 5.0, '2024-01-15 10:00:00'),
('D002', 'T001', 'C001', 'YD-MD-S02', '墓道东壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 0.5, 1.2, 10.0, '2024-01-15 10:00:00'),
('D003', 'T001', 'C001', 'YD-MD-S03', '墓道东壁盐离子传感器3', 'SALT', 'SALT-200', 'ONLINE', 0.5, 1.2, 15.0, '2024-01-15 10:00:00'),
('D004', 'T001', 'C001', 'YD-MD-S04', '墓道东壁盐离子传感器4', 'SALT', 'SALT-200', 'ONLINE', 0.5, 1.2, 20.0, '2024-01-15 10:00:00'),
('D005', 'T001', 'C001', 'YD-MD-S05', '墓道西壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 3.3, 1.2, 5.0, '2024-01-15 10:00:00'),
('D006', 'T001', 'C001', 'YD-MD-S06', '墓道西壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 3.3, 1.2, 10.0, '2024-01-15 10:00:00'),
('D007', 'T001', 'C001', 'YD-MD-S07', '墓道西壁盐离子传感器3', 'SALT', 'SALT-200', 'ONLINE', 3.3, 1.2, 15.0, '2024-01-15 10:00:00'),
('D008', 'T001', 'C001', 'YD-MD-S08', '墓道西壁盐离子传感器4', 'SALT', 'SALT-200', 'ONLINE', 3.3, 1.2, 20.0, '2024-01-15 10:00:00'),
-- 懿德太子墓 - 前室 (6台盐离子传感器)
('D009', 'T001', 'C002', 'YD-QS-S01', '前室东壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 0.5, 2.0, 1.5, '2024-01-15 10:00:00'),
('D010', 'T001', 'C002', 'YD-QS-S02', '前室东壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 0.5, 3.5, 1.5, '2024-01-15 10:00:00'),
('D011', 'T001', 'C002', 'YD-QS-S03', '前室西壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 4.0, 2.0, 1.5, '2024-01-15 10:00:00'),
('D012', 'T001', 'C002', 'YD-QS-S04', '前室西壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 4.0, 3.5, 1.5, '2024-01-15 10:00:00'),
('D013', 'T001', 'C002', 'YD-QS-S05', '前室北壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 2.25, 2.0, 0.3, '2024-01-15 10:00:00'),
('D014', 'T001', 'C002', 'YD-QS-S06', '前室北壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 2.25, 3.5, 0.3, '2024-01-15 10:00:00'),
-- 懿德太子墓 - 后室 (6台盐离子传感器)
('D015', 'T001', 'C003', 'YD-HS-S01', '后室东壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 0.5, 2.0, 2.0, '2024-01-15 10:00:00'),
('D016', 'T001', 'C003', 'YD-HS-S02', '后室东壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 0.5, 4.0, 2.0, '2024-01-15 10:00:00'),
('D017', 'T001', 'C003', 'YD-HS-S03', '后室西壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 4.8, 2.0, 2.0, '2024-01-15 10:00:00'),
('D018', 'T001', 'C003', 'YD-HS-S04', '后室西壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 4.8, 4.0, 2.0, '2024-01-15 10:00:00'),
('D019', 'T001', 'C003', 'YD-HS-S05', '后室北壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 2.65, 2.0, 0.3, '2024-01-15 10:00:00'),
('D020', 'T001', 'C003', 'YD-HS-S06', '后室北壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 2.65, 4.0, 0.3, '2024-01-15 10:00:00'),
-- 永泰公主墓 - 墓道 (8台盐离子传感器)
('D021', 'T002', 'C004', 'YT-MD-S01', '墓道东壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 0.5, 1.2, 5.0, '2024-01-15 10:00:00'),
('D022', 'T002', 'C004', 'YT-MD-S02', '墓道东壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 0.5, 1.2, 12.0, '2024-01-15 10:00:00'),
('D023', 'T002', 'C004', 'YT-MD-S03', '墓道东壁盐离子传感器3', 'SALT', 'SALT-200', 'ONLINE', 0.5, 1.2, 20.0, '2024-01-15 10:00:00'),
('D024', 'T002', 'C004', 'YT-MD-S04', '墓道西壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 3.4, 1.2, 5.0, '2024-01-15 10:00:00'),
('D025', 'T002', 'C004', 'YT-MD-S05', '墓道西壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 3.4, 1.2, 12.0, '2024-01-15 10:00:00'),
('D026', 'T002', 'C004', 'YT-MD-S06', '墓道西壁盐离子传感器3', 'SALT', 'SALT-200', 'ONLINE', 3.4, 1.2, 20.0, '2024-01-15 10:00:00'),
('D027', 'T002', 'C004', 'YT-MD-S07', '墓道北壁盐离子传感器', 'SALT', 'SALT-200', 'ONLINE', 1.95, 1.2, 26.0, '2024-01-15 10:00:00'),
('D028', 'T002', 'C004', 'YT-MD-S08', '墓道南壁盐离子传感器', 'SALT', 'SALT-200', 'ONLINE', 1.95, 1.2, 1.0, '2024-01-15 10:00:00'),
-- 永泰公主墓 - 前室 (6台盐离子传感器)
('D029', 'T002', 'C005', 'YT-QS-S01', '前室东壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 0.5, 2.0, 1.5, '2024-01-15 10:00:00'),
('D030', 'T002', 'C005', 'YT-QS-S02', '前室东壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 0.5, 3.5, 1.5, '2024-01-15 10:00:00'),
('D031', 'T002', 'C005', 'YT-QS-S03', '前室西壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 4.1, 2.0, 1.5, '2024-01-15 10:00:00'),
('D032', 'T002', 'C005', 'YT-QS-S04', '前室西壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 4.1, 3.5, 1.5, '2024-01-15 10:00:00'),
('D033', 'T002', 'C005', 'YT-QS-S05', '前室北壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 2.3, 2.0, 0.3, '2024-01-15 10:00:00'),
('D034', 'T002', 'C005', 'YT-QS-S06', '前室北壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 2.3, 3.5, 0.3, '2024-01-15 10:00:00'),
-- 永泰公主墓 - 后室 (6台盐离子传感器)
('D035', 'T002', 'C006', 'YT-HS-S01', '后室东壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 0.5, 2.0, 2.0, '2024-01-15 10:00:00'),
('D036', 'T002', 'C006', 'YT-HS-S02', '后室东壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 0.5, 4.0, 2.0, '2024-01-15 10:00:00'),
('D037', 'T002', 'C006', 'YT-HS-S03', '后室西壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 4.9, 2.0, 2.0, '2024-01-15 10:00:00'),
('D038', 'T002', 'C006', 'YT-HS-S04', '后室西壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 4.9, 4.0, 2.0, '2024-01-15 10:00:00'),
('D039', 'T002', 'C006', 'YT-HS-S05', '后室北壁盐离子传感器1', 'SALT', 'SALT-200', 'ONLINE', 2.7, 2.0, 0.3, '2024-01-15 10:00:00'),
('D040', 'T002', 'C006', 'YT-HS-S06', '后室北壁盐离子传感器2', 'SALT', 'SALT-200', 'ONLINE', 2.7, 4.0, 0.3, '2024-01-15 10:00:00')
ON DUPLICATE KEY UPDATE update_time = CURRENT_TIMESTAMP;

-- 初始化30台微环境传感器
INSERT INTO device (id, tomb_id, chamber_id, code, name, type, model, status, position_x, position_y, position_z, install_time) VALUES
-- 懿德太子墓 (15台微环境传感器
('E001', 'T001', 'C001', 'YD-MD-E01', '墓道微环境传感器1', 'ENV', 'ENV-300', 'ONLINE', 1.9, 2.0, 8.0, '2024-01-15 10:00:00'),
('E002', 'T001', 'C001', 'YD-MD-E02', '墓道微环境传感器2', 'ENV', 'ENV-300', 'ONLINE', 1.9, 2.0, 18.0, '2024-01-15 10:00:00'),
('E003', 'T001', 'C001', 'YD-MD-E03', '墓道微环境传感器3', 'ENV', 'ENV-300', 'ONLINE', 1.9, 0.5, 13.0, '2024-01-15 10:00:00'),
('E004', 'T001', 'C002', 'YD-QS-E01', '前室微环境传感器1', 'ENV', 'ENV-300', 'ONLINE', 2.25, 2.5, 2.4, '2024-01-15 10:00:00'),
('E005', 'T001', 'C002', 'YD-QS-E02', '前室微环境传感器2', 'ENV', 'ENV-300', 'ONLINE', 2.25, 4.5, 2.4, '2024-01-15 10:00:00'),
('E006', 'T001', 'C002', 'YD-QS-E03', '前室微环境传感器3', 'ENV', 'ENV-300', 'ONLINE', 2.25, 0.5, 2.4, '2024-01-15 10:00:00'),
('E007', 'T001', 'C003', 'YD-HS-E01', '后室微环境传感器1', 'ENV', 'ENV-300', 'ONLINE', 2.65, 2.5, 2.5, '2024-01-15 10:00:00'),
('E008', 'T001', 'C003', 'YD-HS-E02', '后室微环境传感器2', 'ENV', 'ENV-300', 'ONLINE', 2.65, 4.5, 2.5, '2024-01-15 10:00:00'),
('E009', 'T001', 'C003', 'YD-HS-E03', '后室微环境传感器3', 'ENV', 'ENV-300', 'ONLINE', 2.65, 0.5, 2.5, '2024-01-15 10:00:00'),
('E010', 'T001', 'C001', 'YD-GD-E01', '墓道入口微环境传感器', 'ENV', 'ENV-300', 'ONLINE', 1.9, 1.0, 0.5, '2024-01-15 10:00:00'),
('E011', 'T001', 'C001', 'YD-GD-E02', '墓道出口微环境传感器', 'ENV', 'ENV-300', 'ONLINE', 1.9, 1.0, 25.8, '2024-01-15 10:00:00'),
('E012', 'T001', 'C002', 'YD-QS-E04', '前室顶部微环境传感器', 'ENV', 'ENV-300', 'ONLINE', 2.25, 4.9, 2.4, '2024-01-15 10:00:00'),
('E013', 'T001', 'C003', 'YD-HS-E04', '后室顶部微环境传感器', 'ENV', 'ENV-300', 'ONLINE', 2.65, 5.4, 2.5, '2024-01-15 10:00:00'),
('E014', 'T001', 'C002', 'YD-QS-E05', '前室地面微环境传感器', 'ENV', 'ENV-300', 'ONLINE', 2.25, 0.1, 2.4, '2024-01-15 10:00:00'),
('E015', 'T001', 'C003', 'YD-HS-E05', '后室地面微环境传感器', 'ENV', 'ENV-300', 'ONLINE', 2.65, 0.1, 2.5, '2024-01-15 10:00:00'),
-- 永泰公主墓 (15台微环境传感器
('E016', 'T002', 'C004', 'YT-MD-E01', '墓道微环境传感器1', 'ENV', 'ENV-300', 'ONLINE', 1.95, 2.0, 8.0, '2024-01-15 10:00:00'),
('E017', 'T002', 'C004', 'YT-MD-E02', '墓道微环境传感器2', 'ENV', 'ENV-300', 'ONLINE', 1.95, 2.0, 18.0, '2024-01-15 10:00:00'),
('E018', 'T002', 'C004', 'YT-MD-E03', '墓道微环境传感器3', 'ENV', 'ENV-300', 'ONLINE', 1.95, 0.5, 13.0, '2024-01-15 10:00:00'),
('E019', 'T002', 'C005', 'YT-QS-E01', '前室微环境传感器1', 'ENV', 'ENV-300', 'ONLINE', 2.3, 2.5, 2.45, '2024-01-15 10:00:00'),
('E020', 'T002', 'C005', 'YT-QS-E02', '前室微环境传感器2', 'ENV', 'ENV-300', 'ONLINE', 2.3, 4.5, 2.45, '2024-01-15 10:00:00'),
('E021', 'T002', 'C005', 'YT-QS-E03', '前室微环境传感器3', 'ENV', 'ENV-300', 'ONLINE', 2.3, 0.5, 2.45, '2024-01-15 10:00:00'),
('E022', 'T002', 'C006', 'YT-HS-E01', '后室微环境传感器1', 'ENV', 'ENV-300', 'ONLINE', 2.7, 2.5, 2.55, '2024-01-15 10:00:00'),
('E023', 'T002', 'C006', 'YT-HS-E02', '后室微环境传感器2', 'ENV', 'ENV-300', 'ONLINE', 2.7, 4.5, 2.55, '2024-01-15 10:00:00'),
('E024', 'T002', 'C006', 'YT-HS-E03', '后室微环境传感器3', 'ENV', 'ENV-300', 'ONLINE', 2.7, 0.5, 2.55, '2024-01-15 10:00:00'),
('E025', 'T002', 'C004', 'YT-GD-E01', '墓道入口微环境传感器', 'ENV', 'ENV-300', 'ONLINE', 1.95, 1.0, 0.5, '2024-01-15 10:00:00'),
('E026', 'T002', 'C004', 'YT-GD-E02', '墓道出口微环境传感器', 'ENV', 'ENV-300', 'ONLINE', 1.95, 1.0, 26.5, '2024-01-15 10:00:00'),
('E027', 'T002', 'C005', 'YT-QS-E04', '前室顶部微环境传感器', 'ENV', 'ENV-300', 'ONLINE', 2.3, 5.1, 2.45, '2024-01-15 10:00:00'),
('E028', 'T002', 'C006', 'YT-HS-E04', '后室顶部微环境传感器', 'ENV', 'ENV-300', 'ONLINE', 2.7, 5.5, 2.55, '2024-01-15 10:00:00'),
('E029', 'T002', 'C005', 'YT-QS-E05', '前室地面微环境传感器', 'ENV', 'ENV-300', 'ONLINE', 2.3, 0.1, 2.45, '2024-01-15 10:00:00'),
('E030', 'T002', 'C006', 'YT-HS-E05', '后室地面微环境传感器', 'ENV', 'ENV-300', 'ONLINE', 2.7, 0.1, 2.55, '2024-01-15 10:00:00')
ON DUPLICATE KEY UPDATE update_time = CURRENT_TIMESTAMP;

-- 告警配置
INSERT INTO alarm_config (id, type, threshold_value, duration_hours, level, enabled, notify_dingtalk, notify_websocket) VALUES
('AC001', 'SALT_EXCEED', 5.0, 0, 'CRITICAL', 1, 1, 1),
('AC002', 'HUMIDITY_EXCEED', 75.0, 48, 'WARNING', 1, 1, 1),
('AC003', 'DEVICE_OFFLINE', 4, 0, 'WARNING', 1, 1, 0)
ON DUPLICATE KEY UPDATE update_time = CURRENT_TIMESTAMP;

-- 初始化用户
INSERT INTO sys_user (id, username, password, real_name, role, email, phone) VALUES
('U001', 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', 'ADMIN', 'admin@saltdamage.com', '13800000001'),
('U002', 'expert', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '张教授', 'EXPERT', 'expert@saltdamage.com', '13800000002'),
('U003', 'operator', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '李运维', 'OPERATOR', 'operator@saltdamage.com', '13800000003')
ON DUPLICATE KEY UPDATE update_time = CURRENT_TIMESTAMP;

-- 钉钉配置示例
INSERT INTO dingtalk_config (id, app_key, app_secret, webhook_url, secret, enabled) VALUES
('DC001', 'your_app_key', 'your_app_secret', 'https://oapi.dingtalk.com/robot/send?access_token=your_token', 'your_sign_secret', 0)
ON DUPLICATE KEY UPDATE update_time = CURRENT_TIMESTAMP;
