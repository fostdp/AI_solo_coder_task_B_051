package com.saltdamage.algorithm;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 壁画颜料层起甲风险评估模型
 *
 * 本模型基于逻辑回归算法，结合盐结晶压力与颜料层附着力等多维度特征，
 * 预测壁画颜料层发生起甲（delamination）病害的概率。
 *
 * 起甲是壁画最常见的病害之一，表现为颜料层与地仗层分离、翘起、脱落。
 * 主要诱因是盐结晶压力超过颜料层的附着力，导致层间剥离。
 *
 * 模型公式：
 * P(delamination) = sigmoid(w₀ + w₁x₁ + w₂x₂ + ... + w₆x₆)
 *
 * 其中：
 * - w₀: 偏置项
 * - w₁~w₆: 各特征权重
 * - x₁~x₆: 归一化后的输入特征
 *
 * 特征说明：
 * 1. crystallizationPressure - 盐结晶压力 (MPa)，归一化到[0,5]
 * 2. adhesionStrength - 颜料层附着力 (MPa)，归一化到[0,2]
 * 3. pressureAdhesionRatio - 压附比 = 结晶压力/附着力，核心特征
 * 4. cycleCount7d - 近7天结晶-潮解循环次数
 * 5. avgDailyRhFluctuation - 日均RH波动幅度(%)
 * 6. temperatureVariation - 温度变幅(℃)
 *
 * 风险等级划分：
 * - LOW: P < 10%
 * - MEDIUM: 10% ≤ P < 30%
 * - HIGH: 30% ≤ P < 60%
 * - CRITICAL: P ≥ 60%
 */
@Slf4j
@Component
public class DelaminationRiskModel {

    // ==================== 模型权重（默认值，基于专家经验） ====================

    /**
     * 偏置项 w₀，基线概率低
     */
    @Value("${algorithm.delamination.weights.w0:-3.0}")
    private double w0;

    /**
     * 结晶压力权重 w₁，正相关
     */
    @Value("${algorithm.delamination.weights.w1:1.5}")
    private double w1;

    /**
     * 附着力权重 w₂，负相关，越牢固越不起甲
     */
    @Value("${algorithm.delamination.weights.w2:-2.0}")
    private double w2;

    /**
     * 压附比权重 w₃，最重要特征
     */
    @Value("${algorithm.delamination.weights.w3:2.5}")
    private double w3;

    /**
     * 循环次数权重 w₄，正相关，疲劳效应
     */
    @Value("${algorithm.delamination.weights.w4:0.8}")
    private double w4;

    /**
     * RH波动权重 w₅，正相关
     */
    @Value("${algorithm.delamination.weights.w5:0.5}")
    private double w5;

    /**
     * 温度变幅权重 w₆，正相关
     */
    @Value("${algorithm.delamination.weights.w6:0.3}")
    private double w6;

    // ==================== 特征归一化参数 ====================

    /**
     * 结晶压力归一化最大值，单位: MPa
     */
    private static final double CRYSTALLIZATION_PRESSURE_MAX = 5.0;

    /**
     * 附着力归一化最大值，单位: MPa
     */
    private static final double ADHESION_STRENGTH_MAX = 2.0;

    /**
     * 压附比归一化最大值
     */
    private static final double PRESSURE_ADHESION_RATIO_MAX = 5.0;

    /**
     * 循环次数归一化最大值
     */
    private static final double CYCLE_COUNT_MAX = 30.0;

    /**
     * RH波动归一化最大值，单位: %
     */
    private static final double RH_FLUCTUATION_MAX = 50.0;

    /**
     * 温度变幅归一化最大值，单位: ℃
     */
    private static final double TEMPERATURE_VARIATION_MAX = 20.0;

    // ==================== 风险等级阈值 ====================

    /**
     * 低风险阈值（概率）
     */
    @Value("${algorithm.delamination.risk-levels.low:0.1}")
    private double lowRiskThreshold;

    /**
     * 中风险阈值（概率）
     */
    @Value("${algorithm.delamination.risk-levels.medium:0.3}")
    private double mediumRiskThreshold;

    /**
     * 高风险阈值（概率）
     */
    @Value("${algorithm.delamination.risk-levels.high:0.6}")
    private double highRiskThreshold;

    // ==================== 颜料附着力模拟参数 ====================

    /**
     * 矿物颜料基础附着力，单位: MPa
     */
    private static final double MINERAL_BASE_ADHESION = 1.8;

    /**
     * 植物颜料基础附着力，单位: MPa
     */
    private static final double PLANT_BASE_ADHESION = 1.2;

    /**
     * 合成颜料基础附着力，单位: MPa
     */
    private static final double SYNTHETIC_BASE_ADHESION = 2.0;

    /**
     * 矿物颜料半衰期（年）
     */
    private static final double MINERAL_HALF_LIFE = 200.0;

    /**
     * 植物颜料半衰期（年）
     */
    private static final double PLANT_HALF_LIFE = 100.0;

    /**
     * 合成颜料半衰期（年）
     */
    private static final double SYNTHETIC_HALF_LIFE = 150.0;

    /**
     * 风险等级枚举
     */
    @Getter
    public enum RiskLevel {
        LOW("低风险", "起甲概率低，无需特殊干预", "保持常规监测，维持稳定环境"),
        MEDIUM("中风险", "存在一定起甲风险，需关注", "加强环境监控，控制温湿度波动"),
        HIGH("高风险", "起甲风险较高，建议采取防护措施", "实施脱盐处理，优化环境控制"),
        CRITICAL("极高风险", "起甲风险极高，需紧急干预", "立即采取加固措施，启动应急预案");

        private final String displayName;
        private final String description;
        private final String recommendation;

        RiskLevel(String displayName, String description, String recommendation) {
            this.displayName = displayName;
            this.description = description;
            this.recommendation = recommendation;
        }
    }

    /**
     * 输入特征类
     *
     * 封装逻辑回归模型的6个输入特征，均为原始物理量（未归一化）。
     * 模型内部会自动进行归一化处理。
     */
    @Getter
    public static class FeatureInput {

        /**
         * 盐结晶压力，单位: MPa
         */
        private final double crystallizationPressure;

        /**
         * 颜料层附着力，单位: MPa
         */
        private final double adhesionStrength;

        /**
         * 压附比 = 结晶压力 / 附着力
         * 若未显式设置，将自动计算
         */
        private final double pressureAdhesionRatio;

        /**
         * 近7天结晶-潮解循环次数
         */
        private final double cycleCount7d;

        /**
         * 日均相对湿度波动幅度，单位: %
         */
        private final double avgDailyRhFluctuation;

        /**
         * 温度变幅，单位: ℃
         */
        private final double temperatureVariation;

        /**
         * 构造方法（自动计算压附比）
         *
         * @param crystallizationPressure 盐结晶压力 (MPa)
         * @param adhesionStrength 颜料层附着力 (MPa)
         * @param cycleCount7d 近7天循环次数
         * @param avgDailyRhFluctuation 日均RH波动 (%)
         * @param temperatureVariation 温度变幅 (℃)
         */
        public FeatureInput(double crystallizationPressure,
                            double adhesionStrength,
                            double cycleCount7d,
                            double avgDailyRhFluctuation,
                            double temperatureVariation) {
            this.crystallizationPressure = crystallizationPressure;
            this.adhesionStrength = adhesionStrength;
            this.pressureAdhesionRatio = adhesionStrength > 0
                    ? crystallizationPressure / adhesionStrength
                    : Double.POSITIVE_INFINITY;
            this.cycleCount7d = cycleCount7d;
            this.avgDailyRhFluctuation = avgDailyRhFluctuation;
            this.temperatureVariation = temperatureVariation;
        }

        /**
         * 构造方法（显式指定压附比）
         *
         * @param crystallizationPressure 盐结晶压力 (MPa)
         * @param adhesionStrength 颜料层附着力 (MPa)
         * @param pressureAdhesionRatio 压附比
         * @param cycleCount7d 近7天循环次数
         * @param avgDailyRhFluctuation 日均RH波动 (%)
         * @param temperatureVariation 温度变幅 (℃)
         */
        public FeatureInput(double crystallizationPressure,
                            double adhesionStrength,
                            double pressureAdhesionRatio,
                            double cycleCount7d,
                            double avgDailyRhFluctuation,
                            double temperatureVariation) {
            this.crystallizationPressure = crystallizationPressure;
            this.adhesionStrength = adhesionStrength;
            this.pressureAdhesionRatio = pressureAdhesionRatio;
            this.cycleCount7d = cycleCount7d;
            this.avgDailyRhFluctuation = avgDailyRhFluctuation;
            this.temperatureVariation = temperatureVariation;
        }
    }

    /**
     * 评估结果类
     *
     * 封装起甲风险评估的完整输出，包括概率、等级、特征贡献度和建议。
     */
    @Getter
    public static class DelaminationResult {

        /**
         * 起甲概率，范围: [0, 1]
         */
        private final double probability;

        /**
         * 风险等级
         */
        private final RiskLevel riskLevel;

        /**
         * 各特征贡献度（SHAP值简化版：w_i * (x_i - mean_i)）
         * key为特征名称，value为贡献度
         */
        private final Map<String, Double> featureContributions;

        /**
         * 关键建议
         */
        private final String recommendation;

        /**
         * 构造方法
         *
         * @param probability 起甲概率
         * @param riskLevel 风险等级
         * @param featureContributions 特征贡献度
         * @param recommendation 关键建议
         */
        public DelaminationResult(double probability,
                                  RiskLevel riskLevel,
                                  Map<String, Double> featureContributions,
                                  String recommendation) {
            this.probability = probability;
            this.riskLevel = riskLevel;
            this.featureContributions = featureContributions;
            this.recommendation = recommendation;
        }
    }

    /**
     * 默认构造方法
     *
     * 使用Spring注入的默认权重值（基于专家经验）。
     */
    public DelaminationRiskModel() {
    }

    /**
     * 自定义权重构造方法
     *
     * 用于手动指定模型权重，如训练后或实验场景。
     *
     * @param w0 偏置项
     * @param w1 结晶压力权重
     * @param w2 附着力权重
     * @param w3 压附比权重
     * @param w4 循环次数权重
     * @param w5 RH波动权重
     * @param w6 温度变幅权重
     */
    public DelaminationRiskModel(double w0, double w1, double w2, double w3,
                                 double w4, double w5, double w6) {
        this.w0 = w0;
        this.w1 = w1;
        this.w2 = w2;
        this.w3 = w3;
        this.w4 = w4;
        this.w5 = w5;
        this.w6 = w6;
    }

    /**
     * 预测起甲风险
     *
     * 根据输入特征，使用逻辑回归模型预测颜料层起甲概率，
     * 并评估风险等级、计算特征贡献度、给出防护建议。
     *
     * 计算流程：
     * 1. 输入特征归一化
     * 2. 计算线性组合 z = w₀ + Σ(w_i * x_i)
     * 3. sigmoid函数得到概率 P = 1/(1+e^(-z))
     * 4. 根据概率划分风险等级
     * 5. 计算各特征贡献度
     * 6. 生成防护建议
     *
     * @param input 输入特征
     * @return 评估结果（概率、等级、贡献度、建议）
     * @throws IllegalArgumentException 输入参数无效时抛出
     */
    public DelaminationResult predict(FeatureInput input) {
        validateInput(input);

        log.debug("起甲风险预测 - 原始特征: 结晶压力={} MPa, 附着力={} MPa, 压附比={}, " +
                        "循环次数={}, RH波动={}%, 温度变幅={}℃",
                input.getCrystallizationPressure(),
                input.getAdhesionStrength(),
                input.getPressureAdhesionRatio(),
                input.getCycleCount7d(),
                input.getAvgDailyRhFluctuation(),
                input.getTemperatureVariation());

        double[] normalizedFeatures = normalizeFeatures(input);
        log.debug("归一化特征: [{}, {}, {}, {}, {}, {}]",
                normalizedFeatures[0], normalizedFeatures[1], normalizedFeatures[2],
                normalizedFeatures[3], normalizedFeatures[4], normalizedFeatures[5]);

        double z = w0
                + w1 * normalizedFeatures[0]
                + w2 * normalizedFeatures[1]
                + w3 * normalizedFeatures[2]
                + w4 * normalizedFeatures[3]
                + w5 * normalizedFeatures[4]
                + w6 * normalizedFeatures[5];

        double probability = sigmoid(z);
        log.debug("线性组合 z = {}, 起甲概率 P = {}", z, probability);

        RiskLevel riskLevel = determineRiskLevel(probability);
        log.debug("风险等级: {}", riskLevel.getDisplayName());

        Map<String, Double> contributions = calculateFeatureContributions(input);

        String recommendation = generateRecommendation(riskLevel, contributions);

        return new DelaminationResult(probability, riskLevel, contributions, recommendation);
    }

    /**
     * 训练模型（梯度下降法）
     *
     * 使用批量梯度下降算法优化逻辑回归模型的权重参数。
     * 损失函数为交叉熵损失（负对数似然）。
     *
     * 梯度下降更新公式：
     * w_j := w_j - α * (1/m) * Σ(h(x^i) - y^i) * x_j^i
     *
     * 其中：
     * - α: 学习率
     * - m: 样本数量
     * - h(x): 预测概率 = sigmoid(w·x)
     * - y: 真实标签 (0或1)
     *
     * @param inputs 训练样本特征列表
     * @param labels 训练样本标签列表（true表示发生起甲）
     * @param learningRate 学习率
     * @param epochs 迭代次数
     * @throws IllegalArgumentException 参数无效时抛出
     */
    public void train(List<FeatureInput> inputs, List<Boolean> labels,
                      double learningRate, int epochs) {
        if (inputs == null || labels == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("训练数据不能为空");
        }
        if (inputs.size() != labels.size()) {
            throw new IllegalArgumentException(
                    "特征数量与标签数量不一致: " + inputs.size() + " vs " + labels.size());
        }
        if (learningRate <= 0) {
            throw new IllegalArgumentException("学习率必须为正数: " + learningRate);
        }
        if (epochs <= 0) {
            throw new IllegalArgumentException("迭代次数必须为正数: " + epochs);
        }

        int m = inputs.size();
        log.info("开始训练 - 样本数: {}, 学习率: {}, 迭代次数: {}", m, learningRate, epochs);

        double[][] X = new double[m][7];
        double[] y = new double[m];

        for (int i = 0; i < m; i++) {
            FeatureInput input = inputs.get(i);
            double[] norm = normalizeFeatures(input);
            X[i][0] = 1.0;
            X[i][1] = norm[0];
            X[i][2] = norm[1];
            X[i][3] = norm[2];
            X[i][4] = norm[3];
            X[i][5] = norm[4];
            X[i][6] = norm[5];
            y[i] = labels.get(i) ? 1.0 : 0.0;
        }

        double[] weights = {w0, w1, w2, w3, w4, w5, w6};

        for (int epoch = 1; epoch <= epochs; epoch++) {
            double[] gradients = new double[7];
            double totalLoss = 0.0;

            for (int i = 0; i < m; i++) {
                double z = 0.0;
                for (int j = 0; j < 7; j++) {
                    z += weights[j] * X[i][j];
                }
                double h = sigmoid(z);
                double error = h - y[i];

                for (int j = 0; j < 7; j++) {
                    gradients[j] += error * X[i][j];
                }

                double loss = -(y[i] * Math.log(h + 1e-15)
                        + (1 - y[i]) * Math.log(1 - h + 1e-15));
                totalLoss += loss;
            }

            for (int j = 0; j < 7; j++) {
                weights[j] -= learningRate * gradients[j] / m;
            }

            double avgLoss = totalLoss / m;
            if (epoch % (epochs / 10) == 0 || epoch == 1 || epoch == epochs) {
                log.debug("迭代 {}/{} - 平均损失: {}", epoch, epochs, avgLoss);
            }
        }

        w0 = weights[0];
        w1 = weights[1];
        w2 = weights[2];
        w3 = weights[3];
        w4 = weights[4];
        w5 = weights[5];
        w6 = weights[6];

        log.info("训练完成 - 最终权重: w0={}, w1={}, w2={}, w3={}, w4={}, w5={}, w6={}",
                w0, w1, w2, w3, w4, w5, w6);
    }

    /**
     * Sigmoid函数
     *
     * 将任意实数映射到(0, 1)区间，用于将线性组合转换为概率。
     *
     * 公式：sigmoid(z) = 1 / (1 + e^(-z))
     *
     * 数值稳定性优化：
     * - 当z很大时，sigmoid(z) ≈ 1
     * - 当z很小时，sigmoid(z) ≈ 0
     * - 避免指数溢出
     *
     * @param z 输入值
     * @return sigmoid函数值，范围(0, 1)
     */
    public double sigmoid(double z) {
        if (z >= 30) {
            return 1.0;
        }
        if (z <= -30) {
            return 0.0;
        }
        return 1.0 / (1.0 + Math.exp(-z));
    }

    /**
     * 计算特征贡献度
     *
     * 采用SHAP值的简化版本，衡量各特征对预测结果的贡献程度：
     * contribution_i = w_i * (x_i - mean_i)
     *
     * 其中：
     * - w_i: 第i个特征的权重
     * - x_i: 第i个特征的归一化值
     * - mean_i: 第i个特征的均值（此处取归一化范围中点）
     *
     * 正贡献表示增加起甲风险，负贡献表示降低起甲风险。
     *
     * @param input 输入特征
     * @return 各特征贡献度映射
     */
    public Map<String, Double> calculateFeatureContributions(FeatureInput input) {
        double[] normalized = normalizeFeatures(input);

        Map<String, Double> contributions = new HashMap<>();

        contributions.put("crystallizationPressure", w1 * (normalized[0] - 0.5));
        contributions.put("adhesionStrength", w2 * (normalized[1] - 0.5));
        contributions.put("pressureAdhesionRatio", w3 * (normalized[2] - 0.5));
        contributions.put("cycleCount7d", w4 * (normalized[3] - 0.5));
        contributions.put("avgDailyRhFluctuation", w5 * (normalized[4] - 0.5));
        contributions.put("temperatureVariation", w6 * (normalized[5] - 0.5));

        log.debug("特征贡献度 - 结晶压力: {}, 附着力: {}, 压附比: {}, " +
                        "循环次数: {}, RH波动: {}, 温度变幅: {}",
                contributions.get("crystallizationPressure"),
                contributions.get("adhesionStrength"),
                contributions.get("pressureAdhesionRatio"),
                contributions.get("cycleCount7d"),
                contributions.get("avgDailyRhFluctuation"),
                contributions.get("temperatureVariation"));

        return contributions;
    }

    /**
     * 生成模拟颜料层附着力数据
     *
     * 根据颜料类型和年代，模拟计算颜料层的附着力。
     * 年代越久，附着力越低，按指数衰减模型：
     * adhesion = baseAdhesion * exp(-age / halfLife)
     *
     * 颜料类型：
     * - mineral（矿物颜料）：基础附着力高，衰减慢
     * - plant（植物颜料）：基础附着力低，衰减快
     * - synthetic（合成颜料）：基础附着力最高，衰减中等
     *
     * @param pigmentType 颜料类型：mineral / plant / synthetic
     * @param age 年代（年）
     * @return 颜料层附着力 (MPa)
     * @throws IllegalArgumentException 颜料类型无效时抛出
     */
    public double generateAdhesionData(String pigmentType, int age) {
        if (pigmentType == null || pigmentType.trim().isEmpty()) {
            throw new IllegalArgumentException("颜料类型不能为空");
        }
        if (age < 0) {
            throw new IllegalArgumentException("年代不能为负数: " + age);
        }

        double baseAdhesion;
        double halfLife;

        switch (pigmentType.toLowerCase().trim()) {
            case "mineral":
                baseAdhesion = MINERAL_BASE_ADHESION;
                halfLife = MINERAL_HALF_LIFE;
                break;
            case "plant":
                baseAdhesion = PLANT_BASE_ADHESION;
                halfLife = PLANT_HALF_LIFE;
                break;
            case "synthetic":
                baseAdhesion = SYNTHETIC_BASE_ADHESION;
                halfLife = SYNTHETIC_HALF_LIFE;
                break;
            default:
                throw new IllegalArgumentException("未知颜料类型: " + pigmentType
                        + "，支持的类型: mineral, plant, synthetic");
        }

        double adhesion = baseAdhesion * Math.exp(-age / halfLife);

        log.debug("颜料附着力模拟 - 类型: {}, 年代: {}年, 基础附着力: {} MPa, " +
                        "半衰期: {}年, 计算值: {} MPa",
                pigmentType, age, baseAdhesion, halfLife, adhesion);

        return adhesion;
    }

    /**
     * 特征归一化
     *
     * 将原始物理量归一化到[0, 1]区间，使各特征量级一致，
     * 便于逻辑回归模型训练和权重解释。
     *
     * 使用最大最小归一化：x_norm = x / max
     * 超出范围的值将被截断。
     *
     * @param input 原始输入特征
     * @return 归一化后的特征数组 [x1, x2, x3, x4, x5, x6]
     */
    private double[] normalizeFeatures(FeatureInput input) {
        double[] normalized = new double[6];

        normalized[0] = clip(input.getCrystallizationPressure() / CRYSTALLIZATION_PRESSURE_MAX, 0, 1);
        normalized[1] = clip(input.getAdhesionStrength() / ADHESION_STRENGTH_MAX, 0, 1);
        normalized[2] = clip(input.getPressureAdhesionRatio() / PRESSURE_ADHESION_RATIO_MAX, 0, 1);
        normalized[3] = clip(input.getCycleCount7d() / CYCLE_COUNT_MAX, 0, 1);
        normalized[4] = clip(input.getAvgDailyRhFluctuation() / RH_FLUCTUATION_MAX, 0, 1);
        normalized[5] = clip(input.getTemperatureVariation() / TEMPERATURE_VARIATION_MAX, 0, 1);

        return normalized;
    }

    /**
     * 数值截断
     *
     * 将值限制在[min, max]范围内。
     *
     * @param value 输入值
     * @param min 最小值
     * @param max 最大值
     * @return 截断后的值
     */
    private double clip(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /**
     * 确定风险等级
     *
     * 根据起甲概率划分风险等级：
     * - LOW: P < 10%
     * - MEDIUM: 10% ≤ P < 30%
     * - HIGH: 30% ≤ P < 60%
     * - CRITICAL: P ≥ 60%
     *
     * @param probability 起甲概率
     * @return 风险等级
     */
    private RiskLevel determineRiskLevel(double probability) {
        if (probability < lowRiskThreshold) {
            return RiskLevel.LOW;
        } else if (probability < mediumRiskThreshold) {
            return RiskLevel.MEDIUM;
        } else if (probability < highRiskThreshold) {
            return RiskLevel.HIGH;
        } else {
            return RiskLevel.CRITICAL;
        }
    }

    /**
     * 生成防护建议
     *
     * 综合风险等级和特征贡献度，给出针对性的防护建议。
     * 优先针对贡献度最大的风险因素给出建议。
     *
     * @param riskLevel 风险等级
     * @param contributions 特征贡献度
     * @return 防护建议文本
     */
    private String generateRecommendation(RiskLevel riskLevel, Map<String, Double> contributions) {
        StringBuilder sb = new StringBuilder();
        sb.append(riskLevel.getRecommendation());

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(contributions.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        sb.append("。主要风险因素：");
        int count = 0;
        for (Map.Entry<String, Double> entry : sorted) {
            if (entry.getValue() > 0 && count < 3) {
                if (count > 0) sb.append("、");
                sb.append(getFeatureDisplayName(entry.getKey()));
                count++;
            }
        }
        if (count == 0) {
            sb.append("无明显风险因素");
        }

        sb.append("。");
        return sb.toString();
    }

    /**
     * 获取特征的显示名称
     *
     * @param key 特征键名
     * @return 中文显示名称
     */
    private String getFeatureDisplayName(String key) {
        switch (key) {
            case "crystallizationPressure":
                return "盐结晶压力";
            case "adhesionStrength":
                return "颜料层附着力";
            case "pressureAdhesionRatio":
                return "压附比";
            case "cycleCount7d":
                return "结晶循环次数";
            case "avgDailyRhFluctuation":
                return "RH波动";
            case "temperatureVariation":
                return "温度变幅";
            default:
                return key;
        }
    }

    /**
     * 验证输入参数有效性
     *
     * @param input 输入特征
     * @throws IllegalArgumentException 参数无效时抛出
     */
    private void validateInput(FeatureInput input) {
        if (input == null) {
            throw new IllegalArgumentException("输入特征不能为空");
        }
        if (input.getCrystallizationPressure() < 0) {
            throw new IllegalArgumentException(
                    "结晶压力不能为负数: " + input.getCrystallizationPressure());
        }
        if (input.getAdhesionStrength() < 0) {
            throw new IllegalArgumentException(
                    "附着力不能为负数: " + input.getAdhesionStrength());
        }
        if (input.getCycleCount7d() < 0) {
            throw new IllegalArgumentException(
                    "循环次数不能为负数: " + input.getCycleCount7d());
        }
        if (input.getAvgDailyRhFluctuation() < 0) {
            throw new IllegalArgumentException(
                    "RH波动幅度不能为负数: " + input.getAvgDailyRhFluctuation());
        }
        if (input.getTemperatureVariation() < 0) {
            throw new IllegalArgumentException(
                    "温度变幅不能为负数: " + input.getTemperatureVariation());
        }
    }

    /**
     * 使用SMOTE算法生成合成训练样本
     *
     * 修复缺陷：起甲评估缺乏训练数据
     * 根因：壁画起甲事件属于不可逆文物损伤，真实样本极度稀缺，
     *       导致逻辑回归训练集严重不平衡（少数类"起甲"样本远少于"安全"样本），
     *       直接用梯度下降训练会导致模型偏向多数类（全预测为安全），对高风险场景欠敏感。
     * 修复：实现SMOTE（Synthetic Minority Over-sampling Technique）算法，
     *       在少数类样本之间线性插值生成合成样本，使训练集类别平衡。
     *       算法：对每个少数类样本，找k个最近邻，随机选一个邻居，
     *             沿连线的随机比例处生成新样本：x_new = x + delta * (x_neighbor - x)
     *
     * @param inputs 原始训练特征列表
     * @param labels 原始标签列表（true=起甲，false=安全）
     * @param k SMOTE近邻数，默认5
     * @param oversamplingRatio 过采样比率（合成少数类样本数/多数类样本数），1.0=完全平衡
     * @return 包含原始+合成样本的增广数据 [增广特征列表, 增广标签列表]
     * @throws IllegalArgumentException 参数无效时抛出
     */
    public SmoteResult generateSmoteSamples(List<FeatureInput> inputs, List<Boolean> labels,
                                            int k, double oversamplingRatio) {
        if (inputs == null || labels == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("训练数据不能为空");
        }
        if (inputs.size() != labels.size()) {
            throw new IllegalArgumentException(
                    "特征数量与标签数量不一致: " + inputs.size() + " vs " + labels.size());
        }
        if (k < 1) {
            throw new IllegalArgumentException("近邻数k必须>=1: " + k);
        }
        if (oversamplingRatio <= 0) {
            throw new IllegalArgumentException("过采样比率必须为正数: " + oversamplingRatio);
        }

        Random rng = new Random(42);

        List<FeatureInput> minorityInputs = new ArrayList<>();
        List<Boolean> minorityLabels = new ArrayList<>();
        List<FeatureInput> majorityInputs = new ArrayList<>();
        List<Boolean> majorityLabels = new ArrayList<>();

        for (int i = 0; i < inputs.size(); i++) {
            if (labels.get(i)) {
                minorityInputs.add(inputs.get(i));
                minorityLabels.add(labels.get(i));
            } else {
                majorityInputs.add(inputs.get(i));
                majorityLabels.add(labels.get(i));
            }
        }

        log.info("SMOTE样本分布 - 多数类(安全): {}, 少数类(起甲): {}",
                majorityInputs.size(), minorityInputs.size());

        if (minorityInputs.isEmpty()) {
            log.warn("少数类样本为空，无法执行SMOTE，返回原始数据");
            return new SmoteResult(new ArrayList<>(inputs), new ArrayList<>(labels));
        }

        int numSynthetic = (int) (majorityInputs.size() * oversamplingRatio) - minorityInputs.size();
        if (numSynthetic <= 0) {
            log.info("数据已平衡或少数类已足够，无需SMOTE");
            return new SmoteResult(new ArrayList<>(inputs), new ArrayList<>(labels));
        }

        List<FeatureInput> syntheticInputs = new ArrayList<>();
        List<Boolean> syntheticLabels = new ArrayList<>();

        int minoritySize = minorityInputs.size();
        int actualK = Math.min(k, minoritySize - 1);
        if (actualK < 1) {
            actualK = 1;
        }

        for (int n = 0; n < numSynthetic; n++) {
            int idx = rng.nextInt(minoritySize);
            FeatureInput sample = minorityInputs.get(idx);

            List<Integer> neighbors = findKNearestNeighbors(sample, minorityInputs, actualK, idx);

            int neighborIdx = neighbors.get(rng.nextInt(neighbors.size()));
            FeatureInput neighbor = minorityInputs.get(neighborIdx);

            double delta = rng.nextDouble();

            FeatureInput synthetic = interpolateFeatures(sample, neighbor, delta);
            syntheticInputs.add(synthetic);
            syntheticLabels.add(true);
        }

        List<FeatureInput> augmentedInputs = new ArrayList<>(inputs);
        List<Boolean> augmentedLabels = new ArrayList<>(labels);
        augmentedInputs.addAll(syntheticInputs);
        augmentedLabels.addAll(syntheticLabels);

        log.info("SMOTE生成完成 - 原始样本: {}, 合成样本: {}, 总样本: {}",
                inputs.size(), syntheticInputs.size(), augmentedInputs.size());

        return new SmoteResult(augmentedInputs, augmentedLabels);
    }

    /**
     * SMOTE简化版：使用默认参数(k=5, ratio=1.0)
     *
     * @param inputs 原始训练特征列表
     * @param labels 原始标签列表
     * @return 增广后的数据
     */
    public SmoteResult generateSmoteSamples(List<FeatureInput> inputs, List<Boolean> labels) {
        return generateSmoteSamples(inputs, labels, 5, 1.0);
    }

    /**
     * 在特征空间中寻找k个最近邻
     *
     * 使用欧氏距离（归一化特征空间）计算近邻。
     *
     * @param target 目标样本
     * @param candidates 候选样本列表
     * @param k 近邻数
     * @param excludeIdx 排除的索引（通常是自身）
     * @return 最近邻的索引列表
     */
    private List<Integer> findKNearestNeighbors(FeatureInput target, List<FeatureInput> candidates,
                                                 int k, int excludeIdx) {
        double[] targetNorm = normalizeFeatures(target);

        List<double[]> allNorm = new ArrayList<>();
        for (FeatureInput candidate : candidates) {
            allNorm.add(normalizeFeatures(candidate));
        }

        List<NeighborDistance> distances = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (i == excludeIdx) continue;
            double dist = euclideanDistance(targetNorm, allNorm.get(i));
            distances.add(new NeighborDistance(i, dist));
        }

        distances.sort((a, b) -> Double.compare(a.distance, b.distance));

        List<Integer> neighbors = new ArrayList<>();
        for (int i = 0; i < Math.min(k, distances.size()); i++) {
            neighbors.add(distances.get(i).index);
        }
        return neighbors;
    }

    /**
     * 在两个样本之间线性插值生成合成样本
     *
     * @param a 样本A
     * @param b 样本B
     * @param delta 插值比例 [0, 1]，0=完全等于A，1=完全等于B
     * @return 合成样本
     */
    private FeatureInput interpolateFeatures(FeatureInput a, FeatureInput b, double delta) {
        double pressure = a.getCrystallizationPressure()
                + delta * (b.getCrystallizationPressure() - a.getCrystallizationPressure());
        double adhesion = a.getAdhesionStrength()
                + delta * (b.getAdhesionStrength() - a.getAdhesionStrength());
        double cycles = a.getCycleCount7d()
                + delta * (b.getCycleCount7d() - a.getCycleCount7d());
        double rhFluc = a.getAvgDailyRhFluctuation()
                + delta * (b.getAvgDailyRhFluctuation() - a.getAvgDailyRhFluctuation());
        double tempVar = a.getTemperatureVariation()
                + delta * (b.getTemperatureVariation() - a.getTemperatureVariation());

        return new FeatureInput(pressure, adhesion, cycles, rhFluc, tempVar);
    }

    /**
     * 计算欧氏距离
     *
     * @param a 向量A
     * @param b 向量B
     * @return 欧氏距离
     */
    private double euclideanDistance(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /**
     * SMOTE结果封装类
     */
    @Getter
    public static class SmoteResult {
        private final List<FeatureInput> augmentedInputs;
        private final List<Boolean> augmentedLabels;

        public SmoteResult(List<FeatureInput> augmentedInputs, List<Boolean> augmentedLabels) {
            this.augmentedInputs = augmentedInputs;
            this.augmentedLabels = augmentedLabels;
        }
    }

    /**
     * 近邻距离辅助类
     */
    private static class NeighborDistance {
        final int index;
        final double distance;

        NeighborDistance(int index, double distance) {
            this.index = index;
            this.distance = distance;
        }
    }

    /**
     * 使用SMOTE增广后训练模型
     *
     * 便捷方法：先SMOTE增广，再梯度下降训练。
     *
     * @param inputs 原始训练特征
     * @param labels 原始标签
     * @param learningRate 学习率
     * @param epochs 迭代次数
     * @param smoteK SMOTE近邻数
     * @param smoteRatio SMOTE过采样比率
     */
    public void trainWithSmote(List<FeatureInput> inputs, List<Boolean> labels,
                               double learningRate, int epochs,
                               int smoteK, double smoteRatio) {
        SmoteResult smoteResult = generateSmoteSamples(inputs, labels, smoteK, smoteRatio);
        train(smoteResult.getAugmentedInputs(), smoteResult.getAugmentedLabels(), learningRate, epochs);
    }

    /**
     * 获取当前模型权重
     *
     * @return 权重数组 [w0, w1, w2, w3, w4, w5, w6]
     */
    public double[] getWeights() {
        return new double[]{w0, w1, w2, w3, w4, w5, w6};
    }

    /**
     * 获取风险等级阈值
     *
     * @return 阈值数组 [低, 中, 高]，对应概率值
     */
    public double[] getRiskThresholds() {
        return new double[]{lowRiskThreshold, mediumRiskThreshold, highRiskThreshold};
    }
}
