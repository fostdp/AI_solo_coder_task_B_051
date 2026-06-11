package com.saltdamage.service;

import com.saltdamage.algorithm.SaltMigrationModel;
import com.saltdamage.dto.AnalysisResultDTO;
import com.saltdamage.dto.AnalysisRunRequest;
import com.saltdamage.dto.PredictionDTO;
import com.saltdamage.entity.AnalysisResult;
import com.saltdamage.entity.PredictionData;
import com.saltdamage.entity.SensorData;
import com.saltdamage.repository.AnalysisResultRepository;
import com.saltdamage.repository.PredictionDataRepository;
import com.saltdamage.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisResultRepository analysisResultRepository;
    private final PredictionDataRepository predictionDataRepository;
    private final SensorDataRepository sensorDataRepository;
    private final SaltMigrationModel saltMigrationModel;

    public Page<AnalysisResultDTO> getAnalysisResults(Long tombId, Long chamberId, Pageable pageable) {
        log.info("查询分析结果, tombId: {}, chamberId: {}", tombId, chamberId);

        Page<AnalysisResult> resultPage;
        if (chamberId != null) {
            resultPage = analysisResultRepository.findByChamberIdOrderByAnalysisTimeDesc(chamberId, pageable);
        } else if (tombId != null) {
            resultPage = analysisResultRepository.findByTombIdOrderByAnalysisTimeDesc(tombId, pageable);
        } else {
            resultPage = analysisResultRepository.findAll(pageable);
        }

        return resultPage.map(this::convertToResultDTO);
    }

    public AnalysisResultDTO getLatestAnalysisResult(Long tombId, Long chamberId) {
        log.info("获取最新分析结果, tombId: {}, chamberId: {}", tombId, chamberId);

        if (chamberId != null) {
            return analysisResultRepository.findFirstByChamberIdOrderByAnalysisTimeDesc(chamberId)
                    .map(this::convertToResultDTO)
                    .orElse(null);
        } else if (tombId != null) {
            return analysisResultRepository.findFirstByTombIdOrderByAnalysisTimeDesc(tombId)
                    .map(this::convertToResultDTO)
                    .orElse(null);
        }

        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public AnalysisResultDTO runAnalysis(AnalysisRunRequest request) {
        log.info("执行盐害分析, tombId: {}, chamberId: {}, analysisType: {}",
                request.getTombId(), request.getChamberId(), request.getAnalysisType());

        LocalDateTime startTime = LocalDateTime.of(LocalDate.now().minusDays(7), LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.now();

        List<SensorData> dataList;
        if (request.getChamberId() != null) {
            dataList = sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                    request.getChamberId(), startTime, endTime);
        } else if (request.getTombId() != null) {
            dataList = sensorDataRepository.findByTombIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                    request.getTombId(), startTime, endTime);
        } else {
            throw new IllegalArgumentException("tombId和chamberId不能同时为空");
        }

        if (dataList.isEmpty()) {
            throw new IllegalArgumentException("没有足够的数据进行分析");
        }

        BigDecimal avgSaltConcentration = dataList.stream()
                .filter(d -> d.getSaltConcentration() != null)
                .map(SensorData::getSaltConcentration)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dataList.stream()
                        .filter(d -> d.getSaltConcentration() != null).count()), 4, BigDecimal.ROUND_HALF_UP);

        BigDecimal riskScore = calculateRiskScore(avgSaltConcentration);
        String saltDamageLevel = determineSaltDamageLevel(riskScore);

        String conclusion = generateConclusion(avgSaltConcentration, riskScore, saltDamageLevel, dataList.size());
        String suggestion = generateSuggestion(saltDamageLevel, avgSaltConcentration);

        AnalysisResult result = new AnalysisResult();
        result.setTombId(request.getTombId());
        result.setChamberId(request.getChamberId());
        result.setAnalysisType(request.getAnalysisType() != null ? request.getAnalysisType() : "SALT_DAMAGE");
        result.setSaltDamageLevel(saltDamageLevel);
        result.setRiskScore(riskScore);
        result.setConclusion(conclusion);
        result.setSuggestion(suggestion);
        result.setAnalysisTime(LocalDateTime.now());

        result = analysisResultRepository.save(result);
        log.info("分析结果已保存, id: {}", result.getId());

        asyncGeneratePrediction(request.getTombId(), request.getChamberId(), dataList);

        return convertToResultDTO(result);
    }

    public List<PredictionDTO> getPredictions(Long tombId, Long chamberId, int days) {
        log.info("获取预测数据, tombId: {}, chamberId: {}, days: {}", tombId, chamberId, days);

        LocalDateTime startTime = LocalDateTime.now();

        List<PredictionData> predictions;
        if (chamberId != null) {
            predictions = predictionDataRepository
                    .findByChamberIdAndPredictTimeAfterOrderByPredictTimeDesc(chamberId, startTime);
        } else if (tombId != null) {
            predictions = predictionDataRepository
                    .findByTombIdAndPredictTimeAfterOrderByPredictTimeDesc(tombId, startTime);
        } else {
            throw new IllegalArgumentException("tombId和chamberId不能同时为空");
        }

        return predictions.stream()
                .limit(days * 24L)
                .map(this::convertToPredictionDTO)
                .collect(Collectors.toList());
    }

    public PredictionDTO getLatestPrediction(Long tombId, Long chamberId) {
        log.info("获取最新预测数据, tombId: {}, chamberId: {}", tombId, chamberId);

        if (chamberId != null) {
            return predictionDataRepository.findFirstByChamberIdOrderByPredictTimeDesc(chamberId)
                    .map(this::convertToPredictionDTO)
                    .orElse(null);
        } else if (tombId != null) {
            return predictionDataRepository.findFirstByTombIdOrderByPredictTimeDesc(tombId)
                    .map(this::convertToPredictionDTO)
                    .orElse(null);
        }

        return null;
    }

    @Async
    @Transactional(rollbackFor = Exception.class)
    public void asyncGeneratePrediction(Long tombId, Long chamberId, List<SensorData> historicalData) {
        log.info("开始异步生成预测数据, tombId: {}, chamberId: {}", tombId, chamberId);

        try {
            for (int i = 1; i <= 24; i++) {
                LocalDateTime predictTime = LocalDateTime.now().plusHours(i);

                BigDecimal predictedSalt = predictNextSaltConcentration(
                        historicalData.stream()
                                .map(SensorData::getSaltConcentration)
                                .toList(),
                        i
                );

                BigDecimal avgTemp = historicalData.stream()
                        .filter(d -> d.getTemperature() != null)
                        .map(SensorData::getTemperature)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(historicalData.size()), 4, BigDecimal.ROUND_HALF_UP);

                BigDecimal avgHumidity = historicalData.stream()
                        .filter(d -> d.getHumidity() != null)
                        .map(SensorData::getHumidity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(historicalData.size()), 4, BigDecimal.ROUND_HALF_UP);

                String riskLevel = determineRiskLevel(predictedSalt);

                PredictionData prediction = new PredictionData();
                prediction.setTombId(tombId);
                prediction.setChamberId(chamberId);
                prediction.setPredictTime(predictTime);
                prediction.setPredictedSaltConcentration(predictedSalt);
                prediction.setPredictedTemperature(avgTemp);
                prediction.setPredictedHumidity(avgHumidity);
                prediction.setRiskLevel(riskLevel);

                predictionDataRepository.save(prediction);
            }

            log.info("预测数据生成完成, tombId: {}, chamberId: {}", tombId, chamberId);
        } catch (Exception e) {
            log.error("生成预测数据失败", e);
        }
    }

    private BigDecimal predictNextSaltConcentration(List<BigDecimal> historicalData, int steps) {
        if (historicalData == null || historicalData.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<BigDecimal> validData = historicalData.stream()
                .filter(d -> d != null)
                .toList();

        if (validData.isEmpty()) {
            return BigDecimal.ZERO;
        }

        int size = validData.size();
        int windowSize = Math.min(size, 10);
        List<BigDecimal> recentData = validData.subList(size - windowSize, size);

        BigDecimal sum = recentData.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(recentData.size()), 4, BigDecimal.ROUND_HALF_UP);

        if (size >= 2) {
            BigDecimal first = recentData.get(0);
            BigDecimal last = recentData.get(recentData.size() - 1);
            BigDecimal trend = last.subtract(first)
                    .divide(BigDecimal.valueOf(recentData.size() - 1), 4, BigDecimal.ROUND_HALF_UP);

            BigDecimal prediction = avg.add(trend.multiply(BigDecimal.valueOf(steps * 0.1)));
            return prediction.max(BigDecimal.ZERO);
        }

        return avg;
    }

    private BigDecimal calculateRiskScore(BigDecimal avgSaltConcentration) {
        BigDecimal threshold = new BigDecimal("5");
        BigDecimal ratio = avgSaltConcentration.divide(threshold, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal score = ratio.multiply(new BigDecimal("50"));
        return score.min(new BigDecimal("100"));
    }

    private String determineSaltDamageLevel(BigDecimal riskScore) {
        if (riskScore.compareTo(new BigDecimal("75")) >= 0) {
            return "SEVERE";
        } else if (riskScore.compareTo(new BigDecimal("50")) >= 0) {
            return "MODERATE";
        } else if (riskScore.compareTo(new BigDecimal("25")) >= 0) {
            return "MILD";
        } else {
            return "NONE";
        }
    }

    private String determineRiskLevel(BigDecimal predictedSalt) {
        if (predictedSalt.compareTo(new BigDecimal("5")) >= 0) {
            return "HIGH";
        } else if (predictedSalt.compareTo(new BigDecimal("3")) >= 0) {
            return "MEDIUM";
        } else if (predictedSalt.compareTo(new BigDecimal("1")) >= 0) {
            return "LOW";
        } else {
            return "NONE";
        }
    }

    private String generateConclusion(BigDecimal avgSalt, BigDecimal riskScore, String level, int dataCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("基于最近7天的").append(dataCount).append("条监测数据分析：\n");
        sb.append("平均盐离子浓度为 ").append(avgSalt).append(" mg/cm²，\n");
        sb.append("风险评分为 ").append(riskScore).append(" 分，\n");
        sb.append("盐害等级为 ").append(level).append("。\n");

        switch (level) {
            case "SEVERE" -> sb.append("当前盐害情况严重，需要立即采取干预措施。");
            case "MODERATE" -> sb.append("当前存在中度盐害风险，建议加强监测并制定预防性保护方案。");
            case "MILD" -> sb.append("存在轻度盐害风险，应继续保持监测并做好预防工作。");
            default -> sb.append("目前盐害风险较低，壁画保存状况良好。");
        }

        return sb.toString();
    }

    private String generateSuggestion(String level, BigDecimal avgSalt) {
        StringBuilder sb = new StringBuilder();

        switch (level) {
            case "SEVERE" -> {
                sb.append("【紧急建议】\n");
                sb.append("1. 立即启动应急保护预案\n");
                sb.append("2. 调整环境控制参数，降低湿度至60%以下\n");
                sb.append("3. 考虑使用脱盐材料进行紧急处理\n");
                sb.append("4. 增加监测频率至每小时一次\n");
                sb.append("5. 组织文物保护专家现场评估");
            }
            case "MODERATE" -> {
                sb.append("【防治建议】\n");
                sb.append("1. 优化通风系统，控制相对湿度在60-65%\n");
                sb.append("2. 检查防水设施，排除渗水隐患\n");
                sb.append("3. 准备预防性保护材料\n");
                sb.append("4. 监测频率调整为每4小时一次\n");
                sb.append("5. 制定详细的保护方案");
            }
            case "MILD" -> {
                sb.append("【预防建议】\n");
                sb.append("1. 保持现有环境控制措施\n");
                sb.append("2. 定期检查监测设备运行状态\n");
                sb.append("3. 每季度进行一次全面评估\n");
                sb.append("4. 做好环境数据记录和趋势分析");
            }
            default -> {
                sb.append("【维护建议】\n");
                sb.append("1. 继续保持良好的保存环境\n");
                sb.append("2. 维持每日监测频率\n");
                sb.append("3. 每年进行一次综合检查");
            }
        }

        return sb.toString();
    }

    private AnalysisResultDTO convertToResultDTO(AnalysisResult result) {
        AnalysisResultDTO dto = new AnalysisResultDTO();
        dto.setId(result.getId());
        dto.setTombId(result.getTombId());
        dto.setChamberId(result.getChamberId());
        dto.setAnalysisType(result.getAnalysisType());
        dto.setSaltDamageLevel(result.getSaltDamageLevel());
        dto.setRiskScore(result.getRiskScore());
        dto.setConclusion(result.getConclusion());
        dto.setSuggestion(result.getSuggestion());
        dto.setAnalysisTime(result.getAnalysisTime());
        return dto;
    }

    private PredictionDTO convertToPredictionDTO(PredictionData prediction) {
        PredictionDTO dto = new PredictionDTO();
        dto.setId(prediction.getId());
        dto.setTombId(prediction.getTombId());
        dto.setChamberId(prediction.getChamberId());
        dto.setPredictTime(prediction.getPredictTime());
        dto.setPredictedSaltConcentration(prediction.getPredictedSaltConcentration());
        dto.setPredictedTemperature(prediction.getPredictedTemperature());
        dto.setPredictedHumidity(prediction.getPredictedHumidity());
        dto.setRiskLevel(prediction.getRiskLevel());
        dto.setCreateTime(prediction.getCreateTime());
        return dto;
    }
}
