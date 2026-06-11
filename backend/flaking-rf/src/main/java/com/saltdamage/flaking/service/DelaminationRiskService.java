package com.saltdamage.flaking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saltdamage.flaking.algorithm.DelaminationRiskModel;
import com.saltdamage.flaking.dto.DelaminationAssessmentRequest;
import com.saltdamage.flaking.dto.DelaminationRiskDTO;
import com.saltdamage.entity.Chamber;
import com.saltdamage.flaking.entity.DelaminationRiskRecord;
import com.saltdamage.entity.Tomb;
import com.saltdamage.repository.ChamberRepository;
import com.saltdamage.flaking.repository.DelaminationRiskRepository;
import com.saltdamage.repository.TombRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DelaminationRiskService {

    private final DelaminationRiskRepository delaminationRiskRepository;
    private final DelaminationRiskModel delaminationRiskModel;
    private final TombRepository tombRepository;
    private final ChamberRepository chamberRepository;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public DelaminationRiskDTO assessRisk(DelaminationAssessmentRequest request) {
        log.info("执行起甲风险评估, tombId: {}, chamberId: {}, muralId: {}",
                request.getTombId(), request.getChamberId(), request.getMuralId());

        if (request.getChamberId() == null && request.getTombId() == null) {
            throw new IllegalArgumentException("tombId和chamberId不能同时为空");
        }
        if (request.getCrystallizationPressure() == null) {
            throw new IllegalArgumentException("结晶压力不能为空");
        }
        if (request.getAvgDailyRhFluctuation() == null) {
            throw new IllegalArgumentException("日均RH波动不能为空");
        }
        if (request.getTemperatureVariation() == null) {
            throw new IllegalArgumentException("温度变幅不能为空");
        }

        double adhesionStrength;
        if (request.getAdhesionStrength() != null) {
            adhesionStrength = request.getAdhesionStrength().doubleValue();
        } else {
            if (request.getPigmentType() == null || request.getMuralAge() == null) {
                throw new IllegalArgumentException("未提供附着力时，必须提供颜料类型和壁画年代");
            }
            adhesionStrength = delaminationRiskModel.generateAdhesionData(
                    request.getPigmentType(), request.getMuralAge());
            log.debug("模拟生成附着力: {} MPa", adhesionStrength);
        }

        int cycleCount7d;
        if (request.getCycleCount7d() != null) {
            cycleCount7d = request.getCycleCount7d();
        } else {
            cycleCount7d = estimateCycleCount(request.getAvgDailyRhFluctuation().doubleValue());
            log.debug("估算7天循环次数: {}", cycleCount7d);
        }

        DelaminationRiskModel.FeatureInput featureInput = new DelaminationRiskModel.FeatureInput(
                request.getCrystallizationPressure().doubleValue(),
                adhesionStrength,
                cycleCount7d,
                request.getAvgDailyRhFluctuation().doubleValue(),
                request.getTemperatureVariation().doubleValue()
        );

        DelaminationRiskModel.DelaminationResult result = delaminationRiskModel.predict(featureInput);

        DelaminationRiskRecord record = new DelaminationRiskRecord();
        record.setTombId(request.getTombId());
        record.setChamberId(request.getChamberId());
        record.setMuralId(request.getMuralId());
        record.setPigmentType(request.getPigmentType());
        record.setMuralAge(request.getMuralAge());
        record.setCrystallizationPressure(request.getCrystallizationPressure());
        record.setAdhesionStrength(BigDecimal.valueOf(adhesionStrength));
        record.setPressureAdhesionRatio(BigDecimal.valueOf(featureInput.getPressureAdhesionRatio()));
        record.setCycleCount7d(cycleCount7d);
        record.setAvgDailyRhFluctuation(request.getAvgDailyRhFluctuation());
        record.setTemperatureVariation(request.getTemperatureVariation());
        record.setDelaminationProbability(BigDecimal.valueOf(result.getProbability()));
        record.setRiskLevel(result.getRiskLevel().name());
        record.setFeatureContributions(serializeFeatureContributions(result.getFeatureContributions()));
        record.setAssessmentTime(LocalDateTime.now());
        record.setSuggestion(generateMitigationSuggestion(result));

        record = delaminationRiskRepository.save(record);
        log.info("起甲风险评估结果已保存, id: {}, 风险等级: {}", record.getId(), record.getRiskLevel());

        return convertToDTO(record);
    }

    public Page<DelaminationRiskDTO> getRiskList(Long tombId, Long chamberId, String riskLevel, Pageable pageable) {
        log.info("查询起甲风险记录, tombId: {}, chamberId: {}, riskLevel: {}", tombId, chamberId, riskLevel);

        Page<DelaminationRiskRecord> recordPage;

        if (chamberId != null) {
            recordPage = delaminationRiskRepository.findByChamberIdOrderByAssessmentTimeDesc(chamberId, pageable);
        } else if (tombId != null && riskLevel != null) {
            recordPage = delaminationRiskRepository.findByTombIdAndRiskLevelOrderByAssessmentTimeDesc(tombId, riskLevel, pageable);
        } else if (tombId != null) {
            recordPage = delaminationRiskRepository.findAll(pageable);
        } else {
            recordPage = delaminationRiskRepository.findAll(pageable);
        }

        return recordPage.map(this::convertToDTO);
    }

    public DelaminationRiskDTO getLatestRisk(Long tombId, Long chamberId) {
        log.info("获取最新起甲风险评估, tombId: {}, chamberId: {}", tombId, chamberId);

        if (chamberId != null) {
            return delaminationRiskRepository.findFirstByChamberIdOrderByAssessmentTimeDesc(chamberId)
                    .map(this::convertToDTO)
                    .orElse(null);
        }

        return null;
    }

    public Map<String, Object> getRiskStatistics(Long tombId, int days) {
        log.info("获取起甲风险统计, tombId: {}, days: {}", tombId, days);

        LocalDateTime startTime = LocalDateTime.now().minusDays(days);

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("tombId", tombId);
        statistics.put("days", days);
        statistics.put("startTime", startTime);

        long lowCount = delaminationRiskRepository.countByTombIdAndRiskLevelAndAssessmentTimeAfter(
                tombId, "LOW", startTime);
        long mediumCount = delaminationRiskRepository.countByTombIdAndRiskLevelAndAssessmentTimeAfter(
                tombId, "MEDIUM", startTime);
        long highCount = delaminationRiskRepository.countByTombIdAndRiskLevelAndAssessmentTimeAfter(
                tombId, "HIGH", startTime);
        long criticalCount = delaminationRiskRepository.countByTombIdAndRiskLevelAndAssessmentTimeAfter(
                tombId, "CRITICAL", startTime);
        long totalCount = lowCount + mediumCount + highCount + criticalCount;

        Map<String, Long> levelCounts = new HashMap<>();
        levelCounts.put("LOW", lowCount);
        levelCounts.put("MEDIUM", mediumCount);
        levelCounts.put("HIGH", highCount);
        levelCounts.put("CRITICAL", criticalCount);
        levelCounts.put("TOTAL", totalCount);

        statistics.put("levelCounts", levelCounts);

        Map<String, Double> levelPercentages = new HashMap<>();
        if (totalCount > 0) {
            levelPercentages.put("LOW", (double) lowCount / totalCount * 100);
            levelPercentages.put("MEDIUM", (double) mediumCount / totalCount * 100);
            levelPercentages.put("HIGH", (double) highCount / totalCount * 100);
            levelPercentages.put("CRITICAL", (double) criticalCount / totalCount * 100);
        } else {
            levelPercentages.put("LOW", 0.0);
            levelPercentages.put("MEDIUM", 0.0);
            levelPercentages.put("HIGH", 0.0);
            levelPercentages.put("CRITICAL", 0.0);
        }
        statistics.put("levelPercentages", levelPercentages);

        DelaminationRiskDTO latest = getLatestRisk(tombId, null);
        statistics.put("latestAssessment", latest);

        return statistics;
    }

    public String generateMitigationSuggestion(DelaminationRiskModel.DelaminationResult result) {
        return result.getRecommendation();
    }

    private int estimateCycleCount(double avgDailyRhFluctuation) {
        double cyclesPerDay = avgDailyRhFluctuation / 15.0;
        return (int) Math.round(cyclesPerDay * 7);
    }

    private String serializeFeatureContributions(Map<String, Double> contributions) {
        try {
            return objectMapper.writeValueAsString(contributions);
        } catch (JsonProcessingException e) {
            log.error("序列化特征贡献度失败", e);
            return "{}";
        }
    }

    private DelaminationRiskDTO convertToDTO(DelaminationRiskRecord record) {
        DelaminationRiskDTO dto = new DelaminationRiskDTO();
        dto.setId(record.getId());
        dto.setTombId(record.getTombId());
        dto.setChamberId(record.getChamberId());
        dto.setMuralId(record.getMuralId());
        dto.setPigmentType(record.getPigmentType());
        dto.setMuralAge(record.getMuralAge());
        dto.setCrystallizationPressure(record.getCrystallizationPressure());
        dto.setAdhesionStrength(record.getAdhesionStrength());
        dto.setPressureAdhesionRatio(record.getPressureAdhesionRatio());
        dto.setCycleCount7d(record.getCycleCount7d());
        dto.setAvgDailyRhFluctuation(record.getAvgDailyRhFluctuation());
        dto.setTemperatureVariation(record.getTemperatureVariation());
        dto.setDelaminationProbability(record.getDelaminationProbability());
        dto.setRiskLevel(record.getRiskLevel());
        dto.setFeatureContributions(record.getFeatureContributions());
        dto.setAssessmentTime(record.getAssessmentTime());
        dto.setSuggestion(record.getSuggestion());
        dto.setCreateTime(record.getCreateTime());

        if (record.getTombId() != null) {
            tombRepository.findById(record.getTombId())
                    .ifPresent(tomb -> dto.setTombName(tomb.getName()));
        }
        if (record.getChamberId() != null) {
            chamberRepository.findById(record.getChamberId())
                    .ifPresent(chamber -> dto.setChamberName(chamber.getName()));
        }

        return dto;
    }
}
