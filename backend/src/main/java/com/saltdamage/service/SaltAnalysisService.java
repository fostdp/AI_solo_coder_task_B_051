package com.saltdamage.service;

import com.saltdamage.algorithm.CrystallizationPressureModel;
import com.saltdamage.algorithm.SaltDamageAnalysisService;
import com.saltdamage.algorithm.SaltMigrationModel;
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
import java.util.List;
import java.util.Map;

/**
 * 盐害分析业务服务
 *
 * 本服务作为业务层，调用核心算法模块提供的盐害分析能力，
 * 为上层业务（如Controller、定时任务等）提供更易用的业务接口。
 *
 * 主要功能：
 * 1. 单点盐害分析（面向单个监测点）
 * 2. 区域盐害分析（面向整个监测区域）
 * 3. 趋势预测分析（预测未来72小时盐害发展）
 * 4. 批量数据处理（处理历史监测数据）
 * 5. 风险告警评估（判断是否需要触发告警）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaltAnalysisService {

    private final SaltDamageAnalysisService saltDamageAnalysisService;
    private final SaltMigrationModel saltMigrationModel;
    private final CrystallizationPressureModel crystallizationPressureModel;

    // ==================== 算法参数配置 ====================

    @Value("${algorithm.salt-migration.porosity:0.35}")
    private double defaultPorosity;

    @Value("${algorithm.salt-migration.permeability:1.0e-12}")
    private double defaultPermeability;

    @Value("${algorithm.salt-migration.viscosity:0.001002}")
    private double defaultViscosity;

    @Value("${algorithm.salt-migration.diffusion-coeff:1.33e-9}")
    private double defaultDiffusionCoeff;

    /**
     * 网格间距（默认0.1米）
     */
    private static final double DEFAULT_DELTA = 0.1;

    /**
     * 盐害分析请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaltAnalysisRequest {
        /**
         * 监测点ID
         */
        private String monitorId;

        /**
         * 盐浓度，单位: g/L
         */
        private double concentration;

        /**
         * 温度，单位: ℃
         */
        private double temperature;

        /**
         * 相对湿度，范围: 0~100
         */
        private double relativeHumidity;

        /**
         * 孔隙度（可选）
         */
        private Double porosity;

        /**
         * 渗透率（可选），单位: m²
         */
        private Double permeability;
    }

    /**
     * 盐害分析响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaltAnalysisResponse {
        /**
         * 监测点ID
         */
        private String monitorId;

        /**
         * 分析时间
         */
        private LocalDateTime analysisTime;

        /**
         * 结晶压力，单位: MPa
         */
        private double crystallizationPressure;

        /**
         * 饱和指数
         */
        private double saturationIndex;

        /**
         * 盐分运移速度，单位: m/s
         */
        private double migrationVelocity;

        /**
         * 运移速度向量
         */
        private Vector3D velocityVector;

        /**
         * 结晶速率（相对值）
         */
        private double crystallizationRate;

        /**
         * 风险等级
         */
        private String riskLevel;

        /**
         * 风险等级描述
         */
        private String riskDescription;

        /**
         * 是否需要告警
         */
        private boolean alarmRequired;

        /**
         * 建议措施
         */
        private String suggestion;
    }

    /**
     * 趋势预测请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPredictionRequest {
        /**
         * 监测点ID
         */
        private String monitorId;

        /**
         * 当前浓度，单位: g/L
         */
        private double currentConcentration;

        /**
         * 当前温度，单位: ℃
         */
        private double currentTemperature;

        /**
         * 当前湿度，范围: 0~100
         */
        private double currentHumidity;

        /**
         * 温度变化趋势，单位: ℃/hour
         */
        private double temperatureTrend;

        /**
         * 湿度变化趋势，单位: %/hour
         */
        private double humidityTrend;

        /**
         * 预测小时数（默认72）
         */
        private Integer predictionHours;
    }

    /**
     * 趋势预测响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPredictionResponse {
        /**
         * 监测点ID
         */
        private String monitorId;

        /**
         * 预测时长（小时）
         */
        private int predictionHours;

        /**
         * 趋势数据
         */
        private List<TrendDataItem> trendData;

        /**
         * 最终风险等级
         */
        private String finalRiskLevel;

        /**
         * 最大结晶压力，单位: MPa
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

        /**
         * 风险趋势评估
         */
        private String riskTrendAssessment;
    }

    /**
     * 趋势数据项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendDataItem {
        /**
         * 预测小时
         */
        private int hour;

        /**
         * 预测时间
         */
        private LocalDateTime timestamp;

        /**
         * 浓度，单位: mol/kg
         */
        private double concentration;

        /**
         * 结晶压力，单位: MPa
         */
        private double crystallizationPressure;

        /**
         * 饱和指数
         */
        private double saturationIndex;

        /**
         * 风险等级
         */
        private String riskLevel;
    }

    /**
     * 区域分析请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AreaAnalysisRequest {
        /**
         * 区域ID
         */
        private String areaId;

        /**
         * 浓度网格，单位: g/L
         */
        private double[][] concentrationGrid;

        /**
         * 温度，单位: ℃
         */
        private double temperature;

        /**
         * 相对湿度，范围: 0~100
         */
        private double relativeHumidity;

        /**
         * 网格间距，单位: m（默认0.1）
         */
        private Double gridSpacing;
    }

    /**
     * 区域分析响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AreaAnalysisResponse {
        /**
         * 区域ID
         */
        private String areaId;

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
         * 各点分析结果
         */
        private double[][] pressureGrid;

        /**
         * 风险等级网格
         */
        private String[][] riskGrid;

        /**
         * 统计信息
         */
        private Map<String, Object> statistics;

        /**
         * 整体风险等级
         */
        private String overallRiskLevel;

        /**
         * 最高风险点位置
         */
        private int[] highestRiskPosition;

        /**
         * 最高压力值，单位: MPa
         */
        private double highestPressure;
    }

    // ==================== 业务方法 ====================

    /**
     * 单点盐害分析
     *
     * 对单个监测点进行盐害分析，返回综合分析结果和风险评估。
     *
     * @param request 分析请求
     * @return 分析响应
     * @throws IllegalArgumentException 参数无效时抛出
     */
    public SaltAnalysisResponse analyzeSinglePoint(SaltAnalysisRequest request) {
        log.info("开始单点盐害分析 - 监测点: {}", request.getMonitorId());

        // 参数校验
        validateAnalysisRequest(request);

        try {
            // 转换参数
            double concentration = request.getConcentration();
            double temperature = request.getTemperature();
            double relativeHumidity = request.getRelativeHumidity() / 100.0; // 转换为0~1

            double porosity = request.getPorosity() != null ? request.getPorosity() : defaultPorosity;
            double permeability = request.getPermeability() != null ?
                    request.getPermeability() : defaultPermeability;

            // 转换浓度单位: g/L -> mol/m³
            // 1 g/L = 1 kg/m³，除以摩尔质量得到 mol/m³
            double molarConcentration = crystallizationPressureModel.convertToMolarConcentration(
                    concentration, 1000.0);
            double concentrationGridValue = molarConcentration * 1000.0; // mol/m³

            // 创建1x1网格用于单点分析
            double[][] concentrationGrid = {{concentrationGridValue}};
            double[][] pressureGrid = {{101325.0}}; // 标准大气压

            // 调用核心算法进行分析
            SaltDamageAnalysisService.PointAnalysisResult result =
                    saltDamageAnalysisService.analyzePoint(
                            concentrationGrid, pressureGrid,
                            temperature, relativeHumidity,
                            0, 0, DEFAULT_DELTA, DEFAULT_DELTA,
                            porosity, permeability);

            // 构建响应
            SaltAnalysisResponse response = SaltAnalysisResponse.builder()
                    .monitorId(request.getMonitorId())
                    .analysisTime(result.getAnalysisTime())
                    .crystallizationPressure(result.getCrystallizationPressure() / 1e6) // 转换为MPa
                    .saturationIndex(result.getSaturationIndex())
                    .migrationVelocity(result.getVelocityMagnitude())
                    .velocityVector(result.getMigrationVelocity())
                    .crystallizationRate(result.getCrystallizationRate())
                    .riskLevel(result.getRiskLevel().name())
                    .riskDescription(result.getRiskLevel().getDescription())
                    .alarmRequired(isAlarmRequired(result.getRiskLevel()))
                    .suggestion(generateSuggestion(result.getRiskLevel(), result.getCrystallizationPressure()))
                    .build();

            log.info("单点盐害分析完成 - 监测点: {}, 风险等级: {}, 压力: {:.4f} MPa",
                    request.getMonitorId(), response.getRiskLevel(), response.getCrystallizationPressure());

            return response;

        } catch (Exception e) {
            log.error("单点盐害分析失败 - 监测点: {}, 错误: {}", request.getMonitorId(), e.getMessage(), e);
            throw new RuntimeException("盐害分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量单点盐害分析
     *
     * 对多个监测点进行批量盐害分析。
     *
     * @param requests 分析请求列表
     * @return 分析响应列表
     */
    public List<SaltAnalysisResponse> analyzeBatchPoints(List<SaltAnalysisRequest> requests) {
        log.info("开始批量盐害分析 - 监测点数量: {}", requests.size());

        List<SaltAnalysisResponse> responses = requests.stream()
                .map(this::analyzeSinglePoint)
                .toList();

        log.info("批量盐害分析完成 - 成功: {}/{}", responses.size(), requests.size());

        return responses;
    }

    /**
     * 区域盐害分析
     *
     * 对整个区域进行网格分析，返回各点的压力分布和风险等级分布。
     *
     * @param request 区域分析请求
     * @return 区域分析响应
     */
    public AreaAnalysisResponse analyzeArea(AreaAnalysisRequest request) {
        log.info("开始区域盐害分析 - 区域: {}", request.getAreaId());

        // 参数校验
        if (request.getConcentrationGrid() == null || request.getConcentrationGrid().length == 0) {
            throw new IllegalArgumentException("浓度网格不能为空");
        }

        int rows = request.getConcentrationGrid().length;
        int cols = request.getConcentrationGrid()[0].length;

        double temperature = request.getTemperature();
        double relativeHumidity = request.getRelativeHumidity() / 100.0;
        double delta = request.getGridSpacing() != null ? request.getGridSpacing() : DEFAULT_DELTA;

        try {
            // 转换浓度单位: g/L -> mol/m³
            double[][] concentrationGrid = new double[rows][cols];
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    double gL = request.getConcentrationGrid()[y][x];
                    double molar = crystallizationPressureModel.convertToMolarConcentration(gL, 1000.0);
                    concentrationGrid[y][x] = molar * 1000.0; // mol/m³
                }
            }

            // 创建压力网格（初始为标准大气压）
            double[][] pressureGrid = new double[rows][cols];
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    pressureGrid[y][x] = 101325.0; // 标准大气压
                }
            }

            // 调用核心算法进行批量分析
            SaltDamageAnalysisService.BatchAnalysisResult batchResult =
                    saltDamageAnalysisService.analyzeBatch(
                            concentrationGrid, pressureGrid,
                            temperature, relativeHumidity,
                            delta, delta,
                            defaultPorosity, defaultPermeability);

            // 提取压力和风险等级网格
            double[][] pressureResult = new double[rows][cols];
            String[][] riskResult = new String[rows][cols];

            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    SaltDamageAnalysisService.PointAnalysisResult point = batchResult.getResults()[y][x];
                    pressureResult[y][x] = point.getCrystallizationPressure() / 1e6; // MPa
                    riskResult[y][x] = point.getRiskLevel().name();
                }
            }

            // 获取最高压力点位置
            int[] maxPos = (int[]) batchResult.getStatistics().get("maxPressurePosition");
            double maxPressure = (double) batchResult.getStatistics().get("maxPressure") / 1e6;

            // 构建响应
            AreaAnalysisResponse response = AreaAnalysisResponse.builder()
                    .areaId(request.getAreaId())
                    .analysisTime(batchResult.getAnalysisTime())
                    .rows(rows)
                    .cols(cols)
                    .pressureGrid(pressureResult)
                    .riskGrid(riskResult)
                    .statistics(batchResult.getStatistics())
                    .overallRiskLevel(batchResult.getOverallRiskLevel().name())
                    .highestRiskPosition(maxPos)
                    .highestPressure(maxPressure)
                    .build();

            log.info("区域盐害分析完成 - 区域: {}, 整体风险: {}, 最高压力: {:.4f} MPa",
                    request.getAreaId(), response.getOverallRiskLevel(), maxPressure);

            return response;

        } catch (Exception e) {
            log.error("区域盐害分析失败 - 区域: {}, 错误: {}", request.getAreaId(), e.getMessage(), e);
            throw new RuntimeException("区域盐害分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 趋势预测分析
     *
     * 预测单个监测点未来72小时的盐害发展趋势。
     *
     * @param request 趋势预测请求
     * @return 趋势预测响应
     */
    public TrendPredictionResponse predictTrend(TrendPredictionRequest request) {
        log.info("开始盐害趋势预测 - 监测点: {}", request.getMonitorId());

        // 参数校验
        if (request.getCurrentHumidity() < 0 || request.getCurrentHumidity() > 100) {
            throw new IllegalArgumentException("相对湿度必须在[0, 100]范围内");
        }

        try {
            // 转换参数
            double currentConcentration = request.getCurrentConcentration();
            double currentTemperature = request.getCurrentTemperature();
            double currentHumidity = request.getCurrentHumidity() / 100.0;
            double temperatureTrend = request.getTemperatureTrend();
            double humidityTrend = request.getHumidityTrend() / 100.0; // %/hour -> 1/hour

            // 转换浓度单位: g/L -> mol/m³
            double molarConcentration = crystallizationPressureModel.convertToMolarConcentration(
                    currentConcentration, 1000.0);
            double concentrationGridValue = molarConcentration * 1000.0;

            // 创建1x1网格
            double[][] concentrationGrid = {{concentrationGridValue}};
            double[][] pressureGrid = {{101325.0}};

            // 调用核心算法进行趋势预测
            SaltDamageAnalysisService.TrendPredictionResult result =
                    saltDamageAnalysisService.predictTrend(
                            concentrationGrid, pressureGrid,
                            currentTemperature, currentHumidity,
                            temperatureTrend, humidityTrend,
                            0, 0, DEFAULT_DELTA, DEFAULT_DELTA,
                            defaultPorosity, defaultPermeability);

            // 转换趋势数据
            List<TrendDataItem> trendData = result.getTrendData().stream()
                    .map(point -> TrendDataItem.builder()
                            .hour(point.getHour())
                            .timestamp(point.getTimestamp())
                            .concentration(point.getConcentration())
                            .crystallizationPressure(point.getCrystallizationPressure() / 1e6)
                            .saturationIndex(point.getSaturationIndex())
                            .riskLevel(point.getRiskLevel().name())
                            .build())
                    .toList();

            // 评估风险趋势
            String riskTrendAssessment = assessRiskTrend(result);

            // 构建响应
            TrendPredictionResponse response = TrendPredictionResponse.builder()
                    .monitorId(request.getMonitorId())
                    .predictionHours(result.getPredictionHours())
                    .trendData(trendData)
                    .finalRiskLevel(result.getFinalRiskLevel().name())
                    .maxPressure(result.getMaxPressure() / 1e6)
                    .maxPressureHour(result.getMaxPressureHour())
                    .concentrationChangeRate(result.getConcentrationChangeRate())
                    .riskTrendAssessment(riskTrendAssessment)
                    .build();

            log.info("盐害趋势预测完成 - 监测点: {}, 最终风险: {}, 最大压力: {:.4f} MPa (第{}小时)",
                    request.getMonitorId(), response.getFinalRiskLevel(),
                    response.getMaxPressure(), response.getMaxPressureHour());

            return response;

        } catch (Exception e) {
            log.error("盐害趋势预测失败 - 监测点: {}, 错误: {}", request.getMonitorId(), e.getMessage(), e);
            throw new RuntimeException("盐害趋势预测失败: " + e.getMessage(), e);
        }
    }

    /**
     * 计算盐分运移速度
     *
     * 便捷方法，直接计算指定条件下的盐分运移速度。
     *
     * @param concentration 盐浓度，单位: g/L
     * @param pressure 压力，单位: Pa
     * @param temperature 温度，单位: ℃
     * @param porosity 孔隙度
     * @param permeability 渗透率，单位: m²
     * @return 运移速度向量，单位: m/s
     */
    public Vector3D calculateMigrationVelocity(
            double concentration, double pressure, double temperature,
            double porosity, double permeability) {

        // 转换浓度单位
        double molar = crystallizationPressureModel.convertToMolarConcentration(concentration, 1000.0);
        double concValue = molar * 1000.0; // mol/m³

        // 创建小网格（3x3）用于计算梯度
        double[][] concentrationGrid = {
                {concValue * 0.9, concValue * 0.95, concValue},
                {concValue * 0.95, concValue, concValue * 1.05},
                {concValue, concValue * 1.05, concValue * 1.1}
        };

        double[][] pressureGrid = {
                {pressure * 1.02, pressure * 1.01, pressure},
                {pressure * 1.01, pressure, pressure * 0.99},
                {pressure, pressure * 0.99, pressure * 0.98}
        };

        return saltMigrationModel.calculateMigrationVelocity(
                concentrationGrid, pressureGrid,
                porosity, permeability, defaultViscosity, defaultDiffusionCoeff,
                1, 1, DEFAULT_DELTA, DEFAULT_DELTA);
    }

    // ==================== 辅助方法 ====================

    /**
     * 验证分析请求参数
     */
    private void validateAnalysisRequest(SaltAnalysisRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("分析请求不能为空");
        }
        if (request.getMonitorId() == null || request.getMonitorId().trim().isEmpty()) {
            throw new IllegalArgumentException("监测点ID不能为空");
        }
        if (request.getConcentration() < 0) {
            throw new IllegalArgumentException("盐浓度不能为负数");
        }
        if (request.getRelativeHumidity() < 0 || request.getRelativeHumidity() > 100) {
            throw new IllegalArgumentException("相对湿度必须在[0, 100]范围内");
        }
    }

    /**
     * 判断是否需要触发告警
     *
     * @param riskLevel 风险等级
     * @return 是否需要告警
     */
    private boolean isAlarmRequired(CrystallizationPressureModel.RiskLevel riskLevel) {
        return riskLevel == CrystallizationPressureModel.RiskLevel.HIGH
                || riskLevel == CrystallizationPressureModel.RiskLevel.CRITICAL;
    }

    /**
     * 生成处理建议
     *
     * @param riskLevel 风险等级
     * @param pressure 结晶压力
     * @return 建议措施
     */
    private String generateSuggestion(CrystallizationPressureModel.RiskLevel riskLevel, double pressure) {
        return switch (riskLevel) {
            case LOW -> "当前盐害风险较低，建议继续保持常规监测频率。";
            case MEDIUM -> "存在一定盐害风险，建议加强监测频率，关注环境温湿度变化。";
            case HIGH -> "盐害风险较高，建议立即采取措施：1. 调整环境湿度至75%以下；" +
                    "2. 检查通风系统；3. 考虑使用脱盐处理。";
            case CRITICAL -> "盐害风险极高！建议立即启动应急预案：1. 紧急除湿处理；" +
                    "2. 组织专家现场评估；3. 采取临时加固措施防止文物损坏。";
        };
    }

    /**
     * 评估风险趋势
     *
     * @param result 趋势预测结果
     * @return 风险趋势评估描述
     */
    private String assessRiskTrend(SaltDamageAnalysisService.TrendPredictionResult result) {
        double changeRate = result.getConcentrationChangeRate();
        CrystallizationPressureModel.RiskLevel finalRisk = result.getFinalRiskLevel();

        if (changeRate > 0.01) {
            return "浓度呈显著上升趋势，预计" + result.getMaxPressureHour() +
                    "小时后达到最大压力 " + String.format("%.4f", result.getMaxPressure() / 1e6) +
                    " MPa，风险等级将升高至" + finalRisk.getDisplayName() + "。";
        } else if (changeRate < -0.01) {
            return "浓度呈下降趋势，盐害风险正在缓解，最终风险等级为" +
                    finalRisk.getDisplayName() + "。";
        } else {
            return "浓度基本稳定，盐害风险维持在" + finalRisk.getDisplayName() + "水平。";
        }
    }

    /**
     * 获取风险等级阈值
     *
     * @return 阈值数组 [低, 中, 高, 极高]，单位: MPa
     */
    public double[] getRiskThresholds() {
        return crystallizationPressureModel.getRiskThresholds();
    }

    /**
     * 获取默认算法参数
     *
     * @return 参数映射
     */
    public Map<String, Object> getDefaultAlgorithmParameters() {
        return Map.of(
                "porosity", defaultPorosity,
                "permeability", defaultPermeability,
                "viscosity", defaultViscosity,
                "diffusionCoeff", defaultDiffusionCoeff,
                "predictionHours", saltDamageAnalysisService.getPredictionHours()
        );
    }
}
