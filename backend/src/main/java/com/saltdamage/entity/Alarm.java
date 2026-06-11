package com.saltdamage.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "alarm", indexes = {
        @Index(name = "idx_device_no", columnList = "device_no"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_alarm_time", columnList = "alarm_time")
})
public class Alarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_no", nullable = false, length = 50)
    private String deviceNo;

    @Column(name = "alarm_type", nullable = false, length = 50)
    private String alarmType;

    @Column(name = "alarm_level", nullable = false, length = 20)
    private String alarmLevel;

    @Column(name = "alarm_content", length = 500)
    private String alarmContent;

    @Column(name = "threshold_value", precision = 10, scale = 4)
    private BigDecimal thresholdValue;

    @Column(name = "current_value", precision = 10, scale = 4)
    private BigDecimal currentValue;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "process_result", length = 500)
    private String processResult;

    @Column(length = 50)
    private String processor;

    @Column(name = "alarm_time", nullable = false)
    private LocalDateTime alarmTime;

    @Column(name = "process_time")
    private LocalDateTime processTime;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}
