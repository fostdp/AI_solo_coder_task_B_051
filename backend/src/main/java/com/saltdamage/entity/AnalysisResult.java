package com.saltdamage.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "analysis_result", indexes = {
        @Index(name = "idx_tomb_id", columnList = "tomb_id"),
        @Index(name = "idx_analysis_time", columnList = "analysis_time")
})
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tomb_id")
    private Long tombId;

    @Column(name = "chamber_id")
    private Long chamberId;

    @Column(name = "analysis_type", length = 50)
    private String analysisType;

    @Column(name = "salt_damage_level", length = 20)
    private String saltDamageLevel;

    @Column(name = "risk_score", precision = 10, scale = 4)
    private BigDecimal riskScore;

    @Column(columnDefinition = "TEXT")
    private String conclusion;

    @Column(columnDefinition = "TEXT")
    private String suggestion;

    @Column(name = "analysis_time", nullable = false)
    private LocalDateTime analysisTime;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}
