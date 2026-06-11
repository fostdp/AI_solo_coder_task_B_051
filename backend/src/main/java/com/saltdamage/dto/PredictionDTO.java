package com.saltdamage.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PredictionDTO {

    private Long id;
    private Long tombId;
    private Long chamberId;
    private LocalDateTime predictTime;
    private BigDecimal predictedSaltConcentration;
    private BigDecimal predictedTemperature;
    private BigDecimal predictedHumidity;
    private String riskLevel;
    private LocalDateTime createTime;
}
