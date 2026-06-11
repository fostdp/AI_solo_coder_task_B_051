package com.saltdamage.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvDataVO {

    private Long id;

    private Long chamberId;

    private String chamberName;

    private Long deviceId;

    private String deviceCode;

    private String deviceName;

    private Double temperature;

    private Double humidity;

    private Double co2Concentration;

    private Double illuminance;

    private Double airPressure;

    private Double windSpeed;

    private LocalDateTime collectTime;

    private LocalDateTime createTime;
}
