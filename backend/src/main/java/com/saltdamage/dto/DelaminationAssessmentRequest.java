package com.saltdamage.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DelaminationAssessmentRequest {

    private Long tombId;
    private Long chamberId;
    private Long muralId;
    private String pigmentType;
    private Integer muralAge;
    private BigDecimal crystallizationPressure;
    private BigDecimal adhesionStrength;
    private Integer cycleCount7d;
    private BigDecimal avgDailyRhFluctuation;
    private BigDecimal temperatureVariation;
}
