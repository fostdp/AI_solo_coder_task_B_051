package com.saltdamage.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MonitorDataDTO {

    private Long id;
    private String deviceNo;
    private Long tombId;
    private Long chamberId;
    private BigDecimal saltConcentration;
    private BigDecimal temperature;
    private BigDecimal humidity;
    private BigDecimal phValue;
    private BigDecimal co2Concentration;
    private BigDecimal illuminance;
    private BigDecimal pressure;
    private BigDecimal totalSaltAmount;
    private LocalDateTime collectTime;
}
