package com.saltdamage.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChamberDTO {

    private Long id;
    private Long tombId;
    private String name;
    private String description;
    private String location;
    private Double areaSize;
    private String wallMaterial;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
