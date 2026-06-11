package com.saltdamage.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AlarmDTO {

    private Long id;
    private String deviceNo;
    private String alarmType;
    private String alarmLevel;
    private String alarmContent;
    private BigDecimal thresholdValue;
    private BigDecimal currentValue;
    private String status;
    private String processResult;
    private LocalDateTime alarmTime;
    private LocalDateTime processTime;
}
