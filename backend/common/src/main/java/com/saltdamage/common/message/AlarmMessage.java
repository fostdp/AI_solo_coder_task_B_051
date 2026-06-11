package com.saltdamage.common.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlarmMessage implements Serializable {

    private String messageId;
    private long timestamp;
    private String deviceId;
    private String tombId;
    private String chamberId;
    private String alarmType;
    private String alarmLevel;
    private String alarmContent;
    private Double thresholdValue;
    private Double currentValue;
}
