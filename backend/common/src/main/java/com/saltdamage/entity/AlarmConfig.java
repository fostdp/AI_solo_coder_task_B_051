package com.saltdamage.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "alarm_config")
public class AlarmConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salt_threshold", precision = 10, scale = 4, nullable = false)
    private BigDecimal saltThreshold;

    @Column(name = "humidity_threshold", precision = 10, scale = 4, nullable = false)
    private BigDecimal humidityThreshold;

    @Column(name = "humidity_duration_hours", nullable = false)
    private Integer humidityDurationHours;

    @Column(name = "temperature_threshold", precision = 10, scale = 4)
    private BigDecimal temperatureThreshold;

    @Column(name = "co2_threshold", precision = 10, scale = 4)
    private BigDecimal co2Threshold;

    @Column(name = "enable_ding_talk")
    private Boolean enableDingTalk;

    @Column(name = "enable_web_socket")
    private Boolean enableWebSocket;

    @Column(name = "ding_talk_webhook", length = 500)
    private String dingTalkWebhook;

    @Column(name = "ding_talk_secret", length = 200)
    private String dingTalkSecret;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}
