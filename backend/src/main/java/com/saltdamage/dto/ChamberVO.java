package com.saltdamage.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChamberVO {

    private Long id;

    private Long tombId;

    private String tombName;

    private String name;

    private String description;

    private Double positionX;

    private Double positionY;

    private Double positionZ;

    private Double width;

    private Double height;

    private Double depth;

    private String imageUrl;

    private String status;

    private List<DeviceVO> devices;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
