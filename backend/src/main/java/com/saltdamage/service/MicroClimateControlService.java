package com.saltdamage.service;

import com.saltdamage.algorithm.DQNMicroClimateController;
import com.saltdamage.dto.ControlRequest;
import com.saltdamage.dto.EnvironmentStateDTO;
import com.saltdamage.dto.MicroClimateControlDTO;
import com.saltdamage.entity.Chamber;
import com.saltdamage.entity.MicroClimateControlRecord;
import com.saltdamage.entity.SensorData;
import com.saltdamage.entity.Tomb;
import com.saltdamage.repository.ChamberRepository;
import com.saltdamage.repository.MicroClimateControlRepository;
import com.saltdamage.repository.SensorDataRepository;
import com.saltdamage.repository.TombRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MicroClimateControlService {

    private final MicroClimateControlRepository controlRecordRepository;
    private final DQNMicroClimateController dqnController;
    private final SensorDataRepository sensorDataRepository;
    private final TombRepository tombRepository;
    private final ChamberRepository chamberRepository;

    public EnvironmentStateDTO getCurrentState(Long chamberId) {
        log.info("获取当前环境状态, chamberId: {}", chamberId);

        LocalDateTime startTime = LocalDateTime.now().minusHours(2);
        List<SensorData> recentData = sensorDataRepository
                .findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(chamberId, startTime, LocalDateTime.now());

        if (recentData.isEmpty()) {
            throw new IllegalArgumentException("没有找到环境监测数据");
        }

        SensorData latestData = recentData.get(0);
        BigDecimal currentRh = latestData.getHumidity();

        BigDecimal rhTrend = calculateRhTrend(recentData);

        int currentHour = LocalTime.now().getHour();

        MicroClimateControlRecord lastRecord = controlRecordRepository
                .findFirstByChamberIdOrderByControlTimestampDesc(chamberId)
                .orElse(null);

        boolean dehumidifierStatus = lastRecord != null && Boolean.TRUE.equals(lastRecord.getDehumidifierStatus());
        boolean humidifierStatus = lastRecord != null && Boolean.TRUE.equals(lastRecord.getHumidifierStatus());

        DQNMicroClimateController.State state = DQNMicroClimateController.State.builder()
                .currentRh(currentRh.doubleValue() / 100.0)
                .rhTrend(rhTrend.doubleValue() / 100.0)
                .hourOfDay(currentHour / 23.0)
                .dehumidifierOn(dehumidifierStatus ? 1 : 0)
                .humidifierOn(humidifierStatus ? 1 : 0)
                .build();

        int recommendedAction = dqnController.selectBestAction(state);

        double[] qValues = dqnController.getQValues(state);
        BigDecimal expectedReward = BigDecimal.valueOf(qValues[recommendedAction])
                .setScale(4, RoundingMode.HALF_UP);

        EnvironmentStateDTO dto = new EnvironmentStateDTO();
        dto.setCurrentRh(currentRh);
        dto.setRhTrend(rhTrend);
        dto.setCurrentHour(currentHour);
        dto.setDehumidifierStatus(dehumidifierStatus);
        dto.setHumidifierStatus(humidifierStatus);
        dto.setRecommendedAction(recommendedAction);
        dto.setExpectedReward(expectedReward);

        return dto;
    }

    @Transactional(rollbackFor = Exception.class)
    public MicroClimateControlDTO executeControl(ControlRequest request) {
        log.info("执行控制操作, chamberId: {}, mode: {}", request.getChamberId(), request.getMode());

        if (request.getChamberId() == null) {
            throw new IllegalArgumentException("chamberId不能为空");
        }

        Chamber chamber = chamberRepository.findById(request.getChamberId())
                .orElseThrow(() -> new IllegalArgumentException("墓室不存在"));

        LocalDateTime startTime = LocalDateTime.now().minusHours(2);
        List<SensorData> recentData = sensorDataRepository
                .findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                        request.getChamberId(), startTime, LocalDateTime.now());

        if (recentData.isEmpty()) {
            throw new IllegalArgumentException("没有找到环境监测数据");
        }

        SensorData latestData = recentData.get(0);
        BigDecimal currentRh = latestData.getHumidity();

        int actionTaken;
        boolean dehumidifierOn;
        boolean humidifierOn;
        BigDecimal rewardScore;
        BigDecimal energyConsumption;

        if ("AUTO_DQN".equalsIgnoreCase(request.getMode())) {
            BigDecimal rhTrend = calculateRhTrend(recentData);
            int currentHour = LocalTime.now().getHour();

            MicroClimateControlRecord lastRecord = controlRecordRepository
                    .findFirstByChamberIdOrderByControlTimestampDesc(request.getChamberId())
                    .orElse(null);

            boolean lastDehumidifier = lastRecord != null && Boolean.TRUE.equals(lastRecord.getDehumidifierStatus());
            boolean lastHumidifier = lastRecord != null && Boolean.TRUE.equals(lastRecord.getHumidifierStatus());

            DQNMicroClimateController.State state = DQNMicroClimateController.State.builder()
                    .currentRh(currentRh.doubleValue() / 100.0)
                    .rhTrend(rhTrend.doubleValue() / 100.0)
                    .hourOfDay(currentHour / 23.0)
                    .dehumidifierOn(lastDehumidifier ? 1 : 0)
                    .humidifierOn(lastHumidifier ? 1 : 0)
                    .build();

            actionTaken = dqnController.selectBestAction(state);
            dehumidifierOn = (actionTaken == 1 || actionTaken == 3);
            humidifierOn = (actionTaken == 2 || actionTaken == 3);

            double[] qValues = dqnController.getQValues(state);
            rewardScore = BigDecimal.valueOf(qValues[actionTaken])
                    .setScale(4, RoundingMode.HALF_UP);

            energyConsumption = calculateEnergyConsumption(dehumidifierOn, humidifierOn);

        } else if ("SCHEDULE".equalsIgnoreCase(request.getMode())) {
            dehumidifierOn = Boolean.TRUE.equals(request.getDehumidifierOn());
            humidifierOn = Boolean.TRUE.equals(request.getHumidifierOn());
            actionTaken = convertToAction(dehumidifierOn, humidifierOn);
            rewardScore = BigDecimal.ZERO;
            energyConsumption = calculateEnergyConsumption(dehumidifierOn, humidifierOn);

        } else {
            dehumidifierOn = Boolean.TRUE.equals(request.getDehumidifierOn());
            humidifierOn = Boolean.TRUE.equals(request.getHumidifierOn());
            actionTaken = convertToAction(dehumidifierOn, humidifierOn);
            rewardScore = BigDecimal.ZERO;
            energyConsumption = calculateEnergyConsumption(dehumidifierOn, humidifierOn);
        }

        MicroClimateControlRecord record = new MicroClimateControlRecord();
        record.setTombId(chamber.getTombId());
        record.setChamberId(request.getChamberId());
        record.setControlMode(request.getMode() != null ? request.getMode() : "MANUAL");
        record.setCurrentRh(currentRh);
        record.setTargetRh(request.getTargetRh());
        record.setDehumidifierStatus(dehumidifierOn);
        record.setHumidifierStatus(humidifierOn);
        record.setEnergyConsumption(energyConsumption);
        record.setRewardScore(rewardScore);
        record.setActionTaken(actionTaken);
        record.setControlTimestamp(LocalDateTime.now());

        record = controlRecordRepository.save(record);
        log.info("控制记录已保存, id: {}, action: {}", record.getId(), actionTaken);

        return convertToDTO(record);
    }

    public List<Double> trainModel(int episodes, Long chamberId) {
        log.info("开始训练DQN模型, episodes: {}, chamberId: {}", episodes, chamberId);

        if (episodes <= 0) {
            throw new IllegalArgumentException("训练回合数必须大于0");
        }

        List<Double> rewards = dqnController.train(episodes);

        log.info("DQN模型训练完成, 总回合数: {}", episodes);
        return rewards;
    }

    public List<MicroClimateControlDTO> getControlHistory(Long chamberId, int limit) {
        log.info("获取控制历史, chamberId: {}, limit: {}", chamberId, limit);

        Pageable pageable = PageRequest.of(0, limit);
        Page<MicroClimateControlRecord> recordPage = controlRecordRepository
                .findByChamberIdOrderByControlTimestampDesc(chamberId, pageable);

        return recordPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public MicroClimateControlDTO getOptimizationSuggestion(Long chamberId) {
        log.info("获取优化建议, chamberId: {}", chamberId);

        EnvironmentStateDTO currentState = getCurrentState(chamberId);

        String suggestion = generateOptimizationSuggestion(currentState);

        MicroClimateControlRecord lastRecord = controlRecordRepository
                .findFirstByChamberIdOrderByControlTimestampDesc(chamberId)
                .orElse(null);

        MicroClimateControlDTO dto;
        if (lastRecord != null) {
            dto = convertToDTO(lastRecord);
        } else {
            dto = new MicroClimateControlDTO();
            dto.setChamberId(chamberId);
            dto.setCurrentRh(currentState.getCurrentRh());
            dto.setDehumidifierStatus(currentState.getDehumidifierStatus());
            dto.setHumidifierStatus(currentState.getHumidifierStatus());
        }
        dto.setSuggestion(suggestion);

        return dto;
    }

    private BigDecimal calculateRhTrend(List<SensorData> dataList) {
        if (dataList.size() < 2) {
            return BigDecimal.ZERO;
        }

        int n = Math.min(dataList.size(), 12);
        List<SensorData> recentData = dataList.subList(0, n);

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = recentData.get(i).getHumidity() != null
                    ? recentData.get(i).getHumidity().doubleValue() : 0;
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return BigDecimal.valueOf(slope).setScale(4, RoundingMode.HALF_UP);
    }

    private int convertToAction(boolean dehumidifierOn, boolean humidifierOn) {
        if (dehumidifierOn && humidifierOn) {
            return 3;
        } else if (dehumidifierOn) {
            return 1;
        } else if (humidifierOn) {
            return 2;
        } else {
            return 0;
        }
    }

    private BigDecimal calculateEnergyConsumption(boolean dehumidifierOn, boolean humidifierOn) {
        double consumption = 0.0;
        if (dehumidifierOn) {
            consumption += 0.5;
        }
        if (humidifierOn) {
            consumption += 0.5;
        }
        if (dehumidifierOn && humidifierOn) {
            consumption += 0.5;
        }
        return BigDecimal.valueOf(consumption).setScale(4, RoundingMode.HALF_UP);
    }

    private String generateOptimizationSuggestion(EnvironmentStateDTO state) {
        StringBuilder sb = new StringBuilder();
        BigDecimal currentRh = state.getCurrentRh();
        int action = state.getRecommendedAction();

        sb.append("【微环境优化建议】\n");
        sb.append("当前相对湿度: ").append(currentRh).append("%\n");

        if (currentRh.compareTo(new BigDecimal("65")) >= 0 && currentRh.compareTo(new BigDecimal("80")) <= 0) {
            sb.append("⚠️ 当前处于盐分潮解带(65%-80%)，盐害风险较高！\n");
        } else if (currentRh.compareTo(new BigDecimal("40")) < 0 || currentRh.compareTo(new BigDecimal("90")) > 0) {
            sb.append("⚠️ 当前湿度处于极端范围，需立即调整！\n");
        } else {
            sb.append("✅ 当前湿度处于安全范围。\n");
        }

        sb.append("\n【推荐动作】");
        switch (action) {
            case 0:
                sb.append("待机 - 保持当前状态，无需操作\n");
                break;
            case 1:
                sb.append("开启除湿机 - 降低环境湿度\n");
                break;
            case 2:
                sb.append("开启加湿器 - 提高环境湿度\n");
                break;
            case 3:
                sb.append("同时开启除湿机和加湿器 - 快速调节湿度\n");
                break;
            default:
                sb.append("未知动作\n");
        }

        sb.append("\n【能耗与风险权衡建议】\n");
        BigDecimal expectedReward = state.getExpectedReward();
        if (expectedReward.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("当前控制策略整体表现良好，预计可获得正奖励。\n");
        } else {
            sb.append("当前控制策略存在优化空间，建议调整控制参数。\n");
        }

        sb.append("\n【长期优化建议】\n");
        sb.append("1. 建议定期训练DQN模型以适应当前季节环境\n");
        sb.append("2. 可根据实际能耗数据调整奖励函数权重\n");
        sb.append("3. 建议设置湿度预警阈值，提前采取干预措施");

        return sb.toString();
    }

    private MicroClimateControlDTO convertToDTO(MicroClimateControlRecord record) {
        MicroClimateControlDTO dto = new MicroClimateControlDTO();
        dto.setId(record.getId());
        dto.setTombId(record.getTombId());
        dto.setChamberId(record.getChamberId());
        dto.setControlMode(record.getControlMode());
        dto.setCurrentRh(record.getCurrentRh());
        dto.setTargetRh(record.getTargetRh());
        dto.setDehumidifierStatus(record.getDehumidifierStatus());
        dto.setHumidifierStatus(record.getHumidifierStatus());
        dto.setEnergyConsumption(record.getEnergyConsumption());
        dto.setRewardScore(record.getRewardScore());
        dto.setActionTaken(record.getActionTaken());
        dto.setControlTimestamp(record.getControlTimestamp());
        dto.setCreateTime(record.getCreateTime());

        if (record.getTombId() != null) {
            tombRepository.findById(record.getTombId()).ifPresent(tomb ->
                    dto.setTombName(tomb.getName()));
        }
        if (record.getChamberId() != null) {
            chamberRepository.findById(record.getChamberId()).ifPresent(chamber ->
                    dto.setChamberName(chamber.getName()));
        }

        return dto;
    }
}
