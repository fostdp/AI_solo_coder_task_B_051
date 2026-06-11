package com.saltdamage.common.message;

public final class KafkaTopics {

    public static final String TOPIC_SENSOR_DATA = "salt-damage-sensor-data";
    public static final String TOPIC_ANALYSIS_REQUEST = "salt-damage-analysis-request";
    public static final String TOPIC_SALT_MIGRATION_RESULT = "salt-damage-salt-migration-result";
    public static final String TOPIC_CRYSTALLIZATION_RESULT = "salt-damage-crystallization-result";
    public static final String TOPIC_ALARM_EVENT = "salt-damage-alarm-event";

    private KafkaTopics() {
    }
}
