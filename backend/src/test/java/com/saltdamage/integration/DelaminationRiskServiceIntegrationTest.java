package com.saltdamage.integration;

import com.saltdamage.algorithm.DelaminationRiskModel;
import com.saltdamage.dto.DelaminationAssessmentRequest;
import com.saltdamage.dto.DelaminationRiskDTO;
import com.saltdamage.entity.Chamber;
import com.saltdamage.entity.DelaminationRiskRecord;
import com.saltdamage.entity.Tomb;
import com.saltdamage.repository.ChamberRepository;
import com.saltdamage.repository.DelaminationRiskRepository;
import com.saltdamage.repository.TombRepository;
import com.saltdamage.service.DelaminationRiskService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("二、DelaminationRiskService 起甲风险服务集成测试")
class DelaminationRiskServiceIntegrationTest {

    @Autowired
    private DelaminationRiskService delaminationRiskService;

    @Autowired
    private DelaminationRiskModel delaminationRiskModel;

    @MockBean
    private DelaminationRiskRepository delaminationRiskRepository;

    @MockBean
    private TombRepository tombRepository;

    @MockBean
    private ChamberRepository chamberRepository;

    @BeforeEach
    void setUp() {
        reset(delaminationRiskRepository, tombRepository, chamberRepository);
    }

    // ===== 正常场景测试 =====

    @Test
    @DisplayName("1. 高压低附输入-高概率高等级")
    void testAssessRisk_ValidRequest_ReturnsHighRisk() {
        log.info("[测试开始] 高压低附输入-高风险场景");

        DelaminationAssessmentRequest request = new DelaminationAssessmentRequest();
        request.setChamberId(1L);
        request.setMuralId(1L);
        request.setCrystallizationPressure(new BigDecimal("4.5"));
        request.setAdhesionStrength(new BigDecimal("0.3"));
        request.setCycleCount7d(25);
        request.setAvgDailyRhFluctuation(new BigDecimal("35.0"));
        request.setTemperatureVariation(new BigDecimal("15.0"));

        when(delaminationRiskRepository.save(any(DelaminationRiskRecord.class))).thenAnswer(invocation -> {
            DelaminationRiskRecord record = invocation.getArgument(0);
            record.setId(1L);
            return record;
        });

        DelaminationRiskDTO result = delaminationRiskService.assessRisk(request);

        assertNotNull(result, "评估结果不应为空");
        assertNotNull(result.getDelaminationProbability(), "起甲概率不应为空");
        assertNotNull(result.getRiskLevel(), "风险等级不应为空");

        double probability = result.getDelaminationProbability().doubleValue();
        assertTrue(probability >= 0 && probability <= 1,
                "概率应在[0,1]范围内，实际: " + probability);
        assertTrue(probability > 0.3,
                "高压低附应有较高概率(>30%)，实际: " + probability);

        String level = result.getRiskLevel();
        assertTrue(Arrays.asList("HIGH", "CRITICAL").contains(level),
                "风险等级应为HIGH或CRITICAL，实际: " + level);

        log.info("[测试通过] 起甲概率: {:.2%}, 风险等级: {}", probability, level);
    }

    @Test
    @DisplayName("2. assessRisk调用后save方法被调用")
    void testAssessRisk_SavesToDatabase() {
        log.info("[测试开始] 验证Repository.save方法被调用");

        DelaminationAssessmentRequest request = buildBasicRequest();
        request.setChamberId(2L);

        when(delaminationRiskRepository.save(any(DelaminationRiskRecord.class))).thenAnswer(invocation -> {
            DelaminationRiskRecord record = invocation.getArgument(0);
            record.setId(2L);
            return record;
        });

        delaminationRiskService.assessRisk(request);

        verify(delaminationRiskRepository, times(1)).save(any(DelaminationRiskRecord.class));

        ArgumentCaptor<DelaminationRiskRecord> captor = ArgumentCaptor.forClass(DelaminationRiskRecord.class);
        verify(delaminationRiskRepository).save(captor.capture());
        DelaminationRiskRecord saved = captor.getValue();

        assertEquals(2L, saved.getChamberId());
        assertNotNull(saved.getDelaminationProbability());
        assertNotNull(saved.getRiskLevel());
        assertNotNull(saved.getAssessmentTime());
        assertNotNull(saved.getSuggestion());

        log.info("[测试通过] save方法正确调用，保存的风险等级: {}", saved.getRiskLevel());
    }

    @Test
    @DisplayName("3. 用户未提供附着力-调用generateAdhesionData自动模拟")
    void testAssessRisk_AutoGeneratesAdhesionIfMissing() {
        log.info("[测试开始] 自动模拟附着力场景");

        DelaminationAssessmentRequest request = new DelaminationAssessmentRequest();
        request.setTombId(3L);
        request.setPigmentType("mineral");
        request.setMuralAge(500);
        request.setCrystallizationPressure(new BigDecimal("2.0"));
        request.setAvgDailyRhFluctuation(new BigDecimal("20.0"));
        request.setTemperatureVariation(new BigDecimal("8.0"));
        request.setCycleCount7d(10);

        when(delaminationRiskRepository.save(any(DelaminationRiskRecord.class))).thenAnswer(invocation -> {
            DelaminationRiskRecord record = invocation.getArgument(0);
            record.setId(3L);
            return record;
        });

        DelaminationRiskDTO result = delaminationRiskService.assessRisk(request);

        assertNotNull(result);
        assertNotNull(result.getAdhesionStrength(), "附着力应被自动生成");
        assertTrue(result.getAdhesionStrength().compareTo(BigDecimal.ZERO) > 0,
                "附着力应大于0");
        assertTrue(result.getAdhesionStrength().compareTo(new BigDecimal("2.0")) <= 0,
                "附着力不应超过最大值2.0MPa");

        log.info("[测试通过] 自动生成附着力: {} MPa", result.getAdhesionStrength());
    }

    @Test
    @DisplayName("4. 未提供循环次数-根据RH波动估算")
    void testAssessRisk_AutoEstimatesCycleCountIfMissing() {
        log.info("[测试开始] 自动估算循环次数场景");

        DelaminationAssessmentRequest request = new DelaminationAssessmentRequest();
        request.setChamberId(4L);
        request.setCrystallizationPressure(new BigDecimal("2.0"));
        request.setAdhesionStrength(new BigDecimal("1.0"));
        request.setAvgDailyRhFluctuation(new BigDecimal("30.0"));
        request.setTemperatureVariation(new BigDecimal("10.0"));

        when(delaminationRiskRepository.save(any(DelaminationRiskRecord.class))).thenAnswer(invocation -> {
            DelaminationRiskRecord record = invocation.getArgument(0);
            record.setId(4L);
            return record;
        });

        DelaminationRiskDTO result = delaminationRiskService.assessRisk(request);

        assertNotNull(result);
        assertNotNull(result.getCycleCount7d(), "循环次数应被自动估算");
        assertTrue(result.getCycleCount7d() > 0,
                "估算的循环次数应>0，实际: " + result.getCycleCount7d());
        assertTrue(result.getCycleCount7d() <= 50,
                "估算的循环次数不应过高，实际: " + result.getCycleCount7d());

        log.info("[测试通过] 自动估算7天循环次数: {}", result.getCycleCount7d());
    }

    @Test
    @DisplayName("5. 统计各等级占比正确")
    void testGetRiskStatistics_ReturnsCorrectBreakdown() {
        log.info("[测试开始] 风险等级统计场景");

        Long tombId = 10L;
        int days = 30;

        when(delaminationRiskRepository.countByTombIdAndRiskLevelAndAssessmentTimeAfter(
                eq(tombId), eq("LOW"), any(LocalDateTime.class))).thenReturn(5L);
        when(delaminationRiskRepository.countByTombIdAndRiskLevelAndAssessmentTimeAfter(
                eq(tombId), eq("MEDIUM"), any(LocalDateTime.class))).thenReturn(3L);
        when(delaminationRiskRepository.countByTombIdAndRiskLevelAndAssessmentTimeAfter(
                eq(tombId), eq("HIGH"), any(LocalDateTime.class))).thenReturn(1L);
        when(delaminationRiskRepository.countByTombIdAndRiskLevelAndAssessmentTimeAfter(
                eq(tombId), eq("CRITICAL"), any(LocalDateTime.class))).thenReturn(1L);

        Map<String, Object> stats = delaminationRiskService.getRiskStatistics(tombId, days);

        assertNotNull(stats);
        assertEquals(tombId, stats.get("tombId"));
        assertEquals(days, stats.get("days"));

        @SuppressWarnings("unchecked")
        Map<String, Long> levelCounts = (Map<String, Long>) stats.get("levelCounts");
        assertNotNull(levelCounts);
        assertEquals(5L, levelCounts.get("LOW"));
        assertEquals(3L, levelCounts.get("MEDIUM"));
        assertEquals(1L, levelCounts.get("HIGH"));
        assertEquals(1L, levelCounts.get("CRITICAL"));
        assertEquals(10L, levelCounts.get("TOTAL"));

        @SuppressWarnings("unchecked")
        Map<String, Double> percentages = (Map<String, Double>) stats.get("levelPercentages");
        assertNotNull(percentages);
        assertEquals(50.0, percentages.get("LOW"), 0.01);
        assertEquals(30.0, percentages.get("MEDIUM"), 0.01);
        assertEquals(10.0, percentages.get("HIGH"), 0.01);
        assertEquals(10.0, percentages.get("CRITICAL"), 0.01);

        log.info("[测试通过] 统计正确 - LOW:50%, MEDIUM:30%, HIGH:10%, CRITICAL:10%");
    }

    // ===== 边界场景测试 =====

    @Test
    @DisplayName("6. 最小输入-低概率")
    void testAssessRisk_MinimalInputs_LowProbability() {
        log.info("[测试开始] 最小输入低风险场景");

        DelaminationAssessmentRequest request = new DelaminationAssessmentRequest();
        request.setChamberId(6L);
        request.setCrystallizationPressure(new BigDecimal("0.1"));
        request.setAdhesionStrength(new BigDecimal("1.8"));
        request.setCycleCount7d(1);
        request.setAvgDailyRhFluctuation(new BigDecimal("1.0"));
        request.setTemperatureVariation(new BigDecimal("1.0"));

        when(delaminationRiskRepository.save(any(DelaminationRiskRecord.class))).thenAnswer(invocation -> {
            DelaminationRiskRecord record = invocation.getArgument(0);
            record.setId(6L);
            return record;
        });

        DelaminationRiskDTO result = delaminationRiskService.assessRisk(request);

        assertNotNull(result);
        double probability = result.getDelaminationProbability().doubleValue();
        assertTrue(probability <= 0.5,
                "良好条件概率应较低(<=50%)，实际: " + probability);
        assertEquals("LOW", result.getRiskLevel(),
                "良好条件风险等级应为LOW，实际: " + result.getRiskLevel());

        log.info("[测试通过] 低风险场景 - 概率: {:.2%}, 等级: {}", probability, result.getRiskLevel());
    }

    @Test
    @DisplayName("7. 输入极大值不报错，概率<=1")
    void testAssessRisk_ExtremeValues_TruncatedSafely() {
        log.info("[测试开始] 极端值输入场景");

        DelaminationAssessmentRequest request = new DelaminationAssessmentRequest();
        request.setChamberId(7L);
        request.setCrystallizationPressure(new BigDecimal("999.0"));
        request.setAdhesionStrength(new BigDecimal("0.001"));
        request.setCycleCount7d(9999);
        request.setAvgDailyRhFluctuation(new BigDecimal("999.0"));
        request.setTemperatureVariation(new BigDecimal("999.0"));

        when(delaminationRiskRepository.save(any(DelaminationRiskRecord.class))).thenAnswer(invocation -> {
            DelaminationRiskRecord record = invocation.getArgument(0);
            record.setId(7L);
            return record;
        });

        DelaminationRiskDTO result = delaminationRiskService.assessRisk(request);

        assertNotNull(result);
        double probability = result.getDelaminationProbability().doubleValue();
        assertTrue(probability <= 1.0,
                "概率不应超过1.0，实际: " + probability);
        assertTrue(probability >= 0, "概率不应小于0");
        assertNotNull(result.getRiskLevel());

        log.info("[测试通过] 极端值处理 - 概率: {:.2%}, 等级: {}", probability, result.getRiskLevel());
    }

    @Test
    @DisplayName("8. 完美条件-概率接近0")
    void testAssessRisk_ZeroProbability_Safe() {
        log.info("[测试开始] 完美条件近0概率场景");

        DelaminationAssessmentRequest request = new DelaminationAssessmentRequest();
        request.setChamberId(8L);
        request.setCrystallizationPressure(new BigDecimal("0.0"));
        request.setAdhesionStrength(new BigDecimal("2.0"));
        request.setCycleCount7d(0);
        request.setAvgDailyRhFluctuation(new BigDecimal("0.0"));
        request.setTemperatureVariation(new BigDecimal("0.0"));

        when(delaminationRiskRepository.save(any(DelaminationRiskRecord.class))).thenAnswer(invocation -> {
            DelaminationRiskRecord record = invocation.getArgument(0);
            record.setId(8L);
            return record;
        });

        DelaminationRiskDTO result = delaminationRiskService.assessRisk(request);

        assertNotNull(result);
        double probability = result.getDelaminationProbability().doubleValue();
        assertTrue(probability < 0.2,
                "完美条件概率应接近0(<20%)，实际: " + probability);
        assertEquals("LOW", result.getRiskLevel());

        log.info("[测试通过] 完美条件 - 概率: {:.2%}, 等级: {}", probability, result.getRiskLevel());
    }

    // ===== 异常场景测试 =====

    @Test
    @DisplayName("9. 压力为负抛异常")
    void testAssessRisk_NegativePressure_ThrowsException() {
        log.info("[测试开始] 压力为负异常场景");

        DelaminationAssessmentRequest request = new DelaminationAssessmentRequest();
        request.setChamberId(9L);
        request.setCrystallizationPressure(new BigDecimal("-1.0"));
        request.setAdhesionStrength(new BigDecimal("1.0"));
        request.setCycleCount7d(10);
        request.setAvgDailyRhFluctuation(new BigDecimal("20.0"));
        request.setTemperatureVariation(new BigDecimal("10.0"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> delaminationRiskService.assessRisk(request));

        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("负数") || exception.getMessage().contains("负"),
                "异常消息应提及负数，实际: " + exception.getMessage());
        log.info("[测试通过] 压力为负抛出异常: {}", exception.getMessage());
    }

    @Test
    @DisplayName("10. Repository抛异常正确传播")
    void testAssessRisk_RepositorySaveFails_PropagatesException() {
        log.info("[测试开始] Repository保存失败场景");

        DelaminationAssessmentRequest request = buildBasicRequest();
        request.setChamberId(10L);

        when(delaminationRiskRepository.save(any(DelaminationRiskRecord.class)))
                .thenThrow(new RuntimeException("数据库写入超时"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> delaminationRiskService.assessRisk(request));

        assertEquals("数据库写入超时", exception.getMessage());
        log.info("[测试通过] Repository异常正确传播: {}", exception.getMessage());
    }

    private DelaminationAssessmentRequest buildBasicRequest() {
        DelaminationAssessmentRequest request = new DelaminationAssessmentRequest();
        request.setCrystallizationPressure(new BigDecimal("2.0"));
        request.setAdhesionStrength(new BigDecimal("1.0"));
        request.setCycleCount7d(10);
        request.setAvgDailyRhFluctuation(new BigDecimal("20.0"));
        request.setTemperatureVariation(new BigDecimal("10.0"));
        return request;
    }
}
