package com.saltdamage.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CycleCountMessage implements Serializable {

    private Long chamberId;
    private Long tombId;
    private List<Double> humidityData;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String source;
    private Long requestId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result implements Serializable {
        private Long requestId;
        private int totalCycles;
        private int fullCycles;
        private int crossingCycles;
        private double maxRange;
        private double totalDamage;
        private String riskLevel;
        private boolean success;
        private String errorMessage;
    }
}
