package com.saltdamage.rainflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Configuration
public class RainflowThreadPoolConfig {

    @Value("${rainflow.thread-pool.core-size:4}")
    private int coreSize;

    @Value("${rainflow.thread-pool.max-size:8}")
    private int maxSize;

    @Value("${rainflow.thread-pool.queue-capacity:100}")
    private int queueCapacity;

    @Bean("rainflowTaskExecutor")
    public ThreadPoolTaskExecutor rainflowTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("rainflow-");
        executor.initialize();
        return executor;
    }

    @Bean("rainflowExecutor")
    public Executor rainflowExecutor() {
        return rainflowTaskExecutor();
    }
}
