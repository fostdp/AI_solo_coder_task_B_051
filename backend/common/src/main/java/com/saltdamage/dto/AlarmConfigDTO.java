package com.saltdamage.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AlarmConfigDTO {

    private Long id;

    @NotNull(message = "盐离子浓度阈值不能为空")
    private BigDecimal saltThreshold;

    @NotNull(message = "湿度阈值不能为空")
    private BigDecimal humidityThreshold;

    @NotNull(message = "湿度持续时间不能为空")
    private Integer humidityDurationHours;

    private BigDecimal temperatureThreshold;

    private BigDecimal co2Threshold;

    private Boolean enableDingTalk;

    private Boolean enableWebSocket;

    private String dingTalkWebhook;

    private String dingTalkSecret;
}
