package com.saltdamage.service;

import com.saltdamage.dto.AlarmConfigDTO;
import com.saltdamage.dto.AlarmDTO;
import com.saltdamage.dto.AlarmProcessRequest;
import com.saltdamage.dto.AlarmStatisticsDTO;
import com.saltdamage.entity.Alarm;
import com.saltdamage.entity.AlarmConfig;
import com.saltdamage.entity.SensorData;
import com.saltdamage.repository.AlarmConfigRepository;
import com.saltdamage.repository.AlarmRepository;
import com.saltdamage.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmService {

    private final AlarmRepository alarmRepository;
    private final AlarmConfigRepository alarmConfigRepository;
    private final SensorDataRepository sensorDataRepository;
    private final DingTalkService dingTalkService;
    private final WebSocketService webSocketService;

    private static final BigDecimal DEFAULT_SALT_THRESHOLD = new BigDecimal("5");
    private static final BigDecimal DEFAULT_HUMIDITY_THRESHOLD = new BigDecimal("75");
    private static final int DEFAULT_HUMIDITY_DURATION_HOURS = 48;

    public Page<AlarmDTO> getAlarmList(String status, String alarmType, String alarmLevel, Pageable pageable) {
        log.info("获取告警列表, status: {}, alarmType: {}, alarmLevel: {}", status, alarmType, alarmLevel);

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

        return alarmPage.map(this::convertToDTO);
    }

    @Transactional(rollbackFor = Exception.class)
    public AlarmDTO processAlarm(Long id, AlarmProcessRequest request) {
        log.info("处理告警, id: {}, processResult: {}", id, request.getProcessResult());

        Alarm alarm = alarmRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("告警不存在"));

        alarm.setStatus("processed");
        alarm.setProcessResult(request.getProcessResult());
        alarm.setProcessor(request.getProcessor());
        alarm.setProcessTime(LocalDateTime.now());

        alarm = alarmRepository.save(alarm);
        log.info("告警已处理, id: {}", id);

        return convertToDTO(alarm);
    }

    public AlarmConfigDTO getAlarmConfig() {
        log.info("获取告警配置");
        return alarmConfigRepository.findFirstByOrderByIdDesc()
                .map(this::convertToConfigDTO)
                .orElseGet(this::getDefaultConfig);
    }

    @Transactional(rollbackFor = Exception.class)
    public AlarmConfigDTO updateAlarmConfig(AlarmConfigDTO configDTO) {
        log.info("更新告警配置");
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

        return convertToConfigDTO(config);
    }

    public AlarmStatisticsDTO getAlarmStatistics() {
        log.info("获取告警统计");
        AlarmStatisticsDTO statistics = new AlarmStatisticsDTO();

        statistics.setTotalCount(alarmRepository.count());
        statistics.setUnprocessedCount(alarmRepository.countByStatus("unprocessed"));
        statistics.setProcessingCount(alarmRepository.countByStatus("processing"));
        statistics.setProcessedCount(alarmRepository.countByStatus("processed"));

        statistics.setHighLevelCount(alarmRepository.countByAlarmLevel("high"));
        statistics.setMediumLevelCount(alarmRepository.countByAlarmLevel("medium"));
        statistics.setLowLevelCount(alarmRepository.countByAlarmLevel("low"));

        statistics.setSaltAlarmCount(alarmRepository.countByAlarmType("SALT_EXCEEDED"));
        statistics.setHumidityAlarmCount(alarmRepository.countByAlarmType("HUMIDITY_EXCEEDED"));
        statistics.setTemperatureAlarmCount(alarmRepository.countByAlarmType("TEMPERATURE_EXCEEDED"));

        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime weekStart = LocalDateTime.of(LocalDate.now().minusDays(7), LocalTime.MIN);
        LocalDateTime monthStart = LocalDateTime.of(LocalDate.now().minusDays(30), LocalTime.MIN);

        statistics.setTodayCount(alarmRepository.countByAlarmTimeAfter(todayStart));
        statistics.setWeekCount(alarmRepository.countByAlarmTimeAfter(weekStart));
        statistics.setMonthCount(alarmRepository.countByAlarmTimeAfter(monthStart));

        return statistics;
    }

    @Async
    @Transactional(rollbackFor = Exception.class)
    public void checkAndTriggerAlarm(SensorData sensorData) {
        log.info("开始告警检测, deviceNo: {}", sensorData.getDeviceNo());

        AlarmConfig config = alarmConfigRepository.findFirstByOrderByIdDesc()
                .orElse(null);

        BigDecimal saltThreshold = config != null && config.getSaltThreshold() != null
                ? config.getSaltThreshold() : DEFAULT_SALT_THRESHOLD;
        BigDecimal humidityThreshold = config != null && config.getHumidityThreshold() != null
                ? config.getHumidityThreshold() : DEFAULT_HUMIDITY_THRESHOLD;
        int humidityDurationHours = config != null && config.getHumidityDurationHours() != null
                ? config.getHumidityDurationHours() : DEFAULT_HUMIDITY_DURATION_HOURS;

        checkSaltAlarm(sensorData, saltThreshold);
        checkHumidityAlarm(sensorData, humidityThreshold, humidityDurationHours);
        checkTemperatureAlarm(sensorData, config);
    }

    private void checkSaltAlarm(SensorData sensorData, BigDecimal threshold) {
        if (sensorData.getSaltConcentration() != null
                && sensorData.getSaltConcentration().compareTo(threshold) >= 0) {

            boolean existingAlarm = alarmRepository.existsByDeviceNoAndAlarmTypeAndStatusIn(
                    sensorData.getDeviceNo(),
                    "SALT_EXCEEDED",
                    Arrays.asList("unprocessed", "processing"));

            if (!existingAlarm) {
                createAndSendAlarm(
                        sensorData.getDeviceNo(),
                        "SALT_EXCEEDED",
                        "high",
                        String.format("盐离子浓度超标，当前值：%s mg/cm²，阈值：%s mg/cm²",
                                sensorData.getSaltConcentration(), threshold),
                        threshold,
                        sensorData.getSaltConcentration()
                );
            }
        }
    }

    private void checkHumidityAlarm(SensorData sensorData, BigDecimal threshold, int durationHours) {
        if (sensorData.getHumidity() != null
                && sensorData.getHumidity().compareTo(threshold) >= 0) {

            LocalDateTime startTime = LocalDateTime.now().minusHours(durationHours);
            List<SensorData> humidityData = sensorDataRepository.findHumidityDataAboveThreshold(
                    sensorData.getDeviceNo(), threshold, startTime);

            if (humidityData.size() >= 2) {
                boolean existingAlarm = alarmRepository.existsByDeviceNoAndAlarmTypeAndStatusIn(
                        sensorData.getDeviceNo(),
                        "HUMIDITY_EXCEEDED",
                        Arrays.asList("unprocessed", "processing"));

                if (!existingAlarm) {
                    createAndSendAlarm(
                            sensorData.getDeviceNo(),
                            "HUMIDITY_EXCEEDED",
                            "medium",
                            String.format("湿度过高且持续%d小时，当前湿度：%s%%，阈值：%s%%",
                                    durationHours, sensorData.getHumidity(), threshold),
                            threshold,
                            sensorData.getHumidity()
                    );
                }
            }
        }
    }

    private void checkTemperatureAlarm(SensorData sensorData, AlarmConfig config) {
        if (config == null || config.getTemperatureThreshold() == null) {
            return;
        }

        if (sensorData.getTemperature() != null
                && sensorData.getTemperature().compareTo(config.getTemperatureThreshold()) >= 0) {

            boolean existingAlarm = alarmRepository.existsByDeviceNoAndAlarmTypeAndStatusIn(
                    sensorData.getDeviceNo(),
                    "TEMPERATURE_EXCEEDED",
                    Arrays.asList("unprocessed", "processing"));

            if (!existingAlarm) {
                createAndSendAlarm(
                        sensorData.getDeviceNo(),
                        "TEMPERATURE_EXCEEDED",
                        "medium",
                        String.format("温度超标，当前温度：%s℃，阈值：%s℃",
                                sensorData.getTemperature(), config.getTemperatureThreshold()),
                        config.getTemperatureThreshold(),
                        sensorData.getTemperature()
                );
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void createAndSendAlarm(String deviceNo, String alarmType, String alarmLevel,
                                   String alarmContent, BigDecimal thresholdValue, BigDecimal currentValue) {
        Alarm alarm = new Alarm();
        alarm.setDeviceNo(deviceNo);
        alarm.setAlarmType(alarmType);
        alarm.setAlarmLevel(alarmLevel);
        alarm.setAlarmContent(alarmContent);
        alarm.setThresholdValue(thresholdValue);
        alarm.setCurrentValue(currentValue);
        alarm.setStatus("unprocessed");
        alarm.setAlarmTime(LocalDateTime.now());

        alarm = alarmRepository.save(alarm);
        log.info("告警已创建, id: {}, type: {}", alarm.getId(), alarmType);

        asyncSendAlarmNotification(alarm);
    }

    @Async
    public void asyncSendAlarmNotification(Alarm alarm) {
        log.info("开始异步发送告警通知, alarmId: {}", alarm.getId());

        AlarmConfig config = alarmConfigRepository.findFirstByOrderByIdDesc().orElse(null);

        if (config != null && Boolean.TRUE.equals(config.getEnableDingTalk())) {
            try {
                dingTalkService.sendAlarmMessage(alarm);
                log.info("钉钉告警发送成功, alarmId: {}", alarm.getId());
            } catch (Exception e) {
                log.error("钉钉告警发送失败", e);
            }
        }

        if (config != null && Boolean.TRUE.equals(config.getEnableWebSocket())) {
            try {
                webSocketService.pushAlarm(alarm);
                log.info("WebSocket告警推送成功, alarmId: {}", alarm.getId());
            } catch (Exception e) {
                log.error("WebSocket告警推送失败", e);
            }
        }
    }

    private AlarmDTO convertToDTO(Alarm alarm) {
        AlarmDTO dto = new AlarmDTO();
        dto.setId(alarm.getId());
        dto.setDeviceNo(alarm.getDeviceNo());
        dto.setAlarmType(alarm.getAlarmType());
        dto.setAlarmLevel(alarm.getAlarmLevel());
        dto.setAlarmContent(alarm.getAlarmContent());
        dto.setThresholdValue(alarm.getThresholdValue());
        dto.setCurrentValue(alarm.getCurrentValue());
        dto.setStatus(alarm.getStatus());
        dto.setProcessResult(alarm.getProcessResult());
        dto.setAlarmTime(alarm.getAlarmTime());
        dto.setProcessTime(alarm.getProcessTime());
        return dto;
    }

    private AlarmConfigDTO convertToConfigDTO(AlarmConfig config) {
        AlarmConfigDTO dto = new AlarmConfigDTO();
        dto.setId(config.getId());
        dto.setSaltThreshold(config.getSaltThreshold());
        dto.setHumidityThreshold(config.getHumidityThreshold());
        dto.setHumidityDurationHours(config.getHumidityDurationHours());
        dto.setTemperatureThreshold(config.getTemperatureThreshold());
        dto.setCo2Threshold(config.getCo2Threshold());
        dto.setEnableDingTalk(config.getEnableDingTalk());
        dto.setEnableWebSocket(config.getEnableWebSocket());
        dto.setDingTalkWebhook(config.getDingTalkWebhook());
        dto.setDingTalkSecret(config.getDingTalkSecret());
        return dto;
    }

    private AlarmConfigDTO getDefaultConfig() {
        AlarmConfigDTO dto = new AlarmConfigDTO();
        dto.setSaltThreshold(DEFAULT_SALT_THRESHOLD);
        dto.setHumidityThreshold(DEFAULT_HUMIDITY_THRESHOLD);
        dto.setHumidityDurationHours(DEFAULT_HUMIDITY_DURATION_HOURS);
        dto.setEnableDingTalk(true);
        dto.setEnableWebSocket(true);
        return dto;
    }
}
