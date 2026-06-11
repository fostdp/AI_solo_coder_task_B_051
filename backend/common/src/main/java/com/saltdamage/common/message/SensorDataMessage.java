package com.saltdamage.common.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SensorDataMessage implements Serializable {

    private String messageId;
    private long timestamp;
    private String deviceId;
    private String tombId;
    private String chamberId;
    private String sensorType;

    private Double naPlus;
    private Double ca2Plus;
    private Double so42Minus;
    private Double clMinus;
    private Double totalSalt;

    private Double temperature;
    private Double humidity;
    private Double windSpeed;

    private Double positionX;
    private Double positionY;
    private Double positionZ;
}
