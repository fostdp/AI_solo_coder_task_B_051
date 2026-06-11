package com.saltdamage.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "delamination_risk_record", indexes = {
        @Index(name = "idx_tomb_id", columnList = "tomb_id"),
        @Index(name = "idx_chamber_id", columnList = "chamber_id"),
        @Index(name = "idx_risk_level", columnList = "risk_level"),
        @Index(name = "idx_assessment_time", columnList = "assessment_time")
})
public class DelaminationRiskRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tomb_id")
    private Long tombId;

    @Column(name = "chamber_id")
    private Long chamberId;

    @Column(name = "mural_id")
    private Long muralId;

    @Column(name = "pigment_type", length = 20)
    private String pigmentType;

    @Column(name = "mural_age")
    private Integer muralAge;

    @Column(name = "crystallization_pressure", precision = 10, scale = 4)
    private BigDecimal crystallizationPressure;

    @Column(name = "adhesion_strength", precision = 10, scale = 4)
    private BigDecimal adhesionStrength;

    @Column(name = "pressure_adhesion_ratio", precision = 10, scale = 4)
    private BigDecimal pressureAdhesionRatio;

    @Column(name = "cycle_count_7d")
    private Integer cycleCount7d;

    @Column(name = "avg_daily_rh_fluctuation", precision = 10, scale = 4)
    private BigDecimal avgDailyRhFluctuation;

    @Column(name = "temperature_variation", precision = 10, scale = 4)
    private BigDecimal temperatureVariation;

    @Column(name = "delamination_probability", precision = 10, scale = 6)
    private BigDecimal delaminationProbability;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "feature_contributions", columnDefinition = "TEXT")
    private String featureContributions;

    @Column(name = "assessment_time", nullable = false)
    private LocalDateTime assessmentTime;

    @Column(columnDefinition = "TEXT")
    private String suggestion;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}
