package com.saltdamage.rainflow.algorithm;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 盐结晶潮解循环次数统计算法（雨流计数法）
 *
 * 本类基于雨流计数法(Rainflow Counting)，统计Na₂SO₄在潮解点(RH=75%)附近的
 * 相对湿度波动循环次数，用于评估盐结晶-潮解循环对多孔材料的疲劳损伤。
 *
 * 雨流计数法是一种广泛应用于疲劳分析的循环计数方法，能够将随机载荷历程
 * 转化为一系列具有特定幅值和均值的完整循环和半循环。
 *
 * 核心算法：
 * 1. 提取峰值谷值（极值点），去除无效的小波动
 * 2. 采用ASTM E1049-85标准三点雨流法进行循环计数（修复：原四峰谷法对平顶波形误计）
 * 3. 基于Miner线性累积损伤准则评估疲劳损伤
 * 4. 考虑潮解点附近的损伤权重（高斯权重函数）
 *
 * 损伤模型：
 * - Miner准则：损伤 = Σ(n_i / N_i)
 * - S-N曲线：N_i = K * (ΔRH)^(-m)
 *   其中 K=referenceCycles, m=snExponent
 * - 潮解点权重：w = exp(-((mean - deliquescenceRh)²) / (2σ²))
 *
 * @author SaltDamage Team
 */
@Slf4j
public class RainflowCycleCounter {

    // ==================== 默认参数常量 ====================

    /**
     * 默认潮解点相对湿度，单位: %
     * Na₂SO₄的潮解相对湿度约为75%（25℃）
     */
    private static final double DEFAULT_DELIQUESCENCE_RH = 75.0;

    /**
     * 默认S-N曲线指数（疲劳强度指数）
     * 对于盐结晶疲劳损伤，典型取值为3.0
     */
    private static final double DEFAULT_SN_EXPONENT = 3.0;

    /**
     * 默认参考循环次数（K值），单位: 次
     * 对应ΔRH=1时的疲劳寿命
     */
    private static final double DEFAULT_REFERENCE_CYCLES = 1e6;

    /**
     * 默认极值点提取阈值，单位: %RH
     * 小于此阈值的波动视为噪声，不计入极值点
     */
    private static final double DEFAULT_EXTREMA_THRESHOLD = 0.5;

    /**
     * 高斯权重函数的标准差，单位: %RH
     * 控制潮解点附近损伤权重的衰减速率
     */
    private static final double DEFAULT_WEIGHT_SIGMA = 10.0;

    /**
     * 直方图默认分桶数量
     */
    private static final int DEFAULT_HISTOGRAM_BINS = 10;

    // ==================== 配置参数 ====================

    /**
     * 潮解点相对湿度，单位: %
     */
    @Getter
    private final double deliquescenceRh;

    /**
     * S-N曲线指数（疲劳强度指数m）
     */
    @Getter
    private final double snExponent;

    /**
     * 参考循环次数（K值）
     */
    @Getter
    private final double referenceCycles;

    /**
     * 极值点提取阈值，单位: %RH
     */
    @Getter
    private final double extremaThreshold;

    /**
     * 高斯权重函数的标准差，单位: %RH
     */
    @Getter
    private final double weightSigma;

    /**
     * 直方图分桶数量
     */
    @Getter
    private final int histogramBins;

    // ==================== 构造方法 ====================

    /**
     * 默认构造方法，使用所有默认参数
     */
    public RainflowCycleCounter() {
        this(DEFAULT_DELIQUESCENCE_RH, DEFAULT_SN_EXPONENT, DEFAULT_REFERENCE_CYCLES);
    }

    /**
     * 构造方法，指定核心参数
     *
     * @param deliquescenceRh 潮解点相对湿度，单位: %
     * @param snExponent      S-N曲线指数（疲劳强度指数）
     * @param referenceCycles 参考循环次数（K值）
     */
    public RainflowCycleCounter(double deliquescenceRh, double snExponent, double referenceCycles) {
        this(deliquescenceRh, snExponent, referenceCycles,
                DEFAULT_EXTREMA_THRESHOLD, DEFAULT_WEIGHT_SIGMA, DEFAULT_HISTOGRAM_BINS);
    }

    /**
     * 完整构造方法，指定所有参数
     *
     * @param deliquescenceRh    潮解点相对湿度，单位: %
     * @param snExponent         S-N曲线指数（疲劳强度指数）
     * @param referenceCycles    参考循环次数（K值）
     * @param extremaThreshold   极值点提取阈值，单位: %RH
     * @param weightSigma        高斯权重函数的标准差，单位: %RH
     * @param histogramBins      直方图分桶数量
     */
    public RainflowCycleCounter(double deliquescenceRh, double snExponent, double referenceCycles,
                                double extremaThreshold, double weightSigma, int histogramBins) {
        this.deliquescenceRh = deliquescenceRh;
        this.snExponent = snExponent;
        this.referenceCycles = referenceCycles;
        this.extremaThreshold = extremaThreshold;
        this.weightSigma = weightSigma;
        this.histogramBins = histogramBins;

        log.debug("雨流计数器初始化 - 潮解点: {}% RH, S-N指数: {}, 参考循环: {}次",
                deliquescenceRh, snExponent, referenceCycles);
    }

    // ==================== 核心公共方法 ====================

    /**
     * 对湿度序列进行雨流计数，统计循环次数并评估疲劳损伤
     *
     * 处理流程：
     * 1. 输入参数校验
     * 2. 提取峰值谷值（极值点）
     * 3. 雨流法循环计数
     * 4. 统计循环特征（幅值、均值等）
     * 5. 计算疲劳损伤（基于Miner准则）
     * 6. 生成循环幅度分布直方图
     *
     * @param humidityData 按时间排序的相对湿度序列，单位: %
     * @return 雨流计数结果，包含循环统计和损伤评估
     * @throws IllegalArgumentException 输入数据无效时抛出
     */
    public RainflowResult countCycles(List<Double> humidityData) {
        validateInputData(humidityData);

        log.info("开始雨流计数 - 数据点数: {}", humidityData.size());

        List<Double> extrema = extractExtrema(humidityData);
        log.debug("极值点提取完成 - 原始点数: {}, 极值点数: {}", humidityData.size(), extrema.size());

        if (extrema.size() < 3) {
            log.warn("极值点数量不足（{}个），无法进行有效的雨流计数", extrema.size());
            return createEmptyResult(humidityData);
        }

        List<Cycle> cycles = rainflowCount(extrema);
        log.debug("雨流计数完成 - 检测到 {} 个循环", cycles.size());

        return analyzeCycles(cycles, humidityData.size(), extrema.size());
    }

    // ==================== 极值点提取 ====================

    /**
     * 从湿度序列中提取峰值和谷值（极值点）
     *
     * 极值点定义：
     * - 峰值：大于其前后相邻点的局部最大值
     * - 谷值：小于其前后相邻点的局部最小值
     *
     * 采用"门限法"过滤小波动：
     * - 只有当波动幅度超过extremaThreshold时才视为有效极值
     * - 避免噪声干扰导致的虚假循环
     *
     * 算法特点：
     * - 保留序列的第一个和最后一个点
     * - 交替出现峰值和谷值
     * - 过滤掉幅度小于阈值的微小波动
     *
     * @param humidityData 原始湿度序列
     * @return 极值点序列（交替出现峰谷）
     */
    public List<Double> extractExtrema(List<Double> humidityData) {
        if (humidityData == null || humidityData.isEmpty()) {
            return new ArrayList<>();
        }

        List<Double> extrema = new ArrayList<>();
        int n = humidityData.size();

        if (n <= 2) {
            extrema.addAll(humidityData);
            return extrema;
        }

        extrema.add(humidityData.get(0));

        double lastExtreme = humidityData.get(0);
        int direction = 0;

        for (int i = 1; i < n - 1; i++) {
            double current = humidityData.get(i);
            double prev = humidityData.get(i - 1);
            double next = humidityData.get(i + 1);

            boolean isPeak = isPeak(prev, current, next);
            boolean isValley = isValley(prev, current, next);

            if (isPeak || isValley) {
                double diff = Math.abs(current - lastExtreme);
                if (diff >= extremaThreshold) {
                    extrema.add(current);
                    lastExtreme = current;
                    direction = isPeak ? 1 : -1;
                }
            }
        }

        double lastValue = humidityData.get(n - 1);
        double lastExtremaValue = extrema.get(extrema.size() - 1);
        if (Math.abs(lastValue - lastExtremaValue) >= extremaThreshold * 0.5
                || extrema.size() == 1) {
            extrema.add(lastValue);
        }

        extrema = ensureAlternating(extrema);

        return extrema;
    }

    /**
     * 判断给定点是否为峰值
     *
     * @param prev   前一个点的值
     * @param current 当前点的值
     * @param next   后一个点的值
     * @return true表示是峰值
     */
    public boolean isPeak(double prev, double current, double next) {
        return current > prev && current > next;
    }

    /**
     * 判断给定点是否为谷值
     *
     * @param prev   前一个点的值
     * @param current 当前点的值
     * @param next   后一个点的值
     * @return true表示是谷值
     */
    public boolean isValley(double prev, double current, double next) {
        return current < prev && current < next;
    }

    /**
     * 确保极值点序列交替出现峰谷
     *
     * @param extrema 原始极值点序列
     * @return 交替排列的极值点序列
     */
    private List<Double> ensureAlternating(List<Double> extrema) {
        if (extrema.size() <= 2) {
            return extrema;
        }

        List<Double> result = new ArrayList<>();
        result.add(extrema.get(0));

        boolean expectPeak = extrema.get(1) > extrema.get(0);

        for (int i = 1; i < extrema.size(); i++) {
            double current = extrema.get(i);
            double lastResult = result.get(result.size() - 1);

            if (expectPeak) {
                if (current > lastResult) {
                    result.set(result.size() - 1, current);
                } else {
                    result.add(current);
                    expectPeak = true;
                }
            } else {
                if (current < lastResult) {
                    result.set(result.size() - 1, current);
                } else {
                    result.add(current);
                    expectPeak = false;
                }
            }
        }

        return result;
    }

    // ==================== 雨流计数核心算法 ====================

    /**
     * 基于极值点序列执行雨流计数（三点雨流法）
     *
     * 修复缺陷：原四峰谷法对平顶波形误计循环
     * 修复：改用ASTM E1049-85标准三点雨流法，只需3个连续极值点即可识别循环：
     *       对连续三个极值点S1,S2,S3，若|S2-S3|<=|S1-S2|，则S1-S2构成一个完整循环
     *       移除S1和S2，回退检查；否则前进一步。
     *
     * @param extrema 极值点序列（交替峰谷）
     * @return 检测到的循环列表
     */
    public List<Cycle> rainflowCount(List<Double> extrema) {
        List<Cycle> cycles = new ArrayList<>();

        if (extrema == null || extrema.size() < 3) {
            return cycles;
        }

        List<Double> points = new ArrayList<>(extrema);

        int i = 0;
        while (i <= points.size() - 3) {
            double s1 = points.get(i);
            double s2 = points.get(i + 1);
            double s3 = points.get(i + 2);

            double range12 = Math.abs(s2 - s1);
            double range23 = Math.abs(s3 - s2);

            if (range23 <= range12 && range12 >= extremaThreshold) {
                double cycleRange = range12;
                double cycleMean = (s1 + s2) / 2.0;

                cycles.add(new Cycle(cycleRange, cycleMean, true, i, i + 1));

                points.remove(i + 1);
                points.remove(i);

                if (i > 0) {
                    i--;
                }
            } else {
                i++;
            }
        }

        for (int j = 0; j < points.size() - 1; j++) {
            double range = Math.abs(points.get(j + 1) - points.get(j));
            if (range >= extremaThreshold) {
                double mean = (points.get(j) + points.get(j + 1)) / 2.0;
                cycles.add(new Cycle(range, mean, false, j, j + 1));
            }
        }

        cycles.sort((a, b) -> Double.compare(b.range, a.range));

        return cycles;
    }

    // ==================== 损伤计算 ====================

    /**
     * 计算单个循环的疲劳损伤
     *
     * @param range 循环幅度（峰值-谷值），单位: %RH
     * @param mean  循环均值，单位: %RH
     * @return 单个循环造成的损伤值
     */
    public double calculateDamage(double range, double mean) {
        if (range <= 0) {
            return 0.0;
        }

        double N = referenceCycles * Math.pow(range, -snExponent);

        if (N <= 0) {
            return 0.0;
        }

        double weight = calculateDeliquescenceWeight(mean);

        double damage = weight / N;

        log.trace("损伤计算 - 幅值: {}%, 均值: {}%, 寿命: {:.2e}次, 权重: {:.4f}, 损伤: {:.2e}",
                range, mean, N, weight, damage);

        return damage;
    }

    /**
     * 计算潮解点权重（高斯型权重函数）
     *
     * @param mean 循环均值，单位: %RH
     * @return 权重值（范围: 0~1）
     */
    public double calculateDeliquescenceWeight(double mean) {
        double diff = mean - deliquescenceRh;
        double sigma = weightSigma;
        double weight = Math.exp(-(diff * diff) / (2.0 * sigma * sigma));
        return weight;
    }

    // ==================== 潮解点穿越判断 ====================

    /**
     * 判断循环是否穿越潮解点
     *
     * @param range 循环幅度，单位: %RH
     * @param mean  循环均值，单位: %RH
     * @return true表示循环穿越潮解点
     */
    public boolean crossesDeliquescencePoint(double range, double mean) {
        double halfRange = range / 2.0;
        double peak = mean + halfRange;
        double valley = mean - halfRange;
        return peak > deliquescenceRh && valley < deliquescenceRh;
    }

    // ==================== 结果分析与统计 ====================

    private RainflowResult analyzeCycles(List<Cycle> cycles, int totalDataPoints, int extremaCount) {
        RainflowResult result = new RainflowResult();
        result.setTotalDataPoints(totalDataPoints);
        result.setExtremaCount(extremaCount);

        if (cycles.isEmpty()) {
            return result;
        }

        int totalCycles = cycles.size();
        int fullCycles = 0;
        int partialCycles = 0;
        int crossingCycles = 0;

        double totalRange = 0.0;
        double maxRange = 0.0;
        double minRange = Double.MAX_VALUE;

        double totalDamage = 0.0;
        double totalFullCycleDamage = 0.0;

        for (Cycle cycle : cycles) {
            double range = cycle.getRange();
            double mean = cycle.getMean();
            double damage = calculateDamage(range, mean);

            cycle.setDamage(damage);
            totalDamage += damage;

            if (cycle.isFullCycle()) {
                fullCycles++;
                totalFullCycleDamage += damage;
            } else {
                partialCycles++;
            }

            if (crossesDeliquescencePoint(range, mean)) {
                crossingCycles++;
            }

            totalRange += range;
            if (range > maxRange) {
                maxRange = range;
            }
            if (range < minRange) {
                minRange = range;
            }
        }

        result.setTotalCycles(totalCycles);
        result.setFullCycles(fullCycles);
        result.setPartialCycles(partialCycles);
        result.setCrossingCycles(crossingCycles);

        result.setTotalDamage(totalDamage);
        result.setFullCycleDamage(totalFullCycleDamage);

        if (totalCycles > 0) {
            result.setAverageRange(totalRange / totalCycles);
        }
        result.setMaxRange(maxRange);
        result.setMinRange(minRange);

        double[][] histogram = createHistogram(cycles, maxRange, minRange);
        result.setAmplitudeHistogram(histogram);

        result.setDamageLevel(assessDamageLevel(totalDamage));

        log.info("雨流计数完成 - 总循环: {}, 完整循环: {}, 部分循环: {}, 穿越潮解点: {}",
                totalCycles, fullCycles, partialCycles, crossingCycles);
        log.info("损伤评估 - 总损伤: {:.4e}, 损伤等级: {}",
                totalDamage, result.getDamageLevel().getDisplayName());

        return result;
    }

    private double[][] createHistogram(List<Cycle> cycles, double maxRange, double minRange) {
        if (cycles.isEmpty()) {
            return new double[0][3];
        }

        int bins = Math.min(histogramBins, cycles.size());
        double rangeSpan = maxRange - minRange;

        if (rangeSpan < 1e-6) {
            double[][] histogram = new double[1][3];
            histogram[0][0] = minRange - 0.01;
            histogram[0][1] = maxRange + 0.01;
            histogram[0][2] = cycles.size();
            return histogram;
        }

        double binWidth = rangeSpan / bins;
        double start = minRange - binWidth * 0.1;
        double end = maxRange + binWidth * 0.1;
        binWidth = (end - start) / bins;

        double[][] histogram = new double[bins][3];

        for (int i = 0; i < bins; i++) {
            histogram[i][0] = start + i * binWidth;
            histogram[i][1] = start + (i + 1) * binWidth;
            histogram[i][2] = 0;
        }

        for (Cycle cycle : cycles) {
            double range = cycle.getRange();
            int binIndex = (int) ((range - start) / binWidth);
            binIndex = Math.max(0, Math.min(bins - 1, binIndex));
            histogram[binIndex][2]++;
        }

        return histogram;
    }

    private DamageLevel assessDamageLevel(double totalDamage) {
        if (totalDamage < 0.1) {
            return DamageLevel.LOW;
        } else if (totalDamage < 0.5) {
            return DamageLevel.MEDIUM;
        } else if (totalDamage < 1.0) {
            return DamageLevel.HIGH;
        } else {
            return DamageLevel.CRITICAL;
        }
    }

    // ==================== 辅助方法 ====================

    private void validateInputData(List<Double> humidityData) {
        if (humidityData == null) {
            throw new IllegalArgumentException("湿度数据不能为空");
        }
        if (humidityData.isEmpty()) {
            throw new IllegalArgumentException("湿度数据列表为空");
        }
        for (int i = 0; i < humidityData.size(); i++) {
            Double rh = humidityData.get(i);
            if (rh == null) {
                throw new IllegalArgumentException("湿度数据包含null值，索引: " + i);
            }
            if (rh < 0 || rh > 100) {
                log.warn("湿度数据超出合理范围[0, 100] - 索引: {}, 值: {}%", i, rh);
            }
        }
    }

    private RainflowResult createEmptyResult(List<Double> humidityData) {
        RainflowResult result = new RainflowResult();
        result.setTotalDataPoints(humidityData.size());
        result.setExtremaCount(0);
        result.setTotalCycles(0);
        result.setFullCycles(0);
        result.setPartialCycles(0);
        result.setCrossingCycles(0);
        result.setTotalDamage(0.0);
        result.setDamageLevel(DamageLevel.LOW);
        result.setAmplitudeHistogram(new double[0][3]);
        return result;
    }

    // ==================== 内部类：循环对象 ====================

    @Getter
    public static class Cycle {
        private final double range;
        private final double mean;
        private final boolean fullCycle;
        private final int startIndex;
        private final int endIndex;
        private double damage;

        public Cycle(double range, double mean, boolean fullCycle, int startIndex, int endIndex) {
            this.range = range;
            this.mean = mean;
            this.fullCycle = fullCycle;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.damage = 0.0;
        }

        public void setDamage(double damage) {
            this.damage = damage;
        }

        @Override
        public String toString() {
            return String.format("Cycle{range=%.2f%%, mean=%.2f%%, full=%b, damage=%.2e}",
                    range, mean, fullCycle, damage);
        }
    }

    // ==================== 内部类：雨流计数结果 ====================

    @Getter
    public static class RainflowResult {

        private int totalDataPoints;
        private int extremaCount;
        private int totalCycles;
        private int fullCycles;
        private int partialCycles;
        private int crossingCycles;
        private double averageRange;
        private double maxRange;
        private double minRange;
        private double totalDamage;
        private double fullCycleDamage;
        private double[][] amplitudeHistogram;
        private DamageLevel damageLevel;

        public RainflowResult() {
            this.totalDataPoints = 0;
            this.extremaCount = 0;
            this.totalCycles = 0;
            this.fullCycles = 0;
            this.partialCycles = 0;
            this.crossingCycles = 0;
            this.averageRange = 0.0;
            this.maxRange = 0.0;
            this.minRange = 0.0;
            this.totalDamage = 0.0;
            this.fullCycleDamage = 0.0;
            this.damageLevel = DamageLevel.LOW;
        }

        public void setTotalDataPoints(int totalDataPoints) {
            this.totalDataPoints = totalDataPoints;
        }

        public void setExtremaCount(int extremaCount) {
            this.extremaCount = extremaCount;
        }

        public void setTotalCycles(int totalCycles) {
            this.totalCycles = totalCycles;
        }

        public void setFullCycles(int fullCycles) {
            this.fullCycles = fullCycles;
        }

        public void setPartialCycles(int partialCycles) {
            this.partialCycles = partialCycles;
        }

        public void setCrossingCycles(int crossingCycles) {
            this.crossingCycles = crossingCycles;
        }

        public void setAverageRange(double averageRange) {
            this.averageRange = averageRange;
        }

        public void setMaxRange(double maxRange) {
            this.maxRange = maxRange;
        }

        public void setMinRange(double minRange) {
            this.minRange = minRange;
        }

        public void setTotalDamage(double totalDamage) {
            this.totalDamage = totalDamage;
        }

        public void setFullCycleDamage(double fullCycleDamage) {
            this.fullCycleDamage = fullCycleDamage;
        }

        public void setAmplitudeHistogram(double[][] amplitudeHistogram) {
            this.amplitudeHistogram = amplitudeHistogram;
        }

        public void setDamageLevel(DamageLevel damageLevel) {
            this.damageLevel = damageLevel;
        }

        public double getCrossingCycleRatio() {
            if (totalCycles == 0) {
                return 0.0;
            }
            return (double) crossingCycles / totalCycles;
        }

        @Override
        public String toString() {
            return String.format(
                    "RainflowResult{数据点=%d, 极值点=%d, 总循环=%d, 完整循环=%d, " +
                            "部分循环=%d, 穿越潮解点=%d, 平均幅值=%.2f%%, 最大幅值=%.2f%%, " +
                            "总损伤=%.4e, 损伤等级=%s}",
                    totalDataPoints, extremaCount, totalCycles, fullCycles,
                    partialCycles, crossingCycles, averageRange, maxRange,
                    totalDamage, damageLevel.getDisplayName());
        }
    }

    // ==================== 枚举：损伤等级 ====================

    @Getter
    public enum DamageLevel {
        LOW("低损伤", "疲劳损伤轻微，材料处于安全状态"),
        MEDIUM("中等损伤", "存在一定疲劳损伤，建议定期监测"),
        HIGH("高损伤", "疲劳损伤较严重，接近疲劳寿命极限"),
        CRITICAL("危险", "疲劳损伤已达临界值，材料可能发生失效");

        private final String displayName;
        private final String description;

        DamageLevel(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }
}
