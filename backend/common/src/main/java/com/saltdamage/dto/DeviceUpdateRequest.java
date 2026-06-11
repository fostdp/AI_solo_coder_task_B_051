package com.saltdamage.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceUpdateRequest {

    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    private String location;

    private String remark;
}
