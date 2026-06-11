package com.saltdamage.alert.consumer;

import com.saltdamage.alert.service.AlarmCheckService;
import com.saltdamage.alert.service.DingTalkService;
import com.saltdamage.alert.service.WebSocketService;
import com.saltdamage.common.message.AlarmMessage;
import com.saltdamage.common.message.AnalysisMessage;
import com.saltdamage.common.message.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrystallizationResultConsumer {

    private final AlarmCheckService alarmCheckService;
    private final DingTalkService dingTalkService;
    private final WebSocketService webSocketService;

    @KafkaListener(
            topics = KafkaTopics.TOPIC_CRYSTALLIZATION_RESULT,
            groupId = "alert-push-crystal-group"
    )
    public void onMessage(AnalysisMessage message) {
        log.info("收到结晶压力分析结果, messageId: {}, riskLevel: {}",
                message.getMessageId(), message.getRiskLevel());

        try {
            String riskLevel = message.getRiskLevel();
            if ("HIGH".equalsIgnoreCase(riskLevel) || "CRITICAL".equalsIgnoreCase(riskLevel)) {
                log.warn("检测到高风险结晶压力, deviceId: {}, riskLevel: {}",
                        message.getDeviceId(), riskLevel);

                AlarmMessage alarmMessage = buildAlarmFromAnalysis(message);
                alarmCheckService.checkAndTriggerAlarm(alarmMessage);
            }
        } catch (Exception e) {
            log.error("处理结晶压力分析结果失败, messageId: {}", message.getMessageId(), e);
        }
    }

    private AlarmMessage buildAlarmFromAnalysis(AnalysisMessage analysis) {
        AlarmMessage alarm = new AlarmMessage();
        alarm.setMessageId(analysis.getMessageId());
        alarm.setTimestamp(System.currentTimeMillis());
        alarm.setDeviceId(analysis.getDeviceId());
        alarm.setTombId(analysis.getTombId());
        alarm.setChamberId(analysis.getChamberId());
        alarm.setAlarmType("CRYSTALLIZATION_PRESSURE");
        alarm.setAlarmLevel(mapRiskToAlarmLevel(analysis.getRiskLevel()));
        alarm.setAlarmContent(String.format("结晶压力风险等级: %s，结晶压力: %s MPa",
                analysis.getRiskLevel(), analysis.getCrystallizationPressure()));
        alarm.setThresholdValue(BigDecimal.valueOf(5.0));
        alarm.setCurrentValue(analysis.getCrystallizationPressure() != null
                ? BigDecimal.valueOf(analysis.getCrystallizationPressure()) : null);
        return alarm;
    }

    private String mapRiskToAlarmLevel(String riskLevel) {
        return switch (riskLevel.toUpperCase()) {
            case "CRITICAL" -> "high";
            case "HIGH" -> "high";
            case "MEDIUM" -> "medium";
            default -> "low";
        };
    }
}
