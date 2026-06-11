package com.saltdamage.dto;

import lombok.Data;

@Data
public class AnalysisRunRequest {

    private Long tombId;
    private Long chamberId;
    private String analysisType;
}
