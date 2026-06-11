package com.saltdamage.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EnvironmentStateDTO {

    private BigDecimal currentRh;

    private BigDecimal rhTrend;

    private Integer currentHour;

    private Boolean dehumidifierStatus;

    private Boolean humidifierStatus;

    private Integer recommendedAction;

    private BigDecimal expectedReward;
}
