package com.saltdamage.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StatisticsDTO {

    private Long deviceCount;
    private Long onlineDeviceCount;
    private Long offlineDeviceCount;
    private Long alarmCount;
    private Long unprocessedAlarmCount;
    private BigDecimal avgSaltConcentration;
    private BigDecimal maxSaltConcentration;
    private BigDecimal avgHumidity;
    private BigDecimal avgTemperature;
    private Long dataCountToday;
}
