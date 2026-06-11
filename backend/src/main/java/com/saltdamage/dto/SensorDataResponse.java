package com.saltdamage.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SensorDataResponse {

    private Long id;

    private String deviceCode;

    private String sensorType;

    private LocalDateTime collectTime;

    private String data;

    private LocalDateTime createTime;
}
