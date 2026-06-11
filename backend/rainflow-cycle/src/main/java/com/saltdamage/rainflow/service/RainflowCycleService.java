package com.saltdamage.rainflow.service;

import com.saltdamage.rainflow.algorithm.RainflowCycleCounter;
import com.saltdamage.rainflow.dto.CycleCountRequest;
import com.saltdamage.rainflow.entity.CycleCountRecord;
import com.saltdamage.rainflow.repository.CycleCountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RainflowCycleService {

    private final RainflowCycleCounter counter;
    private final CycleCountRepository cycleCountRepository;

    @Qualifier("rainflowExecutor")
    private final Executor rainflowExecutor;

    /**
     * Asynchronously count cycles for the given humidity data.
     *
     * @param humidityData time-ordered relative humidity sequence
     * @return CompletableFuture wrapping the rainflow result
     */
    @Async("rainflowExecutor")
    public CompletableFuture<RainflowCycleCounter.RainflowResult> countCyclesAsync(List<Double> humidityData) {
        log.info("Async cycle counting started - data points: {}", humidityData.size());
        RainflowCycleCounter.RainflowResult result = counter.countCycles(humidityData);
        log.info("Async cycle counting completed - total cycles: {}", result.getTotalCycles());
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Synchronous cycle counting for backwards compatibility.
     *
     * @param humidityData time-ordered relative humidity sequence
     * @return rainflow result
     */
    public RainflowCycleCounter.RainflowResult countCycles(List<Double> humidityData) {
        return counter.countCycles(humidityData);
    }

    /**
     * Persist a cycle count record to the database.
     *
     * @param result  the rainflow counting result
     * @param request the original request context
     * @return the saved entity
     */
    @Transactional
    public CycleCountRecord saveResult(RainflowCycleCounter.RainflowResult result, CycleCountRequest request) {
        CycleCountRecord record = new CycleCountRecord();
        record.setTombId(request.getTombId());
        record.setChamberId(request.getChamberId());
        record.setDeviceId(request.getDeviceId());
        record.setPeriodType(request.getPeriodType());
        record.setPeriodStart(request.getStartTime());
        record.setPeriodEnd(request.getEndTime());
        record.setTotalCycles(result.getTotalCycles());
        record.setFullCycles(result.getFullCycles());
        record.setPartialCycles(result.getPartialCycles());
        record.setCrossingCycles(result.getCrossingCycles());
        record.setAverageRange(result.getAverageRange() > 0 ? BigDecimal.valueOf(result.getAverageRange()) : BigDecimal.ZERO);
        record.setMaxRange(result.getMaxRange() > 0 ? BigDecimal.valueOf(result.getMaxRange()) : BigDecimal.ZERO);
        record.setMinRange(result.getMinRange() > 0 ? BigDecimal.valueOf(result.getMinRange()) : BigDecimal.ZERO);
        record.setTotalDamage(BigDecimal.valueOf(result.getTotalDamage()));
        record.setDamageLevel(result.getDamageLevel().getDisplayName());
        record.setAmplitudeHistogram(formatHistogram(result.getAmplitudeHistogram()));
        record.setAnalysisTime(LocalDateTime.now());

        CycleCountRecord saved = cycleCountRepository.save(record);
        log.info("Cycle count record saved - id: {}, damage level: {}", saved.getId(), saved.getDamageLevel());
        return saved;
    }

    private String formatHistogram(double[][] histogram) {
        if (histogram == null || histogram.length == 0) {
            return "";
        }
        return Arrays.stream(histogram)
                .map(bin -> String.format("[%.2f,%.2f,%.0f]", bin[0], bin[1], bin[2]))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
