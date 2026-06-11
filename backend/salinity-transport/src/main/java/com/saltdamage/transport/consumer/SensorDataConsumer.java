package com.saltdamage.transport.consumer;

import com.saltdamage.common.message.AnalysisMessage;
import com.saltdamage.common.message.KafkaTopics;
import com.saltdamage.common.message.SensorDataMessage;
import com.saltdamage.transport.producer.AnalysisResultProducer;
import com.saltdamage.transport.service.SaltMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorDataConsumer {

    private final SaltMigrationService saltMigrationService;
    private final AnalysisResultProducer analysisResultProducer;

    @KafkaListener(
            topics = KafkaTopics.TOPIC_SENSOR_DATA,
            groupId = "salinity-transport-group",
            containerFactory = "sensorDataKafkaListenerContainerFactory"
    )
    public void consume(SensorDataMessage message) {
        log.info("接收到传感器数据: messageId={}, deviceId={}, sensorType={}",
                message.getMessageId(), message.getDeviceId(), message.getSensorType());

        if (!isSaltTypeData(message)) {
            log.debug("非盐类传感器数据，跳过处理: sensorType={}", message.getSensorType());
            return;
        }

        try {
            AnalysisMessage result = saltMigrationService.analyzeSensorData(message);
            analysisResultProducer.sendAnalysisResult(result);
        } catch (Exception e) {
            log.error("盐分运移分析处理失败: messageId={}, deviceId={}",
                    message.getMessageId(), message.getDeviceId(), e);
        }
    }

    private boolean isSaltTypeData(SensorDataMessage message) {
        if (message.getSensorType() != null) {
            String type = message.getSensorType().toLowerCase();
            return type.contains("salt") || type.contains("盐") ||
                    type.contains("ion") || type.contains("离子") ||
                    type.contains("conductivity") || type.contains("电导率");
        }
        return message.getNaPlus() != null || message.getCa2Plus() != null ||
                message.getSo42Minus() != null || message.getClMinus() != null ||
                message.getTotalSalt() != null;
    }
}
