package com.saltdamage.dto;

import lombok.Data;

@Data
public class StoreDataRequest {

    private String dataType;
    private String dataJson;
    private String operator;
    private String description;
}
