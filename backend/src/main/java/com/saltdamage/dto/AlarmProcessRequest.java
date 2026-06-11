package com.saltdamage.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AlarmProcessRequest {

    @NotBlank(message = "处理结果不能为空")
    private String processResult;

    private String processor;
}
