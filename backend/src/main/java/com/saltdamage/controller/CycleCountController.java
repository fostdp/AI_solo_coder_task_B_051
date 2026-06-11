package com.saltdamage.controller;

import com.saltdamage.common.ApiResponse;
import com.saltdamage.dto.CycleCountDTO;
import com.saltdamage.dto.CycleCountRequest;
import com.saltdamage.service.CycleCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/cycle-count")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class CycleCountController {

    private final CycleCountService cycleCountService;

    @PostMapping("/count")
    public ApiResponse<CycleCountDTO> countCycles(@RequestBody CycleCountRequest request) {
        log.info("执行循环计数统计, tombId: {}, chamberId: {}", request.getTombId(), request.getChamberId());
        try {
            CycleCountDTO result = cycleCountService.countCycles(request);
            return ApiResponse.success("循环统计执行成功", result);
        } catch (IllegalArgumentException e) {
            log.warn("循环统计参数错误: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("执行循环统计失败", e);
            return ApiResponse.error(500, "统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse<Page<CycleCountDTO>> getCycleCountList(
            @RequestParam(required = false) Long tombId,
            @RequestParam(required = false) Long chamberId,
            @RequestParam(required = false) String periodType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询循环统计列表, tombId: {}, chamberId: {}, periodType: {}, page: {}, size: {}",
                tombId, chamberId, periodType, page, size);
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<CycleCountDTO> results = cycleCountService.getCycleCountList(tombId, chamberId, periodType, pageable);
            return ApiResponse.success(results);
        } catch (Exception e) {
            log.error("查询循环统计列表失败", e);
            return ApiResponse.error(500, "查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/latest")
    public ApiResponse<CycleCountDTO> getLatestCycleCount(
            @RequestParam(required = false) Long tombId,
            @RequestParam(required = false) Long chamberId) {
        log.info("获取最新循环统计, tombId: {}, chamberId: {}", tombId, chamberId);
        try {
            CycleCountDTO result = cycleCountService.getLatestCycleCount(tombId, chamberId);
            if (result != null) {
                return ApiResponse.success(result);
            } else {
                return ApiResponse.error(404, "未找到循环统计记录");
            }
        } catch (Exception e) {
            log.error("获取最新循环统计失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }
}
