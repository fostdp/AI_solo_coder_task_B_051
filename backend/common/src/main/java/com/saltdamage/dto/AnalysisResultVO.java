package com.saltdamage.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResultVO {

    private Long id;

    private Long chamberId;

    private String chamberName;

    private String analysisType;

    private String resultData;

    private Double riskLevel;

    private String riskDescription;

    private String suggestions;

    private LocalDateTime analysisTime;

    private LocalDateTime createTime;
}
