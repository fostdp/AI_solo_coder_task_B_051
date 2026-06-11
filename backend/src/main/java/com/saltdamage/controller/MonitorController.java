package com.saltdamage.controller;

import com.saltdamage.common.ApiResponse;
import com.saltdamage.dto.MonitorDataDTO;
import com.saltdamage.dto.StatisticsDTO;
import com.saltdamage.service.MonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class MonitorController {

    private final MonitorService monitorService;

    @GetMapping("/salt")
    public ApiResponse<List<MonitorDataDTO>> getSaltData(
            @RequestParam(required = false) Long tombId,
            @RequestParam(required = false) Long chamberId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        log.info("查询盐离子数据, tombId: {}, chamberId: {}", tombId, chamberId);
        try {
            List<MonitorDataDTO> data = monitorService.getSaltData(tombId, chamberId, startTime, endTime);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("查询盐离子数据失败", e);
            return ApiResponse.error(500, "查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/env")
    public ApiResponse<List<MonitorDataDTO>> getEnvData(
            @RequestParam(required = false) Long tombId,
            @RequestParam(required = false) Long chamberId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        log.info("查询微环境数据, tombId: {}, chamberId: {}", tombId, chamberId);
        try {
            List<MonitorDataDTO> data = monitorService.getEnvData(tombId, chamberId, startTime, endTime);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("查询微环境数据失败", e);
            return ApiResponse.error(500, "查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/realtime")
    public ApiResponse<MonitorDataDTO> getRealtimeData(@RequestParam String deviceNo) {
        log.info("获取最新实时数据, deviceNo: {}", deviceNo);
        try {
            MonitorDataDTO data = monitorService.getRealtimeData(deviceNo);
            if (data != null) {
                return ApiResponse.success(data);
            } else {
                return ApiResponse.error(404, "未找到实时数据");
            }
        } catch (Exception e) {
            log.error("获取实时数据失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/realtime/list")
    public ApiResponse<List<MonitorDataDTO>> getRealtimeDataList(@RequestParam Long tombId) {
        log.info("获取墓葬所有设备实时数据, tombId: {}", tombId);
        try {
            List<MonitorDataDTO> data = monitorService.getRealtimeDataList(tombId);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取实时数据列表失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/statistics")
    public ApiResponse<StatisticsDTO> getStatistics() {
        log.info("获取统计数据");
        try {
            StatisticsDTO statistics = monitorService.getStatistics();
            return ApiResponse.success(statistics);
        } catch (Exception e) {
            log.error("获取统计数据失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }
}
