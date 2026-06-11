package com.saltdamage.alert.service;

import com.saltdamage.alert.repository.AlarmConfigRepository;
import com.saltdamage.alert.repository.AlarmRepository;
import com.saltdamage.common.message.AlarmMessage;
import com.saltdamage.entity.Alarm;
import com.saltdamage.entity.AlarmConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmCheckService {

    private final AlarmRepository alarmRepository;
    private final AlarmConfigRepository alarmConfigRepository;
    private final DingTalkService dingTalkService;
    private final WebSocketService webSocketService;

    @Value("${alarm.salt-threshold:5}")
    private BigDecimal defaultSaltThreshold;

    @Value("${alarm.humidity-threshold:75}")
    private BigDecimal defaultHumidityThreshold;

    @Value("${alarm.humidity-duration:48}")
    private int defaultHumidityDurationHours;

    public boolean checkSaltAlarm(AlarmMessage message) {
        BigDecimal saltThreshold = getSaltThreshold();
        if (message.getCurrentValue() != null && message.getCurrentValue().compareTo(saltThreshold) >= 0) {
            log.info("盐离子超标告警, device: {}, current: {}, threshold: {}",
                    message.getDeviceId(), message.getCurrentValue(), saltThreshold);
            return true;
        }
        return false;
    }

    public boolean checkHumidityAlarm(AlarmMessage message) {
        BigDecimal humidityThreshold = getHumidityThreshold();
        int durationHours = getHumidityDurationHours();

        if (message.getCurrentValue() != null && message.getCurrentValue().compareTo(humidityThreshold) >= 0) {
            LocalDateTime startTime = LocalDateTime.now().minusHours(durationHours);
            Long humidityDataCount = alarmRepository.countByAlarmTimeAfter(startTime);

            if (humidityDataCount != null && humidityDataCount >= 2) {
                log.info("湿度持续超标告警, device: {}, duration: {}h, current: {}, threshold: {}%",
                        message.getDeviceId(), durationHours, message.getCurrentValue(), humidityThreshold);
                return true;
            }
        }
        return false;
    }

    @Async
    @Transactional(rollbackFor = Exception.class)
    public void checkAndTriggerAlarm(AlarmMessage message) {
        log.info("开始告警检测与触发, device: {}, type: {}", message.getDeviceId(), message.getAlarmType());

        boolean saltAlarm = checkSaltAlarm(message);
        boolean humidityAlarm = checkHumidityAlarm(message);

        if (saltAlarm) {
            BigDecimal threshold = getSaltThreshold();
            createAndSendAlarm(
                    message.getDeviceId(),
                    "SALT_EXCEEDED",
                    "high",
                    String.format("盐离子浓度超标，当前值：%s mg/cm²，阈值：%s mg/cm²",
                            message.getCurrentValue(), threshold),
                    threshold,
                    message.getCurrentValue() != null ? new BigDecimal(message.getCurrentValue().toString()) : null
            );
        }

        if (humidityAlarm) {
            BigDecimal threshold = getHumidityThreshold();
            int duration = getHumidityDurationHours();
            createAndSendAlarm(
                    message.getDeviceId(),
                    "HUMIDITY_EXCEEDED",
                    "medium",
                    String.format("湿度过高且持续%d小时，当前湿度：%s%%，阈值：%s%%",
                            duration, message.getCurrentValue(), threshold),
                    threshold,
                    message.getCurrentValue() != null ? new BigDecimal(message.getCurrentValue().toString()) : null
            );
        }

        if ("CRYSTALLIZATION_PRESSURE".equals(message.getAlarmType())) {
            createAndSendAlarm(
                    message.getDeviceId(),
                    message.getAlarmType(),
                    message.getAlarmLevel(),
                    message.getAlarmContent(),
                    message.getThresholdValue() != null ? new BigDecimal(message.getThresholdValue().toString()) : null,
                    message.getCurrentValue() != null ? new BigDecimal(message.getCurrentValue().toString()) : null
            );
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void createAndSendAlarm(String deviceNo, String alarmType, String alarmLevel,
                                   String alarmContent, BigDecimal thresholdValue, BigDecimal currentValue) {
        boolean existingAlarm = alarmRepository.existsByDeviceNoAndAlarmTypeAndStatusIn(
                deviceNo, alarmType, Arrays.asList("unprocessed", "processing"));

        if (existingAlarm) {
            log.info("告警已存在，跳过创建, device: {}, type: {}", deviceNo, alarmType);
            return;
        }

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
        log.info("异步发送告警通知, alarmId: {}", alarm.getId());

        AlarmConfig config = alarmConfigRepository.findFirstByOrderByIdDesc().orElse(null);

        if (config != null && Boolean.TRUE.equals(config.getEnableDingTalk())) {
            try {
                dingTalkService.sendAlarmMessage(alarm);
                log.info("钉钉告警发送成功, alarmId: {}", alarm.getId());
            } catch (Exception e) {
                log.error("钉钉告警发送失败", e);
            }
        } else if (config == null) {
            try {
                dingTalkService.sendAlarmMessage(alarm);
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
        } else if (config == null) {
            try {
                webSocketService.pushAlarm(alarm);
            } catch (Exception e) {
                log.error("WebSocket告警推送失败", e);
            }
        }
    }

    private BigDecimal getSaltThreshold() {
        return alarmConfigRepository.findFirstByOrderByIdDesc()
                .filter(c -> c.getSaltThreshold() != null)
                .map(AlarmConfig::getSaltThreshold)
                .orElse(defaultSaltThreshold);
    }

    private BigDecimal getHumidityThreshold() {
        return alarmConfigRepository.findFirstByOrderByIdDesc()
                .filter(c -> c.getHumidityThreshold() != null)
                .map(AlarmConfig::getHumidityThreshold)
                .orElse(defaultHumidityThreshold);
    }

    private int getHumidityDurationHours() {
        return alarmConfigRepository.findFirstByOrderByIdDesc()
                .filter(c -> c.getHumidityDurationHours() != null)
                .map(AlarmConfig::getHumidityDurationHours)
                .orElse(defaultHumidityDurationHours);
    }
}
