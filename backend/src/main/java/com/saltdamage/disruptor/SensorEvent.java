package com.saltdamage.disruptor;

import com.alibaba.fastjson2.JSON;
import com.saltdamage.dto.SensorDataRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SensorEvent {

    private String eventId;
    private long timestamp;
    private String eventType;
    private String payload;
    private int retryCount;
    private long firstAttemptTime;
    private String source;

    public static SensorEvent fromSensorData(SensorDataRequest request) {
        SensorEvent event = new SensorEvent();
        event.setEventId(java.util.UUID.randomUUID().toString());
        event.setTimestamp(System.currentTimeMillis());
        event.setEventType("SENSOR_DATA");
        event.setPayload(JSON.toJSONString(request));
        event.setRetryCount(0);
        event.setFirstAttemptTime(System.currentTimeMillis());
        event.setSource("DTU");
        return event;
    }

    public SensorDataRequest toSensorDataRequest() {
        return JSON.parseObject(payload, SensorDataRequest.class);
    }

    public boolean canRetry(int maxRetries) {
        return retryCount < maxRetries;
    }

    public SensorEvent incrementRetry() {
        this.retryCount++;
        return this;
    }

    public long getAgeMs() {
        return System.currentTimeMillis() - firstAttemptTime;
    }

    public boolean isExpired(long maxAgeMs) {
        return getAgeMs() > maxAgeMs;
    }
}
