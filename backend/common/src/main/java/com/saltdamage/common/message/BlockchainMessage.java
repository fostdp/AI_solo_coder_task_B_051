package com.saltdamage.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockchainMessage implements Serializable {

    private String dataType;
    private String dataJson;
    private String operator;
    private String recordId;
    private Long requestId;
    private boolean waitForMining;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result implements Serializable {
        private Long requestId;
        private String dataHash;
        private String txHash;
        private int blockNumber;
        private boolean exists;
        private boolean valid;
        private LocalDateTime timestamp;
        private String merkleProof;
        private boolean success;
        private String errorMessage;
    }

    public enum DataType {
        SALT_DATA,
        ENV_DATA,
        REPAIR_RECORD,
        ANALYSIS_REPORT,
        CYCLE_COUNT,
        CONTROL_RECORD,
        DELAMINATION_ASSESSMENT
    }
}
