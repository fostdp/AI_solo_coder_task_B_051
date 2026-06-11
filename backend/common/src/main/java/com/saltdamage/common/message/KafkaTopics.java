package com.saltdamage.common.message;

public final class KafkaTopics {

    public static final String TOPIC_SENSOR_DATA = "salt-damage-sensor-data";
    public static final String TOPIC_ANALYSIS_REQUEST = "salt-damage-analysis-request";
    public static final String TOPIC_SALT_MIGRATION_RESULT = "salt-damage-salt-migration-result";
    public static final String TOPIC_CRYSTALLIZATION_RESULT = "salt-damage-crystallization-result";
    public static final String TOPIC_ALARM_EVENT = "salt-damage-alarm-event";

    public static final String TOPIC_CYCLE_COUNT_REQUEST = "salt-damage-cycle-count-request";
    public static final String TOPIC_CYCLE_COUNT_RESULT = "salt-damage-cycle-count-result";
    public static final String TOPIC_MICROCLIMATE_REQUEST = "salt-damage-microclimate-request";
    public static final String TOPIC_MICROCLIMATE_RESULT = "salt-damage-microclimate-result";
    public static final String TOPIC_DELAMINATION_REQUEST = "salt-damage-delamination-request";
    public static final String TOPIC_DELAMINATION_RESULT = "salt-damage-delamination-result";
    public static final String TOPIC_BLOCKCHAIN_STORE = "salt-damage-blockchain-store";
    public static final String TOPIC_BLOCKCHAIN_RESULT = "salt-damage-blockchain-result";

    private KafkaTopics() {
    }
}
