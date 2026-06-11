package com.saltdamage.crystal.producer;

import com.saltdamage.common.message.AlarmMessage;
import com.saltdamage.common.message.AnalysisMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrystallizationResultProducer {

    private final KafkaTemplate<String, AnalysisMessage> analysisKafkaTemplate;
    private final KafkaTemplate<String, AlarmMessage> alarmKafkaTemplate;

    public void sendCrystallizationResult(AnalysisMessage message) {
        analysisKafkaTemplate.send("salt-damage-crystallization-result", message.getMessageId(), message);
        log.info("已发送结晶压力分析结果 - messageId: {}", message.getMessageId());

        if (isHighOrCriticalRisk(message.getRiskLevel())) {
            AlarmMessage alarm = buildAlarmMessage(message);
            alarmKafkaTemplate.send("salt-damage-alarm-event", alarm.getMessageId(), alarm);
            log.warn("风险等级 {} 触发告警 - messageId: {}, tombId: {}",
                    message.getRiskLevel(), message.getMessageId(), message.getTombId());
        }
    }

    private boolean isHighOrCriticalRisk(String riskLevel) {
        return "HIGH".equalsIgnoreCase(riskLevel) || "CRITICAL".equalsIgnoreCase(riskLevel);
    }

    private AlarmMessage buildAlarmMessage(AnalysisMessage message) {
        AlarmMessage alarm = new AlarmMessage();
        alarm.setMessageId(UUID.randomUUID().toString());
        alarm.setTimestamp(System.currentTimeMillis());
        alarm.setDeviceId(message.getDeviceId());
        alarm.setTombId(message.getTombId());
        alarm.setChamberId(message.getChamberId());
        alarm.setAlarmType("CRYSTALLIZATION_PRESSURE");
        alarm.setAlarmLevel(message.getRiskLevel());
        alarm.setAlarmContent(String.format("结晶压力异常，当前压力: %.2f Pa，风险等级: %s",
                message.getCrystallizationPressure() != null ? message.getCrystallizationPressure() : 0.0,
                message.getRiskLevel()));
        alarm.setCurrentValue(message.getCrystallizationPressure());
        return alarm;
    }
}
