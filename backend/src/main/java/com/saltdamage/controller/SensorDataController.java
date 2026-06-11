package com.saltdamage.controller;

import com.saltdamage.common.ApiResponse;
import com.saltdamage.dto.BatchSensorDataRequest;
import com.saltdamage.dto.SensorDataRequest;
import com.saltdamage.dto.SensorDataResponse;
import com.saltdamage.service.SensorDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/sensor")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class SensorDataController {

    private final SensorDataService sensorDataService;

    @PostMapping("/data")
    public ApiResponse<SensorDataResponse> receiveSensorData(@Valid @RequestBody SensorDataRequest request) {
        log.info("接收传感器数据, deviceNo: {}", request.getDeviceNo());
        try {
            SensorDataResponse response = sensorDataService.processSensorData(request);
            return ApiResponse.success("数据接收成功", response);
        } catch (Exception e) {
            log.error("处理传感器数据失败", e);
            return ApiResponse.error(500, "数据处理失败: " + e.getMessage());
        }
    }

    @PostMapping("/data/batch")
    public ApiResponse<List<SensorDataResponse>> receiveBatchSensorData(
            @Valid @RequestBody BatchSensorDataRequest request) {
        log.info("接收批量传感器数据, 数量: {}", request.getDataList().size());
        try {
            List<SensorDataResponse> responses = sensorDataService.processBatchSensorData(request);
            return ApiResponse.success("批量数据接收成功", responses);
        } catch (Exception e) {
            log.error("处理批量传感器数据失败", e);
            return ApiResponse.error(500, "批量数据处理失败: " + e.getMessage());
        }
    }

    @GetMapping("/data/latest/{deviceNo}")
    public ApiResponse<SensorDataResponse> getLatestData(@PathVariable String deviceNo) {
        log.info("获取最新传感器数据, deviceNo: {}", deviceNo);
        try {
            SensorDataResponse response = sensorDataService.getLatestData(deviceNo);
            if (response != null) {
                return ApiResponse.success(response);
            } else {
                return ApiResponse.error(404, "未找到数据");
            }
        } catch (Exception e) {
            log.error("获取最新数据失败", e);
            return ApiResponse.error(500, "获取数据失败: " + e.getMessage());
        }
    }
}
