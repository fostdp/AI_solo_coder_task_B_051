package com.saltdamage.alert.controller;

import com.saltdamage.alert.repository.AlarmConfigRepository;
import com.saltdamage.alert.repository.AlarmRepository;
import com.saltdamage.common.ApiResponse;
import com.saltdamage.entity.Alarm;
import com.saltdamage.entity.AlarmConfig;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/alarm")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class AlarmController {

    private final AlarmRepository alarmRepository;
    private final AlarmConfigRepository alarmConfigRepository;

    @GetMapping("/list")
    public ApiResponse<Page<Alarm>> getAlarmList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String alarmType,
            @RequestParam(required = false) String alarmLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("获取告警列表, status: {}, alarmType: {}, alarmLevel: {}", status, alarmType, alarmLevel);
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "alarmTime"));
            Page<Alarm> alarmPage;

            if (status != null && !status.isEmpty()) {
                alarmPage = alarmRepository.findByStatusOrderByAlarmTimeDesc(status, pageable);
            } else if (alarmType != null && !alarmType.isEmpty()) {
                alarmPage = alarmRepository.findByAlarmTypeOrderByAlarmTimeDesc(alarmType, pageable);
            } else if (alarmLevel != null && !alarmLevel.isEmpty()) {
                alarmPage = alarmRepository.findByAlarmLevelOrderByAlarmTimeDesc(alarmLevel, pageable);
            } else {
                alarmPage = alarmRepository.findAll(pageable);
            }

            return ApiResponse.success(alarmPage);
        } catch (Exception e) {
            log.error("获取告警列表失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @PostMapping("/process")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Alarm> processAlarm(@RequestBody Map<String, Object> request) {
        Long id = Long.valueOf(request.get("id").toString());
        String processResult = (String) request.get("processResult");
        String processor = (String) request.get("processor");

        log.info("处理告警, id: {}", id);
        try {
            Alarm alarm = alarmRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("告警不存在"));

            alarm.setStatus("processed");
            alarm.setProcessResult(processResult);
            alarm.setProcessor(processor);
            alarm.setProcessTime(LocalDateTime.now());

            alarm = alarmRepository.save(alarm);
            log.info("告警已处理, id: {}", id);

            return ApiResponse.success("告警处理成功", alarm);
        } catch (Exception e) {
            log.error("处理告警失败", e);
            return ApiResponse.error(500, "处理失败: " + e.getMessage());
        }
    }

    @GetMapping("/config")
    public ApiResponse<AlarmConfig> getAlarmConfig() {
        log.info("获取告警配置");
        try {
            AlarmConfig config = alarmConfigRepository.findFirstByOrderByIdDesc()
                    .orElseGet(this::getDefaultConfig);
            return ApiResponse.success(config);
        } catch (Exception e) {
            log.error("获取告警配置失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @PutMapping("/config")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<AlarmConfig> updateAlarmConfig(@Valid @RequestBody AlarmConfig configDTO) {
        log.info("更新告警配置");
        try {
            AlarmConfig config = new AlarmConfig();
            config.setSaltThreshold(configDTO.getSaltThreshold());
            config.setHumidityThreshold(configDTO.getHumidityThreshold());
            config.setHumidityDurationHours(configDTO.getHumidityDurationHours());
            config.setTemperatureThreshold(configDTO.getTemperatureThreshold());
            config.setCo2Threshold(configDTO.getCo2Threshold());
            config.setEnableDingTalk(configDTO.getEnableDingTalk());
            config.setEnableWebSocket(configDTO.getEnableWebSocket());
            config.setDingTalkWebhook(configDTO.getDingTalkWebhook());
            config.setDingTalkSecret(configDTO.getDingTalkSecret());

            config = alarmConfigRepository.save(config);
            log.info("告警配置已更新, id: {}", config.getId());

            return ApiResponse.success("配置更新成功", config);
        } catch (Exception e) {
            log.error("更新告警配置失败", e);
            return ApiResponse.error(500, "更新失败: " + e.getMessage());
        }
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getAlarmStatistics() {
        log.info("获取告警统计");
        try {
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("totalCount", alarmRepository.count());
            statistics.put("unprocessedCount", alarmRepository.countByStatus("unprocessed"));
            statistics.put("processingCount", alarmRepository.countByStatus("processing"));
            statistics.put("processedCount", alarmRepository.countByStatus("processed"));

            statistics.put("highLevelCount", alarmRepository.countByAlarmLevel("high"));
            statistics.put("mediumLevelCount", alarmRepository.countByAlarmLevel("medium"));
            statistics.put("lowLevelCount", alarmRepository.countByAlarmLevel("low"));

            statistics.put("saltAlarmCount", alarmRepository.countByAlarmType("SALT_EXCEEDED"));
            statistics.put("humidityAlarmCount", alarmRepository.countByAlarmType("HUMIDITY_EXCEEDED"));
            statistics.put("temperatureAlarmCount", alarmRepository.countByAlarmType("TEMPERATURE_EXCEEDED"));

            LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
            LocalDateTime weekStart = LocalDateTime.of(LocalDate.now().minusDays(7), LocalTime.MIN);
            LocalDateTime monthStart = LocalDateTime.of(LocalDate.now().minusDays(30), LocalTime.MIN);

            statistics.put("todayCount", alarmRepository.countByAlarmTimeAfter(todayStart));
            statistics.put("weekCount", alarmRepository.countByAlarmTimeAfter(weekStart));
            statistics.put("monthCount", alarmRepository.countByAlarmTimeAfter(monthStart));

            return ApiResponse.success(statistics);
        } catch (Exception e) {
            log.error("获取告警统计失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    private AlarmConfig getDefaultConfig() {
        AlarmConfig config = new AlarmConfig();
        config.setSaltThreshold(new BigDecimal("5"));
        config.setHumidityThreshold(new BigDecimal("75"));
        config.setHumidityDurationHours(48);
        config.setEnableDingTalk(true);
        config.setEnableWebSocket(true);
        return config;
    }
}
