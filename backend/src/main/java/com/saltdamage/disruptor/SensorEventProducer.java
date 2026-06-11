package com.saltdamage.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.saltdamage.dto.SensorDataRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorEventProducer {

    private final Disruptor<SensorEvent> disruptor;

    public void publish(SensorDataRequest request) {
        RingBuffer<SensorEvent> ringBuffer = disruptor.getRingBuffer();
        long sequence = ringBuffer.next();

        try {
            SensorEvent event = ringBuffer.get(sequence);
            event.setEventId(java.util.UUID.randomUUID().toString());
            event.setTimestamp(System.currentTimeMillis());
            event.setEventType("SENSOR_DATA");
            event.setPayload(com.alibaba.fastjson2.JSON.toJSONString(request));
            event.setRetryCount(0);
            event.setFirstAttemptTime(System.currentTimeMillis());
            event.setSource("DTU");
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    public void publishBatch(List<SensorDataRequest> requests) {
        for (SensorDataRequest request : requests) {
            publish(request);
        }
        log.debug("批量发布事件: {} 条", requests.size());
    }
}
