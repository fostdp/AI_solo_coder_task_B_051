package com.saltdamage.common.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisMessage implements Serializable {

    private String messageId;
    private long timestamp;
    private String deviceId;
    private String tombId;
    private String chamberId;

    private Double migrationVelocityX;
    private Double migrationVelocityY;
    private Double migrationVelocityZ;
    private Double crystallizationPressure;
    private String riskLevel;
    private Integer predictionHours;
    private Double predictedTotalSalt;
    private Double predictedCrystallizationPressure;
    private String porosityGridJson;
}
