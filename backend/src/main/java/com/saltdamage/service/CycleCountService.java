package com.saltdamage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saltdamage.algorithm.RainflowCycleCounter;
import com.saltdamage.dto.CycleCountDTO;
import com.saltdamage.dto.CycleCountRequest;
import com.saltdamage.entity.CycleCountRecord;
import com.saltdamage.entity.SensorData;
import com.saltdamage.repository.CycleCountRepository;
import com.saltdamage.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CycleCountService {

    private final CycleCountRepository cycleCountRepository;
    private final SensorDataRepository sensorDataRepository;
    private final RainflowCycleCounter rainflowCycleCounter = new RainflowCycleCounter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(rollbackFor = Exception.class)
    public CycleCountDTO countCycles(CycleCountRequest request) {
        log.info("执行循环计数统计, tombId: {}, chamberId: {}, periodType: {}",
                request.getTombId(), request.getChamberId(), request.getPeriodType());

        LocalDateTime startTime = request.getStartTime();
        LocalDateTime endTime = request.getEndTime();

        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("开始时间和结束时间不能为空");
        }

        if (request.getTombId() == null && request.getChamberId() == null) {
            throw new IllegalArgumentException("tombId和chamberId不能同时为空");
        }

        List<SensorData> dataList;
        if (request.getChamberId() != null) {
            dataList = sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                    request.getChamberId(), startTime, endTime);
        } else {
            dataList = sensorDataRepository.findByTombIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                    request.getTombId(), startTime, endTime);
        }

        if (dataList.isEmpty()) {
            throw new IllegalArgumentException("没有找到指定时间范围内的监测数据");
        }

        List<Double> humidityData = dataList.stream()
                .filter(d -> d.getHumidity() != null)
                .map(d -> d.getHumidity().doubleValue())
                .collect(Collectors.toList());

        if (humidityData.size() < 3) {
            throw new IllegalArgumentException("有效湿度数据点不足，至少需要3个点");
        }

        RainflowCycleCounter.RainflowResult result = rainflowCycleCounter.countCycles(humidityData);

        CycleCountRecord record = new CycleCountRecord();
        record.setTombId(request.getTombId());
        record.setChamberId(request.getChamberId());
        record.setDeviceId(request.getDeviceId());
        record.setPeriodType(request.getPeriodType() != null ? request.getPeriodType() : "CUSTOM");
        record.setPeriodStart(startTime);
        record.setPeriodEnd(endTime);
        record.setTotalCycles(result.getTotalCycles());
        record.setFullCycles(result.getFullCycles());
        record.setPartialCycles(result.getPartialCycles());
        record.setCrossingCycles(result.getCrossingCycles());
        record.setAverageRange(BigDecimal.valueOf(result.getAverageRange()).setScale(4, RoundingMode.HALF_UP));
        record.setMaxRange(BigDecimal.valueOf(result.getMaxRange()).setScale(4, RoundingMode.HALF_UP));
        record.setMinRange(BigDecimal.valueOf(result.getMinRange()).setScale(4, RoundingMode.HALF_UP));
        record.setTotalDamage(BigDecimal.valueOf(result.getTotalDamage()).setScale(8, RoundingMode.HALF_UP));
        record.setDamageLevel(result.getDamageLevel().name());

        try {
            String histogramJson = objectMapper.writeValueAsString(result.getAmplitudeHistogram());
            record.setAmplitudeHistogram(histogramJson);
        } catch (JsonProcessingException e) {
            log.warn("直方图数据序列化失败", e);
            record.setAmplitudeHistogram("[]");
        }

        record.setAnalysisTime(LocalDateTime.now());

        record = cycleCountRepository.save(record);
        log.info("循环计数统计结果已保存, id: {}, 总循环数: {}", record.getId(), record.getTotalCycles());

        return convertToDTO(record);
    }

    public Page<CycleCountDTO> getCycleCountList(Long tombId, Long chamberId, String periodType, Pageable pageable) {
        log.info("查询循环计数统计列表, tombId: {}, chamberId: {}, periodType: {}", tombId, chamberId, periodType);

        Page<CycleCountRecord> recordPage;

        if (chamberId != null) {
            recordPage = cycleCountRepository.findByChamberIdOrderByAnalysisTimeDesc(chamberId, pageable);
        } else if (tombId != null && periodType != null) {
            recordPage = cycleCountRepository.findByTombIdAndPeriodTypeOrderByAnalysisTimeDesc(tombId, periodType, pageable);
        } else {
            recordPage = cycleCountRepository.findAll(pageable);
        }

        return recordPage.map(this::convertToDTO);
    }

    public CycleCountDTO getLatestCycleCount(Long tombId, Long chamberId) {
        log.info("获取最新循环计数统计, tombId: {}, chamberId: {}", tombId, chamberId);

        if (chamberId != null) {
            return cycleCountRepository.findFirstByChamberIdOrderByAnalysisTimeDesc(chamberId)
                    .map(this::convertToDTO)
                    .orElse(null);
        }

        return null;
    }

    public String analyzeCycleDamage(CycleCountRecord record) {
        log.info("分析循环损伤, recordId: {}, damageLevel: {}", record.getId(), record.getDamageLevel());

        StringBuilder sb = new StringBuilder();
        sb.append("【循环损伤分析报告】\n");
        sb.append("统计周期: ").append(record.getPeriodStart()).append(" 至 ").append(record.getPeriodEnd()).append("\n");
        sb.append("总循环次数: ").append(record.getTotalCycles()).append(" 次\n");
        sb.append("完整循环: ").append(record.getFullCycles()).append(" 次\n");
        sb.append("部分循环: ").append(record.getPartialCycles()).append(" 次\n");
        sb.append("穿越潮解点: ").append(record.getCrossingCycles()).append(" 次\n");
        sb.append("平均循环幅度: ").append(record.getAverageRange()).append("% RH\n");
        sb.append("最大循环幅度: ").append(record.getMaxRange()).append("% RH\n");
        sb.append("总疲劳损伤: ").append(record.getTotalDamage()).append("\n");
        sb.append("损伤等级: ").append(record.getDamageLevel()).append("\n\n");

        String level = record.getDamageLevel();
        switch (level) {
            case "LOW":
                sb.append("【评估结论】\n");
                sb.append("当前盐结晶-潮解循环造成的疲劳损伤较低，壁画保存状态良好。\n\n");
                sb.append("【建议措施】\n");
                sb.append("1. 继续保持现有环境控制措施\n");
                sb.append("2. 维持正常监测频率\n");
                sb.append("3. 每季度进行一次循环计数评估");
                break;
            case "MEDIUM":
                sb.append("【评估结论】\n");
                sb.append("存在一定程度的盐结晶疲劳损伤，需引起关注。\n");
                sb.append("穿越潮解点的循环次数较多，可能对壁画表面产生影响。\n\n");
                sb.append("【建议措施】\n");
                sb.append("1. 优化环境控制，尽量减少湿度大幅波动\n");
                sb.append("2. 增加监测频率，密切关注湿度变化趋势\n");
                sb.append("3. 考虑安装除湿设备，稳定相对湿度\n");
                sb.append("4. 每月进行一次循环损伤评估");
                break;
            case "HIGH":
                sb.append("【评估结论】\n");
                sb.append("盐结晶疲劳损伤较严重，接近疲劳寿命极限。\n");
                sb.append("频繁的潮解-结晶循环可能导致壁画表层脱落、空鼓等病害。\n\n");
                sb.append("【紧急建议】\n");
                sb.append("1. 立即启动环境调控预案，将相对湿度稳定在安全范围\n");
                sb.append("2. 增加监测频率至每小时一次\n");
                sb.append("3. 组织文物保护专家现场勘查评估\n");
                sb.append("4. 准备预防性保护材料和设备\n");
                sb.append("5. 每周进行一次循环损伤评估");
                break;
            case "CRITICAL":
                sb.append("【评估结论】\n");
                sb.append("⚠️ 警告：盐结晶疲劳损伤已达临界值！\n");
                sb.append("材料可能发生疲劳失效，壁画面临严重病害风险。\n\n");
                sb.append("【紧急处置建议】\n");
                sb.append("1. 立即启动应急保护预案\n");
                sb.append("2. 采取紧急措施稳定环境湿度，避免进一步波动\n");
                sb.append("3. 立即组织专家团队进行现场评估和抢救性保护\n");
                sb.append("4. 持续24小时监测环境参数\n");
                sb.append("5. 制定详细的修复和保护方案");
                break;
            default:
                sb.append("损伤等级未知，建议进一步检查。");
        }

        return sb.toString();
    }

    private CycleCountDTO convertToDTO(CycleCountRecord record) {
        CycleCountDTO dto = new CycleCountDTO();
        dto.setId(record.getId());
        dto.setTombId(record.getTombId());
        dto.setChamberId(record.getChamberId());
        dto.setDeviceId(record.getDeviceId());
        dto.setPeriodType(record.getPeriodType());
        dto.setPeriodStart(record.getPeriodStart());
        dto.setPeriodEnd(record.getPeriodEnd());
        dto.setTotalCycles(record.getTotalCycles());
        dto.setFullCycles(record.getFullCycles());
        dto.setPartialCycles(record.getPartialCycles());
        dto.setCrossingCycles(record.getCrossingCycles());
        dto.setAverageRange(record.getAverageRange());
        dto.setMaxRange(record.getMaxRange());
        dto.setMinRange(record.getMinRange());
        dto.setTotalDamage(record.getTotalDamage());
        dto.setDamageLevel(record.getDamageLevel());
        dto.setAmplitudeHistogram(record.getAmplitudeHistogram());
        dto.setAnalysisTime(record.getAnalysisTime());
        dto.setCreateTime(record.getCreateTime());
        return dto;
    }
}
