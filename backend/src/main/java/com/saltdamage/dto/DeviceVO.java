package com.saltdamage.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceVO {

    private Long id;

    private Long chamberId;

    private String chamberName;

    private Long tombId;

    private String tombName;

    private String deviceCode;

    private String name;

    private String deviceType;

    private String description;

    private Double positionX;

    private Double positionY;

    private Double positionZ;

    private String sensorType;

    private Integer samplingInterval;

    private String status;

    private LocalDateTime lastOnlineTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
