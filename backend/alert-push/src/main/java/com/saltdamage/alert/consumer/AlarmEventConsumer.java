package com.saltdamage.alert.consumer;

import com.saltdamage.alert.repository.AlarmRepository;
import com.saltdamage.alert.service.DingTalkService;
import com.saltdamage.alert.service.WebSocketService;
import com.saltdamage.common.message.AlarmMessage;
import com.saltdamage.common.message.KafkaTopics;
import com.saltdamage.entity.Alarm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmEventConsumer {

    private final AlarmRepository alarmRepository;
    private final DingTalkService dingTalkService;
    private final WebSocketService webSocketService;

    @KafkaListener(
            topics = KafkaTopics.TOPIC_ALARM_EVENT,
            groupId = "alert-push-alarm-group"
    )
    public void onMessage(AlarmMessage message) {
        log.info("收到告警事件, messageId: {}, alarmType: {}, alarmLevel: {}",
                message.getMessageId(), message.getAlarmType(), message.getAlarmLevel());

        try {
            Alarm alarm = persistAlarm(message);
            log.info("告警已持久化, alarmId: {}", alarm.getId());

            sendNotifications(alarm);
        } catch (Exception e) {
            log.error("处理告警事件失败, messageId: {}", message.getMessageId(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Alarm persistAlarm(AlarmMessage message) {
        Alarm alarm = new Alarm();
        alarm.setDeviceNo(message.getDeviceId());
        alarm.setAlarmType(message.getAlarmType());
        alarm.setAlarmLevel(message.getAlarmLevel());
        alarm.setAlarmContent(message.getAlarmContent());
        alarm.setThresholdValue(message.getThresholdValue() != null
                ? new BigDecimal(message.getThresholdValue().toString()) : null);
        alarm.setCurrentValue(message.getCurrentValue() != null
                ? new BigDecimal(message.getCurrentValue().toString()) : null);
        alarm.setStatus("unprocessed");
        alarm.setAlarmTime(LocalDateTime.now());

        return alarmRepository.save(alarm);
    }

    private void sendNotifications(Alarm alarm) {
        try {
            dingTalkService.sendAlarmMessage(alarm);
            log.info("钉钉告警通知发送成功, alarmId: {}", alarm.getId());
        } catch (Exception e) {
            log.error("钉钉告警通知发送失败, alarmId: {}", alarm.getId(), e);
        }

        try {
            webSocketService.pushAlarm(alarm);
            log.info("WebSocket告警推送成功, alarmId: {}", alarm.getId());
        } catch (Exception e) {
            log.error("WebSocket告警推送失败, alarmId: {}", alarm.getId(), e);
        }
    }
}
