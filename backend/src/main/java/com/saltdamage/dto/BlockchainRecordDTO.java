package com.saltdamage.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BlockchainRecordDTO {

    private Long id;
    private String txHash;
    private Long blockNumber;
    private String blockHash;
    private String previousBlockHash;
    private String dataType;
    private String dataHash;
    private String dataSummary;
    private String operator;
    private String merkleProof;
    private LocalDateTime timestamp;
    private Boolean verified;
    private LocalDateTime createTime;
}
