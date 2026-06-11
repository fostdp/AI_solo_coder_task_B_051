package com.saltdamage.crystal.algorithm;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CrystallizationPressureModel {

    private static final double R = 8.314;
    private static final double V_M = 2.20e-4;
    private static final double DELTA_HF = 78500.0;
    private static final double T0 = 298.15;
    private static final double ABSOLUTE_ZERO = -273.15;
    private static final double MOLAR_MASS_NA2SO4 = 142.04;

    @Value("${algorithm.crystallization.risk-levels.low:0.5}")
    private double lowRiskThreshold;

    @Value("${algorithm.crystallization.risk-levels.medium:1.0}")
    private double mediumRiskThreshold;

    @Value("${algorithm.crystallization.risk-levels.high:2.0}")
    private double highRiskThreshold;

    @Value("${algorithm.crystallization.risk-levels.critical:5.0}")
    private double criticalRiskThreshold;

    @Getter
    public enum RiskLevel {
        LOW("低风险", "结晶压力较低，无明显破坏"),
        MEDIUM("中风险", "存在结晶压力，长期作用可能造成破坏"),
        HIGH("高风险", "结晶压力较高，可能导致表层脱落"),
        CRITICAL("极高风险", "结晶压力极高，会造成严重破坏");

        private final String displayName;
        private final String description;

        RiskLevel(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    public double calculateCrystallizationPressure(
            double concentration,
            double temperature,
            double relativeHumidity) {

        validateInputParameters(concentration, temperature, relativeHumidity);

        double T = temperature - ABSOLUTE_ZERO;

        log.debug("结晶压力计算 - 浓度: {} mol/kg, 温度: {} ℃ ({} K), 相对湿度: {}",
                concentration, temperature, T, relativeHumidity);

        double activityCoefficient = calculateActivityCoefficient(concentration);
        log.debug("活度系数 γ = {}", activityCoefficient);

        double actualActivity = activityCoefficient * concentration;
        log.debug("实际活度 a = {}", actualActivity);

        double equilibriumActivity = calculateEquilibriumActivity(temperature, relativeHumidity);
        log.debug("平衡活度 a₀ = {}", equilibriumActivity);

        double activityRatio = actualActivity / equilibriumActivity;
        if (activityRatio <= 0) {
            log.warn("活度比 {} 非正，设置为极小值", activityRatio);
            activityRatio = 1e-10;
        }

        double pressureFromActivity = (R * T / V_M) * Math.log(activityRatio);
        double pressureFromTemperature = DELTA_HF * (1.0 / T0 - 1.0 / T) / V_M;
        double totalPressure = pressureFromActivity + pressureFromTemperature;

        log.debug("结晶压力组成 - 活度项: {} Pa, 温度项: {} Pa, 总计: {} Pa",
                pressureFromActivity, pressureFromTemperature, totalPressure);

        return totalPressure;
    }

    public double calculateActivityCoefficient(double concentration) {
        if (concentration < 0) {
            throw new IllegalArgumentException("浓度不能为负数");
        }

        if (concentration < 1e-6) {
            return 1.0;
        }

        double ionicStrength = 3.0 * concentration;

        double A_phi = 0.3915;
        double b = 1.2;
        double sqrtI = Math.sqrt(ionicStrength);
        double denominator = 1.0 + b * sqrtI;
        double f_phi = -A_phi * (sqrtI / denominator + (2.0 / b) * Math.log(denominator));

        double alpha = 2.0;
        double beta0 = 0.0496;
        double beta1 = 0.4815;
        double B_MX = beta0 + beta1 * Math.exp(-alpha * sqrtI);

        double C_phi = 0.00447;
        double nu = 3.0;
        double C_MX = C_phi / (2.0 * Math.sqrt(2.0 * 1.0));

        double B_gamma;
        if (ionicStrength < 1e-6) {
            B_gamma = 2.0 * (beta0 + beta1);
        } else {
            double term1 = 1.0 + alpha * sqrtI - 0.5 * alpha * alpha * ionicStrength;
            double term2 = Math.exp(-alpha * sqrtI);
            B_gamma = 2.0 * beta0 + 2.0 * beta1 * (1.0 - term1 * term2) / (alpha * alpha * ionicStrength);
        }

        double C_gamma = 1.5 * C_phi * Math.sqrt(2.0 * 1.0) / nu;
        double lnGamma = f_phi + concentration * B_gamma + concentration * concentration * C_gamma;
        double gamma = Math.exp(lnGamma);

        log.debug("活度系数计算 - 离子强度: {}, f^φ: {}, B^γ: {}, C^γ: {}, γ±: {}",
                ionicStrength, f_phi, B_gamma, C_gamma, gamma);

        return gamma;
    }

    public double calculateEquilibriumActivity(double temperature, double relativeHumidity) {
        double m_eq;

        if (temperature < -5.0) {
            log.warn("温度 {}℃ 低于十水硫酸钠稳定范围，使用外推计算", temperature);
            m_eq = 0.05 + 0.005 * temperature;
        } else if (temperature <= 32.4) {
            double T = temperature;
            m_eq = 0.049 + 0.286 * T + 0.0048 * T * T - 0.000032 * T * T * T;
        } else {
            log.warn("温度 {}℃ 高于十水硫酸钠转变点，使用无水硫酸钠溶解度", temperature);
            double T = temperature;
            m_eq = 6.20 - 0.08 * (T - 32.4);
        }

        m_eq = Math.max(m_eq, 0.01);
        log.debug("温度 {}℃ 下的平衡溶解度: {} mol/kg", temperature, m_eq);

        double humidityCorrection;
        if (relativeHumidity <= 0) {
            humidityCorrection = 0.01;
            log.warn("相对湿度为0，使用最小修正因子");
        } else {
            double k = 0.75;
            humidityCorrection = Math.pow(relativeHumidity, k);
        }
        log.debug("湿度修正因子: {}", humidityCorrection);

        double gamma_eq = calculateActivityCoefficient(m_eq);
        double equilibriumActivity = gamma_eq * m_eq * humidityCorrection;
        equilibriumActivity = Math.max(equilibriumActivity, 1e-6);

        log.debug("平衡活度计算 - 平衡溶解度: {} mol/kg, γ_eq: {}, 湿度修正: {}, a₀: {}",
                m_eq, gamma_eq, humidityCorrection, equilibriumActivity);

        return equilibriumActivity;
    }

    public double calculateSaturationIndex(double concentration, double temperature) {
        if (concentration < 0) {
            throw new IllegalArgumentException("浓度不能为负数");
        }

        double gamma = calculateActivityCoefficient(concentration);
        double a_Na = gamma * 2.0 * concentration;
        double a_SO4 = gamma * concentration;
        double IAP = a_Na * a_Na * a_SO4;
        double K_sp = calculateSolubilityProduct(temperature);
        double SI = Math.log10(IAP / K_sp);

        log.debug("饱和指数计算 - IAP: {}, K_sp: {}, SI: {}", IAP, K_sp, SI);

        return SI;
    }

    private double calculateSolubilityProduct(double temperature) {
        double K_sp0 = 1.2e-5;
        double deltaH_sol = -78500.0;
        double T = temperature - ABSOLUTE_ZERO;
        double lnK_sp = Math.log(K_sp0) - deltaH_sol / R * (1.0 / T - 1.0 / T0);
        return Math.exp(lnK_sp);
    }

    public RiskLevel assessRiskLevel(double crystallizationPressure, double saturationIndex) {
        double pressureMPa = crystallizationPressure / 1e6;

        RiskLevel level;

        if (pressureMPa < lowRiskThreshold || saturationIndex < 0) {
            level = RiskLevel.LOW;
        } else if (pressureMPa < mediumRiskThreshold) {
            level = RiskLevel.MEDIUM;
        } else if (pressureMPa < highRiskThreshold && saturationIndex >= 0.5) {
            level = RiskLevel.HIGH;
        } else if (pressureMPa >= criticalRiskThreshold || saturationIndex >= 1.0) {
            level = RiskLevel.CRITICAL;
        } else {
            level = RiskLevel.HIGH;
        }

        log.debug("风险评估 - 压力: {} MPa, SI: {}, 等级: {}",
                pressureMPa, saturationIndex, level.getDisplayName());

        return level;
    }

    public double calculateCrystallizationRate(double concentration, double temperature,
                                               double relativeHumidity) {
        validateInputParameters(concentration, temperature, relativeHumidity);

        double gamma = calculateActivityCoefficient(concentration);
        double actualActivity = gamma * concentration;
        double equilibriumActivity = calculateEquilibriumActivity(temperature, relativeHumidity);
        double S = actualActivity / equilibriumActivity;

        if (S <= 1.0) {
            return 0.0;
        }

        double k = 1.0;
        double B = 10.0;
        double rate = k * Math.exp(-B / (Math.log(S) * Math.log(S)));

        log.debug("结晶速率 - 过饱和度 S: {}, 速率: {}", S, rate);

        return rate;
    }

    private void validateInputParameters(double concentration, double temperature,
                                         double relativeHumidity) {
        if (concentration < 0) {
            throw new IllegalArgumentException("浓度不能为负数，当前值: " + concentration);
        }
        if (temperature <= ABSOLUTE_ZERO) {
            throw new IllegalArgumentException(
                    String.format("温度 %.2f℃ 低于绝对零度", temperature));
        }
        if (relativeHumidity < 0 || relativeHumidity > 1) {
            throw new IllegalArgumentException(
                    String.format("相对湿度 %.2f 超出范围 [0, 1]", relativeHumidity));
        }
    }

    public double convertToMolarConcentration(double massConcentration, double solutionDensity) {
        if (massConcentration < 0) {
            throw new IllegalArgumentException("质量浓度不能为负数");
        }
        if (solutionDensity <= 0) {
            solutionDensity = 1000.0;
        }
        return massConcentration / MOLAR_MASS_NA2SO4;
    }

    public double[] getRiskThresholds() {
        return new double[]{lowRiskThreshold, mediumRiskThreshold, highRiskThreshold, criticalRiskThreshold};
    }
}
