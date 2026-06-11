package com.saltdamage.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MicroClimateMessage implements Serializable {

    private Long chamberId;
    private double currentRh;
    private double rhTrend;
    private int hourOfDay;
    private boolean dehumidifierOn;
    private boolean humidifierOn;
    private String controlMode;
    private Long requestId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result implements Serializable {
        private Long requestId;
        private int recommendedAction;
        private String actionName;
        private double targetRh;
        private double expectedReward;
        private boolean dehumidifierOn;
        private boolean humidifierOn;
        private String onnxModelUsed;
        private boolean success;
        private String errorMessage;
    }
}
