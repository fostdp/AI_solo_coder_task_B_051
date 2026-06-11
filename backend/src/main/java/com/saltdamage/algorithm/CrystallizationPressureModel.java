package com.saltdamage.algorithm;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 盐结晶压力预测模型
 *
 * 本模型基于热力学平衡理论，预测Na₂SO₄·10H₂O（芒硝）在多孔介质中的结晶压力。
 * 结晶压力是导致文物岩石风化、壁画脱落的主要力学因素。
 *
 * 核心公式（修正的热力学结晶压力方程）：
 * ΔP = (RT/Vm) * ln(a/a₀) + ΔHf * (1/T₀ - 1/T) / Vm
 *
 * 其中：
 * - ΔP: 结晶压力 (Pa)
 * - R: 理想气体常数 (8.314 J/(mol·K))
 * - T: 绝对温度 (K)
 * - Vm: 摩尔体积 (m³/mol)
 * - a: 实际活度
 * - a₀: 平衡活度
 * - ΔHf: 熔化焓 (J/mol)
 * - T₀: 参考温度 (K)
 *
 * 模型特点：
 * - 采用简化的Pitzer方程计算活度系数，考虑离子强度的影响
 * - 考虑温度和相对湿度对平衡活度的影响
 * - 计算饱和指数，判断结晶发生的可能性
 * - 提供风险等级评估功能
 */
@Slf4j
@Component
public class CrystallizationPressureModel {

    // ==================== 物理化学常数 ====================

    /**
     * 理想气体常数，单位: J/(mol·K)
     */
    private static final double R = 8.314;

    /**
     * 十水硫酸钠(Na₂SO₄·10H₂O)的摩尔体积，单位: m³/mol
     * 计算值: 322.2 g/mol / 1464 kg/m³ ≈ 0.000220 m³/mol
     */
    private static final double V_M = 2.20e-4;

    /**
     * 十水硫酸钠的熔化焓，单位: J/mol
     * 实验值约为 78.5 kJ/mol
     */
    private static final double DELTA_HF = 78500.0;

    /**
     * 参考温度（25℃），单位: K
     */
    private static final double T0 = 298.15;

    /**
     * 绝对零度，单位: ℃
     */
    private static final double ABSOLUTE_ZERO = -273.15;

    /**
     * Na₂SO₄的摩尔质量，单位: g/mol
     */
    private static final double MOLAR_MASS_NA2SO4 = 142.04;

    // ==================== 风险等级阈值（可配置） ====================

    @Value("${algorithm.crystallization.risk-levels.low:0.5}")
    private double lowRiskThreshold;

    @Value("${algorithm.crystallization.risk-levels.medium:1.0}")
    private double mediumRiskThreshold;

    @Value("${algorithm.crystallization.risk-levels.high:2.0}")
    private double highRiskThreshold;

    @Value("${algorithm.crystallization.risk-levels.critical:5.0}")
    private double criticalRiskThreshold;

    /**
     * 风险等级枚举
     */
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

    /**
     * 计算盐结晶压力
     *
     * 本方法基于热力学平衡理论，计算Na₂SO₄·10H₂O在多孔介质中的结晶压力。
     * 结晶压力来源于两个方面：
     * 1. 活度差项：实际活度与平衡活度的差异，反映过饱和度
     * 2. 温度修正项：温度对平衡的影响，考虑熔化焓的贡献
     *
     * 计算公式：
     * ΔP = (RT/Vm) * ln(a/a₀) + ΔHf * (1/T₀ - 1/T) / Vm
     *
     * @param concentration 盐溶液浓度，单位: mol/kg（摩尔浓度）
     * @param temperature 环境温度，单位: ℃
     * @param relativeHumidity 相对湿度，范围: 0~1（如0.75表示75%）
     * @return 结晶压力，单位: Pa
     * @throws IllegalArgumentException 参数无效时抛出
     */
    public double calculateCrystallizationPressure(
            double concentration,
            double temperature,
            double relativeHumidity) {

        // ==================== 参数校验 ====================
        validateInputParameters(concentration, temperature, relativeHumidity);

        // 将温度转换为绝对温度
        double T = temperature - ABSOLUTE_ZERO;

        log.debug("结晶压力计算 - 浓度: {} mol/kg, 温度: {} ℃ ({} K), 相对湿度: {}",
                concentration, temperature, T, relativeHumidity);

        // ==================== 步骤1: 计算活度系数（简化Pitzer方程） ====================
        // 活度系数 γ 反映溶液的非理想性
        double activityCoefficient = calculateActivityCoefficient(concentration);
        log.debug("活度系数 γ = {}", activityCoefficient);

        // ==================== 步骤2: 计算实际活度 ====================
        // a = γ * m，其中m为质量摩尔浓度
        double actualActivity = activityCoefficient * concentration;
        log.debug("实际活度 a = {}", actualActivity);

        // ==================== 步骤3: 计算平衡活度（考虑温度和相对湿度） ====================
        // 平衡活度 a₀ 是盐与晶体平衡时的活度，受温度和湿度影响
        double equilibriumActivity = calculateEquilibriumActivity(temperature, relativeHumidity);
        log.debug("平衡活度 a₀ = {}", equilibriumActivity);

        // ==================== 步骤4: 计算结晶压力 ====================
        // 防止对数运算出现非正数
        double activityRatio = actualActivity / equilibriumActivity;
        if (activityRatio <= 0) {
            log.warn("活度比 {} 非正，设置为极小值", activityRatio);
            activityRatio = 1e-10;
        }

        // 第一项：活度差引起的压力
        // ΔP₁ = (RT/Vm) * ln(a/a₀)
        double pressureFromActivity = (R * T / V_M) * Math.log(activityRatio);

        // 第二项：温度偏离参考温度引起的压力修正
        // ΔP₂ = ΔHf * (1/T₀ - 1/T) / Vm
        double pressureFromTemperature = DELTA_HF * (1.0 / T0 - 1.0 / T) / V_M;

        // 总结晶压力
        double totalPressure = pressureFromActivity + pressureFromTemperature;

        log.debug("结晶压力组成 - 活度项: {} Pa, 温度项: {} Pa, 总计: {} Pa",
                pressureFromActivity, pressureFromTemperature, totalPressure);

        return totalPressure;
    }

    /**
     * 计算活度系数（简化Pitzer方程）
     *
     * Pitzer方程是计算电解质溶液活度系数的半经验公式，考虑了离子强度的影响。
     * 本方法采用简化的Pitzer单电解质参数模型：
     *
     * ln(γ±) = f^φ + m * B^MX + m² * C^MX
     *
     * 其中：
     * - f^φ: Debye-Hückel极限项
     * - B^MX: 第二维里系数
     * - C^MX: 第三维里系数
     * - m: 质量摩尔浓度
     *
     * 对于Na₂SO₄溶液，使用25℃下的Pitzer参数。
     *
     * @param concentration 质量摩尔浓度，单位: mol/kg
     * @return 平均活度系数 γ±
     */
    public double calculateActivityCoefficient(double concentration) {
        if (concentration < 0) {
            throw new IllegalArgumentException("浓度不能为负数");
        }

        // 极低浓度下，活度系数趋近于1
        if (concentration < 1e-6) {
            return 1.0;
        }

        // ==================== 离子强度计算 ====================
        // I = 1/2 * Σ(m_i * z_i²)
        // 对于Na₂SO₄，解离为 2Na+ + SO4^2-
        // m(Na+) = 2m, z=+1
        // m(SO4^2-) = m, z=-2
        // I = 1/2 * [2m*1² + m*(-2)²] = 1/2 * (2m + 4m) = 3m
        double ionicStrength = 3.0 * concentration;

        // ==================== Debye-Hückel极限项 ====================
        // f^φ = -A^φ * [√I / (1 + b*√I) + (2/b) * ln(1 + b*√I)]
        // 其中:
        //   A^φ = 0.3915 kg^(1/2)·mol^(-1/2) (25℃时水的Debye-Hückel常数)
        //   b = 1.2 kg^(1/2)·mol^(-1/2) (通用常数)
        double A_phi = 0.3915;
        double b = 1.2;
        double sqrtI = Math.sqrt(ionicStrength);
        double denominator = 1.0 + b * sqrtI;
        double f_phi = -A_phi * (sqrtI / denominator + (2.0 / b) * Math.log(denominator));

        // ==================== 第二维里系数 B^MX ====================
        // B^MX = β^(0) + β^(1) * exp(-α*√I)
        // 对于2-2型电解质，α = 2.0 kg^(1/2)·mol^(-1/2)
        // Na₂SO₄的Pitzer参数(25℃):
        //   β^(0) = 0.0496 kg·mol^(-1)
        //   β^(1) = 0.4815 kg·mol^(-1)
        double alpha = 2.0;
        double beta0 = 0.0496;
        double beta1 = 0.4815;
        double B_MX = beta0 + beta1 * Math.exp(-alpha * sqrtI);

        // ==================== 第三维里系数 C^MX ====================
        // C^MX 与浓度无关，对于Na₂SO₄:
        //   C^φ = 0.00447 kg²·mol^(-2)
        //   C^MX = C^φ / (2 * √|ν_M * ν_X|)
        // 其中 ν_M=2 (Na+数目), ν_X=1 (SO4^2-数目)
        double C_phi = 0.00447;
        double nu = 3.0; // 总离子数 = 2 + 1
        double C_MX = C_phi / (2.0 * Math.sqrt(2.0 * 1.0));

        // ==================== 计算ln(γ±) ====================
        // 对于Mν+ Xν-型电解质：
        // ln(γ±) = (ν+/ν) * ln(γ_M) + (ν-/ν) * ln(γ_X)
        // 简化后:
        // ln(γ±) = f^γ + m * B^γ + m² * C^γ
        // 其中关系为:
        //   f^γ = -A^φ * [√I/(1+b√I) + 2/b * ln(1+b√I)]
        //   B^γ = 2β^(0) + 2β^(1) * [1 - (1+α√I - α²I/2)exp(-α√I)] / (α²I)
        //   C^γ = (3/2) * C^φ * √(ν+|ν-|) / ν

        // 计算B^γ
        double B_gamma;
        if (ionicStrength < 1e-6) {
            B_gamma = 2.0 * (beta0 + beta1);
        } else {
            double term1 = 1.0 + alpha * sqrtI - 0.5 * alpha * alpha * ionicStrength;
            double term2 = Math.exp(-alpha * sqrtI);
            B_gamma = 2.0 * beta0 + 2.0 * beta1 * (1.0 - term1 * term2) / (alpha * alpha * ionicStrength);
        }

        // 计算C^γ
        double C_gamma = 1.5 * C_phi * Math.sqrt(2.0 * 1.0) / nu;

        // 计算ln(γ±)
        double lnGamma = f_phi + concentration * B_gamma + concentration * concentration * C_gamma;

        // 转换为γ±
        double gamma = Math.exp(lnGamma);

        log.debug("活度系数计算 - 离子强度: {}, f^φ: {}, B^γ: {}, C^γ: {}, γ±: {}",
                ionicStrength, f_phi, B_gamma, C_gamma, gamma);

        return gamma;
    }

    /**
     * 计算平衡活度
     *
     * 平衡活度 a₀ 是盐溶液与晶体达到相平衡时的活度，受温度和相对湿度影响。
     *
     * 本方法综合考虑：
     * 1. 温度对溶解度的影响（Van't Hoff方程）
     * 2. 相对湿度对水活度的影响（Kelvin方程和吸附等温线）
     *
     * 对于Na₂SO₄·10H₂O系统：
     * 溶解度随温度升高而显著增加，在32.4℃以上转变为无水Na₂SO₄
     *
     * @param temperature 温度，单位: ℃
     * @param relativeHumidity 相对湿度，范围: 0~1
     * @return 平衡活度 a₀
     */
    public double calculateEquilibriumActivity(double temperature, double relativeHumidity) {
        // ==================== 温度对溶解度的影响 ====================
        // 使用经验多项式拟合Na₂SO₄的溶解度曲线（单位: mol/kg）
        // 适用温度范围: -5℃ ~ 32.4℃（十水硫酸钠稳定区）
        double m_eq;

        if (temperature < -5.0) {
            // 低于共晶点，使用外推
            log.warn("温度 {}℃ 低于十水硫酸钠稳定范围，使用外推计算", temperature);
            m_eq = 0.05 + 0.005 * temperature;
        } else if (temperature <= 32.4) {
            // 十水硫酸钠稳定区
            // 经验公式: m_eq = 0.049 + 0.286*T + 0.0048*T² - 0.000032*T³
            // 其中T为摄氏温度
            double T = temperature;
            m_eq = 0.049 + 0.286 * T + 0.0048 * T * T - 0.000032 * T * T * T;
        } else {
            // 高于32.4℃，十水硫酸钠转变为无水硫酸钠
            // 无水硫酸钠的溶解度随温度升高而降低
            log.warn("温度 {}℃ 高于十水硫酸钠转变点，使用无水硫酸钠溶解度", temperature);
            double T = temperature;
            m_eq = 6.20 - 0.08 * (T - 32.4);
        }

        // 确保溶解度为正值
        m_eq = Math.max(m_eq, 0.01);
        log.debug("温度 {}℃ 下的平衡溶解度: {} mol/kg", temperature, m_eq);

        // ==================== 相对湿度的影响 ====================
        // 相对湿度影响水的活度，进而影响盐的溶解度
        // 水的活度 a_w ≈ RH（当溶液较稀时）
        // 根据Gibbs-Duhem方程，盐的活度与水的活度相关
        // 简化处理：平衡活度随湿度降低而降低
        //
        // 修正因子: f(RH) = RH^k
        // 其中k为经验指数，对于Na₂SO₄约为0.75
        double humidityCorrection;
        if (relativeHumidity <= 0) {
            humidityCorrection = 0.01;
            log.warn("相对湿度为0，使用最小修正因子");
        } else {
            double k = 0.75;
            humidityCorrection = Math.pow(relativeHumidity, k);
        }
        log.debug("湿度修正因子: {}", humidityCorrection);

        // 计算平衡活度
        // a₀ = γ_eq * m_eq * humidityCorrection
        // 其中γ_eq是平衡浓度下的活度系数
        double gamma_eq = calculateActivityCoefficient(m_eq);
        double equilibriumActivity = gamma_eq * m_eq * humidityCorrection;

        // 确保平衡活度为正值
        equilibriumActivity = Math.max(equilibriumActivity, 1e-6);

        log.debug("平衡活度计算 - 平衡溶解度: {} mol/kg, γ_eq: {}, 湿度修正: {}, a₀: {}",
                m_eq, gamma_eq, humidityCorrection, equilibriumActivity);

        return equilibriumActivity;
    }

    /**
     * 计算饱和指数（Saturation Index, SI）
     *
     * 饱和指数用于判断溶液是否达到过饱和状态：
     * SI = log(IAP / K_sp)
     *
     * 其中：
     * - IAP: 离子活度积 = (a_Na+)² * (a_SO4^2-)
     * - K_sp: 溶度积常数
     *
     * SI > 0: 过饱和，可能发生结晶
     * SI = 0: 平衡状态
     * SI < 0: 不饱和，晶体将溶解
     *
     * @param concentration 溶液浓度，单位: mol/kg
     * @param temperature 温度，单位: ℃
     * @return 饱和指数 SI
     */
    public double calculateSaturationIndex(double concentration, double temperature) {
        if (concentration < 0) {
            throw new IllegalArgumentException("浓度不能为负数");
        }

        // 计算活度系数
        double gamma = calculateActivityCoefficient(concentration);

        // 计算各离子的活度
        // a_Na+ = γ * m_Na+ = γ * 2m
        // a_SO4^2- = γ * m_SO4^2- = γ * m
        double a_Na = gamma * 2.0 * concentration;
        double a_SO4 = gamma * concentration;

        // 计算离子活度积 IAP
        double IAP = a_Na * a_Na * a_SO4;

        // 计算溶度积 K_sp（与温度相关）
        double K_sp = calculateSolubilityProduct(temperature);

        // 计算饱和指数
        double SI = Math.log10(IAP / K_sp);

        log.debug("饱和指数计算 - IAP: {}, K_sp: {}, SI: {}", IAP, K_sp, SI);

        return SI;
    }

    /**
     * 计算溶度积常数 K_sp
     *
     * 使用Van't Hoff方程计算温度相关的溶度积：
     * ln(K_sp(T)) = ln(K_sp(T₀)) - ΔH_sol/R * (1/T - 1/T₀)
     *
     * 对于Na₂SO₄·10H₂O：
     * K_sp(T₀) ≈ 1.2e-5 (25℃)
     * ΔH_sol ≈ -78.5 kJ/mol（溶解焓，负号表示放热）
     *
     * @param temperature 温度，单位: ℃
     * @return 溶度积 K_sp
     */
    private double calculateSolubilityProduct(double temperature) {
        // 参考温度下的溶度积（25℃）
        double K_sp0 = 1.2e-5;

        // 溶解焓（注意符号：十水硫酸钠溶解是放热的）
        double deltaH_sol = -78500.0;

        // 转换为绝对温度
        double T = temperature - ABSOLUTE_ZERO;

        // Van't Hoff方程
        double lnK_sp = Math.log(K_sp0) - deltaH_sol / R * (1.0 / T - 1.0 / T0);
        double K_sp = Math.exp(lnK_sp);

        return K_sp;
    }

    /**
     * 评估风险等级
     *
     * 根据结晶压力和饱和指数综合评估盐害风险等级：
     * - LOW: 结晶压力 < 0.5 MPa 或 SI < 0
     * - MEDIUM: 0.5 MPa ≤ 结晶压力 < 1.0 MPa 且 SI ≥ 0
     * - HIGH: 1.0 MPa ≤ 结晶压力 < 2.0 MPa 且 SI ≥ 0.5
     * - CRITICAL: 结晶压力 ≥ 2.0 MPa 且 SI ≥ 1.0
     *
     * @param crystallizationPressure 结晶压力，单位: Pa
     * @param saturationIndex 饱和指数
     * @return 风险等级
     */
    public RiskLevel assessRiskLevel(double crystallizationPressure, double saturationIndex) {
        // 转换为MPa便于比较
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

    /**
     * 计算结晶速率（简化模型）
     *
     * 基于经典成核理论，结晶速率与过饱和度的关系：
     * J = k * exp(-B / (ln(S))²)
     *
     * 其中：
     * - S: 过饱和度 = a/a₀
     * - k, B: 经验常数
     *
     * @param concentration 溶液浓度，单位: mol/kg
     * @param temperature 温度，单位: ℃
     * @param relativeHumidity 相对湿度
     * @return 结晶速率（相对值）
     */
    public double calculateCrystallizationRate(double concentration, double temperature,
                                               double relativeHumidity) {
        validateInputParameters(concentration, temperature, relativeHumidity);

        // 计算活度系数
        double gamma = calculateActivityCoefficient(concentration);
        double actualActivity = gamma * concentration;

        // 计算平衡活度
        double equilibriumActivity = calculateEquilibriumActivity(temperature, relativeHumidity);

        // 过饱和度
        double S = actualActivity / equilibriumActivity;

        if (S <= 1.0) {
            // 未饱和，无结晶
            return 0.0;
        }

        // 经典成核理论（简化）
        double k = 1.0;  // 速率常数
        double B = 10.0; // 表面张力相关参数
        double rate = k * Math.exp(-B / (Math.log(S) * Math.log(S)));

        log.debug("结晶速率 - 过饱和度 S: {}, 速率: {}", S, rate);

        return rate;
    }

    /**
     * 验证输入参数有效性
     *
     * @param concentration 浓度
     * @param temperature 温度
     * @param relativeHumidity 相对湿度
     * @throws IllegalArgumentException 参数无效时抛出
     */
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

    /**
     * 将质量浓度（g/L或kg/m³）转换为摩尔浓度（mol/kg）
     *
     * @param massConcentration 质量浓度，单位: g/L
     * @param solutionDensity 溶液密度，单位: kg/m³（默认1000 kg/m³）
     * @return 摩尔浓度，单位: mol/kg
     */
    public double convertToMolarConcentration(double massConcentration, double solutionDensity) {
        if (massConcentration < 0) {
            throw new IllegalArgumentException("质量浓度不能为负数");
        }
        if (solutionDensity <= 0) {
            solutionDensity = 1000.0;
        }

        // 转换为 mol/kg
        // mol/kg = (g/L) / (M g/mol) / (kg/L * (1 - 盐的质量分数))
        // 简化处理：稀溶液近似
        return massConcentration / MOLAR_MASS_NA2SO4;
    }

    /**
     * 获取风险等级阈值
     *
     * @return 阈值数组 [低, 中, 高, 极高]，单位: MPa
     */
    public double[] getRiskThresholds() {
        return new double[]{lowRiskThreshold, mediumRiskThreshold, highRiskThreshold, criticalRiskThreshold};
    }
}
