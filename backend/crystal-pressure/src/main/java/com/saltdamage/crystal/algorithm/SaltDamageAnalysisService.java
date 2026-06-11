package com.saltdamage.crystal.algorithm;

import com.saltdamage.crystal.algorithm.util.Vector3D;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SaltDamageAnalysisService {

    private final CrystallizationPressureModel crystallizationPressureModel;

    public SaltDamageAnalysisService(CrystallizationPressureModel crystallizationPressureModel) {
        this.crystallizationPressureModel = crystallizationPressureModel;
    }

    @Value("${algorithm.crystallization.prediction-hours:72}")
    private int predictionHours;

    private static final double TIME_STEP = 1.0;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointAnalysisResult {
        private int x;
        private int y;
        private double concentration;
        private Vector3D migrationVelocity;
        private double velocityMagnitude;
        private double crystallizationPressure;
        private double saturationIndex;
        private double crystallizationRate;
        private CrystallizationPressureModel.RiskLevel riskLevel;
        private LocalDateTime analysisTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendDataPoint {
        private int hour;
        private double concentration;
        private double crystallizationPressure;
        private double saturationIndex;
        private CrystallizationPressureModel.RiskLevel riskLevel;
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPredictionResult {
        private int x;
        private int y;
        private int predictionHours;
        private List<TrendDataPoint> trendData;
        private CrystallizationPressureModel.RiskLevel finalRiskLevel;
        private double maxPressure;
        private int maxPressureHour;
        private double concentrationChangeRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchAnalysisResult {
        private LocalDateTime analysisTime;
        private int rows;
        private int cols;
        private PointAnalysisResult[][] results;
        private Map<String, Object> statistics;
        private CrystallizationPressureModel.RiskLevel overallRiskLevel;
    }

    public CrystallizationPressureModel.RiskLevel assessComprehensiveRisk(
            PointAnalysisResult pointResult,
            TrendPredictionResult trendResult) {

        if (pointResult == null) {
            throw new IllegalArgumentException("单点分析结果不能为空");
        }

        CrystallizationPressureModel.RiskLevel baseLevel = pointResult.getRiskLevel();

        if (trendResult == null) {
            return baseLevel;
        }

        double pressureIncreaseRate = (trendResult.getMaxPressure() - pointResult.getCrystallizationPressure())
                / pointResult.getCrystallizationPressure();

        boolean isRiskIncreasing = trendResult.getFinalRiskLevel().compareTo(baseLevel) > 0
                || pressureIncreaseRate > 0.3;

        boolean isRiskCritical = trendResult.getMaxPressure() >= 5e6
                || trendResult.getFinalRiskLevel() == CrystallizationPressureModel.RiskLevel.CRITICAL;

        if (isRiskCritical) {
            return CrystallizationPressureModel.RiskLevel.CRITICAL;
        } else if (isRiskIncreasing && baseLevel != CrystallizationPressureModel.RiskLevel.CRITICAL) {
            switch (baseLevel) {
                case LOW: return CrystallizationPressureModel.RiskLevel.MEDIUM;
                case MEDIUM: return CrystallizationPressureModel.RiskLevel.HIGH;
                case HIGH: return CrystallizationPressureModel.RiskLevel.CRITICAL;
                default: return baseLevel;
            }
        }

        return baseLevel;
    }

    private PointAnalysisResult createErrorResult(int x, int y, String errorMessage) {
        return PointAnalysisResult.builder()
                .x(x)
                .y(y)
                .concentration(Double.NaN)
                .migrationVelocity(Vector3D.zero())
                .velocityMagnitude(0)
                .crystallizationPressure(Double.NaN)
                .saturationIndex(Double.NaN)
                .crystallizationRate(0)
                .riskLevel(CrystallizationPressureModel.RiskLevel.LOW)
                .analysisTime(LocalDateTime.now())
                .build();
    }

    public int getPredictionHours() {
        return predictionHours;
    }
}
