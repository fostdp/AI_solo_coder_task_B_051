package com.saltdamage.rainflow.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CycleCountRequest {

    private Long tombId;
    private Long chamberId;
    private Long deviceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String periodType;
    private List<Double> humidityData;
}
