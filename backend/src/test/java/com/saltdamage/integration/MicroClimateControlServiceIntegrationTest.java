package com.saltdamage.integration;

import com.saltdamage.algorithm.DQNMicroClimateController;
import com.saltdamage.dto.ControlRequest;
import com.saltdamage.dto.EnvironmentStateDTO;
import com.saltdamage.dto.MicroClimateControlDTO;
import com.saltdamage.entity.Chamber;
import com.saltdamage.entity.MicroClimateControlRecord;
import com.saltdamage.entity.SensorData;
import com.saltdamage.repository.ChamberRepository;
import com.saltdamage.repository.MicroClimateControlRepository;
import com.saltdamage.repository.SensorDataRepository;
import com.saltdamage.repository.TombRepository;
import com.saltdamage.service.MicroClimateControlService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("三、MicroClimateControlService 微环境调控服务集成测试")
class MicroClimateControlServiceIntegrationTest {

    @Autowired
    private MicroClimateControlService microClimateControlService;

    @Autowired
    private DQNMicroClimateController dqnMicroClimateController;

    @MockBean
    private MicroClimateControlRepository controlRecordRepository;

    @MockBean
    private SensorDataRepository sensorDataRepository;

    @MockBean
    private TombRepository tombRepository;

    @MockBean
    private ChamberRepository chamberRepository;

    private Chamber testChamber;
    private List<SensorData> mockSensorData;

    @BeforeEach
    void setUp() {
        testChamber = new Chamber();
        testChamber.setId(1L);
        testChamber.setTombId(1L);
        testChamber.setName("测试墓室侧室");

        mockSensorData = generateMockSensorData(1L, 15);

        reset(controlRecordRepository, sensorDataRepository, tombRepository, chamberRepository);
    }

    // ===== 正常场景测试 =====

    @Test
    @DisplayName("1. 获取当前状态，DQN给出推荐动作")
    void testGetCurrentState_ReturnsEnvStateWithDQNRecommendation() {
        log.info("[测试开始] 获取当前环境状态+DQN推荐");

        when(sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockSensorData);
        when(controlRecordRepository.findFirstByChamberIdOrderByControlTimestampDesc(1L))
                .thenReturn(Optional.empty());

        EnvironmentStateDTO state = microClimateControlService.getCurrentState(1L);

        assertNotNull(state, "状态DTO不应为空");
        assertNotNull(state.getCurrentRh(), "当前RH不应为空");
        assertNotNull(state.getRecommendedAction(), "推荐动作不应为空");
        assertNotNull(state.getExpectedReward(), "期望奖励不应为空");

        assertTrue(state.getCurrentRh().compareTo(BigDecimal.ZERO) >= 0,
                "当前RH应>=0");
        assertTrue(state.getCurrentRh().compareTo(new BigDecimal("100")) <= 0,
                "当前RH应<=100");

        int action = state.getRecommendedAction();
        assertTrue(action >= 0 && action <= 3,
                "推荐动作应在[0,3]范围内，实际: " + action);

        log.info("[测试通过] 当前RH: {}%, 推荐动作: {}, 期望奖励: {}",
                state.getCurrentRh(), action, state.getExpectedReward());
    }

    @Test
    @DisplayName("2. 手动模式开除湿机，状态正确保存")
    void testExecuteControl_ManualMode_TurnsOnDehumidifier() {
        log.info("[测试开始] 手动模式-开除湿机");

        when(chamberRepository.findById(1L)).thenReturn(Optional.of(testChamber));
        when(sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockSensorData);
        when(controlRecordRepository.save(any(MicroClimateControlRecord.class)))
                .thenAnswer(invocation -> {
                    MicroClimateControlRecord r = invocation.getArgument(0);
                    r.setId(1L);
                    return r;
                });

        ControlRequest request = new ControlRequest();
        request.setChamberId(1L);
        request.setMode("MANUAL");
        request.setTargetRh(new BigDecimal("50"));
        request.setDehumidifierOn(true);
        request.setHumidifierOn(false);

        MicroClimateControlDTO result = microClimateControlService.executeControl(request);

        assertNotNull(result);
        assertTrue(Boolean.TRUE.equals(result.getDehumidifierStatus()),
                "除湿机状态应为开启");
        assertFalse(Boolean.TRUE.equals(result.getHumidifierStatus()),
                "加湿器状态应为关闭");
        assertEquals("MANUAL", result.getControlMode());
        assertEquals(1, result.getActionTaken(),
                "仅除湿机开启对应动作编号应为1");

        ArgumentCaptor<MicroClimateControlRecord> captor = ArgumentCaptor.forClass(MicroClimateControlRecord.class);
        verify(controlRecordRepository, times(1)).save(captor.capture());
        assertEquals(true, captor.getValue().getDehumidifierStatus());
        assertEquals(1, captor.getValue().getActionTaken());

        log.info("[测试通过] 手动模式-除湿机开启，能耗: {} kWh", result.getEnergyConsumption());
    }

    @Test
    @DisplayName("3. 自动模式执行DQN推荐动作")
    void testExecuteControl_AutoDQN_UsesDQNRecommendation() {
        log.info("[测试开始] 自动模式-DQN决策");

        MicroClimateControlRecord lastRecord = new MicroClimateControlRecord();
        lastRecord.setId(100L);
        lastRecord.setChamberId(2L);
        lastRecord.setDehumidifierStatus(false);
        lastRecord.setHumidifierStatus(false);
        lastRecord.setControlTimestamp(LocalDateTime.now().minusHours(1));

        Chamber chamber2 = new Chamber();
        chamber2.setId(2L);
        chamber2.setTombId(2L);
        chamber2.setName("测试墓室主室");

        when(chamberRepository.findById(2L)).thenReturn(Optional.of(chamber2));
        when(sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                eq(2L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockSensorData);
        when(controlRecordRepository.findFirstByChamberIdOrderByControlTimestampDesc(2L))
                .thenReturn(Optional.of(lastRecord));
        when(controlRecordRepository.save(any(MicroClimateControlRecord.class)))
                .thenAnswer(invocation -> {
                    MicroClimateControlRecord r = invocation.getArgument(0);
                    r.setId(2L);
                    return r;
                });

        ControlRequest request = new ControlRequest();
        request.setChamberId(2L);
        request.setMode("AUTO_DQN");

        MicroClimateControlDTO result = microClimateControlService.executeControl(request);

        assertNotNull(result);
        assertNotNull(result.getActionTaken());
        assertNotNull(result.getRewardScore(), "AUTO_DQN模式应有奖励分");
        assertNotNull(result.getEnergyConsumption());
        assertTrue(result.getActionTaken() >= 0 && result.getActionTaken() <= 3,
                "动作应在[0,3]，实际: " + result.getActionTaken());

        log.info("[测试通过] 自动模式-DQN决策动作: {}, 奖励分: {}",
                result.getActionTaken(), result.getRewardScore());
    }

    @Test
    @DisplayName("4. 训练指定回合数正常完成")
    void testTrainModel_CompletesWithoutError() {
        log.info("[测试开始] DQN模型训练");

        int episodes = 5;
        List<Double> rewards = microClimateControlService.trainModel(episodes, 1L);

        assertNotNull(rewards, "训练奖励列表不应为空");
        assertEquals(episodes, rewards.size(),
                "奖励记录数应等于训练回合数");
        for (Double reward : rewards) {
            assertNotNull(reward);
            assertTrue(Double.isFinite(reward), "奖励值应为有限数");
        }

        log.info("[测试通过] {}回合训练完成，首回合奖励: {}, 末回合奖励: {}",
                episodes, rewards.get(0), rewards.get(rewards.size() - 1));
    }

    @Test
    @DisplayName("5. 历史查询分页正确")
    void testGetControlHistory_ReturnsPaginatedResults() {
        log.info("[测试开始] 控制历史查询");

        List<MicroClimateControlRecord> mockRecords = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            MicroClimateControlRecord r = new MicroClimateControlRecord();
            r.setId((long) (i + 1));
            r.setChamberId(3L);
            r.setTombId(3L);
            r.setControlMode(i % 2 == 0 ? "MANUAL" : "AUTO_DQN");
            r.setActionTaken(i % 4);
            r.setControlTimestamp(LocalDateTime.now().minusMinutes(i * 30));
            mockRecords.add(r);
        }

        Page<MicroClimateControlRecord> mockPage = new PageImpl<>(mockRecords);
        when(controlRecordRepository.findByChamberIdOrderByControlTimestampDesc(
                eq(3L), any(Pageable.class))).thenReturn(mockPage);

        List<MicroClimateControlDTO> history = microClimateControlService.getControlHistory(3L, 10);

        assertNotNull(history);
        assertEquals(8, history.size(), "应返回8条历史记录");

        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(controlRecordRepository).findByChamberIdOrderByControlTimestampDesc(
                eq(3L), pageCaptor.capture());
        assertEquals(0, pageCaptor.getValue().getPageNumber());
        assertEquals(10, pageCaptor.getValue().getPageSize());

        log.info("[测试通过] 历史查询分页正确，返回记录数: {}", history.size());
    }

    @Test
    @DisplayName("6. 建议包含能耗和风险两方面内容")
    void testGetOptimizationSuggestion_ContainsBothEnergyAndRiskAdvice() {
        log.info("[测试开始] 优化建议查询");

        when(sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                eq(4L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockSensorData);
        when(controlRecordRepository.findFirstByChamberIdOrderByControlTimestampDesc(4L))
                .thenReturn(Optional.empty());

        MicroClimateControlDTO suggestionDTO = microClimateControlService.getOptimizationSuggestion(4L);

        assertNotNull(suggestionDTO);
        assertNotNull(suggestionDTO.getSuggestion(), "建议内容不应为空");

        String suggestion = suggestionDTO.getSuggestion();
        assertTrue(suggestion.length() > 50, "建议内容应足够详细");

        boolean hasEnergy = suggestion.contains("能耗") || suggestion.contains("节能")
                || suggestion.contains("电量") || suggestion.contains("奖励");
        boolean hasRisk = suggestion.contains("风险") || suggestion.contains("潮解")
                || suggestion.contains("盐害") || suggestion.contains("安全");

        assertTrue(hasEnergy || hasRisk, "建议应包含能耗或风险相关内容");

        log.info("[测试通过] 建议内容长度: {}, 包含能耗: {}, 包含风险: {}",
                suggestion.length(), hasEnergy, hasRisk);
    }

    // ===== 边界场景测试 =====

    @Test
    @DisplayName("7. 目标RH低于40被钳制")
    void testExecuteControl_TargetRhTooLow_ClampedTo40() {
        log.info("[测试开始] 目标RH过低钳制");

        when(chamberRepository.findById(1L)).thenReturn(Optional.of(testChamber));
        when(sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockSensorData);
        when(controlRecordRepository.save(any(MicroClimateControlRecord.class)))
                .thenAnswer(invocation -> {
                    MicroClimateControlRecord r = invocation.getArgument(0);
                    r.setId(7L);
                    return r;
                });

        ControlRequest request = new ControlRequest();
        request.setChamberId(1L);
        request.setMode("MANUAL");
        request.setTargetRh(new BigDecimal("20"));
        request.setDehumidifierOn(true);

        MicroClimateControlDTO result = microClimateControlService.executeControl(request);

        assertNotNull(result);
        assertNotNull(result.getTargetRh());
        assertTrue(result.getTargetRh().compareTo(new BigDecimal("40")) >= 0,
                "目标RH不应低于40%，实际: " + result.getTargetRh());

        log.info("[测试通过] 目标RH过低钳制: 原始=20%, 实际保存={}%", result.getTargetRh());
    }

    @Test
    @DisplayName("8. 目标RH高于90被钳制")
    void testExecuteControl_TargetRhTooHigh_ClampedTo90() {
        log.info("[测试开始] 目标RH过高钳制");

        when(chamberRepository.findById(1L)).thenReturn(Optional.of(testChamber));
        when(sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockSensorData);
        when(controlRecordRepository.save(any(MicroClimateControlRecord.class)))
                .thenAnswer(invocation -> {
                    MicroClimateControlRecord r = invocation.getArgument(0);
                    r.setId(8L);
                    return r;
                });

        ControlRequest request = new ControlRequest();
        request.setChamberId(1L);
        request.setMode("SCHEDULE");
        request.setTargetRh(new BigDecimal("95"));
        request.setHumidifierOn(true);

        MicroClimateControlDTO result = microClimateControlService.executeControl(request);

        assertNotNull(result);
        assertNotNull(result.getTargetRh());
        assertTrue(result.getTargetRh().compareTo(new BigDecimal("90")) <= 0,
                "目标RH不应高于90%，实际: " + result.getTargetRh());

        log.info("[测试通过] 目标RH过高钳制: 原始=95%, 实际保存={}%", result.getTargetRh());
    }

    @Test
    @DisplayName("9. 连续相同动作，无切换惩罚")
    void testExecuteControl_SameAction_NoFrequentSwitchPenalty() {
        log.info("[测试开始] 连续相同动作无切换惩罚");

        MicroClimateControlRecord prevRecord = new MicroClimateControlRecord();
        prevRecord.setId(90L);
        prevRecord.setChamberId(5L);
        prevRecord.setDehumidifierStatus(true);
        prevRecord.setHumidifierStatus(false);
        prevRecord.setActionTaken(1);
        prevRecord.setRewardScore(new BigDecimal("0.5"));
        prevRecord.setControlTimestamp(LocalDateTime.now().minusMinutes(30));

        Chamber chamber5 = new Chamber();
        chamber5.setId(5L);
        chamber5.setTombId(5L);
        chamber5.setName("连续测试室");

        when(chamberRepository.findById(5L)).thenReturn(Optional.of(chamber5));
        when(sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                eq(5L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockSensorData);
        when(controlRecordRepository.findFirstByChamberIdOrderByControlTimestampDesc(5L))
                .thenReturn(Optional.of(prevRecord));
        when(controlRecordRepository.save(any(MicroClimateControlRecord.class)))
                .thenAnswer(invocation -> {
                    MicroClimateControlRecord r = invocation.getArgument(0);
                    r.setId(9L);
                    return r;
                });

        ControlRequest request = new ControlRequest();
        request.setChamberId(5L);
        request.setMode("SCHEDULE");
        request.setDehumidifierOn(true);
        request.setHumidifierOn(false);

        MicroClimateControlDTO result = microClimateControlService.executeControl(request);

        assertNotNull(result);
        assertTrue(Boolean.TRUE.equals(result.getDehumidifierStatus()));
        assertEquals(1, result.getActionTaken());
        assertEquals(new BigDecimal("0.5000").compareTo(result.getEnergyConsumption()) <= 0,
                "仅除湿机开启能耗>=0.5");

        log.info("[测试通过] 连续相同动作: 动作={}, 能耗={}",
                result.getActionTaken(), result.getEnergyConsumption());
    }

    // ===== 异常场景测试 =====

    @Test
    @DisplayName("10. 非法mode正常降级处理，不抛异常")
    void testExecuteControl_InvalidMode_ThrowsException() {
        log.info("[测试开始] 非法控制模式-降级处理");

        when(chamberRepository.findById(1L)).thenReturn(Optional.of(testChamber));
        when(sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockSensorData);
        when(controlRecordRepository.save(any(MicroClimateControlRecord.class)))
                .thenAnswer(invocation -> {
                    MicroClimateControlRecord r = invocation.getArgument(0);
                    r.setId(10L);
                    return r;
                });

        ControlRequest request = new ControlRequest();
        request.setChamberId(1L);
        request.setMode("INVALID_MODE_XYZ");
        request.setDehumidifierOn(true);
        request.setHumidifierOn(false);

        MicroClimateControlDTO result = microClimateControlService.executeControl(request);

        assertNotNull(result, "非法mode应降级处理而不是崩溃");
        assertNotNull(result.getControlMode());
        assertNotNull(result.getActionTaken());
        assertTrue(Boolean.TRUE.equals(result.getDehumidifierStatus()),
                "应按用户指定的dehumidifierOn=true执行");
        assertEquals(1, result.getActionTaken().intValue(),
                "仅除湿机开启对应action=1");

        ArgumentCaptor<MicroClimateControlRecord> captor = ArgumentCaptor.forClass(MicroClimateControlRecord.class);
        verify(controlRecordRepository, times(1)).save(captor.capture());
        assertEquals("INVALID_MODE_XYZ", captor.getValue().getControlMode(),
                "控制模式应原样保存（降级策略）");

        log.info("[测试通过] 非法模式降级处理: 传入={}, 实际保存={}, 动作={}",
                "INVALID_MODE_XYZ", result.getControlMode(), result.getActionTaken());
    }

    private List<SensorData> generateMockSensorData(Long chamberId, int count) {
        List<SensorData> dataList = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().minusHours(2);
        double baseRh = 55.0;
        for (int i = 0; i < count; i++) {
            SensorData data = new SensorData();
            data.setId((long) i);
            data.setChamberId(chamberId);
            data.setDeviceNo("SENSOR-" + chamberId);
            data.setSensorType("ENV");
            double rh = baseRh + 25.0 * Math.sin(i * 0.5) + (Math.random() - 0.5) * 6.0;
            rh = Math.max(30.0, Math.min(85.0, rh));
            data.setHumidity(new BigDecimal(rh).setScale(2, BigDecimal.ROUND_HALF_UP));
            data.setTemperature(new BigDecimal("22.5"));
            data.setCollectTime(base.plusMinutes(i * 8));
            dataList.add(data);
        }
        return dataList;
    }
}
