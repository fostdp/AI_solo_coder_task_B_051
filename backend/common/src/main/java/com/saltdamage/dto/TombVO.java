package com.saltdamage.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TombVO {

    private Long id;

    private String name;

    private String description;

    private String location;

    private String dynasty;

    private String builtYear;

    private Double areaSize;

    private String imageUrl;

    private String status;

    private List<ChamberVO> chambers;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
