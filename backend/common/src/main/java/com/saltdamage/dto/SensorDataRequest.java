package com.saltdamage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SensorDataRequest {

    @NotBlank(message = "设备编码不能为空")
    private String deviceCode;

    @NotBlank(message = "传感器类型不能为空")
    private String sensorType;

    @NotNull(message = "采集时间不能为空")
    private LocalDateTime collectTime;

    private Map<String, Object> data;
}
