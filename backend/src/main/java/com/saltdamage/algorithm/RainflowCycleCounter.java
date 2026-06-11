package com.saltdamage.algorithm;

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
        // ==================== 参数校验 ====================
        validateInputData(humidityData);

        log.info("开始雨流计数 - 数据点数: {}", humidityData.size());

        // ==================== 步骤1: 提取极值点 ====================
        List<Double> extrema = extractExtrema(humidityData);
        log.debug("极值点提取完成 - 原始点数: {}, 极值点数: {}", humidityData.size(), extrema.size());

        if (extrema.size() < 3) {
            log.warn("极值点数量不足（{}个），无法进行有效的雨流计数", extrema.size());
            return createEmptyResult(humidityData);
        }

        // ==================== 步骤2: 雨流计数 ====================
        List<Cycle> cycles = rainflowCount(extrema);
        log.debug("雨流计数完成 - 检测到 {} 个循环", cycles.size());

        // ==================== 步骤3: 统计分析 ====================
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

        // 第一个点
        extrema.add(humidityData.get(0));

        double lastExtreme = humidityData.get(0);
        int direction = 0; // 0: 初始, 1: 上升, -1: 下降

        for (int i = 1; i < n - 1; i++) {
            double current = humidityData.get(i);
            double prev = humidityData.get(i - 1);
            double next = humidityData.get(i + 1);

            // 判断当前点是否为极值点
            boolean isPeak = isPeak(prev, current, next);
            boolean isValley = isValley(prev, current, next);

            if (isPeak || isValley) {
                // 检查与上一个极值点的差值是否超过阈值
                double diff = Math.abs(current - lastExtreme);
                if (diff >= extremaThreshold) {
                    extrema.add(current);
                    lastExtreme = current;
                    direction = isPeak ? 1 : -1;
                }
            }
        }

        // 最后一个点
        double lastValue = humidityData.get(n - 1);
        double lastExtremaValue = extrema.get(extrema.size() - 1);
        if (Math.abs(lastValue - lastExtremaValue) >= extremaThreshold * 0.5
                || extrema.size() == 1) {
            extrema.add(lastValue);
        }

        // 确保峰谷交替
        extrema = ensureAlternating(extrema);

        return extrema;
    }

    /**
     * 判断给定点是否为峰值
     *
     * 峰值定义：当前点严格大于前后相邻点
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
     * 谷值定义：当前点严格小于前后相邻点
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
     * 如果出现连续两个峰值或两个谷值，则保留极值更大的那个
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

        // 判断第一个趋势
        boolean expectPeak = extrema.get(1) > extrema.get(0);

        for (int i = 1; i < extrema.size(); i++) {
            double current = extrema.get(i);
            double lastResult = result.get(result.size() - 1);

            if (expectPeak) {
                if (current > lastResult) {
                    // 替换上一个值（保留更大的峰值）
                    result.set(result.size() - 1, current);
                } else {
                    // 遇到谷值，添加并切换期望
                    result.add(current);
                    expectPeak = true;
                }
            } else {
                if (current < lastResult) {
                    // 替换上一个值（保留更小的谷值）
                    result.set(result.size() - 1, current);
                } else {
                    // 遇到峰值，添加并切换期望
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
     * 根因：四峰谷法需要4个连续极值点X1,X2,X3,X4，判断|X3-X4|>=|X1-X2|且|X2-X3|<=|X1-X2|
     *       对于平顶波形（连续等值或近似等值段），极值提取后产生连续同向极值点，
     *       导致四峰谷条件永远无法满足，遗漏循环或误计循环幅度。
     * 修复：改用ASTM E1049-85标准三点雨流法，只需3个连续极值点即可识别循环：
     *       对连续三个极值点S1,S2,S3，若|S2-S3|<=|S1-S2|，则S1-S2构成一个完整循环
     *       移除S1和S2，回退检查；否则前进一步。
     *       三点法对平顶段更鲁棒，因为平顶段合并后不会影响相邻循环的识别。
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
     * 损伤计算公式（Miner准则）：
     * damage = (weight / N)
     *
     * 其中：
     * - N = K * (ΔRH)^(-m)  （S-N曲线）
     * - K = referenceCycles （参考循环次数）
     * - m = snExponent      （S-N曲线指数）
     * - ΔRH = cycleRange    （循环幅度）
     * - weight = 高斯权重，考虑循环均值与潮解点的距离
     *
     * 潮解点权重函数（高斯型）：
     * w = exp(-((mean - deliquescenceRh)²) / (2σ²))
     *
     * 物理意义：
     * - 当循环均值接近潮解点时，损伤权重最大（=1.0）
     * - 循环均值离潮解点越远，损伤权重越小
     * - 这是因为Na₂SO₄在75%RH附近发生结晶-潮解相变，
     *   只有跨越相变点的循环才会产生显著的结晶压力
     *
     * @param range 循环幅度（峰值-谷值），单位: %RH
     * @param mean  循环均值，单位: %RH
     * @return 单个循环造成的损伤值
     */
    public double calculateDamage(double range, double mean) {
        // 幅度过小的循环无损伤
        if (range <= 0) {
            return 0.0;
        }

        // 计算S-N曲线对应的疲劳寿命
        double N = referenceCycles * Math.pow(range, -snExponent);

        if (N <= 0) {
            return 0.0;
        }

        // 计算潮解点权重（高斯函数）
        double weight = calculateDeliquescenceWeight(mean);

        // 单个循环的损伤 = 权重 / 寿命
        double damage = weight / N;

        log.trace("损伤计算 - 幅值: {}%, 均值: {}%, 寿命: {:.2e}次, 权重: {:.4f}, 损伤: {:.2e}",
                range, mean, N, weight, damage);

        return damage;
    }

    /**
     * 计算潮解点权重（高斯型权重函数）
     *
     * 权重函数反映了循环均值与潮解点的接近程度对损伤的影响：
     * - 均值等于潮解点时，权重=1.0（最大）
     * - 均值偏离潮解点时，权重按高斯曲线衰减
     *
     * 物理意义：
     * - 当湿度波动跨越潮解点时，Na₂SO₄发生结晶-潮解相变
     * - 相变伴随着体积变化和结晶压力，是造成材料损伤的主要原因
     * - 离潮解点越远，相变效应越弱，损伤越小
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
     * 穿越潮解点的定义：
     * - 循环的峰值高于潮解点，且谷值低于潮解点
     * - 即循环范围包含了潮解点
     *
     * 穿越潮解点的循环会导致Na₂SO₄发生完整的结晶-潮解循环，
     * 通常对应更大的损伤。
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

    /**
     * 分析循环列表，生成完整的统计结果
     *
     * @param cycles        循环列表
     * @param totalDataPoints 原始数据点数
     * @param extremaCount   极值点数量
     * @return 雨流计数结果
     */
    private RainflowResult analyzeCycles(List<Cycle> cycles, int totalDataPoints, int extremaCount) {
        RainflowResult result = new RainflowResult();
        result.setTotalDataPoints(totalDataPoints);
        result.setExtremaCount(extremaCount);

        if (cycles.isEmpty()) {
            return result;
        }

        // ==================== 循环计数统计 ====================
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

            // 完整/部分循环统计
            if (cycle.isFullCycle()) {
                fullCycles++;
                totalFullCycleDamage += damage;
            } else {
                partialCycles++;
            }

            // 潮解点穿越统计
            if (crossesDeliquescencePoint(range, mean)) {
                crossingCycles++;
            }

            // 幅值统计
            totalRange += range;
            if (range > maxRange) {
                maxRange = range;
            }
            if (range < minRange) {
                minRange = range;
            }
        }

        // ==================== 设置统计结果 ====================
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

        // ==================== 生成直方图 ====================
        double[][] histogram = createHistogram(cycles, maxRange, minRange);
        result.setAmplitudeHistogram(histogram);

        // ==================== 损伤评估 ====================
        result.setDamageLevel(assessDamageLevel(totalDamage));

        log.info("雨流计数完成 - 总循环: {}, 完整循环: {}, 部分循环: {}, 穿越潮解点: {}",
                totalCycles, fullCycles, partialCycles, crossingCycles);
        log.info("损伤评估 - 总损伤: {:.4e}, 损伤等级: {}",
                totalDamage, result.getDamageLevel().getDisplayName());

        return result;
    }

    /**
     * 创建循环幅度分布直方图
     *
     * @param cycles   循环列表
     * @param maxRange 最大幅值
     * @param minRange 最小幅值
     * @return 直方图数据，每行是[区间下限, 区间上限, 循环次数]
     */
    private double[][] createHistogram(List<Cycle> cycles, double maxRange, double minRange) {
        if (cycles.isEmpty()) {
            return new double[0][3];
        }

        int bins = Math.min(histogramBins, cycles.size());
        double rangeSpan = maxRange - minRange;

        // 如果所有循环幅值相同，创建单个区间
        if (rangeSpan < 1e-6) {
            double[][] histogram = new double[1][3];
            histogram[0][0] = minRange - 0.01;
            histogram[0][1] = maxRange + 0.01;
            histogram[0][2] = cycles.size();
            return histogram;
        }

        // 计算区间宽度，稍微扩展范围
        double binWidth = rangeSpan / bins;
        double start = minRange - binWidth * 0.1;
        double end = maxRange + binWidth * 0.1;
        binWidth = (end - start) / bins;

        double[][] histogram = new double[bins][3];

        // 初始化区间
        for (int i = 0; i < bins; i++) {
            histogram[i][0] = start + i * binWidth;
            histogram[i][1] = start + (i + 1) * binWidth;
            histogram[i][2] = 0;
        }

        // 统计每个区间的循环数量
        for (Cycle cycle : cycles) {
            double range = cycle.getRange();
            int binIndex = (int) ((range - start) / binWidth);
            // 边界处理
            binIndex = Math.max(0, Math.min(bins - 1, binIndex));
            histogram[binIndex][2]++;
        }

        return histogram;
    }

    /**
     * 根据总损伤值评估损伤等级
     *
     * 损伤等级划分（基于Miner准则的工程经验）：
     * - 轻微：损伤 < 0.1（安全，疲劳寿命充足）
     * - 中等：0.1 ≤ 损伤 < 0.5（需关注，长期可能产生裂纹）
     * - 严重：0.5 ≤ 损伤 < 1.0（高风险，接近疲劳失效）
     * - 危险：损伤 ≥ 1.0（失效，已达到疲劳寿命）
     *
     * 注：根据Miner准则，损伤累积到1.0时发生疲劳失效。
     * 实际工程中通常取安全系数，在损伤达到0.1~0.3时即采取措施。
     *
     * @param totalDamage 总损伤值
     * @return 损伤等级枚举
     */
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

    /**
     * 验证输入湿度数据的有效性
     *
     * @param humidityData 湿度数据序列
     * @throws IllegalArgumentException 数据无效时抛出
     */
    private void validateInputData(List<Double> humidityData) {
        if (humidityData == null) {
            throw new IllegalArgumentException("湿度数据不能为空");
        }
        if (humidityData.isEmpty()) {
            throw new IllegalArgumentException("湿度数据列表为空");
        }
        // 检查数据范围（相对湿度应在0~100%之间）
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

    /**
     * 创建空结果对象（当数据不足时）
     *
     * @param humidityData 原始数据
     * @return 空的结果对象
     */
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

    /**
     * 单个循环的信息
     */
    @Getter
    public static class Cycle {
        /**
         * 循环幅度（峰值 - 谷值），单位: %RH
         */
        private final double range;

        /**
         * 循环均值，单位: %RH
         */
        private final double mean;

        /**
         * 是否为完整循环
         * true: 完整循环（峰-谷-峰 或 谷-峰-谷）
         * false: 部分循环（残留半循环）
         */
        private final boolean fullCycle;

        /**
         * 循环在极值点序列中的起始索引
         */
        private final int startIndex;

        /**
         * 循环在极值点序列中的结束索引
         */
        private final int endIndex;

        /**
         * 该循环造成的损伤值
         */
        private double damage;

        /**
         * 构造循环对象
         *
         * @param range     循环幅度
         * @param mean      循环均值
         * @param fullCycle 是否为完整循环
         * @param startIndex 起始索引
         * @param endIndex   结束索引
         */
        public Cycle(double range, double mean, boolean fullCycle, int startIndex, int endIndex) {
            this.range = range;
            this.mean = mean;
            this.fullCycle = fullCycle;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.damage = 0.0;
        }

        /**
         * 设置损伤值
         *
         * @param damage 损伤值
         */
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

    /**
     * 雨流计数结果类
     *
     * 包含循环统计、损伤评估、幅值分布等完整的分析结果。
     */
    @Getter
    public static class RainflowResult {

        /**
         * 原始数据点数量
         */
        private int totalDataPoints;

        /**
         * 提取的极值点数量
         */
        private int extremaCount;

        /**
         * 总循环次数（完整循环 + 部分循环）
         */
        private int totalCycles;

        /**
         * 完整循环次数
         */
        private int fullCycles;

        /**
         * 部分循环次数（残留半循环）
         */
        private int partialCycles;

        /**
         * 穿越潮解点的循环次数
         */
        private int crossingCycles;

        /**
         * 平均循环幅度，单位: %RH
         */
        private double averageRange;

        /**
         * 最大循环幅度，单位: %RH
         */
        private double maxRange;

        /**
         * 最小循环幅度，单位: %RH
         */
        private double minRange;

        /**
         * 总疲劳损伤（基于Miner准则）
         */
        private double totalDamage;

        /**
         * 完整循环造成的损伤
         */
        private double fullCycleDamage;

        /**
         * 循环幅度分布直方图
         * 每行是 [区间下限, 区间上限, 循环次数]
         */
        private double[][] amplitudeHistogram;

        /**
         * 损伤等级
         */
        private DamageLevel damageLevel;

        /**
         * 构造方法
         */
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

        // ==================== Setter方法 ====================

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

        /**
         * 获取穿越潮解点的循环占比
         *
         * @return 占比（0~1）
         */
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

    /**
     * 损伤等级枚举
     */
    @Getter
    public enum DamageLevel {
        /**
         * 低损伤（安全）
         */
        LOW("低损伤", "疲劳损伤轻微，材料处于安全状态"),

        /**
         * 中等损伤（需关注）
         */
        MEDIUM("中等损伤", "存在一定疲劳损伤，建议定期监测"),

        /**
         * 高损伤（高风险）
         */
        HIGH("高损伤", "疲劳损伤较严重，接近疲劳寿命极限"),

        /**
         * 危险（失效）
         */
        CRITICAL("危险", "疲劳损伤已达临界值，材料可能发生失效");

        /**
         * 显示名称
         */
        private final String displayName;

        /**
         * 描述
         */
        private final String description;

        DamageLevel(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }
}
