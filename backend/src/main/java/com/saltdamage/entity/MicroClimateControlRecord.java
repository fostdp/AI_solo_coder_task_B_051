package com.saltdamage.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "micro_climate_control_record", indexes = {
        @Index(name = "idx_chamber_id", columnList = "chamber_id"),
        @Index(name = "idx_control_timestamp", columnList = "control_timestamp"),
        @Index(name = "idx_chamber_control", columnList = "chamber_id, control_timestamp")
})
public class MicroClimateControlRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tomb_id")
    private Long tombId;

    @Column(name = "chamber_id")
    private Long chamberId;

    @Column(name = "control_mode", length = 20)
    private String controlMode;

    @Column(name = "current_rh", precision = 10, scale = 4)
    private BigDecimal currentRh;

    @Column(name = "target_rh", precision = 10, scale = 4)
    private BigDecimal targetRh;

    @Column(name = "dehumidifier_status")
    private Boolean dehumidifierStatus;

    @Column(name = "humidifier_status")
    private Boolean humidifierStatus;

    @Column(name = "energy_consumption", precision = 10, scale = 4)
    private BigDecimal energyConsumption;

    @Column(name = "reward_score", precision = 10, scale = 4)
    private BigDecimal rewardScore;

    @Column(name = "action_taken")
    private Integer actionTaken;

    @Column(name = "control_timestamp", nullable = false)
    private LocalDateTime controlTimestamp;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}
