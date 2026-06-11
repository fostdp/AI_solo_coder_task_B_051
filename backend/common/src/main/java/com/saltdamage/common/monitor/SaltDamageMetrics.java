package com.saltdamage.common.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class SaltDamageMetrics {

    private final MeterRegistry meterRegistry;

    private final Counter sensorDataReceivedCounter;
    private final Counter sensorDataProcessedCounter;
    private final Counter sensorDataFailedCounter;
    private final Counter kafkaMessageSentCounter;
    private final Counter kafkaMessageReceivedCounter;
    private final Counter alarmTriggeredCounter;
    private final Counter saltAlarmCounter;
    private final Counter humidityAlarmCounter;

    private final Timer dataProcessingTimer;
    private final Timer femCalculationTimer;
    private final Timer crystallizationTimer;

    private final AtomicLong activeSensorCount = new AtomicLong(0);
    private final AtomicLong pendingMessageCount = new AtomicLong(0);
    private final AtomicLong totalSaltConcentration = new AtomicLong(0);

    @Autowired
    public SaltDamageMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        sensorDataReceivedCounter = Counter.builder("saltdamage.sensor.data.received.total")
                .description("Total sensor data points received")
                .tag("unit", "count")
                .register(meterRegistry);

        sensorDataProcessedCounter = Counter.builder("saltdamage.sensor.data.processed.total")
                .description("Total sensor data points processed successfully")
                .tag("unit", "count")
                .register(meterRegistry);

        sensorDataFailedCounter = Counter.builder("saltdamage.sensor.data.failed.total")
                .description("Total sensor data points failed to process")
                .tag("unit", "count")
                .register(meterRegistry);

        kafkaMessageSentCounter = Counter.builder("saltdamage.kafka.messages.sent.total")
                .description("Total Kafka messages sent")
                .tag("unit", "count")
                .register(meterRegistry);

        kafkaMessageReceivedCounter = Counter.builder("saltdamage.kafka.messages.received.total")
                .description("Total Kafka messages received")
                .tag("unit", "count")
                .register(meterRegistry);

        alarmTriggeredCounter = Counter.builder("saltdamage.alarm.triggered.total")
                .description("Total alarms triggered")
                .tag("unit", "count")
                .register(meterRegistry);

        saltAlarmCounter = Counter.builder("saltdamage.alarm.salt.total")
                .description("Total salt concentration alarms")
                .tag("type", "salt_exceeded")
                .register(meterRegistry);

        humidityAlarmCounter = Counter.builder("saltdamage.alarm.humidity.total")
                .description("Total humidity alarms")
                .tag("type", "humidity_exceeded")
                .register(meterRegistry);

        dataProcessingTimer = Timer.builder("saltdamage.data.processing.duration")
                .description("Time to process sensor data")
                .tag("unit", "ms")
                .register(meterRegistry);

        femCalculationTimer = Timer.builder("saltdamage.algorithm.fem.duration")
                .description("Time to run FEM calculation")
                .tag("unit", "ms")
                .register(meterRegistry);

        crystallizationTimer = Timer.builder("saltdamage.algorithm.crystallization.duration")
                .description("Time to run crystallization calculation")
                .tag("unit", "ms")
                .register(meterRegistry);

        Gauge.builder("saltdamage.sensors.active", activeSensorCount, AtomicLong::get)
                .description("Number of active sensors")
                .tag("unit", "count")
                .register(meterRegistry);

        Gauge.builder("saltdamage.kafka.pending", pendingMessageCount, AtomicLong::get)
                .description("Number of pending messages")
                .tag("unit", "count")
                .register(meterRegistry);

        Gauge.builder("saltdamage.salt.concentration.avg", totalSaltConcentration, AtomicLong::get)
                .description("Average salt concentration (scaled by 1000)")
                .tag("unit", "mg/cm2")
                .register(meterRegistry);
    }

    public void incrementSensorDataReceived() {
        sensorDataReceivedCounter.increment();
    }

    public void incrementSensorDataProcessed() {
        sensorDataProcessedCounter.increment();
    }

    public void incrementSensorDataFailed() {
        sensorDataFailedCounter.increment();
    }

    public void incrementKafkaSent() {
        kafkaMessageSentCounter.increment();
    }

    public void incrementKafkaReceived() {
        kafkaMessageReceivedCounter.increment();
    }

    public void incrementAlarm() {
        alarmTriggeredCounter.increment();
    }

    public void incrementSaltAlarm() {
        saltAlarmCounter.increment();
    }

    public void incrementHumidityAlarm() {
        humidityAlarmCounter.increment();
    }

    public Timer.Sample startDataProcessingTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopDataProcessingTimer(Timer.Sample sample) {
        sample.stop(dataProcessingTimer);
    }

    public Timer.Sample startFEMCalculationTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopFEMCalculationTimer(Timer.Sample sample) {
        sample.stop(femCalculationTimer);
    }

    public Timer.Sample startCrystallizationTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopCrystallizationTimer(Timer.Sample sample) {
        sample.stop(crystallizationTimer);
    }

    public void setActiveSensorCount(long count) {
        activeSensorCount.set(count);
    }

    public void setPendingMessageCount(long count) {
        pendingMessageCount.set(count);
    }

    public void recordAvgSaltConcentration(double value) {
        totalSaltConcentration.set((long) (value * 1000));
    }
}
