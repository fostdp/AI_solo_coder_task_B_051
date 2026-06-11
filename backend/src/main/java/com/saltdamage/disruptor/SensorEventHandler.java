package com.saltdamage.disruptor;

import com.lmax.disruptor.EventHandler;
import com.saltdamage.service.SensorDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorEventHandler implements EventHandler<SensorEvent> {

    private final SensorDataService sensorDataService;

    @Value("${disruptor.max-retries:3}")
    private int maxRetries;

    @Value("${disruptor.retry-delay-ms:1000}")
    private long retryDelayMs;

    @Value("${disruptor.max-event-age-ms:300000}")
    private long maxEventAgeMs;

    @Value("${disruptor.dead-letter-capacity:10000}")
    private int deadLetterCapacity;

    private final ConcurrentLinkedQueue<SensorEvent> deadLetterQueue = new ConcurrentLinkedQueue<>();
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong retriedCount = new AtomicLong(0);
    private final AtomicLong deadLetterCount = new AtomicLong(0);

    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "disruptor-retry");
        t.setDaemon(true);
        return t;
    });

    private volatile List<SensorEvent> pendingRetryBuffer = new ArrayList<>();

    public void init() {
        retryExecutor.scheduleWithFixedDelay(this::processRetryBuffer, retryDelayMs, retryDelayMs, TimeUnit.MILLISECONDS);
        log.info("Disruptor事件处理器初始化完成 - 最大重试: {}, 重试延迟: {}ms, 事件最大存活: {}ms",
                maxRetries, retryDelayMs, maxEventAgeMs);
    }

    @Override
    public void onEvent(SensorEvent event, long sequence, boolean endOfBatch) throws Exception {
        try {
            processEvent(event);
            processedCount.incrementAndGet();
        } catch (Exception e) {
            log.error("事件处理异常 - eventId: {}, retryCount: {}, error: {}",
                    event.getEventId(), event.getRetryCount(), e.getMessage());

            handleFailedEvent(event, e);
        }
    }

    private void processEvent(SensorEvent event) throws Exception {
        if (event.isExpired(maxEventAgeMs)) {
            log.warn("事件已过期，丢弃 - eventId: {}, age: {}ms",
                    event.getEventId(), event.getAgeMs());
            sendToDeadLetter(event, "EVENT_EXPIRED");
            return;
        }

        switch (event.getEventType()) {
            case "SENSOR_DATA" -> {
                var request = event.toSensorDataRequest();
                sensorDataService.processSensorData(request);
            }
            default -> log.warn("未知事件类型: {}", event.getEventType());
        }
    }

    private void handleFailedEvent(SensorEvent event, Exception error) {
        failedCount.incrementAndGet();

        if (event.canRetry(maxRetries)) {
            event.incrementRetry();
            retriedCount.incrementAndGet();

            synchronized (pendingRetryBuffer) {
                pendingRetryBuffer.add(event);
            }

            log.info("事件进入重试缓冲 - eventId: {}, retryCount: {}/{}, error: {}",
                    event.getEventId(), event.getRetryCount(), maxRetries, error.getMessage());
        } else {
            sendToDeadLetter(event, "MAX_RETRIES_EXCEEDED:" + error.getMessage());
            log.error("事件超过最大重试次数，进入死信队列 - eventId: {}, retryCount: {}",
                    event.getEventId(), event.getRetryCount());
        }
    }

    private void processRetryBuffer() {
        List<SensorEvent> toRetry;
        synchronized (pendingRetryBuffer) {
            toRetry = new ArrayList<>(pendingRetryBuffer);
            pendingRetryBuffer.clear();
        }

        if (toRetry.isEmpty()) return;

        log.info("开始处理重试缓冲 - 待重试事件数: {}", toRetry.size());

        for (SensorEvent event : toRetry) {
            try {
                Thread.sleep(retryDelayMs / maxRetries);
                processEvent(event);
                processedCount.incrementAndGet();
                log.info("重试成功 - eventId: {}, retryCount: {}", event.getEventId(), event.getRetryCount());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                handleFailedEvent(event, e);
            }
        }
    }

    private void sendToDeadLetter(SensorEvent event, String reason) {
        deadLetterCount.incrementAndGet();

        if (deadLetterQueue.size() < deadLetterCapacity) {
            deadLetterQueue.offer(event);
        } else {
            deadLetterQueue.poll();
            deadLetterQueue.offer(event);
            log.warn("死信队列已满，淘汰最旧事件 - capacity: {}", deadLetterCapacity);
        }

        log.error("事件进入死信队列 - eventId: {}, reason: {}, retryCount: {}",
                event.getEventId(), reason, event.getRetryCount());
    }

    public List<SensorEvent> getDeadLetterEvents(int limit) {
        List<SensorEvent> events = new ArrayList<>();
        for (SensorEvent event : deadLetterQueue) {
            if (events.size() >= limit) break;
            events.add(event);
        }
        return events;
    }

    public boolean replayDeadLetterEvent(String eventId) {
        for (SensorEvent event : deadLetterQueue) {
            if (event.getEventId().equals(eventId)) {
                try {
                    processEvent(event);
                    deadLetterQueue.remove(event);
                    processedCount.incrementAndGet();
                    log.info("死信事件重放成功 - eventId: {}", eventId);
                    return true;
                } catch (Exception e) {
                    log.error("死信事件重放失败 - eventId: {}", eventId, e);
                    return false;
                }
            }
        }
        log.warn("死信事件未找到 - eventId: {}", eventId);
        return false;
    }

    public String getStats() {
        return String.format(
                "Disruptor统计 [已处理: %d, 失败: %d, 已重试: %d, 死信: %d, 待重试: %d]",
                processedCount.get(), failedCount.get(), retriedCount.get(),
                deadLetterCount.get(), pendingRetryBuffer.size());
    }
}
