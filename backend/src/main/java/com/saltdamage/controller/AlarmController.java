package com.saltdamage.controller;

import com.saltdamage.common.ApiResponse;
import com.saltdamage.dto.AlarmConfigDTO;
import com.saltdamage.dto.AlarmDTO;
import com.saltdamage.dto.AlarmProcessRequest;
import com.saltdamage.dto.AlarmStatisticsDTO;
import com.saltdamage.service.AlarmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/alarm")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class AlarmController {

    private final AlarmService alarmService;

    @GetMapping("/list")
    public ApiResponse<Page<AlarmDTO>> getAlarmList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String alarmType,
            @RequestParam(required = false) String alarmLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("获取告警列表, status: {}, alarmType: {}, alarmLevel: {}", status, alarmType, alarmLevel);
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<AlarmDTO> alarms = alarmService.getAlarmList(status, alarmType, alarmLevel, pageable);
            return ApiResponse.success(alarms);
        } catch (Exception e) {
            log.error("获取告警列表失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/process")
    public ApiResponse<AlarmDTO> processAlarm(
            @PathVariable Long id,
            @Valid @RequestBody AlarmProcessRequest request) {
        log.info("处理告警, id: {}", id);
        try {
            AlarmDTO alarm = alarmService.processAlarm(id, request);
            return ApiResponse.success("告警处理成功", alarm);
        } catch (Exception e) {
            log.error("处理告警失败", e);
            return ApiResponse.error(500, "处理失败: " + e.getMessage());
        }
    }

    @GetMapping("/config")
    public ApiResponse<AlarmConfigDTO> getAlarmConfig() {
        log.info("获取告警配置");
        try {
            AlarmConfigDTO config = alarmService.getAlarmConfig();
            return ApiResponse.success(config);
        } catch (Exception e) {
            log.error("获取告警配置失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @PutMapping("/config")
    public ApiResponse<AlarmConfigDTO> updateAlarmConfig(@Valid @RequestBody AlarmConfigDTO configDTO) {
        log.info("更新告警配置");
        try {
            AlarmConfigDTO config = alarmService.updateAlarmConfig(configDTO);
            return ApiResponse.success("配置更新成功", config);
        } catch (Exception e) {
            log.error("更新告警配置失败", e);
            return ApiResponse.error(500, "更新失败: " + e.getMessage());
        }
    }

    @GetMapping("/statistics")
    public ApiResponse<AlarmStatisticsDTO> getAlarmStatistics() {
        log.info("获取告警统计");
        try {
            AlarmStatisticsDTO statistics = alarmService.getAlarmStatistics();
            return ApiResponse.success(statistics);
        } catch (Exception e) {
            log.error("获取告警统计失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }
}
