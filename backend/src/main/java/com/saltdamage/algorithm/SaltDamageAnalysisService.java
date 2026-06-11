package com.saltdamage.algorithm;

import com.saltdamage.algorithm.util.Vector3D;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 盐害分析综合服务
 *
 * 本服务整合盐分运移模型和结晶压力预测模型，提供完整的盐害分析功能：
 * 1. 单点盐害分析
 * 2. 网格区域批量分析
 * 3. 未来72小时盐害发展趋势预测
 * 4. 综合风险等级判定
 *
 * 服务特点：
 * - 采用时间步长迭代算法模拟盐分运移过程
 * - 基于历史数据和环境参数预测未来趋势
 * - 多因素综合风险评估（压力、浓度、速率）
 * - 风险等级：LOW/MEDIUM/HIGH/CRITICAL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaltDamageAnalysisService {

    private final SaltMigrationModel saltMigrationModel;
    private final CrystallizationPressureModel crystallizationPressureModel;

    /**
     * 预测小时数（默认72小时）
     */
    @Value("${algorithm.crystallization.prediction-hours:72}")
    private int predictionHours;

    /**
     * 时间步长（小时），用于数值模拟
     */
    private static final double TIME_STEP = 1.0;

    /**
     * 盐害单点分析结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointAnalysisResult {
        /**
         * 位置x坐标
         */
        private int x;

        /**
         * 位置y坐标
         */
        private int y;

        /**
         * 当前浓度，单位: mol/kg
         */
        private double concentration;

        /**
         * 盐分运移速度，单位: m/s
         */
        private Vector3D migrationVelocity;

        /**
         * 运移速度模长，单位: m/s
         */
        private double velocityMagnitude;

        /**
         * 结晶压力，单位: Pa
         */
        private double crystallizationPressure;

        /**
         * 饱和指数 SI
         */
        private double saturationIndex;

        /**
         * 结晶速率（相对值）
         */
        private double crystallizationRate;

        /**
         * 风险等级
         */
        private CrystallizationPressureModel.RiskLevel riskLevel;

        /**
         * 分析时间
         */
        private LocalDateTime analysisTime;
    }

    /**
     * 趋势预测数据点
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendDataPoint {
        /**
         * 预测时间（小时）
         */
        private int hour;

        /**
         * 预测浓度，单位: mol/kg
         */
        private double concentration;

        /**
         * 预测结晶压力，单位: Pa
         */
        private double crystallizationPressure;

        /**
         * 预测饱和指数
         */
        private double saturationIndex;

        /**
         * 预测风险等级
         */
        private CrystallizationPressureModel.RiskLevel riskLevel;

        /**
         * 预测时间
         */
        private LocalDateTime timestamp;
    }

    /**
     * 趋势预测结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPredictionResult {
        /**
         * 位置x坐标
         */
        private int x;

        /**
         * 位置y坐标
         */
        private int y;

        /**
         * 预测时长（小时）
         */
        private int predictionHours;

        /**
         * 趋势数据点列表
         */
        private List<TrendDataPoint> trendData;

        /**
         * 最终风险等级
         */
        private CrystallizationPressureModel.RiskLevel finalRiskLevel;

        /**
         * 最大结晶压力，单位: Pa
         */
        private double maxPressure;

        /**
         * 最大压力出现时间（小时）
         */
        private int maxPressureHour;

        /**
         * 浓度变化率，单位: mol/kg/hour
         */
        private double concentrationChangeRate;
    }

    /**
     * 批量分析结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchAnalysisResult {
        /**
         * 分析时间
         */
        private LocalDateTime analysisTime;

        /**
         * 网格行数
         */
        private int rows;

        /**
         * 网格列数
         */
        private int cols;

        /**
         * 所有点的分析结果
         */
        private PointAnalysisResult[][] results;

        /**
         * 统计信息
         */
        private Map<String, Object> statistics;

        /**
         * 整体风险等级
         */
        private CrystallizationPressureModel.RiskLevel overallRiskLevel;
    }

    /**
     * 单点盐害分析
     *
     * 对单个位置进行综合盐害分析，包括：
     * 1. 计算盐分运移速度
     * 2. 计算结晶压力
     * 3. 计算饱和指数和结晶速率
     * 4. 评估风险等级
     *
     * @param concentrationGrid 浓度网格，单位: mol/m³
     * @param pressureGrid 压力网格，单位: Pa
     * @param temperature 环境温度，单位: ℃
     * @param relativeHumidity 相对湿度，范围: 0~1
     * @param x x坐标索引
     * @param y y坐标索引
     * @param deltaX x方向网格间距，单位: m
     * @param deltaY y方向网格间距，单位: m
     * @param porosity 孔隙度
     * @param permeability 渗透率，单位: m²
     * @return 单点分析结果
     * @throws IllegalArgumentException 参数无效时抛出
     */
    public PointAnalysisResult analyzePoint(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            double temperature,
            double relativeHumidity,
            int x, int y,
            double deltaX, double deltaY,
            double porosity,
            double permeability) {

        log.info("开始单点盐害分析 - 位置({}, {}), 温度: {}℃, 湿度: {}",
                x, y, temperature, relativeHumidity);

        // 参数校验
        validateGridParameters(concentrationGrid, pressureGrid, x, y, deltaX, deltaY);

        try {
            // 获取当前点浓度（转换为 mol/kg，假设溶液密度约为1000 kg/m³）
            double concentration = concentrationGrid[y][x] / 1000.0;

            // 1. 计算盐分运移速度
            Vector3D migrationVelocity = saltMigrationModel.calculateMigrationVelocity(
                    concentrationGrid, pressureGrid, porosity, permeability, 0, 0, x, y, deltaX, deltaY);

            double velocityMagnitude = migrationVelocity.magnitude();

            // 2. 计算结晶压力
            double crystallizationPressure = crystallizationPressureModel.calculateCrystallizationPressure(
                    concentration, temperature, relativeHumidity);

            // 3. 计算饱和指数
            double saturationIndex = crystallizationPressureModel.calculateSaturationIndex(
                    concentration, temperature);

            // 4. 计算结晶速率
            double crystallizationRate = crystallizationPressureModel.calculateCrystallizationRate(
                    concentration, temperature, relativeHumidity);

            // 5. 评估风险等级
            CrystallizationPressureModel.RiskLevel riskLevel =
                    crystallizationPressureModel.assessRiskLevel(crystallizationPressure, saturationIndex);

            PointAnalysisResult result = PointAnalysisResult.builder()
                    .x(x)
                    .y(y)
                    .concentration(concentration)
                    .migrationVelocity(migrationVelocity)
                    .velocityMagnitude(velocityMagnitude)
                    .crystallizationPressure(crystallizationPressure)
                    .saturationIndex(saturationIndex)
                    .crystallizationRate(crystallizationRate)
                    .riskLevel(riskLevel)
                    .analysisTime(LocalDateTime.now())
                    .build();

            log.info("单点分析完成 - 位置({}, {}), 压力: {} Pa, 风险等级: {}",
                    x, y, String.format("%.2f", crystallizationPressure), riskLevel.getDisplayName());

            return result;

        } catch (Exception e) {
            log.error("单点盐害分析失败 - 位置({}, {}): {}", x, y, e.getMessage(), e);
            throw new RuntimeException("盐害分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 网格区域批量盐害分析
     *
     * 对整个网格区域进行批量盐害分析，返回所有点的分析结果和统计信息。
     *
     * @param concentrationGrid 浓度网格，单位: mol/m³
     * @param pressureGrid 压力网格，单位: Pa
     * @param temperature 环境温度，单位: ℃
     * @param relativeHumidity 相对湿度，范围: 0~1
     * @param deltaX x方向网格间距，单位: m
     * @param deltaY y方向网格间距，单位: m
     * @param porosity 孔隙度
     * @param permeability 渗透率，单位: m²
     * @return 批量分析结果
     * @throws IllegalArgumentException 参数无效时抛出
     */
    public BatchAnalysisResult analyzeBatch(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            double temperature,
            double relativeHumidity,
            double deltaX, double deltaY,
            double porosity,
            double permeability) {

        log.info("开始批量盐害分析 - 温度: {}℃, 湿度: {}", temperature, relativeHumidity);

        // 参数校验
        if (concentrationGrid == null || concentrationGrid.length == 0) {
            throw new IllegalArgumentException("浓度网格不能为空");
        }

        int rows = concentrationGrid.length;
        int cols = concentrationGrid[0].length;

        if (pressureGrid == null || pressureGrid.length != rows || pressureGrid[0].length != cols) {
            throw new IllegalArgumentException("压力网格尺寸与浓度网格不匹配");
        }

        PointAnalysisResult[][] results = new PointAnalysisResult[rows][cols];

        // 统计变量
        int lowCount = 0, mediumCount = 0, highCount = 0, criticalCount = 0;
        double maxPressure = Double.NEGATIVE_INFINITY;
        double minPressure = Double.POSITIVE_INFINITY;
        double sumPressure = 0;
        double sumConcentration = 0;
        int maxPressureX = 0, maxPressureY = 0;

        // 遍历所有网格点进行分析
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                try {
                    PointAnalysisResult result = analyzePoint(
                            concentrationGrid, pressureGrid, temperature, relativeHumidity,
                            x, y, deltaX, deltaY, porosity, permeability);

                    results[y][x] = result;

                    // 更新统计
                    double pressure = result.getCrystallizationPressure();
                    sumPressure += pressure;
                    sumConcentration += result.getConcentration();

                    if (pressure > maxPressure) {
                        maxPressure = pressure;
                        maxPressureX = x;
                        maxPressureY = y;
                    }
                    if (pressure < minPressure) {
                        minPressure = pressure;
                    }

                    switch (result.getRiskLevel()) {
                        case LOW: lowCount++; break;
                        case MEDIUM: mediumCount++; break;
                        case HIGH: highCount++; break;
                        case CRITICAL: criticalCount++; break;
                    }
                } catch (Exception e) {
                    log.warn("分析点({}, {})失败: {}", x, y, e.getMessage());
                    results[y][x] = createErrorResult(x, y, e.getMessage());
                }
            }
        }

        int totalPoints = rows * cols;
        double avgPressure = sumPressure / totalPoints;
        double avgConcentration = sumConcentration / totalPoints;

        // 确定整体风险等级（取最高风险）
        CrystallizationPressureModel.RiskLevel overallRiskLevel;
        if (criticalCount > 0) {
            overallRiskLevel = CrystallizationPressureModel.RiskLevel.CRITICAL;
        } else if (highCount > 0) {
            overallRiskLevel = CrystallizationPressureModel.RiskLevel.HIGH;
        } else if (mediumCount > 0) {
            overallRiskLevel = CrystallizationPressureModel.RiskLevel.MEDIUM;
        } else {
            overallRiskLevel = CrystallizationPressureModel.RiskLevel.LOW;
        }

        // 统计信息
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalPoints", totalPoints);
        statistics.put("lowCount", lowCount);
        statistics.put("mediumCount", mediumCount);
        statistics.put("highCount", highCount);
        statistics.put("criticalCount", criticalCount);
        statistics.put("lowPercentage", (double) lowCount / totalPoints * 100);
        statistics.put("mediumPercentage", (double) mediumCount / totalPoints * 100);
        statistics.put("highPercentage", (double) highCount / totalPoints * 100);
        statistics.put("criticalPercentage", (double) criticalCount / totalPoints * 100);
        statistics.put("maxPressure", maxPressure);
        statistics.put("minPressure", minPressure);
        statistics.put("avgPressure", avgPressure);
        statistics.put("avgConcentration", avgConcentration);
        statistics.put("maxPressurePosition", new int[]{maxPressureX, maxPressureY});

        BatchAnalysisResult result = BatchAnalysisResult.builder()
                .analysisTime(LocalDateTime.now())
                .rows(rows)
                .cols(cols)
                .results(results)
                .statistics(statistics)
                .overallRiskLevel(overallRiskLevel)
                .build();

        log.info("批量分析完成 - 总点数: {}, 最高风险: {}, 最大压力: {} Pa (位置: {}, {})",
                totalPoints, overallRiskLevel.getDisplayName(),
                String.format("%.2f", maxPressure), maxPressureX, maxPressureY);

        return result;
    }

    /**
     * 预测未来72小时盐害发展趋势
     *
     * 使用时间步长迭代算法，基于以下假设预测未来趋势：
     * 1. 环境参数（温度、湿度）按给定速率变化
     * 2. 盐分运移速度在短时间内保持稳定
     * 3. 浓度变化由运移和结晶共同决定
     *
     * 预测算法：
     * C(t+Δt) = C(t) + v·∇C * Δt - J * Δt
     *
     * 其中：
     * - C(t): t时刻浓度
     * - v: 运移速度
     * - ∇C: 浓度梯度
     * - J: 结晶速率
     *
     * @param concentrationGrid 浓度网格，单位: mol/m³
     * @param pressureGrid 压力网格，单位: Pa
     * @param currentTemperature 当前温度，单位: ℃
     * @param currentHumidity 当前相对湿度，范围: 0~1
     * @param temperatureTrend 温度变化趋势，单位: ℃/hour
     * @param humidityTrend 湿度变化趋势，单位: 1/hour
     * @param x x坐标索引
     * @param y y坐标索引
     * @param deltaX x方向网格间距，单位: m
     * @param deltaY y方向网格间距，单位: m
     * @param porosity 孔隙度
     * @param permeability 渗透率，单位: m²
     * @return 趋势预测结果
     * @throws IllegalArgumentException 参数无效时抛出
     */
    public TrendPredictionResult predictTrend(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            double currentTemperature,
            double currentHumidity,
            double temperatureTrend,
            double humidityTrend,
            int x, int y,
            double deltaX, double deltaY,
            double porosity,
            double permeability) {

        log.info("开始盐害趋势预测 - 位置({}, {}), 预测时长: {}小时", x, y, predictionHours);

        // 参数校验
        validateGridParameters(concentrationGrid, pressureGrid, x, y, deltaX, deltaY);

        if (currentHumidity < 0 || currentHumidity > 1) {
            throw new IllegalArgumentException("当前湿度必须在[0, 1]范围内");
        }

        List<TrendDataPoint> trendData = new ArrayList<>();
        LocalDateTime startTime = LocalDateTime.now();

        // 初始化当前状态
        double currentConcentration = concentrationGrid[y][x] / 1000.0; // 转换为 mol/kg
        double temperature = currentTemperature;
        double humidity = currentHumidity;

        // 计算初始运移速度
        Vector3D velocity = saltMigrationModel.calculateMigrationVelocity(
                concentrationGrid, pressureGrid, porosity, permeability, 0, 0, x, y, deltaX, deltaY);

        // 计算浓度梯度（用于平流项）
        Vector3D concentrationGradient = calculateConcentrationGradient(
                concentrationGrid, x, y, deltaX, deltaY);

        double maxPressure = Double.NEGATIVE_INFINITY;
        int maxPressureHour = 0;
        double initialConcentration = currentConcentration;

        // 时间步长迭代
        for (int hour = 0; hour <= predictionHours; hour += TIME_STEP) {
            // 更新环境参数
            temperature = currentTemperature + temperatureTrend * hour;
            humidity = Math.max(0, Math.min(1, currentHumidity + humidityTrend * hour));

            // 1. 计算当前状态的结晶压力和饱和指数
            double crystallizationPressure = crystallizationPressureModel.calculateCrystallizationPressure(
                    currentConcentration, temperature, humidity);

            double saturationIndex = crystallizationPressureModel.calculateSaturationIndex(
                    currentConcentration, temperature);

            CrystallizationPressureModel.RiskLevel riskLevel =
                    crystallizationPressureModel.assessRiskLevel(crystallizationPressure, saturationIndex);

            // 记录最大压力
            if (crystallizationPressure > maxPressure) {
                maxPressure = crystallizationPressure;
                maxPressureHour = hour;
            }

            // 记录数据点
            TrendDataPoint dataPoint = TrendDataPoint.builder()
                    .hour(hour)
                    .concentration(currentConcentration)
                    .crystallizationPressure(crystallizationPressure)
                    .saturationIndex(saturationIndex)
                    .riskLevel(riskLevel)
                    .timestamp(startTime.plusHours(hour))
                    .build();

            trendData.add(dataPoint);

            // 2. 计算浓度变化（不包括最后一步）
            if (hour < predictionHours) {
                // 计算结晶速率
                double crystallizationRate = crystallizationPressureModel.calculateCrystallizationRate(
                        currentConcentration, temperature, humidity);

                // 平流项: v·∇C
                double advectionTerm = velocity.dot(concentrationGradient) / 1000.0; // 转换单位

                // 结晶消耗项（简化模型）
                double crystallizationConsumption = crystallizationRate * currentConcentration * 0.01;

                // 浓度更新
                double deltaC = (advectionTerm - crystallizationConsumption) * TIME_STEP;
                currentConcentration = Math.max(0, currentConcentration + deltaC);

                // 动态更新运移速度（每6小时更新一次）
                if (hour % 6 == 0 && hour > 0) {
                    // 更新浓度网格中当前点的值
                    double[][] updatedConcentrationGrid = concentrationGrid.clone();
                    updatedConcentrationGrid[y][x] = currentConcentration * 1000.0;

                    velocity = saltMigrationModel.calculateMigrationVelocity(
                            updatedConcentrationGrid, pressureGrid, porosity, permeability,
                            0, 0, x, y, deltaX, deltaY);

                    concentrationGradient = calculateConcentrationGradient(
                            updatedConcentrationGrid, x, y, deltaX, deltaY);
                }
            }

            log.debug("预测小时 {} - 温度: {:.2f}℃, 湿度: {:.2f}, 浓度: {:.4f} mol/kg, 压力: {:.2f} Pa, 风险: {}",
                    hour, temperature, humidity, currentConcentration, crystallizationPressure,
                    riskLevel.getDisplayName());
        }

        // 获取最终风险等级
        CrystallizationPressureModel.RiskLevel finalRiskLevel =
                trendData.get(trendData.size() - 1).getRiskLevel();

        // 计算浓度变化率
        double concentrationChangeRate = (currentConcentration - initialConcentration) / predictionHours;

        TrendPredictionResult result = TrendPredictionResult.builder()
                .x(x)
                .y(y)
                .predictionHours(predictionHours)
                .trendData(trendData)
                .finalRiskLevel(finalRiskLevel)
                .maxPressure(maxPressure)
                .maxPressureHour(maxPressureHour)
                .concentrationChangeRate(concentrationChangeRate)
                .build();

        log.info("趋势预测完成 - 位置({}, {}), 最终风险: {}, 最大压力: {} Pa (第{}小时)",
                x, y, finalRiskLevel.getDisplayName(),
                String.format("%.2f", maxPressure), maxPressureHour);

        return result;
    }

    /**
     * 批量趋势预测
     *
     * 对多个位置进行未来72小时盐害发展趋势预测
     *
     * @param concentrationGrid 浓度网格
     * @param pressureGrid 压力网格
     * @param currentTemperature 当前温度
     * @param currentHumidity 当前湿度
     * @param temperatureTrend 温度趋势
     * @param humidityTrend 湿度趋势
     * @param positions 位置列表，每个元素为[x, y]数组
     * @param deltaX x方向网格间距
     * @param deltaY y方向网格间距
     * @param porosity 孔隙度
     * @param permeability 渗透率
     * @return 趋势预测结果列表
     */
    public List<TrendPredictionResult> predictBatchTrend(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            double currentTemperature,
            double currentHumidity,
            double temperatureTrend,
            double humidityTrend,
            List<int[]> positions,
            double deltaX, double deltaY,
            double porosity,
            double permeability) {

        log.info("开始批量趋势预测 - 位置数: {}", positions.size());

        List<TrendPredictionResult> results = new ArrayList<>();

        for (int[] pos : positions) {
            try {
                TrendPredictionResult result = predictTrend(
                        concentrationGrid, pressureGrid,
                        currentTemperature, currentHumidity,
                        temperatureTrend, humidityTrend,
                        pos[0], pos[1], deltaX, deltaY,
                        porosity, permeability);
                results.add(result);
            } catch (Exception e) {
                log.error("位置({}, {})趋势预测失败: {}", pos[0], pos[1], e.getMessage());
            }
        }

        log.info("批量趋势预测完成 - 成功: {}/{}", results.size(), positions.size());

        return results;
    }

    /**
     * 综合风险评估
     *
     * 基于多因素进行综合风险评估，包括：
     * 1. 结晶压力大小
     * 2. 饱和指数
     * 3. 盐分运移速度
     * 4. 浓度变化趋势
     *
     * @param pointResult 单点分析结果
     * @param trendResult 趋势预测结果
     * @return 综合风险等级
     */
    public CrystallizationPressureModel.RiskLevel assessComprehensiveRisk(
            PointAnalysisResult pointResult,
            TrendPredictionResult trendResult) {

        if (pointResult == null) {
            throw new IllegalArgumentException("单点分析结果不能为空");
        }

        // 基础风险等级
        CrystallizationPressureModel.RiskLevel baseLevel = pointResult.getRiskLevel();

        if (trendResult == null) {
            return baseLevel;
        }

        // 如果趋势显示风险升高，则升级
        double pressureIncreaseRate = (trendResult.getMaxPressure() - pointResult.getCrystallizationPressure())
                / pointResult.getCrystallizationPressure();

        boolean isRiskIncreasing = trendResult.getFinalRiskLevel().compareTo(baseLevel) > 0
                || pressureIncreaseRate > 0.3;

        boolean isRiskCritical = trendResult.getMaxPressure() >= 5e6
                || trendResult.getFinalRiskLevel() == CrystallizationPressureModel.RiskLevel.CRITICAL;

        if (isRiskCritical) {
            return CrystallizationPressureModel.RiskLevel.CRITICAL;
        } else if (isRiskIncreasing && baseLevel != CrystallizationPressureModel.RiskLevel.CRITICAL) {
            // 风险升高，升级一级
            switch (baseLevel) {
                case LOW: return CrystallizationPressureModel.RiskLevel.MEDIUM;
                case MEDIUM: return CrystallizationPressureModel.RiskLevel.HIGH;
                case HIGH: return CrystallizationPressureModel.RiskLevel.CRITICAL;
                default: return baseLevel;
            }
        }

        return baseLevel;
    }

    /**
     * 计算浓度梯度
     *
     * @param concentrationGrid 浓度网格
     * @param x x索引
     * @param y y索引
     * @param deltaX x间距
     * @param deltaY y间距
     * @return 浓度梯度向量
     */
    private Vector3D calculateConcentrationGradient(
            double[][] concentrationGrid,
            int x, int y,
            double deltaX, double deltaY) {

        int rows = concentrationGrid.length;
        int cols = concentrationGrid[0].length;

        double dCdx;
        double dCdy;

        // x方向梯度（中心差分）
        if (x == 0) {
            dCdx = (concentrationGrid[y][x + 1] - concentrationGrid[y][x]) / deltaX;
        } else if (x == cols - 1) {
            dCdx = (concentrationGrid[y][x] - concentrationGrid[y][x - 1]) / deltaX;
        } else {
            dCdx = (concentrationGrid[y][x + 1] - concentrationGrid[y][x - 1]) / (2 * deltaX);
        }

        // y方向梯度（中心差分）
        if (y == 0) {
            dCdy = (concentrationGrid[y + 1][x] - concentrationGrid[y][x]) / deltaY;
        } else if (y == rows - 1) {
            dCdy = (concentrationGrid[y][x] - concentrationGrid[y - 1][x]) / deltaY;
        } else {
            dCdy = (concentrationGrid[y + 1][x] - concentrationGrid[y - 1][x]) / (2 * deltaY);
        }

        return Vector3D.of(dCdx, dCdy, 0);
    }

    /**
     * 验证网格参数
     */
    private void validateGridParameters(
            double[][] concentrationGrid,
            double[][] pressureGrid,
            int x, int y,
            double deltaX, double deltaY) {

        if (concentrationGrid == null || pressureGrid == null) {
            throw new IllegalArgumentException("浓度网格和压力网格不能为null");
        }

        int rows = concentrationGrid.length;
        if (rows == 0 || concentrationGrid[0].length == 0) {
            throw new IllegalArgumentException("浓度网格不能为空");
        }

        int cols = concentrationGrid[0].length;

        if (x < 0 || x >= cols) {
            throw new IllegalArgumentException(String.format("x坐标 %d 超出范围 [0, %d)", x, cols));
        }
        if (y < 0 || y >= rows) {
            throw new IllegalArgumentException(String.format("y坐标 %d 超出范围 [0, %d)", y, rows));
        }

        if (deltaX <= 0 || deltaY <= 0) {
            throw new IllegalArgumentException("网格间距必须为正数");
        }
    }

    /**
     * 创建错误分析结果
     */
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

    /**
     * 获取预测小时数配置
     *
     * @return 预测小时数
     */
    public int getPredictionHours() {
        return predictionHours;
    }
}
