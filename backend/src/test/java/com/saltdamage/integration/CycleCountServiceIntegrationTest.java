package com.saltdamage.integration;

import com.saltdamage.algorithm.RainflowCycleCounter;
import com.saltdamage.dto.CycleCountDTO;
import com.saltdamage.dto.CycleCountRequest;
import com.saltdamage.entity.CycleCountRecord;
import com.saltdamage.entity.SensorData;
import com.saltdamage.repository.CycleCountRepository;
import com.saltdamage.repository.SensorDataRepository;
import com.saltdamage.service.CycleCountService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("一、CycleCountService 循环计数服务集成测试")
class CycleCountServiceIntegrationTest {

    @Autowired
    private CycleCountService cycleCountService;

    @MockBean
    private CycleCountRepository cycleCountRepository;

    @MockBean
    private SensorDataRepository sensorDataRepository;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @BeforeEach
    void setUp() {
        startTime = LocalDateTime.now().minusDays(7);
        endTime = LocalDateTime.now();
    }

    // ===== 正常场景测试 =====

    @Test
    @DisplayName("1. 合法请求-传入7天RH数据，返回成功结果")
    void testCountCycles_ValidRequest_ReturnsResult() {
        log.info("[测试开始] 合法请求，传入7天RH数据");

        List<SensorData> sensorDataList = generateSensorData(100, 1L);
        when(sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(sensorDataList);
        when(cycleCountRepository.save(any(CycleCountRecord.class))).thenAnswer(invocation -> {
            CycleCountRecord record = invocation.getArgument(0);
            record.setId(1L);
            return record;
        });

        CycleCountRequest request = new CycleCountRequest();
        request.setChamberId(1L);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setPeriodType("WEEKLY");

        CycleCountDTO result = cycleCountService.countCycles(request);

        assertNotNull(result, "返回结果不应为空");
        assertNotNull(result.getTotalCycles(), "总循环次数不应为空");
        assertNotNull(result.getDamageLevel(), "损伤等级不应为空");
        assertNotNull(result.getTotalDamage(), "总损伤不应为空");
        assertTrue(result.getTotalCycles() >= 0, "总循环次数应非负");
        log.info("[测试通过] 总循环次数: {}, 损伤等级: {}", result.getTotalCycles(), result.getDamageLevel());
    }

    @Test
    @DisplayName("2. countCycles调用后，Repository的save方法被调用1次")
    void testCountCycles_SavesRecordToDatabase() {
        log.info("[测试开始] 验证Repository.save方法被调用");

        List<SensorData> sensorDataList = generateSensorData(50, 1L);
        when(sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(sensorDataList);
        when(cycleCountRepository.save(any(CycleCountRecord.class))).thenAnswer(invocation -> {
            CycleCountRecord record = invocation.getArgument(0);
            record.setId(2L);
            return record;
        });

        CycleCountRequest request = new CycleCountRequest();
        request.setChamberId(1L);
        request.setStartTime(startTime);
        request.setEndTime(endTime);

        cycleCountService.countCycles(request);

        verify(cycleCountRepository, times(1)).save(any(CycleCountRecord.class));
        ArgumentCaptor<CycleCountRecord> captor = ArgumentCaptor.forClass(CycleCountRecord.class);
        verify(cycleCountRepository).save(captor.capture());
        CycleCountRecord savedRecord = captor.getValue();
        assertEquals(1L, savedRecord.getChamberId());
        assertNotNull(savedRecord.getTotalCycles());
        log.info("[测试通过] save方法正确调用，保存的chamberId: {}", savedRecord.getChamberId());
    }

    @Test
    @DisplayName("3. 查询最新记录正常返回")
    void testGetLatestCycleCount_ReturnsLatest() {
        log.info("[测试开始] 查询最新循环计数记录");

        CycleCountRecord mockRecord = new CycleCountRecord();
        mockRecord.setId(10L);
        mockRecord.setChamberId(1L);
        mockRecord.setTotalCycles(25);
        mockRecord.setDamageLevel("MEDIUM");
        mockRecord.setTotalDamage(new BigDecimal("0.25"));
        mockRecord.setAnalysisTime(LocalDateTime.now());

        when(cycleCountRepository.findFirstByChamberIdOrderByAnalysisTimeDesc(1L))
                .thenReturn(Optional.of(mockRecord));

        CycleCountDTO result = cycleCountService.getLatestCycleCount(null, 1L);

        assertNotNull(result, "最新记录不应为空");
        assertEquals(10L, result.getId());
        assertEquals(25, result.getTotalCycles());
        assertEquals("MEDIUM", result.getDamageLevel());
        log.info("[测试通过] 最新记录ID: {}, 总循环: {}", result.getId(), result.getTotalCycles());
    }

    // ===== 边界场景测试 =====

    @Test
    @DisplayName("4. 没有传感器数据时抛出IllegalArgumentException")
    void testCountCycles_NoSensorData_ThrowsException() {
        log.info("[测试开始] 无传感器数据场景");

        when(sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        CycleCountRequest request = new CycleCountRequest();
        request.setChamberId(1L);
        request.setStartTime(startTime);
        request.setEndTime(endTime);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cycleCountService.countCycles(request));

        assertTrue(exception.getMessage().contains("没有找到"),
                "异常消息应包含'没有找到'，实际: " + exception.getMessage());
        log.info("[测试通过] 抛出异常: {}", exception.getMessage());
    }

    @Test
    @DisplayName("5. startTime > endTime抛异常")
    void testCountCycles_InvalidTimeRange_ThrowsException() {
        log.info("[测试开始] 无效时间范围场景");

        CycleCountRequest request = new CycleCountRequest();
        request.setChamberId(1L);
        request.setStartTime(endTime);
        request.setEndTime(startTime);

        when(sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cycleCountService.countCycles(request));

        assertNotNull(exception);
        log.info("[测试通过] 时间范围无效，异常信息: {}", exception.getMessage());
    }

    @Test
    @DisplayName("6. 很短时间段（1小时）只有少量数据，返回低循环数、低损伤")
    void testCountCycles_ShortPeriod_LowCycles() {
        log.info("[测试开始] 短时间段少数据场景");

        List<SensorData> sensorDataList = generateSensorData(5, 2L);
        LocalDateTime shortStart = LocalDateTime.now().minusHours(1);
        LocalDateTime shortEnd = LocalDateTime.now();

        when(sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                eq(2L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(sensorDataList);
        when(cycleCountRepository.save(any(CycleCountRecord.class))).thenAnswer(invocation -> {
            CycleCountRecord record = invocation.getArgument(0);
            record.setId(3L);
            return record;
        });

        CycleCountRequest request = new CycleCountRequest();
        request.setChamberId(2L);
        request.setStartTime(shortStart);
        request.setEndTime(shortEnd);

        CycleCountDTO result = cycleCountService.countCycles(request);

        assertNotNull(result);
        assertNotNull(result.getTotalCycles());
        assertTrue(result.getTotalCycles() <= 10, "短时间循环数应较少，实际: " + result.getTotalCycles());
        assertEquals("LOW", result.getDamageLevel(), "少数据损伤等级应为LOW");
        log.info("[测试通过] 短时间循环数: {}, 损伤等级: {}", result.getTotalCycles(), result.getDamageLevel());
    }

    // ===== 异常场景测试 =====

    @Test
    @DisplayName("7. Mock Repository抛出RuntimeException，Service应正确传播")
    void testCountCycles_RepositoryException_Propagates() {
        log.info("[测试开始] Repository异常传播场景");

        List<SensorData> sensorDataList = generateSensorData(30, 3L);
        when(sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(sensorDataList);
        when(cycleCountRepository.save(any(CycleCountRecord.class)))
                .thenThrow(new RuntimeException("数据库连接失败"));

        CycleCountRequest request = new CycleCountRequest();
        request.setChamberId(3L);
        request.setStartTime(startTime);
        request.setEndTime(endTime);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> cycleCountService.countCycles(request));

        assertEquals("数据库连接失败", exception.getMessage());
        log.info("[测试通过] 异常正确传播: {}", exception.getMessage());
    }

    @Test
    @DisplayName("8. 分页参数page/size正确传递给Repository")
    void testGetCycleCountList_PaginationCorrect() {
        log.info("[测试开始] 分页查询场景");

        List<CycleCountRecord> mockRecords = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            CycleCountRecord r = new CycleCountRecord();
            r.setId((long) (i + 1));
            r.setChamberId(5L);
            r.setTotalCycles(10 + i);
            r.setAnalysisTime(LocalDateTime.now().minusHours(i));
            mockRecords.add(r);
        }
        Page<CycleCountRecord> mockPage = new PageImpl<>(mockRecords, PageRequest.of(1, 10), 25L);
        when(cycleCountRepository.findByChamberIdOrderByAnalysisTimeDesc(eq(5L), any(Pageable.class)))
                .thenReturn(mockPage);

        Pageable pageable = PageRequest.of(1, 10);
        Page<CycleCountDTO> resultPage = cycleCountService.getCycleCountList(null, 5L, null, pageable);

        assertNotNull(resultPage);
        assertEquals(5, resultPage.getContent().size());
        assertEquals(25L, resultPage.getTotalElements());

        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(cycleCountRepository).findByChamberIdOrderByAnalysisTimeDesc(eq(5L), pageCaptor.capture());
        assertEquals(1, pageCaptor.getValue().getPageNumber());
        assertEquals(10, pageCaptor.getValue().getPageSize());
        log.info("[测试通过] 分页参数正确: page={}, size={}",
                pageCaptor.getValue().getPageNumber(), pageCaptor.getValue().getPageSize());
    }

    @Test
    @DisplayName("9. 所有RH值相同，损伤为0")
    void testCountCycles_AllHumiditySame_NoDamage() {
        log.info("[测试开始] 所有RH值相同场景");

        List<SensorData> sensorDataList = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().minusDays(7);
        for (int i = 0; i < 50; i++) {
            SensorData data = new SensorData();
            data.setId((long) i);
            data.setChamberId(4L);
            data.setHumidity(new BigDecimal("50.0"));
            data.setCollectTime(base.plusMinutes(i * 30));
            sensorDataList.add(data);
        }

        when(sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
                eq(4L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(sensorDataList);
        when(cycleCountRepository.save(any(CycleCountRecord.class))).thenAnswer(invocation -> {
            CycleCountRecord record = invocation.getArgument(0);
            record.setId(4L);
            return record;
        });

        CycleCountRequest request = new CycleCountRequest();
        request.setChamberId(4L);
        request.setStartTime(startTime);
        request.setEndTime(endTime);

        CycleCountDTO result = cycleCountService.countCycles(request);

        assertNotNull(result);
        assertEquals(0, result.getTotalCycles(),
                "所有RH相同总循环应为0，实际: " + result.getTotalCycles());
        assertEquals(new BigDecimal("0.00000000").compareTo(result.getTotalDamage()), 0,
                "损伤值应为0，实际: " + result.getTotalDamage());
        assertEquals("LOW", result.getDamageLevel());
        log.info("[测试通过] 无波动数据 - 循环: {}, 损伤: {}, 等级: {}",
                result.getTotalCycles(), result.getTotalDamage(), result.getDamageLevel());
    }

    @Test
    @DisplayName("10. CRITICAL等级生成的建议包含紧急等关键词")
    void testAnalyzeCycleDamage_CriticalLevel_CorrectSuggestion() {
        log.info("[测试开始] CRITICAL等级损伤分析建议");

        CycleCountRecord criticalRecord = new CycleCountRecord();
        criticalRecord.setId(99L);
        criticalRecord.setChamberId(99L);
        criticalRecord.setPeriodStart(startTime);
        criticalRecord.setPeriodEnd(endTime);
        criticalRecord.setTotalCycles(100);
        criticalRecord.setFullCycles(80);
        criticalRecord.setPartialCycles(20);
        criticalRecord.setCrossingCycles(90);
        criticalRecord.setAverageRange(new BigDecimal("25.5"));
        criticalRecord.setMaxRange(new BigDecimal("40.0"));
        criticalRecord.setTotalDamage(new BigDecimal("2.5"));
        criticalRecord.setDamageLevel("CRITICAL");
        criticalRecord.setAnalysisTime(LocalDateTime.now());

        String suggestion = cycleCountService.analyzeCycleDamage(criticalRecord);

        assertNotNull(suggestion);
        assertTrue(suggestion.contains("紧急"), "CRITICAL建议应包含'紧急'关键词");
        assertTrue(suggestion.contains("临界") || suggestion.contains("警告"),
                "CRITICAL建议应包含'临界'或'警告'");
        assertTrue(suggestion.length() > 100, "建议内容应完整丰富");
        log.info("[测试通过] CRITICAL等级建议长度: {}，包含'紧急': {}",
                suggestion.length(), suggestion.contains("紧急"));
    }

    private List<SensorData> generateSensorData(int count, Long chamberId) {
        List<SensorData> dataList = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().minusDays(7);
        double baseRh = 55.0;
        for (int i = 0; i < count; i++) {
            SensorData data = new SensorData();
            data.setId((long) i);
            data.setChamberId(chamberId);
            double rh = baseRh + 30.0 * Math.sin(i * 0.3) + Math.random() * 5.0;
            rh = Math.max(30.0, Math.min(85.0, rh));
            data.setHumidity(new BigDecimal(rh).setScale(2, BigDecimal.ROUND_HALF_UP));
            data.setCollectTime(base.plusMinutes(i * 30));
            dataList.add(data);
        }
        return dataList;
    }
}
