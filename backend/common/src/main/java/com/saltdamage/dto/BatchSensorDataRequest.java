package com.saltdamage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BatchSensorDataRequest {

    @Valid
    @NotEmpty(message = "数据列表不能为空")
    private List<SensorDataRequest> dataList;
}
