package com.saltdamage.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelaminationMessage implements Serializable {

    private Long chamberId;
    private Long tombId;
    private String pigmentType;
    private double crystallizationPressure;
    private double adhesionStrength;
    private int cycleCount7d;
    private double avgDailyRhFluctuation;
    private double temperatureVariation;
    private Long requestId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result implements Serializable {
        private Long requestId;
        private double probability;
        private String riskLevel;
        private double pressureAdhesionRatio;
        private Map<String, Double> featureContributions;
        private String shapSummary;
        private String recommendation;
        private boolean smoteUsed;
        private boolean success;
        private String errorMessage;
    }
}
