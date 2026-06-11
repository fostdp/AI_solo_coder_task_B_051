package com.saltdamage.dto;

import lombok.Data;

@Data
public class AlarmStatisticsDTO {

    private Long totalCount;
    private Long unprocessedCount;
    private Long processingCount;
    private Long processedCount;
    private Long highLevelCount;
    private Long mediumLevelCount;
    private Long lowLevelCount;
    private Long saltAlarmCount;
    private Long humidityAlarmCount;
    private Long temperatureAlarmCount;
    private Long todayCount;
    private Long weekCount;
    private Long monthCount;
}
