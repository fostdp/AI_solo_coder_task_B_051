package com.saltdamage.transport.producer;

import com.saltdamage.common.message.AnalysisMessage;
import com.saltdamage.common.message.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisResultProducer {

    private final KafkaTemplate<String, AnalysisMessage> analysisKafkaTemplate;

    public void sendAnalysisResult(AnalysisMessage message) {
        String key = message.getDeviceId() + "-" + message.getTombId();
        CompletableFuture<SendResult<String, AnalysisMessage>> future =
                analysisKafkaTemplate.send(KafkaTopics.TOPIC_SALT_MIGRATION_RESULT, key, message);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("发送盐分运移分析结果失败: deviceId={}, tombId={}",
                        message.getDeviceId(), message.getTombId(), ex);
            } else {
                log.info("发送盐分运移分析结果成功: deviceId={}, tombId={}, topic={}, partition={}, offset={}",
                        message.getDeviceId(), message.getTombId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
