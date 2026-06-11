package com.saltdamage.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TombDTO {

    private Long id;
    private String name;
    private String description;
    private String location;
    private String dynasty;
    private String builtYear;
    private Double areaSize;
    private String imageUrl;
    private String status;
    private List<ChamberDTO> chambers;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
