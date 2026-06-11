package com.saltdamage.flaking.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DelaminationRiskDTO {

    private Long id;
    private Long tombId;
    private Long chamberId;
    private Long muralId;
    private String tombName;
    private String chamberName;
    private String muralName;
    private String pigmentType;
    private Integer muralAge;
    private BigDecimal crystallizationPressure;
    private BigDecimal adhesionStrength;
    private BigDecimal pressureAdhesionRatio;
    private Integer cycleCount7d;
    private BigDecimal avgDailyRhFluctuation;
    private BigDecimal temperatureVariation;
    private BigDecimal delaminationProbability;
    private String riskLevel;
    private String featureContributions;
    private LocalDateTime assessmentTime;
    private String suggestion;
    private LocalDateTime createTime;
}
