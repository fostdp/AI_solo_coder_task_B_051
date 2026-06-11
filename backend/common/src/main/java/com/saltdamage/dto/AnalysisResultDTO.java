package com.saltdamage.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AnalysisResultDTO {

    private Long id;
    private Long tombId;
    private Long chamberId;
    private String analysisType;
    private String saltDamageLevel;
    private BigDecimal riskScore;
    private String conclusion;
    private String suggestion;
    private LocalDateTime analysisTime;
}
