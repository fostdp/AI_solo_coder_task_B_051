package com.saltdamage.crystal.service;

import com.saltdamage.common.message.AnalysisMessage;
import com.saltdamage.crystal.algorithm.CrystallizationPressureModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrystallizationService {

    private final CrystallizationPressureModel crystallizationPressureModel;

    @Value("${algorithm.crystallization.prediction-hours:72}")
    private int predictionHours;

    @Value("${algorithm.crystallization.default-temperature:25.0}")
    private double defaultTemperature;

    @Value("${algorithm.crystallization.default-humidity:0.6}")
    private double defaultHumidity;

    @Value("${algorithm.crystallization.default-concentration:1.0}")
    private double defaultConcentration;

    public AnalysisMessage analyzeCrystallization(AnalysisMessage message) {
        log.info("开始结晶压力分析 - messageId: {}", message.getMessageId());

        double velocityMagnitude = computeVelocityMagnitude(message);

        double concentration = resolveConcentration(message);

        double temperature = defaultTemperature;
        double humidity = defaultHumidity;

        double crystallizationPressure = crystallizationPressureModel
                .calculateCrystallizationPressure(concentration, temperature, humidity);

        double saturationIndex = crystallizationPressureModel
                .calculateSaturationIndex(concentration, temperature);

        CrystallizationPressureModel.RiskLevel riskLevel = assessRiskWithVelocity(
                crystallizationPressure, saturationIndex, velocityMagnitude);

        double crystallizationRate = crystallizationPressureModel
                .calculateCrystallizationRate(concentration, temperature, humidity);

        message.setCrystallizationPressure(crystallizationPressure);
        message.setRiskLevel(riskLevel.name());
        message.setPredictionHours(predictionHours);

        double predictedPressure = predictFuturePressure(
                concentration, temperature, humidity, predictionHours);
        message.setPredictedCrystallizationPressure(predictedPressure);

        log.info("结晶压力分析完成 - pressure: {} Pa, SI: {}, riskLevel: {}, velocity: {} m/s",
                String.format("%.2f", crystallizationPressure),
                String.format("%.4f", saturationIndex),
                riskLevel.getDisplayName(),
                String.format("%.6f", velocityMagnitude));

        return message;
    }

    private double computeVelocityMagnitude(AnalysisMessage message) {
        double vx = message.getMigrationVelocityX() != null ? message.getMigrationVelocityX() : 0.0;
        double vy = message.getMigrationVelocityY() != null ? message.getMigrationVelocityY() : 0.0;
        double vz = message.getMigrationVelocityZ() != null ? message.getMigrationVelocityZ() : 0.0;
        return Math.sqrt(vx * vx + vy * vy + vz * vz);
    }

    private double resolveConcentration(AnalysisMessage message) {
        if (message.getPredictedTotalSalt() != null && message.getPredictedTotalSalt() > 0) {
            return message.getPredictedTotalSalt() / 142.04;
        }
        return defaultConcentration;
    }

    private CrystallizationPressureModel.RiskLevel assessRiskWithVelocity(
            double pressure, double saturationIndex, double velocityMagnitude) {

        CrystallizationPressureModel.RiskLevel baseLevel =
                crystallizationPressureModel.assessRiskLevel(pressure, saturationIndex);

        if (velocityMagnitude > 1e-4 && baseLevel == CrystallizationPressureModel.RiskLevel.MEDIUM) {
            log.debug("运移速度 {} m/s 较高，风险等级从中风险升级至高风险", velocityMagnitude);
            return CrystallizationPressureModel.RiskLevel.HIGH;
        }

        if (velocityMagnitude > 1e-3 && baseLevel != CrystallizationPressureModel.RiskLevel.CRITICAL) {
            log.debug("运移速度 {} m/s 极高，风险等级升级至极高风险", velocityMagnitude);
            return CrystallizationPressureModel.RiskLevel.CRITICAL;
        }

        return baseLevel;
    }

    private double predictFuturePressure(double concentration, double temperature,
                                          double humidity, int hours) {
        double currentConcentration = concentration;
        double maxPressure = 0.0;

        for (int h = 0; h <= hours; h += 6) {
            double pressure = crystallizationPressureModel
                    .calculateCrystallizationPressure(currentConcentration, temperature, humidity);

            if (pressure > maxPressure) {
                maxPressure = pressure;
            }

            double rate = crystallizationPressureModel
                    .calculateCrystallizationRate(currentConcentration, temperature, humidity);
            double consumption = rate * currentConcentration * 0.01 * 6.0;
            currentConcentration = Math.max(0, currentConcentration - consumption);
        }

        return maxPressure;
    }
}
