package com.saltdamage.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sensor_data", indexes = {
        @Index(name = "idx_device_no", columnList = "device_no"),
        @Index(name = "idx_collect_time", columnList = "collect_time"),
        @Index(name = "idx_device_collect", columnList = "device_no, collect_time")
})
public class SensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_no", nullable = false, length = 50)
    private String deviceNo;

    @Column(name = "sensor_type", nullable = false, length = 50)
    private String sensorType;

    @Column(name = "tomb_id")
    private Long tombId;

    @Column(name = "chamber_id")
    private Long chamberId;

    @Column(name = "salt_concentration", precision = 10, scale = 4)
    private BigDecimal saltConcentration;

    @Column(precision = 10, scale = 4)
    private BigDecimal temperature;

    @Column(precision = 10, scale = 4)
    private BigDecimal humidity;

    @Column(name = "ph_value", precision = 10, scale = 4)
    private BigDecimal phValue;

    @Column(name = "co2_concentration", precision = 10, scale = 4)
    private BigDecimal co2Concentration;

    @Column(precision = 10, scale = 4)
    private BigDecimal illuminance;

    @Column(precision = 10, scale = 4)
    private BigDecimal pressure;

    @Column(name = "total_salt_amount", precision = 15, scale = 6)
    private BigDecimal totalSaltAmount;

    @Column(name = "collect_time", nullable = false)
    private LocalDateTime collectTime;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}
