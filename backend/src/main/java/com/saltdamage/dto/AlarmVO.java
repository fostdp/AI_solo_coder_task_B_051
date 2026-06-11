package com.saltdamage.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlarmVO {

    private Long id;

    private Long deviceId;

    private String deviceCode;

    private String deviceName;

    private Long chamberId;

    private String chamberName;

    private String alarmType;

    private String alarmName;

    private String severity;

    private String alarmMessage;

    private String alarmData;

    private String status;

    private LocalDateTime alarmTime;

    private LocalDateTime handleTime;

    private String handleUser;

    private String handleRemark;

    private LocalDateTime createTime;
}
