package com.saltdamage.ingest.producer;

import com.saltdamage.common.message.KafkaTopics;
import com.saltdamage.common.message.SensorDataMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorDataKafkaProducer {

    private final KafkaTemplate<String, SensorDataMessage> kafkaTemplate;

    public void publishSensorData(SensorDataMessage message) {
        String key = message.getDeviceId();

        CompletableFuture<SendResult<String, SensorDataMessage>> future =
                kafkaTemplate.send(KafkaTopics.TOPIC_SENSOR_DATA, key, message);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("传感器数据发送成功, messageId: {}, partition: {}, offset: {}",
                        message.getMessageId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("传感器数据发送失败, messageId: {}, deviceId: {}",
                        message.getMessageId(), message.getDeviceId(), ex);
            }
        });
    }

    public void publishBatch(List<SensorDataMessage> messages) {
        for (SensorDataMessage message : messages) {
            publishSensorData(message);
        }
        log.info("批量发送传感器数据完成, count: {}", messages.size());
    }
}
