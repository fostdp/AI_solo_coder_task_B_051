package com.saltdamage.flaking.algorithm;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Component
public class DelaminationRiskModel {

    @Value("${algorithm.delamination.weights.w0:-3.0}")
    private double w0;

    @Value("${algorithm.delamination.weights.w1:1.5}")
    private double w1;

    @Value("${algorithm.delamination.weights.w2:-2.0}")
    private double w2;

    @Value("${algorithm.delamination.weights.w3:2.5}")
    private double w3;

    @Value("${algorithm.delamination.weights.w4:0.8}")
    private double w4;

    @Value("${algorithm.delamination.weights.w5:0.5}")
    private double w5;

    @Value("${algorithm.delamination.weights.w6:0.3}")
    private double w6;

    private static final double CRYSTALLIZATION_PRESSURE_MAX = 5.0;
    private static final double ADHESION_STRENGTH_MAX = 2.0;
    private static final double PRESSURE_ADHESION_RATIO_MAX = 5.0;
    private static final double CYCLE_COUNT_MAX = 30.0;
    private static final double RH_FLUCTUATION_MAX = 50.0;
    private static final double TEMPERATURE_VARIATION_MAX = 20.0;

    @Value("${algorithm.delamination.risk-levels.low:0.1}")
    private double lowRiskThreshold;

    @Value("${algorithm.delamination.risk-levels.medium:0.3}")
    private double mediumRiskThreshold;

    @Value("${algorithm.delamination.risk-levels.high:0.6}")
    private double highRiskThreshold;

    private static final double MINERAL_BASE_ADHESION = 1.8;
    private static final double PLANT_BASE_ADHESION = 1.2;
    private static final double SYNTHETIC_BASE_ADHESION = 2.0;
    private static final double MINERAL_HALF_LIFE = 200.0;
    private static final double PLANT_HALF_LIFE = 100.0;
    private static final double SYNTHETIC_HALF_LIFE = 150.0;

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

    @Getter
    public static class FeatureInput {

        private final double crystallizationPressure;
        private final double adhesionStrength;
        private final double pressureAdhesionRatio;
        private final double cycleCount7d;
        private final double avgDailyRhFluctuation;
        private final double temperatureVariation;

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

    @Getter
    public static class DelaminationResult {

        private final double probability;
        private final RiskLevel riskLevel;
        private final Map<String, Double> featureContributions;
        private final String recommendation;

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

    public DelaminationRiskModel() {
    }

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
     * Predict delamination risk based on input features using logistic regression.
     *
     * @param input input features
     * @return assessment result including probability, risk level, contributions, and recommendation
     * @throws IllegalArgumentException if input parameters are invalid
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
     * Train model using gradient descent with cross-entropy loss.
     *
     * @param inputs training feature list
     * @param labels training label list (true = delamination occurred)
     * @param learningRate learning rate
     * @param epochs number of iterations
     * @throws IllegalArgumentException if parameters are invalid
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
     * Sigmoid function mapping any real number to (0, 1).
     *
     * @param z input value
     * @return sigmoid value in range (0, 1)
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
     * Calculate feature contributions (simplified SHAP values).
     *
     * @param input input features
     * @return feature contribution map
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
     * Generate simulated pigment layer adhesion data based on pigment type and age.
     *
     * @param pigmentType pigment type: mineral / plant / synthetic
     * @param age age in years
     * @return adhesion strength in MPa
     * @throws IllegalArgumentException if pigment type is invalid
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

    private double clip(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

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
     * Generate synthetic training samples using SMOTE algorithm.
     *
     * @param inputs original training feature list
     * @param labels original label list (true=delamination, false=safe)
     * @param k SMOTE neighbor count
     * @param oversamplingRatio oversampling ratio
     * @return augmented data containing original + synthetic samples
     * @throws IllegalArgumentException if parameters are invalid
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
     * SMOTE with default parameters (k=5, ratio=1.0).
     *
     * @param inputs original training feature list
     * @param labels original label list
     * @return augmented data
     */
    public SmoteResult generateSmoteSamples(List<FeatureInput> inputs, List<Boolean> labels) {
        return generateSmoteSamples(inputs, labels, 5, 1.0);
    }

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

    private double euclideanDistance(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    @Getter
    public static class SmoteResult {
        private final List<FeatureInput> augmentedInputs;
        private final List<Boolean> augmentedLabels;

        public SmoteResult(List<FeatureInput> augmentedInputs, List<Boolean> augmentedLabels) {
            this.augmentedInputs = augmentedInputs;
            this.augmentedLabels = augmentedLabels;
        }
    }

    private static class NeighborDistance {
        final int index;
        final double distance;

        NeighborDistance(int index, double distance) {
            this.index = index;
            this.distance = distance;
        }
    }

    /**
     * Train model with SMOTE augmentation first, then gradient descent.
     *
     * @param inputs original training features
     * @param labels original labels
     * @param learningRate learning rate
     * @param epochs number of iterations
     * @param smoteK SMOTE neighbor count
     * @param smoteRatio SMOTE oversampling ratio
     */
    public void trainWithSmote(List<FeatureInput> inputs, List<Boolean> labels,
                               double learningRate, int epochs,
                               int smoteK, double smoteRatio) {
        SmoteResult smoteResult = generateSmoteSamples(inputs, labels, smoteK, smoteRatio);
        train(smoteResult.getAugmentedInputs(), smoteResult.getAugmentedLabels(), learningRate, epochs);
    }

    /**
     * Get current model weights.
     *
     * @return weights array [w0, w1, w2, w3, w4, w5, w6]
     */
    public double[] getWeights() {
        return new double[]{w0, w1, w2, w3, w4, w5, w6};
    }

    /**
     * Get risk level thresholds.
     *
     * @return thresholds array [low, medium, high]
     */
    public double[] getRiskThresholds() {
        return new double[]{lowRiskThreshold, mediumRiskThreshold, highRiskThreshold};
    }
}
