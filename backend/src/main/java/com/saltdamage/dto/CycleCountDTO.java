package com.saltdamage.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CycleCountDTO {

    private Long id;
    private Long tombId;
    private Long chamberId;
    private Long deviceId;
    private String tombName;
    private String chamberName;
    private String periodType;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Integer totalCycles;
    private Integer fullCycles;
    private Integer partialCycles;
    private Integer crossingCycles;
    private BigDecimal averageRange;
    private BigDecimal maxRange;
    private BigDecimal minRange;
    private BigDecimal totalDamage;
    private String damageLevel;
    private String amplitudeHistogram;
    private LocalDateTime analysisTime;
    private LocalDateTime createTime;
}
