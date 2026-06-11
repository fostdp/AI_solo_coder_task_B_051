package com.saltdamage.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MicroClimateControlDTO {

    private Long id;

    private Long tombId;

    private Long chamberId;

    private String tombName;

    private String chamberName;

    private String controlMode;

    private BigDecimal currentRh;

    private BigDecimal targetRh;

    private Boolean dehumidifierStatus;

    private Boolean humidifierStatus;

    private BigDecimal energyConsumption;

    private BigDecimal rewardScore;

    private Integer actionTaken;

    private LocalDateTime controlTimestamp;

    private LocalDateTime createTime;

    private String suggestion;
}
