package com.saltdamage.rainflow.controller;

import com.saltdamage.rainflow.algorithm.RainflowCycleCounter;
import com.saltdamage.rainflow.dto.CycleCountDTO;
import com.saltdamage.rainflow.dto.CycleCountRequest;
import com.saltdamage.rainflow.entity.CycleCountRecord;
import com.saltdamage.rainflow.repository.CycleCountRepository;
import com.saltdamage.rainflow.service.RainflowCycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/cycle-count")
@RequiredArgsConstructor
public class RainflowCycleController {

    private final RainflowCycleService rainflowCycleService;
    private final CycleCountRepository cycleCountRepository;

    /**
     * Submit humidity data for async cycle counting.
     *
     * @param request cycle count request with humidity data
     * @return CompletableFuture with the counting result
     */
    @PostMapping("/count")
    public CompletableFuture<ResponseEntity<RainflowCycleCounter.RainflowResult>> countCycles(
            @RequestBody CycleCountRequest request) {
        return rainflowCycleService.countCyclesAsync(request.getHumidityData())
                .thenApply(result -> {
                    rainflowCycleService.saveResult(result, request);
                    return ResponseEntity.ok(result);
                });
    }

    /**
     * List cycle count records with pagination.
     *
     * @param page page number (0-based)
     * @param size page size
     * @return paginated list of cycle count DTOs
     */
    @GetMapping("/list")
    public ResponseEntity<Page<CycleCountDTO>> listRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "analysisTime"));
        Page<CycleCountRecord> records = cycleCountRepository.findAll(pageable);
        Page<CycleCountDTO> dtos = records.map(this::toDTO);
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get the latest cycle count record for a specific chamber.
     *
     * @param chamberId the chamber identifier
     * @return the latest cycle count DTO or 404
     */
    @GetMapping("/latest")
    public ResponseEntity<CycleCountDTO> getLatest(@RequestParam Long chamberId) {
        return cycleCountRepository.findFirstByChamberIdOrderByAnalysisTimeDesc(chamberId)
                .map(record -> ResponseEntity.ok(toDTO(record)))
                .orElse(ResponseEntity.notFound().build());
    }

    private CycleCountDTO toDTO(CycleCountRecord record) {
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
