package com.saltdamage.crystal.consumer;

import com.saltdamage.common.message.AnalysisMessage;
import com.saltdamage.crystal.producer.CrystallizationResultProducer;
import com.saltdamage.crystal.service.CrystallizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SaltMigrationResultConsumer {

    private final CrystallizationService crystallizationService;
    private final CrystallizationResultProducer crystallizationResultProducer;

    @KafkaListener(
            topics = "salt-damage-salt-migration-result",
            groupId = "crystal-pressure-group"
    )
    public void onSaltMigrationResult(AnalysisMessage message) {
        log.info("收到盐分运移结果 - messageId: {}, deviceId: {}", message.getMessageId(), message.getDeviceId());

        try {
            AnalysisMessage result = crystallizationService.analyzeCrystallization(message);

            crystallizationResultProducer.sendCrystallizationResult(result);

            log.info("结晶压力分析完成 - messageId: {}, riskLevel: {}, pressure: {}",
                    result.getMessageId(), result.getRiskLevel(), result.getCrystallizationPressure());
        } catch (Exception e) {
            log.error("结晶压力分析失败 - messageId: {}: {}", message.getMessageId(), e.getMessage(), e);
        }
    }
}
