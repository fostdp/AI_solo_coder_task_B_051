package com.saltdamage.rainflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cycle_count_record", indexes = {
        @Index(name = "idx_tomb_id", columnList = "tomb_id"),
        @Index(name = "idx_chamber_id", columnList = "chamber_id"),
        @Index(name = "idx_analysis_time", columnList = "analysis_time"),
        @Index(name = "idx_period_type", columnList = "period_type")
})
public class CycleCountRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tomb_id")
    private Long tombId;

    @Column(name = "chamber_id")
    private Long chamberId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "period_type", length = 20)
    private String periodType;

    @Column(name = "period_start")
    private LocalDateTime periodStart;

    @Column(name = "period_end")
    private LocalDateTime periodEnd;

    @Column(name = "total_cycles")
    private Integer totalCycles;

    @Column(name = "full_cycles")
    private Integer fullCycles;

    @Column(name = "partial_cycles")
    private Integer partialCycles;

    @Column(name = "crossing_cycles")
    private Integer crossingCycles;

    @Column(name = "average_range", precision = 10, scale = 4)
    private BigDecimal averageRange;

    @Column(name = "max_range", precision = 10, scale = 4)
    private BigDecimal maxRange;

    @Column(name = "min_range", precision = 10, scale = 4)
    private BigDecimal minRange;

    @Column(name = "total_damage", precision = 15, scale = 8)
    private BigDecimal totalDamage;

    @Column(name = "damage_level", length = 20)
    private String damageLevel;

    @Column(name = "amplitude_histogram", columnDefinition = "TEXT")
    private String amplitudeHistogram;

    @Column(name = "analysis_time", nullable = false)
    private LocalDateTime analysisTime;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}
