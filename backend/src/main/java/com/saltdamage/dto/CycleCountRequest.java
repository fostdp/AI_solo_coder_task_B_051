package com.saltdamage.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CycleCountRequest {

    private Long tombId;
    private Long chamberId;
    private Long deviceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String periodType;
}
