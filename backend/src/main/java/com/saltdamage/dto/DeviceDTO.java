package com.saltdamage.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceDTO {

    private Long id;
    private String deviceNo;
    private String deviceName;
    private String deviceType;
    private String manufacturer;
    private String model;
    private String firmwareVersion;
    private String ipAddress;
    private Integer port;
    private String status;
    private Long tombId;
    private Long chamberId;
    private String location;
    private LocalDateTime lastOnlineTime;
    private LocalDateTime installTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
