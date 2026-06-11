package com.saltdamage.blockchain.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "blockchain_record", indexes = {
        @Index(name = "idx_tx_hash", columnList = "tx_hash", unique = true),
        @Index(name = "idx_block_number", columnList = "block_number"),
        @Index(name = "idx_data_type", columnList = "data_type"),
        @Index(name = "idx_data_hash", columnList = "data_hash"),
        @Index(name = "idx_timestamp", columnList = "timestamp")
})
public class BlockchainRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tx_hash", length = 64, nullable = false)
    private String txHash;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "data_type", length = 50)
    private String dataType;

    @Column(name = "data_hash", length = 64)
    private String dataHash;

    @Column(name = "data_summary", length = 500)
    private String dataSummary;

    @Column(name = "operator", length = 100)
    private String operator;

    @Column(name = "merkle_proof", columnDefinition = "TEXT")
    private String merkleProof;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "verified")
    private Boolean verified;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}
