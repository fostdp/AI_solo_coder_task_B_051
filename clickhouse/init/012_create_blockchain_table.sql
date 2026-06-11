-- =============================================================================
-- 区块链存证记录表
-- 将关键监测数据哈希后上链（模拟以太坊测试网）
-- =============================================================================

CREATE TABLE IF NOT EXISTS salt_damage.blockchain_record (
    `id` String COMMENT '记录ID',
    `tx_hash` String COMMENT '交易哈希',
    `block_number` UInt64 COMMENT '区块号',
    `data_type` String COMMENT '数据类型: SALT_DATA/ENV_DATA/REPAIR_RECORD/ANALYSIS_REPORT',
    `data_hash` String COMMENT '原始数据SHA-256哈希',
    `data_summary` String COMMENT '数据摘要/描述',
    `operator` String COMMENT '操作人',
    `merkle_proof` String COMMENT 'Merkle证明 JSON',
    `block_hash` String COMMENT '区块哈希',
    `previous_block_hash` String COMMENT '前一区块哈希',
    `merkle_root` String COMMENT 'Merkle根哈希',
    `nonce` UInt64 COMMENT '工作量证明nonce',
    `transaction_index` UInt32 COMMENT '交易在区块中的索引',
    `verified` UInt8 DEFAULT 0 COMMENT '是否已验证 0否/1是',
    `timestamp` DateTime64(3) COMMENT '时间戳',
    `create_time` DateTime64(3) DEFAULT now64() COMMENT '创建时间'
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (block_number, tx_hash)
TTL timestamp + INTERVAL 10 YEAR
COMMENT '区块链存证记录表';

-- =============================================================================
-- 区块头表（保存区块级统计
-- =============================================================================

CREATE TABLE IF NOT EXISTS salt_damage.blockchain_block (
    `block_number` UInt64 COMMENT '区块号',
    `block_hash` String COMMENT '区块哈希',
    `previous_block_hash` String COMMENT '前一区块哈希',
    `merkle_root` String COMMENT 'Merkle根',
    `nonce` UInt64 COMMENT '工作量证明nonce',
    `difficulty` UInt32 COMMENT '挖矿难度（前导零数量',
    `transaction_count` UInt32 COMMENT '交易数量',
    `data_size_bytes` UInt64 COMMENT '数据大小 字节',
    `miner_address` String COMMENT '矿工地址',
    `timestamp` DateTime64(3) COMMENT '出块时间',
    `create_time` DateTime64(3) DEFAULT now64() COMMENT '创建时间'
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (block_number)
TTL timestamp + INTERVAL 10 YEAR
COMMENT '区块链区块头表';

-- =============================================================================
-- 存证统计表
-- =============================================================================

CREATE TABLE IF NOT EXISTS salt_damage.blockchain_stats_daily (
    `day` Date COMMENT '日期',
    `data_type` String COMMENT '数据类型',
    `record_count` UInt64 COMMENT '存证数量',
    `total_data_size` UInt64 COMMENT '总数据大小 字节',
    `block_count` UInt32 COMMENT '涉及区块数'
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(day)
ORDER BY (day, data_type)
TTL day + INTERVAL 5 YEAR
COMMENT '区块链存证日统计表';

-- =============================================================================
-- 物化视图：日统计
-- =============================================================================

CREATE MATERIALIZED VIEW IF NOT EXISTS salt_damage.blockchain_stats_daily_mv
TO salt_damage.blockchain_stats_daily
AS SELECT
    toDate(timestamp) AS day,
    data_type,
    count() AS record_count,
    sum(length(data_summary)) + sum(length(data_hash)) AS total_data_size,
    uniq(block_number) AS block_count
FROM salt_damage.blockchain_record
GROUP BY day, data_type;

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
WHERE database = 'salt_damage' AND table LIKE '%blockchain%'
ORDER BY table;
