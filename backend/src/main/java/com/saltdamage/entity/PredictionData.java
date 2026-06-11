package com.saltdamage.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "prediction_data", indexes = {
        @Index(name = "idx_tomb_id", columnList = "tomb_id"),
        @Index(name = "idx_predict_time", columnList = "predict_time")
})
public class PredictionData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tomb_id")
    private Long tombId;

    @Column(name = "chamber_id")
    private Long chamberId;

    @Column(name = "predict_time", nullable = false)
    private LocalDateTime predictTime;

    @Column(name = "predicted_salt_concentration", precision = 10, scale = 4)
    private BigDecimal predictedSaltConcentration;

    @Column(name = "predicted_temperature", precision = 10, scale = 4)
    private BigDecimal predictedTemperature;

    @Column(name = "predicted_humidity", precision = 10, scale = 4)
    private BigDecimal predictedHumidity;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}
