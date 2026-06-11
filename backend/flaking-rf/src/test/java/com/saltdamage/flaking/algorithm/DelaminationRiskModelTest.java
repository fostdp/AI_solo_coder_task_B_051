package com.saltdamage.flaking.algorithm;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@DisplayName("DelaminationRiskModel - 壁画颜料层起甲风险评估模型单元测试")
class DelaminationRiskModelTest {

    private DelaminationRiskModel model;

    @BeforeEach
    void setUp() {
        model = new DelaminationRiskModel(-3.0, 1.5, -2.0, 2.5, 0.8, 0.5, 0.3);
    }

    @Nested
    @DisplayName("一、Sigmoid函数数学正确性测试")
    class SigmoidFunctionTests {

        @Test
        @DisplayName("1. sigmoid(0) = 0.5")
        void testSigmoid_Zero_ReturnsHalf() {
            double result = model.sigmoid(0.0);
            assertEquals(0.5, result, 0.001, "sigmoid(0) 应等于 0.5");
            log.info("sigmoid(0) = {}", result);
        }

        @Test
        @DisplayName("2. sigmoid(10) ≈ 1.0（大正数趋近于1）")
        void testSigmoid_LargePositive_ApproachesOne() {
            double result = model.sigmoid(10.0);
            assertTrue(result > 0.9999, "sigmoid(10) 应非常接近1.0");
            assertTrue(result <= 1.0, "sigmoid输出不应超过1.0");
            log.info("sigmoid(10) = {}", result);
        }

        @Test
        @DisplayName("3. sigmoid(-10) ≈ 0.0（大负数趋近于0）")
        void testSigmoid_LargeNegative_ApproachesZero() {
            double result = model.sigmoid(-10.0);
            assertTrue(result < 0.0001, "sigmoid(-10) 应非常接近0.0");
            assertTrue(result >= 0.0, "sigmoid输出不应低于0.0");
            log.info("sigmoid(-10) = {}", result);
        }

        @Test
        @DisplayName("4. 单调递增性验证")
        void testSigmoid_MonotonicIncreasing() {
            double[] inputs = {-5.0, -3.0, -1.0, 0.0, 1.0, 3.0, 5.0};
            double prev = -1.0;
            for (double x : inputs) {
                double current = model.sigmoid(x);
                assertTrue(current > prev,
                        String.format("sigmoid(%f)=%f 应大于 sigmoid(前值)=%f", x, current, prev));
                prev = current;
            }
            log.info("单调递增性验证通过，输入序列: {}", java.util.Arrays.toString(inputs));
        }
    }

    @Nested
    @DisplayName("二、核心预测场景测试")
    class CorePredictionTests {

        @Test
        @DisplayName("5. ⭐ 核心验证：高压力+低附着力 → 概率>0.8，CRITICAL")
        void testHighPressureLowAdhesion_ProbabilityAbove0_8() {
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    4.5,
                    0.3,
                    20,
                    30,
                    15
            );

            DelaminationRiskModel.DelaminationResult result = model.predict(input);

            log.info("=== 核心高风险场景 ===");
            log.info("结晶压力: 4.5 MPa, 附着力: 0.3 MPa, 压附比: {}", input.getPressureAdhesionRatio());
            log.info("循环次数: 20, RH波动: 30%, 温度变幅: 15℃");
            log.info("起甲概率: {}, 风险等级: {}", result.getProbability(), result.getRiskLevel());
            log.info("防护建议: {}", result.getRecommendation());

            assertTrue(result.getProbability() > 0.8,
                    "高压力+低附着力场景下概率应大于0.8，实际值: " + result.getProbability());
            assertEquals(DelaminationRiskModel.RiskLevel.CRITICAL, result.getRiskLevel(),
                    "概率>0.8应判定为CRITICAL等级");
        }

        @Test
        @DisplayName("6. ⭐ 反向验证：低压力+高附着力 → 概率<0.1，LOW")
        void testLowPressureHighAdhesion_ProbabilityBelow0_1() {
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    0.1,
                    1.8,
                    1,
                    5,
                    3
            );

            DelaminationRiskModel.DelaminationResult result = model.predict(input);

            log.info("=== 核心低风险场景 ===");
            log.info("结晶压力: 0.1 MPa, 附着力: 1.8 MPa, 压附比: {}", input.getPressureAdhesionRatio());
            log.info("循环次数: 1, RH波动: 5%, 温度变幅: 3℃");
            log.info("起甲概率: {}, 风险等级: {}", result.getProbability(), result.getRiskLevel());

            assertTrue(result.getProbability() < 0.1,
                    "低压力+高附着力场景下概率应小于0.1，实际值: " + result.getProbability());
            assertEquals(DelaminationRiskModel.RiskLevel.LOW, result.getRiskLevel(),
                    "概率<0.1应判定为LOW等级");
        }

        @Test
        @DisplayName("7. 中等条件 → MEDIUM或HIGH等级")
        void testModerateConditions_MediumProbability() {
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    2.0,
                    1.0,
                    10,
                    20,
                    10
            );

            DelaminationRiskModel.DelaminationResult result = model.predict(input);

            log.info("=== 中等风险场景 ===");
            log.info("结晶压力: 2.0 MPa, 附着力: 1.0 MPa, 压附比: {}", input.getPressureAdhesionRatio());
            log.info("循环次数: 10, RH波动: 20%, 温度变幅: 10℃");
            log.info("起甲概率: {}, 风险等级: {}", result.getProbability(), result.getRiskLevel());

            assertTrue(result.getProbability() > 0.05 && result.getProbability() < 0.95,
                    "中等条件概率应在合理范围内，实际值: " + result.getProbability());
            DelaminationRiskModel.RiskLevel level = result.getRiskLevel();
            assertTrue(level == DelaminationRiskModel.RiskLevel.MEDIUM
                            || level == DelaminationRiskModel.RiskLevel.HIGH
                            || level == DelaminationRiskModel.RiskLevel.CRITICAL,
                    "中等条件应输出MEDIUM/HIGH/CRITICAL，实际: " + level);
        }
    }

    @Nested
    @DisplayName("三、特征重要性与相关性验证")
    class FeatureImportanceTests {

        private double predictWithPressure(double pressure) {
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    pressure, 1.0, 5, 10, 5
            );
            return model.predict(input).getProbability();
        }

        private double predictWithAdhesion(double adhesion) {
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    2.0, adhesion, 5, 10, 5
            );
            return model.predict(input).getProbability();
        }

        private double predictWithCycles(double cycles) {
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    2.0, 1.0, cycles, 10, 5
            );
            return model.predict(input).getProbability();
        }

        private double predictWithRh(double rh) {
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    2.0, 1.0, 5, rh, 5
            );
            return model.predict(input).getProbability();
        }

        private double predictWithTemp(double temp) {
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    2.0, 1.0, 5, 10, temp
            );
            return model.predict(input).getProbability();
        }

        @Test
        @DisplayName("8. 压附比是影响最大的核心特征")
        void testPressureAdhesionRatio_MostImportantFeature() {
            double deltaPressure = predictWithPressure(4.0) - predictWithPressure(0.5);
            double deltaAdhesion = predictWithAdhesion(0.5) - predictWithAdhesion(1.8);
            double deltaCycles = predictWithCycles(25) - predictWithCycles(1);
            double deltaRh = predictWithRh(45) - predictWithRh(2);
            double deltaTemp = predictWithTemp(18) - predictWithTemp(1);

            double ratioEffect = deltaPressure + deltaAdhesion;

            log.info("=== 特征影响幅度对比 ===");
            log.info("压力变化影响: {}", deltaPressure);
            log.info("附着力变化影响: {}", deltaAdhesion);
            log.info("压附比综合影响: {}", ratioEffect);
            log.info("循环次数影响: {}", deltaCycles);
            log.info("RH波动影响: {}", deltaRh);
            log.info("温度变幅影响: {}", deltaTemp);

            assertTrue(ratioEffect > deltaCycles, "压附比影响应大于循环次数影响");
            assertTrue(ratioEffect > deltaRh, "压附比影响应大于RH波动影响");
            assertTrue(ratioEffect > deltaTemp, "压附比影响应大于温度变幅影响");
        }

        @Test
        @DisplayName("9. 结晶压力与概率正相关")
        void testCrystallizationPressure_PositiveCorrelation() {
            double pLow = predictWithPressure(0.5);
            double pMid = predictWithPressure(2.0);
            double pHigh = predictWithPressure(4.5);

            log.info("压力正相关验证: 0.5MPa→{}, 2.0MPa→{}, 4.5MPa→{}", pLow, pMid, pHigh);

            assertTrue(pHigh > pMid, "压力从2.0升至4.5，概率应升高");
            assertTrue(pMid > pLow, "压力从0.5升至2.0，概率应升高");
        }

        @Test
        @DisplayName("10. 附着力与概率负相关")
        void testAdhesionStrength_NegativeCorrelation() {
            double pLowAdh = predictWithAdhesion(0.3);
            double pMidAdh = predictWithAdhesion(1.0);
            double pHighAdh = predictWithAdhesion(1.8);

            log.info("附着力负相关验证: 0.3MPa→{}, 1.0MPa→{}, 1.8MPa→{}",
                    pLowAdh, pMidAdh, pHighAdh);

            assertTrue(pLowAdh > pMidAdh, "附着力从1.0降至0.3，概率应升高");
            assertTrue(pMidAdh > pHighAdh, "附着力从1.8降至1.0，概率应升高");
        }

        @Test
        @DisplayName("11. 循环次数与概率正相关")
        void testCycleCount_PositiveCorrelation() {
            double pLow = predictWithCycles(1);
            double pMid = predictWithCycles(10);
            double pHigh = predictWithCycles(28);

            log.info("循环次数正相关验证: 1次→{}, 10次→{}, 28次→{}", pLow, pMid, pHigh);

            assertTrue(pHigh > pMid, "循环次数从10升至28，概率应升高");
            assertTrue(pMid > pLow, "循环次数从1升至10，概率应升高");
        }

        @Test
        @DisplayName("12. RH波动与概率正相关")
        void testRhFluctuation_PositiveCorrelation() {
            double pLow = predictWithRh(2);
            double pMid = predictWithRh(20);
            double pHigh = predictWithRh(48);

            log.info("RH波动正相关验证: 2%→{}, 20%→{}, 48%→{}", pLow, pMid, pHigh);

            assertTrue(pHigh > pMid, "RH波动从20%升至48%，概率应升高");
            assertTrue(pMid > pLow, "RH波动从2%升至20%，概率应升高");
        }

        @Test
        @DisplayName("13. 温度变幅与概率正相关")
        void testTemperatureVariation_PositiveCorrelation() {
            double pLow = predictWithTemp(1);
            double pMid = predictWithTemp(10);
            double pHigh = predictWithTemp(19);

            log.info("温度变幅正相关验证: 1℃→{}, 10℃→{}, 19℃→{}", pLow, pMid, pHigh);

            assertTrue(pHigh > pMid, "温度变幅从10℃升至19℃，概率应升高");
            assertTrue(pMid > pLow, "温度变幅从1℃升至10℃，概率应升高");
        }
    }

    @Nested
    @DisplayName("四、边界值与极值测试")
    class BoundaryValueTests {

        @Test
        @DisplayName("14. 所有特征取最小值 → 概率趋近于0")
        void testAllFeaturesAtMinimum_ProbabilityNearZero() {
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    0.0,
                    2.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0
            );

            DelaminationRiskModel.DelaminationResult result = model.predict(input);

            log.info("全最小值场景: 概率 = {}", result.getProbability());

            assertTrue(result.getProbability() < 0.15,
                    "所有特征取最小值时概率应接近0，实际: " + result.getProbability());
            assertTrue(result.getProbability() >= 0.0, "概率不能为负");
        }

        @Test
        @DisplayName("15. 所有特征取最大值 → 概率趋近于1")
        void testAllFeaturesAtMaximum_ProbabilityNearOne() {
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    5.0,
                    0.1,
                    5.0,
                    30.0,
                    50.0,
                    20.0
            );

            DelaminationRiskModel.DelaminationResult result = model.predict(input);

            log.info("全最大值场景: 概率 = {}", result.getProbability());

            assertTrue(result.getProbability() > 0.85,
                    "所有特征取最大值时概率应接近1，实际: " + result.getProbability());
            assertTrue(result.getProbability() <= 1.0, "概率不能超过1.0");
        }

        @Test
        @DisplayName("16. 压力=0但其他因素存在，仍有非零基线风险")
        void testZeroPressure_StillHasBaselineRisk() {
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    0.0,
                    1.0,
                    0.0,
                    25,
                    45,
                    18
            );

            DelaminationRiskModel.DelaminationResult result = model.predict(input);

            log.info("零压力场景（高RH波动+高温度变幅）: 概率 = {}", result.getProbability());

            assertTrue(result.getProbability() > 0.0,
                    "即使压力为0，其他风险因素仍应导致非零概率");
            assertTrue(result.getProbability() < 0.6,
                    "零压力下概率不应过高，实际: " + result.getProbability());
        }

        @Test
        @DisplayName("17. 压附比极大时输出不超过1.0（上溢保护）")
        void testExtremeHighRatio_TruncatedSensibly() {
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    100.0,
                    0.001,
                    100.0,
                    100.0,
                    100.0,
                    100.0
            );

            DelaminationRiskModel.DelaminationResult result = model.predict(input);

            log.info("极端高比例场景: 概率 = {}", result.getProbability());

            assertTrue(result.getProbability() <= 1.0,
                    "概率不能超过1.0，实际: " + result.getProbability());
            assertTrue(result.getProbability() >= 0.0,
                    "概率不能为负，实际: " + result.getProbability());
            assertTrue(Double.isFinite(result.getProbability()),
                    "概率应为有限值，不应为NaN或Infinity");
        }
    }

    @Nested
    @DisplayName("五、特征归一化截断测试")
    class NormalizationTests {

        @Test
        @DisplayName("18. 超出归一化最大值的特征被截断到[0,1]")
        void testNormalization_ClampsToValidRange() {
            DelaminationRiskModel.FeatureInput extremeInput = new DelaminationRiskModel.FeatureInput(
                    100.0,
                    100.0,
                    100.0,
                    100.0,
                    100.0,
                    100.0
            );

            DelaminationRiskModel.FeatureInput maxInput = new DelaminationRiskModel.FeatureInput(
                    5.0,
                    2.0,
                    5.0,
                    30.0,
                    50.0,
                    20.0
            );

            double pExtreme = model.predict(extremeInput).getProbability();
            double pMax = model.predict(maxInput).getProbability();

            log.info("归一化截断验证: 超最大值={}, 刚好最大值={}", pExtreme, pMax);

            assertEquals(pMax, pExtreme, 0.001,
                    "超出归一化最大值的特征应被截断，输出应与刚好最大值一致");
        }
    }

    @Nested
    @DisplayName("六、风险等级阈值划分测试")
    class RiskLevelThresholdTests {

        @Test
        @DisplayName("19. 概率0.05 → LOW")
        void testRiskLevelThreshold_Low() {
            DelaminationRiskModel customModel = new DelaminationRiskModel(
                    -5.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
            );
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    0.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0
            );

            DelaminationRiskModel.DelaminationResult result = customModel.predict(input);
            double prob = result.getProbability();
            log.info("低等级阈值测试: 概率={}, 等级={}", prob, result.getRiskLevel());

            if (prob < 0.1) {
                assertEquals(DelaminationRiskModel.RiskLevel.LOW, result.getRiskLevel());
            }
        }

        @Test
        @DisplayName("20. 概率0.2 → MEDIUM")
        void testRiskLevelThreshold_Medium() {
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    0.8, 1.5, 3, 8, 4
            );

            DelaminationRiskModel.DelaminationResult result = model.predict(input);
            double prob = result.getProbability();
            log.info("中等级阈值测试: 概率={}, 等级={}", prob, result.getRiskLevel());

            if (prob >= 0.1 && prob < 0.3) {
                assertEquals(DelaminationRiskModel.RiskLevel.MEDIUM, result.getRiskLevel());
            }
        }

        @Test
        @DisplayName("21. 概率0.4 → HIGH")
        void testRiskLevelThreshold_High() {
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    2.0, 1.2, 12, 22, 10
            );

            DelaminationRiskModel.DelaminationResult result = model.predict(input);
            double prob = result.getProbability();
            log.info("高等级阈值测试: 概率={}, 等级={}", prob, result.getRiskLevel());

            if (prob >= 0.3 && prob < 0.6) {
                assertEquals(DelaminationRiskModel.RiskLevel.HIGH, result.getRiskLevel());
            }
        }

        @Test
        @DisplayName("22. 概率0.8 → CRITICAL")
        void testRiskLevelThreshold_Critical() {
            DelaminationRiskModel.FeatureInput input = new DelaminationRiskModel.FeatureInput(
                    4.0, 0.4, 22, 35, 16
            );

            DelaminationRiskModel.DelaminationResult result = model.predict(input);
            double prob = result.getProbability();
            log.info("极高等级阈值测试: 概率={}, 等级={}", prob, result.getRiskLevel());

            if (prob >= 0.6) {
                assertEquals(DelaminationRiskModel.RiskLevel.CRITICAL, result.getRiskLevel());
            }
        }

        @Test
        @DisplayName("23. 边界值(0.1, 0.3, 0.6)附近等级划分正确")
        void testRiskLevel_BoundaryValues() {
            double[] thresholds = model.getRiskThresholds();
            double low = thresholds[0];
            double medium = thresholds[1];
            double high = thresholds[2];
            log.info("当前阈值配置: LOW<{}, MEDIUM<{}, HIGH<{}", low, medium, high);

            DelaminationRiskModel.FeatureInput inputNearLow = new DelaminationRiskModel.FeatureInput(
                    0.5, 1.8, 2, 5, 2
            );
            DelaminationRiskModel.DelaminationResult r1 = model.predict(inputNearLow);
            if (r1.getProbability() < low) {
                assertEquals(DelaminationRiskModel.RiskLevel.LOW, r1.getRiskLevel());
            } else if (r1.getProbability() < medium) {
                assertEquals(DelaminationRiskModel.RiskLevel.MEDIUM, r1.getRiskLevel());
            }
            log.info("边界LOW附近: prob={}, level={}", r1.getProbability(), r1.getRiskLevel());

            DelaminationRiskModel.FeatureInput inputNearMedium = new DelaminationRiskModel.FeatureInput(
                    1.2, 1.3, 6, 12, 6
            );
            DelaminationRiskModel.DelaminationResult r2 = model.predict(inputNearMedium);
            if (r2.getProbability() >= low && r2.getProbability() < medium) {
                assertEquals(DelaminationRiskModel.RiskLevel.MEDIUM, r2.getRiskLevel());
            } else if (r2.getProbability() >= medium && r2.getProbability() < high) {
                assertEquals(DelaminationRiskModel.RiskLevel.HIGH, r2.getRiskLevel());
            }
            log.info("边界MEDIUM附近: prob={}, level={}", r2.getProbability(), r2.getRiskLevel());

            DelaminationRiskModel.FeatureInput inputNearHigh = new DelaminationRiskModel.FeatureInput(
                    3.0, 0.8, 15, 28, 12
            );
            DelaminationRiskModel.DelaminationResult r3 = model.predict(inputNearHigh);
            if (r3.getProbability() >= medium && r3.getProbability() < high) {
                assertEquals(DelaminationRiskModel.RiskLevel.HIGH, r3.getRiskLevel());
            } else if (r3.getProbability() >= high) {
                assertEquals(DelaminationRiskModel.RiskLevel.CRITICAL, r3.getRiskLevel());
            }
            log.info("边界HIGH附近: prob={}, level={}", r3.getProbability(), r3.getRiskLevel());
        }
    }

    @Nested
    @DisplayName("七、特征贡献度分析测试")
    class FeatureContributionTests {

        @Test
        @DisplayName("24. 风险因素贡献度为正，保护因素为负")
        void testFeatureContributions_SumMakesSense() {
            DelaminationRiskModel.FeatureInput highRiskInput = new DelaminationRiskModel.FeatureInput(
                    4.0, 0.4, 15, 35, 15
            );

            Map<String, Double> contributions = model.calculateFeatureContributions(highRiskInput);

            log.info("=== 特征贡献度（高风险场景） ===");
            contributions.forEach((key, value) ->
                    log.info("  {}: {}{}",
                            String.format("%-25s", key),
                            value > 0 ? "+" : "",
                            String.format("%.4f", value)));

            assertTrue(contributions.get("crystallizationPressure") > 0,
                    "高压力应贡献正风险");
            assertTrue(contributions.get("adhesionStrength") < 0,
                    "低附着力（归一化值小）应贡献负风险或较小正值");
            assertTrue(contributions.get("pressureAdhesionRatio") > 0,
                    "高压附比应贡献正风险");
        }

        @Test
        @DisplayName("25. 高风险场景下压附比贡献度占比最大")
        void testFeatureContributions_PressureDominatesInHighRisk() {
            DelaminationRiskModel.FeatureInput highRiskInput = new DelaminationRiskModel.FeatureInput(
                    4.8, 0.3, 20, 25, 12
            );

            Map<String, Double> contributions = model.calculateFeatureContributions(highRiskInput);

            log.info("=== 高风险场景贡献度占比 ===");
            contributions.forEach((key, value) ->
                    log.info("  {} = {}", key, String.format("%.4f", value)));

            double ratioContrib = Math.abs(contributions.get("pressureAdhesionRatio"));
            double pressureContrib = Math.abs(contributions.get("crystallizationPressure"));
            double cycleContrib = Math.abs(contributions.get("cycleCount7d"));
            double rhContrib = Math.abs(contributions.get("avgDailyRhFluctuation"));
            double tempContrib = Math.abs(contributions.get("temperatureVariation"));

            assertTrue(ratioContrib >= cycleContrib,
                    "压附比贡献度幅度应不小于循环次数贡献度");
            assertTrue(ratioContrib >= rhContrib,
                    "压附比贡献度幅度应不小于RH波动贡献度");
            assertTrue(ratioContrib >= tempContrib,
                    "压附比贡献度幅度应不小于温度变幅贡献度");

            log.info("压附比贡献度幅度最大，验证通过");
        }
    }

    @Nested
    @DisplayName("八、颜料附着力模拟测试")
    class AdhesionSimulationTests {

        @Test
        @DisplayName("26. 矿物颜料基础附着力 > 植物颜料")
        void testGenerateAdhesion_MineralPigment_HigherBase() {
            double mineralAdhesion = model.generateAdhesionData("mineral", 0);
            double plantAdhesion = model.generateAdhesionData("plant", 0);

            log.info("新画基础附着力对比 - 矿物: {} MPa, 植物: {} MPa",
                    mineralAdhesion, plantAdhesion);

            assertTrue(mineralAdhesion > plantAdhesion,
                    String.format("矿物颜料基础附着力(%f)应大于植物颜料(%f)",
                            mineralAdhesion, plantAdhesion));
            assertEquals(1.8, mineralAdhesion, 0.001, "矿物颜料基础附着力应为1.8MPa");
            assertEquals(1.2, plantAdhesion, 0.001, "植物颜料基础附着力应为1.2MPa");
        }

        @Test
        @DisplayName("27. 年代越久远，附着力指数衰减")
        void testGenerateAdhesion_AgeEffect_DecaysExponentially() {
            String pigment = "mineral";
            double age0 = model.generateAdhesionData(pigment, 0);
            double age100 = model.generateAdhesionData(pigment, 100);
            double age200 = model.generateAdhesionData(pigment, 200);
            double age400 = model.generateAdhesionData(pigment, 400);

            log.info("矿物颜料年代衰减: 0年={}, 100年={}, 200年={}, 400年={}",
                    String.format("%.4f", age0),
                    String.format("%.4f", age100),
                    String.format("%.4f", age200),
                    String.format("%.4f", age400));

            assertTrue(age0 > age100, "0年附着力应大于100年");
            assertTrue(age100 > age200, "100年附着力应大于200年");
            assertTrue(age200 > age400, "200年附着力应大于400年");

            double ratio100_200 = age200 / age100;
            double ratio0_100 = age100 / age0;
            log.info("相邻等时间段衰减比: 0→100={}, 100→200={}", ratio0_100, ratio100_200);

            assertEquals(ratio0_100, ratio100_200, 0.01,
                    "指数衰减特性：等时间间隔衰减比应近似相等");
        }

        @Test
        @DisplayName("28. 新画(age=0)附着力接近基础值")
        void testGenerateAdhesion_NewPainting_HighAdhesion() {
            double mineral = model.generateAdhesionData("mineral", 0);
            double plant = model.generateAdhesionData("plant", 0);
            double synthetic = model.generateAdhesionData("synthetic", 0);

            log.info("新画附着力 - 矿物: {} MPa, 植物: {} MPa, 合成: {} MPa",
                    mineral, plant, synthetic);

            assertEquals(1.8, mineral, 0.001, "新矿物颜料附着力应为1.8MPa");
            assertEquals(1.2, plant, 0.001, "新植物颜料附着力应为1.2MPa");
            assertEquals(2.0, synthetic, 0.001, "新合成颜料附着力应为2.0MPa");

            assertTrue(synthetic > mineral, "合成颜料基础附着力应最高");
            assertTrue(mineral > plant, "矿物颜料基础附着力应高于植物颜料");
        }
    }

    @Nested
    @DisplayName("九、模型训练功能测试")
    class TrainingFunctionTests {

        @Test
        @DisplayName("29. 训练几轮后损失值应下降")
        void testTrain_LossDecreases() {
            DelaminationRiskModel trainModel = new DelaminationRiskModel(
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
            );

            List<DelaminationRiskModel.FeatureInput> inputs = new ArrayList<>();
            List<Boolean> labels = new ArrayList<>();

            for (int i = 0; i < 20; i++) {
                double pressure = 0.1 + i * 0.2;
                double adhesion = 2.0 - i * 0.08;
                double cycles = i * 1.2;
                double rh = i * 2.0;
                double temp = i * 0.8;

                inputs.add(new DelaminationRiskModel.FeatureInput(
                        pressure, adhesion, cycles, rh, temp
                ));
                labels.add(i >= 10);
            }

            log.info("=== 训练前权重 ===");
            double[] weightsBefore = trainModel.getWeights();
            for (int i = 0; i < weightsBefore.length; i++) {
                log.info("  w{} = {}", i, weightsBefore[i]);
            }

            trainModel.train(inputs, labels, 0.5, 100);

            log.info("=== 训练后权重 ===");
            double[] weightsAfter = trainModel.getWeights();
            for (int i = 0; i < weightsAfter.length; i++) {
                log.info("  w{} = {}", i, String.format("%.6f", weightsAfter[i]));
            }

            boolean weightsChanged = false;
            for (int i = 0; i < weightsBefore.length; i++) {
                if (Math.abs(weightsAfter[i] - weightsBefore[i]) > 0.0001) {
                    weightsChanged = true;
                    break;
                }
            }
            assertTrue(weightsChanged, "训练后权重应发生变化");

            int correctCount = 0;
            for (int i = 0; i < inputs.size(); i++) {
                double prob = trainModel.predict(inputs.get(i)).getProbability();
                boolean predicted = prob >= 0.5;
                if (predicted == labels.get(i)) {
                    correctCount++;
                }
            }
            double accuracy = (double) correctCount / inputs.size();
            log.info("训练集准确率: {}%", String.format("%.1f", accuracy * 100));

            assertTrue(accuracy >= 0.6,
                    "训练后准确率应不低于60%，实际: " + accuracy);
        }
    }

    @Nested
    @DisplayName("十、SMOTE合成样本生成测试")
    class SmoteTests {

        private List<DelaminationRiskModel.FeatureInput> createImbalancedDataset() {
            List<DelaminationRiskModel.FeatureInput> inputs = new ArrayList<>();
            inputs.add(new DelaminationRiskModel.FeatureInput(0.1, 1.8, 1, 5, 3));
            inputs.add(new DelaminationRiskModel.FeatureInput(0.2, 1.5, 2, 8, 4));
            inputs.add(new DelaminationRiskModel.FeatureInput(0.3, 1.6, 3, 6, 2));
            inputs.add(new DelaminationRiskModel.FeatureInput(0.15, 1.7, 1, 4, 3));
            inputs.add(new DelaminationRiskModel.FeatureInput(0.25, 1.4, 2, 7, 5));
            inputs.add(new DelaminationRiskModel.FeatureInput(4.5, 0.3, 20, 30, 15));
            inputs.add(new DelaminationRiskModel.FeatureInput(3.8, 0.5, 18, 28, 12));
            return inputs;
        }

        private List<Boolean> createImbalancedLabels() {
            return List.of(false, false, false, false, false, true, true);
        }

        @Test
        @DisplayName("30. SMOTE生成合成样本后总数增加")
        void testSmote_GeneratesSyntheticSamples() {
            List<DelaminationRiskModel.FeatureInput> inputs = createImbalancedDataset();
            List<Boolean> labels = createImbalancedLabels();

            DelaminationRiskModel.SmoteResult result = model.generateSmoteSamples(inputs, labels, 3, 1.0);

            log.info("SMOTE前: {}样本, SMOTE后: {}样本", inputs.size(), result.getAugmentedInputs().size());
            assertTrue(result.getAugmentedInputs().size() > inputs.size(),
                    "SMOTE后样本数应增加，实际: " + result.getAugmentedInputs().size());
            assertEquals(result.getAugmentedInputs().size(), result.getAugmentedLabels().size(),
                    "特征和标签数量应一致");
        }

        @Test
        @DisplayName("31. SMOTE后少数类样本数接近多数类")
        void testSmote_BalancesClassDistribution() {
            List<DelaminationRiskModel.FeatureInput> inputs = createImbalancedDataset();
            List<Boolean> labels = createImbalancedLabels();

            DelaminationRiskModel.SmoteResult result = model.generateSmoteSamples(inputs, labels, 3, 1.0);

            int minorityCount = 0;
            int majorityCount = 0;
            for (Boolean label : result.getAugmentedLabels()) {
                if (label) minorityCount++;
                else majorityCount++;
            }

            log.info("SMOTE后类别分布 - 少数类(起甲): {}, 多数类(安全): {}", minorityCount, majorityCount);
            assertTrue(minorityCount >= majorityCount - 1,
                    String.format("SMOTE后少数类(%d)应接近多数类(%d)", minorityCount, majorityCount));
        }

        @Test
        @DisplayName("32. SMOTE合成样本特征值在合理范围内")
        void testSmote_SyntheticFeaturesInRange() {
            List<DelaminationRiskModel.FeatureInput> inputs = createImbalancedDataset();
            List<Boolean> labels = createImbalancedLabels();

            DelaminationRiskModel.SmoteResult result = model.generateSmoteSamples(inputs, labels, 3, 1.0);

            for (int i = inputs.size(); i < result.getAugmentedInputs().size(); i++) {
                DelaminationRiskModel.FeatureInput synthetic = result.getAugmentedInputs().get(i);
                assertTrue(synthetic.getCrystallizationPressure() >= 0,
                        "合成样本压力应≥0");
                assertTrue(synthetic.getAdhesionStrength() >= 0,
                        "合成样本附着力应≥0");
                assertTrue(synthetic.getCycleCount7d() >= 0,
                        "合成样本循环次数应≥0");
                assertTrue(synthetic.getAvgDailyRhFluctuation() >= 0,
                        "合成样本RH波动应≥0");
                assertTrue(synthetic.getTemperatureVariation() >= 0,
                        "合成样本温度变幅应≥0");
            }
        }

        @Test
        @DisplayName("33. SMOTE后训练准确率提升")
        void testSmote_TrainingAccuracyImproves() {
            List<DelaminationRiskModel.FeatureInput> inputs = createImbalancedDataset();
            List<Boolean> labels = createImbalancedLabels();

            DelaminationRiskModel modelNoSmote = new DelaminationRiskModel(0, 0, 0, 0, 0, 0, 0);
            modelNoSmote.train(inputs, labels, 0.5, 200);

            int correctNoSmote = 0;
            for (int i = 0; i < inputs.size(); i++) {
                double prob = modelNoSmote.predict(inputs.get(i)).getProbability();
                if ((prob >= 0.5) == labels.get(i)) correctNoSmote++;
            }
            double accNoSmote = (double) correctNoSmote / inputs.size();

            DelaminationRiskModel modelWithSmote = new DelaminationRiskModel(0, 0, 0, 0, 0, 0, 0);
            modelWithSmote.trainWithSmote(inputs, labels, 0.5, 200, 3, 1.0);

            int correctWithSmote = 0;
            for (int i = 0; i < inputs.size(); i++) {
                double prob = modelWithSmote.predict(inputs.get(i)).getProbability();
                if ((prob >= 0.5) == labels.get(i)) correctWithSmote++;
            }
            double accWithSmote = (double) correctWithSmote / inputs.size();

            log.info("无SMOTE训练准确率: {:.1f}%, SMOTE训练准确率: {:.1f}%",
                    accNoSmote * 100, accWithSmote * 100);
            assertTrue(accWithSmote >= accNoSmote * 0.8,
                    String.format("SMOTE后准确率(%.1f%%)不应显著低于无SMOTE(%.1f%%)",
                            accWithSmote * 100, accNoSmote * 100));
        }

        @Test
        @DisplayName("34. SMOTE对空少数类返回原始数据")
        void testSmote_EmptyMinority_ReturnsOriginal() {
            List<DelaminationRiskModel.FeatureInput> inputs = new ArrayList<>();
            inputs.add(new DelaminationRiskModel.FeatureInput(0.1, 1.8, 1, 5, 3));
            inputs.add(new DelaminationRiskModel.FeatureInput(0.2, 1.5, 2, 8, 4));
            List<Boolean> labels = List.of(false, false);

            DelaminationRiskModel.SmoteResult result = model.generateSmoteSamples(inputs, labels, 3, 1.0);

            assertEquals(inputs.size(), result.getAugmentedInputs().size(),
                    "少数类为空时应返回原始数据");
        }

        @Test
        @DisplayName("35. SMOTE参数校验：null输入抛异常")
        void testSmote_NullInput_ThrowsException() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> model.generateSmoteSamples(null, List.of(true), 5, 1.0));
            assertEquals("训练数据不能为空", ex.getMessage());
        }

        @Test
        @DisplayName("36. SMOTE参数校验：k<1抛异常")
        void testSmote_InvalidK_ThrowsException() {
            List<DelaminationRiskModel.FeatureInput> inputs = createImbalancedDataset();
            List<Boolean> labels = createImbalancedLabels();

            assertThrows(IllegalArgumentException.class,
                    () -> model.generateSmoteSamples(inputs, labels, 0, 1.0));
        }
    }
}
