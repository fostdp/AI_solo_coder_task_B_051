package com.saltdamage.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaltDataVO {

    private Long id;

    private Long chamberId;

    private String chamberName;

    private Long deviceId;

    private String deviceCode;

    private String deviceName;

    private Double saltConcentration;

    private Double conductivity;

    private Double phValue;

    private Double temperature;

    private Double humidity;

    private LocalDateTime collectTime;

    private LocalDateTime createTime;
}
