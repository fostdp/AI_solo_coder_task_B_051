package com.saltdamage.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Slf4j
@Configuration
public class DisruptorConfig {

    @Value("${disruptor.ring-buffer-size:1024}")
    private int ringBufferSize;

    private Disruptor<SensorEvent> disruptor;

    @Bean
    public Disruptor<SensorEvent> sensorDisruptor(SensorEventHandler eventHandler) {
        SensorEventFactory factory = new SensorEventFactory();

        disruptor = new Disruptor<>(
                factory,
                ringBufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI,
                new com.lmax.disruptor.BlockingWaitStrategy()
        );

        disruptor.handleEventsWith(eventHandler);

        disruptor.setDefaultExceptionHandler(new com.lmax.disruptor.ExceptionHandler<SensorEvent>() {
            @Override
            public void handleEventException(Throwable ex, long sequence, SensorEvent event) {
                log.error("Disruptor事件异常 - sequence: {}, eventId: {}", sequence, event.getEventId(), ex);
            }

            @Override
            public void handleOnStartException(Throwable ex) {
                log.error("Disruptor启动异常", ex);
            }

            @Override
            public void handleOnShutdownException(Throwable ex) {
                log.error("Disruptor关闭异常", ex);
            }
        });

        disruptor.start();
        log.info("Disruptor启动完成 - RingBuffer大小: {}", ringBufferSize);

        return disruptor;
    }

    @PreDestroy
    public void shutdown() {
        if (disruptor != null) {
            log.info("正在关闭Disruptor...");
            disruptor.shutdown();
            log.info("Disruptor已关闭");
        }
    }
}
