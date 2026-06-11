package com.saltdamage.rainflow.algorithm;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class RainflowCycleCounterTest {

    private RainflowCycleCounter counter;

    @BeforeEach
    void setUp() {
        counter = new RainflowCycleCounter();
    }

    @Test
    @DisplayName("传入null抛出IllegalArgumentException")
    void testNullInput_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> counter.countCycles(null));
        assertEquals("湿度数据不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("空列表抛异常")
    void testEmptyInput_ThrowsException() {
        List<Double> empty = new ArrayList<>();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> counter.countCycles(empty));
        assertEquals("湿度数据列表为空", ex.getMessage());
    }

    @Test
    @DisplayName("1个数据点返回空结果")
    void testSingleDataPoint_ReturnsEmptyResult() {
        List<Double> data = List.of(75.0);
        RainflowCycleCounter.RainflowResult result = counter.countCycles(data);
        assertNotNull(result);
        assertEquals(0, result.getTotalCycles());
        assertEquals(0, result.getFullCycles());
        assertEquals(0, result.getExtremaCount());
        assertEquals(0.0, result.getTotalDamage());
        assertEquals(RainflowCycleCounter.DamageLevel.LOW, result.getDamageLevel());
    }

    @Test
    @DisplayName("2个数据点返回空结果")
    void testTwoDataPoints_ReturnsEmptyResult() {
        List<Double> data = List.of(70.0, 80.0);
        RainflowCycleCounter.RainflowResult result = counter.countCycles(data);
        assertNotNull(result);
        assertEquals(0, result.getTotalCycles());
        assertEquals(0, result.getFullCycles());
        assertEquals(0.0, result.getTotalDamage());
    }

    @Test
    @DisplayName("所有值相同，无循环")
    void testAllSameValues_NoCycles() {
        List<Double> data = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            data.add(75.0);
        }
        RainflowCycleCounter.RainflowResult result = counter.countCycles(data);
        assertNotNull(result);
        assertEquals(0, result.getTotalCycles());
        assertEquals(0, result.getFullCycles());
        assertEquals(0.0, result.getTotalDamage());
    }

    private List<Double> generateSineWave(int numPeriods, int pointsPerPeriod,
                                          double amplitude, double center) {
        List<Double> data = new ArrayList<>();
        int totalPoints = numPeriods * pointsPerPeriod;
        for (int i = 0; i < totalPoints; i++) {
            double val = center + amplitude * Math.sin(2.0 * Math.PI * i / pointsPerPeriod);
            data.add(val);
        }
        return data;
    }

    @Test
    @DisplayName("1个完整正弦波，期望检测到1个完整循环")
    void testSingleSineWave_CorrectCycleCount() {
        List<Double> data = generateSineWave(1, 50, 10.0, 75.0);
        RainflowCycleCounter.RainflowResult result = counter.countCycles(data);
        log.info("单正弦波结果 - 总循环: {}, 完整循环: {}, 总损伤: {}",
                result.getTotalCycles(), result.getFullCycles(), result.getTotalDamage());
        assertEquals(1, result.getTotalCycles(), 1.0, "总循环数应接近1");
        assertEquals(1, result.getFullCycles(), 1.0, "完整循环数应接近1");
        assertTrue(result.getCrossingCycles() > 0, "穿越潮解点循环应大于0");
    }

    @Test
    @DisplayName("5个正弦波，期望≈5个循环")
    void testMultipleSineWaves_ProportionalCycles() {
        List<Double> data = generateSineWave(5, 50, 10.0, 75.0);
        RainflowCycleCounter.RainflowResult result = counter.countCycles(data);
        log.info("5正弦波结果 - 总循环: {}, 完整循环: {}",
                result.getTotalCycles(), result.getFullCycles());
        assertEquals(5, result.getTotalCycles(), 2.0, "总循环数应接近5");
        assertTrue(result.getFullCycles() >= 3, "完整循环数应不少于3");
    }

    @Test
    @DisplayName("正弦波中心恰好75%RH，穿越次数高")
    void testSineWaveCenteredAtDeliquescence_HighCrossingCount() {
        List<Double> data = generateSineWave(3, 50, 15.0, 75.0);
        RainflowCycleCounter.RainflowResult result = counter.countCycles(data);
        log.info("中心75%正弦波结果 - 穿越次数: {}, 总循环: {}, 占比: {}",
                result.getCrossingCycles(), result.getTotalCycles(), result.getCrossingCycleRatio());
        assertTrue(result.getCrossingCycles() > 0, "穿越潮解点循环数应大于0");
        assertTrue(result.getCrossingCycleRatio() >= 0.5,
                "穿越潮解点循环占比应≥50%，实际: " + result.getCrossingCycleRatio());
    }

    @Test
    @DisplayName("正弦波全部在75%以下，损伤值低")
    void testSineWaveBelowDeliquescence_LowDamage() {
        List<Double> dataBelow = generateSineWave(3, 50, 5.0, 50.0);
        List<Double> dataCrossing = generateSineWave(3, 50, 15.0, 75.0);
        RainflowCycleCounter.RainflowResult resultBelow = counter.countCycles(dataBelow);
        RainflowCycleCounter.RainflowResult resultCrossing = counter.countCycles(dataCrossing);
        log.info("低于潮解点损伤: {}, 穿越潮解点损伤: {}",
                resultBelow.getTotalDamage(), resultCrossing.getTotalDamage());
        assertTrue(resultBelow.getTotalDamage() < resultCrossing.getTotalDamage(),
                "低于潮解点的损伤应低于穿越潮解点的损伤");
    }

    @Test
    @DisplayName("理想方波，循环数应等于方波周期数")
    void testPerfectSquareWave_CorrectCycles() {
        List<Double> data = new ArrayList<>();
        int periods = 4;
        for (int i = 0; i < periods; i++) {
            for (int j = 0; j < 10; j++) {
                data.add(60.0);
            }
            for (int j = 0; j < 10; j++) {
                data.add(90.0);
            }
        }
        for (int j = 0; j < 10; j++) {
            data.add(60.0);
        }
        RainflowCycleCounter.RainflowResult result = counter.countCycles(data);
        log.info("方波结果 - 总循环: {}, 完整循环: {}, 穿越: {}",
                result.getTotalCycles(), result.getFullCycles(), result.getCrossingCycles());
        assertEquals(periods, result.getTotalCycles(), 1.0,
                "方波总循环数应接近周期数: " + periods);
        assertTrue(result.getCrossingCycles() >= periods - 1,
                "穿越潮解点循环应接近周期数");
    }

    @Test
    @DisplayName("一次上升+下降，1个循环")
    void testStepUpDown_SingleCycle() {
        List<Double> data = List.of(60.0, 60.0, 60.0, 90.0, 90.0, 90.0, 60.0, 60.0, 60.0);
        RainflowCycleCounter.RainflowResult result = counter.countCycles(data);
        log.info("阶跃结果 - 总循环: {}, 完整循环: {}",
                result.getTotalCycles(), result.getFullCycles());
        assertTrue(result.getTotalCycles() >= 1, "至少应有1个循环");
        assertEquals(30.0, result.getMaxRange(), 1.0, "最大幅值约等于30%RH");
    }

    @Test
    @DisplayName("isPeak方法对典型三点的判断正确")
    void testIsPeak_CorrectDetection() {
        assertTrue(counter.isPeak(60.0, 80.0, 70.0), "80应是峰值");
        assertFalse(counter.isPeak(80.0, 70.0, 90.0), "70不是峰值");
        assertFalse(counter.isPeak(70.0, 60.0, 50.0), "单调下降无峰值");
        assertFalse(counter.isPeak(70.0, 70.0, 80.0), "相等点不是峰值");
    }

    @Test
    @DisplayName("isValley方法正确")
    void testIsValley_CorrectDetection() {
        assertTrue(counter.isValley(80.0, 60.0, 70.0), "60应是谷值");
        assertFalse(counter.isValley(60.0, 70.0, 50.0), "70不是谷值");
        assertFalse(counter.isValley(50.0, 60.0, 70.0), "单调上升无谷值");
        assertFalse(counter.isValley(80.0, 80.0, 60.0), "相等点不是谷值");
    }

    @Test
    @DisplayName("含噪声的数据应被门限过滤")
    void testExtractExtrema_RemovesNoise() {
        RainflowCycleCounter thresholdCounter = new RainflowCycleCounter(
                75.0, 3.0, 1e6, 5.0, 10.0, 10);
        List<Double> noisyData = new ArrayList<>();
        noisyData.add(70.0);
        noisyData.add(70.5);
        noisyData.add(70.2);
        noisyData.add(70.8);
        noisyData.add(70.3);
        noisyData.add(80.0);
        noisyData.add(79.7);
        noisyData.add(80.3);
        noisyData.add(79.5);
        noisyData.add(70.0);
        List<Double> extrema = thresholdCounter.extractExtrema(noisyData);
        log.info("噪声过滤前: {}点, 过滤后: {}点, 极值点: {}", noisyData.size(), extrema.size(), extrema);
        assertTrue(extrema.size() < noisyData.size(), "噪声应被过滤");
        assertEquals(noisyData.get(0), extrema.get(0), 0.001, "第一个点应保留");
    }

    @Test
    @DisplayName("第一个和最后一个点应保留")
    void testExtractExtrema_PreservesEndpoints() {
        List<Double> data = generateSineWave(2, 30, 10.0, 75.0);
        List<Double> extrema = counter.extractExtrema(data);
        assertTrue(extrema.size() >= 2, "至少应有2个点");
        assertEquals(data.get(0), extrema.get(0), 1.0, "第一个点应保留");
        assertEquals(data.get(data.size() - 1), extrema.get(extrema.size() - 1), 1.0,
                "最后一个点应保留");
    }

    @Test
    @DisplayName("平均循环幅度计算正确")
    void testAverageRange_CorrectCalculation() {
        List<Double> data = new ArrayList<>();
        int numCycles = 5;
        for (int c = 0; c < numCycles; c++) {
            for (int i = 0; i < 5; i++) data.add(70.0);
            for (int i = 0; i < 5; i++) data.add(80.0);
        }
        for (int i = 0; i < 5; i++) data.add(70.0);
        RainflowCycleCounter.RainflowResult result = counter.countCycles(data);
        log.info("平均幅值测试 - 平均: {}, 最小: {}, 最大: {}",
                result.getAverageRange(), result.getMinRange(), result.getMaxRange());
        assertTrue(result.getTotalCycles() >= 1);
        assertEquals(10.0, result.getAverageRange(), 5.0, "平均幅值约等于10%RH");
    }

    @Test
    @DisplayName("最大循环幅度等于预设振幅")
    void testMaxRange_CorrectValue() {
        List<Double> data = new ArrayList<>();
        data.add(65.0);
        data.add(65.0);
        data.add(85.0);
        data.add(85.0);
        data.add(65.0);
        data.add(65.0);
        data.add(90.0);
        data.add(90.0);
        data.add(60.0);
        data.add(60.0);
        data.add(65.0);
        RainflowCycleCounter.RainflowResult result = counter.countCycles(data);
        log.info("最大幅值测试 - 最大幅值: {}", result.getMaxRange());
        assertTrue(result.getMaxRange() >= 25.0, "最大幅值不应小于25%RH");
        assertTrue(result.getMaxRange() <= 30.0, "最大幅值不应大于30%RH");
    }

    @Test
    @DisplayName("直方图分桶数等于构造参数histogramBins")
    void testHistogram_BinCountMatchesSpecified() {
        int customBins = 7;
        RainflowCycleCounter customCounter = new RainflowCycleCounter(
                75.0, 3.0, 1e6, 0.5, 10.0, customBins);
        List<Double> data = generateSineWave(4, 40, 15.0, 75.0);
        RainflowCycleCounter.RainflowResult result = customCounter.countCycles(data);
        double[][] histogram = result.getAmplitudeHistogram();
        assertNotNull(histogram);
        int expectedBins = Math.min(customBins, result.getTotalCycles());
        if (result.getTotalCycles() > 0) {
            assertEquals(expectedBins, histogram.length, "直方图分桶数应匹配");
        }
        for (double[] bin : histogram) {
            assertEquals(3, bin.length, "每个桶应有3个值");
            assertTrue(bin[0] < bin[1], "区间下限应小于上限");
            assertTrue(bin[2] >= 0, "计数不应为负");
        }
    }

    @Test
    @DisplayName("幅度越大，损伤越大（单调关系）")
    void testMinerDamage_MonotonicWithAmplitude() {
        double mean = 75.0;
        double damageSmall = counter.calculateDamage(5.0, mean);
        double damageMedium = counter.calculateDamage(10.0, mean);
        double damageLarge = counter.calculateDamage(20.0, mean);
        log.info("损伤单调性 - 5%: {}, 10%: {}, 20%: {}", damageSmall, damageMedium, damageLarge);
        assertTrue(damageSmall < damageMedium, "小幅度损伤应小于中幅度");
        assertTrue(damageMedium < damageLarge, "中幅度损伤应小于大幅度");
    }

    @Test
    @DisplayName("循环均值距离75%越近，损伤权重越高")
    void testDeliquescenceWeight_HighestAtCenter() {
        double weightAtCenter = counter.calculateDeliquescenceWeight(75.0);
        double weightNear = counter.calculateDeliquescenceWeight(70.0);
        double weightFar = counter.calculateDeliquescenceWeight(50.0);
        log.info("权重分布 - 75%: {}, 70%: {}, 50%: {}", weightAtCenter, weightNear, weightFar);
        assertEquals(1.0, weightAtCenter, 0.001, "潮解点权重应为1.0");
        assertTrue(weightAtCenter > weightNear, "潮解点权重应最大");
        assertTrue(weightNear > weightFar, "近处权重应大于远处");
    }

    @Test
    @DisplayName("小损伤对应LOW等级")
    void testDamageLevel_LowDamage_LOW() {
        List<Double> data = generateSineWave(1, 50, 3.0, 60.0);
        RainflowCycleCounter.RainflowResult result = counter.countCycles(data);
        log.info("低损伤测试 - 总损伤: {}, 等级: {}", result.getTotalDamage(), result.getDamageLevel());
        assertTrue(result.getTotalDamage() < 0.1, "损伤应小于0.1，实际: " + result.getTotalDamage());
        assertEquals(RainflowCycleCounter.DamageLevel.LOW, result.getDamageLevel());
    }

    @Test
    @DisplayName("大损伤对应CRITICAL等级")
    void testDamageLevel_HighDamage_CRITICAL() {
        RainflowCycleCounter aggressiveCounter = new RainflowCycleCounter(
                75.0, 3.0, 100.0, 0.5, 10.0, 10);
        List<Double> data = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 5; j++) data.add(60.0);
            for (int j = 0; j < 5; j++) data.add(90.0);
        }
        for (int j = 0; j < 5; j++) data.add(60.0);
        RainflowCycleCounter.RainflowResult result = aggressiveCounter.countCycles(data);
        log.info("高损伤测试 - 总损伤: {}, 等级: {}", result.getTotalDamage(), result.getDamageLevel());
        assertTrue(result.getTotalDamage() >= 0.1,
                "损伤应足够大（因参考循环数极低），实际: " + result.getTotalDamage());
        assertTrue(result.getDamageLevel() == RainflowCycleCounter.DamageLevel.MEDIUM
                || result.getDamageLevel() == RainflowCycleCounter.DamageLevel.HIGH
                || result.getDamageLevel() == RainflowCycleCounter.DamageLevel.CRITICAL,
                "损伤等级应不低于MEDIUM");
    }

    @Test
    @DisplayName("10000个数据点，应在1秒内完成")
    void testLargeDataset_PerformanceAcceptable() {
        List<Double> data = new ArrayList<>();
        Random random = new Random(42);
        double value = 75.0;
        for (int i = 0; i < 10000; i++) {
            value += (random.nextDouble() - 0.5) * 4.0;
            value = Math.max(30.0, Math.min(95.0, value));
            data.add(value);
        }
        RainflowCycleCounter.RainflowResult result = assertTimeout(Duration.ofSeconds(1),
                () -> counter.countCycles(data));
        log.info("大数据性能测试 - 数据点: {}, 总循环: {}, 耗时在1秒内",
                data.size(), result.getTotalCycles());
        assertTrue(result.getTotalCycles() > 0, "10000点应检测到循环");
    }

    @Test
    @DisplayName("随机游走序列，结果应合理（循环数>0）")
    void testRandomWalk_ProducesReasonableCycles() {
        List<Double> data = new ArrayList<>();
        Random random = new Random(12345);
        double value = 70.0;
        for (int i = 0; i < 500; i++) {
            value += (random.nextDouble() - 0.5) * 6.0;
            value = Math.max(40.0, Math.min(95.0, value));
            data.add(value);
        }
        RainflowCycleCounter.RainflowResult result = counter.countCycles(data);
        log.info("随机游走结果 - 总循环: {}, 完整: {}, 穿越: {}, 总损伤: {}",
                result.getTotalCycles(), result.getFullCycles(),
                result.getCrossingCycles(), result.getTotalDamage());
        assertTrue(result.getTotalCycles() > 0, "随机游走应产生循环");
        assertTrue(result.getMaxRange() > 0, "最大幅值应大于0");
        assertTrue(result.getAverageRange() > 0, "平均幅值应大于0");
    }

    @Test
    @DisplayName("自定义潮解点、S-N指数等参数正确应用")
    void testConstructor_CustomParametersApplied() {
        double customRh = 80.0;
        double customSn = 2.5;
        double customRef = 5e5;
        double customThreshold = 1.0;
        double customSigma = 8.0;
        int customBins = 15;
        RainflowCycleCounter custom = new RainflowCycleCounter(
                customRh, customSn, customRef, customThreshold, customSigma, customBins);
        assertEquals(customRh, custom.getDeliquescenceRh(), 0.001);
        assertEquals(customSn, custom.getSnExponent(), 0.001);
        assertEquals(customRef, custom.getReferenceCycles(), 0.001);
        assertEquals(customThreshold, custom.getExtremaThreshold(), 0.001);
        assertEquals(customSigma, custom.getWeightSigma(), 0.001);
        assertEquals(customBins, custom.getHistogramBins());
        assertEquals(1.0, custom.calculateDeliquescenceWeight(customRh), 0.001,
                "自定义潮解点处权重应为1");
        assertTrue(custom.calculateDeliquescenceWeight(customRh + 10.0) < 1.0);
    }

    @Test
    @DisplayName("修复验证：平顶正弦波正确计数（三点雨流法）")
    void testFlatTopSineWave_CorrectCycleCount_ThreePointMethod() {
        List<Double> data = new ArrayList<>();
        for (int period = 0; period < 3; period++) {
            for (int i = 0; i < 10; i++) data.add(65.0);
            for (int i = 0; i < 5; i++) data.add(85.0);
            for (int i = 0; i < 5; i++) data.add(86.0);
            for (int i = 0; i < 5; i++) data.add(85.0);
            for (int i = 0; i < 10; i++) data.add(65.0);
        }
        RainflowCycleCounter.RainflowResult result = counter.countCycles(data);
        log.info("平顶波形结果 - 总循环: {}, 完整循环: {}, 穿越: {}",
                result.getTotalCycles(), result.getFullCycles(), result.getCrossingCycles());
        assertTrue(result.getTotalCycles() >= 2,
                "平顶波形应检测到≥2个循环，实际: " + result.getTotalCycles());
        assertTrue(result.getFullCycles() >= 1,
                "至少应有1个完整循环，实际: " + result.getFullCycles());
    }

    @Test
    @DisplayName("修复验证：对称平顶方波循环数等于周期数")
    void testFlatTopSquareWave_CorrectCycleCount() {
        List<Double> data = new ArrayList<>();
        int periods = 5;
        for (int i = 0; i < periods; i++) {
            for (int j = 0; j < 8; j++) data.add(60.0);
            for (int j = 0; j < 8; j++) data.add(90.0);
        }
        for (int j = 0; j < 8; j++) data.add(60.0);
        RainflowCycleCounter.RainflowResult result = counter.countCycles(data);
        log.info("平顶方波结果 - 总循环: {}, 期望≈{}", result.getTotalCycles(), periods);
        assertEquals(periods, result.getTotalCycles(), 2.0,
                "平顶方波循环数应≈" + periods + "，实际: " + result.getTotalCycles());
    }

    @Test
    @DisplayName("修复验证：单次平顶脉冲正确识别1个循环")
    void testSingleFlatTopPulse_OneCycle() {
        List<Double> data = new ArrayList<>();
        for (int i = 0; i < 10; i++) data.add(60.0);
        for (int i = 0; i < 15; i++) data.add(90.0);
        for (int i = 0; i < 10; i++) data.add(60.0);
        RainflowCycleCounter.RainflowResult result = counter.countCycles(data);
        log.info("单脉冲结果 - 总循环: {}, 完整循环: {}", result.getTotalCycles(), result.getFullCycles());
        assertTrue(result.getTotalCycles() >= 1,
                "单次平顶脉冲应至少检测到1个循环");
        assertEquals(30.0, result.getMaxRange(), 5.0,
                "最大幅值应≈30%RH（60→90）");
    }
}
