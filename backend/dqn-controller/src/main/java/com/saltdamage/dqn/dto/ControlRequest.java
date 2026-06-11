package com.saltdamage.dqn.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ControlRequest {

    private Long tombId;

    private Long chamberId;

    private BigDecimal targetRh;

    private String mode;

    private Boolean dehumidifierOn;

    private Boolean humidifierOn;
}
