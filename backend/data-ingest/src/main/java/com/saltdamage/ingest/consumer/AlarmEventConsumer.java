package com.saltdamage.ingest.consumer;

import com.saltdamage.common.message.AlarmMessage;
import com.saltdamage.ingest.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmEventConsumer {

    private final WebSocketService webSocketService;

    @KafkaListener(
            topics = "${spring.kafka.consumer.alarm-topic:salt-damage-alarm-event}",
            groupId = "data-ingest-alarm-consumer"
    )
    public void onAlarmEvent(AlarmMessage alarmMessage) {
        log.info("收到告警事件, messageId: {}, deviceId: {}, alarmType: {}",
                alarmMessage.getMessageId(),
                alarmMessage.getDeviceId(),
                alarmMessage.getAlarmType());

        try {
            webSocketService.pushMessage("ALERT", alarmMessage);
        } catch (Exception e) {
            log.error("推送告警WebSocket消息失败, messageId: {}", alarmMessage.getMessageId(), e);
        }
    }
}
