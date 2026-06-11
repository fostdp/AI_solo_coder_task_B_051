package com.saltdamage.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class BlockchainInfoDTO {

    private Long blockHeight;
    private Long totalTransactions;
    private Double totalDataSizeMb;
    private Boolean chainHashValid;
    private Long pendingTxCount;
    private Integer difficulty;
    private LocalDateTime latestBlockTime;
    private String latestBlockHash;
    private Map<String, Long> typeStats;
    private Map<String, Long> dailyStats;
}
