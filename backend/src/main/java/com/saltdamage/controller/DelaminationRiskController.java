package com.saltdamage.controller;

import com.saltdamage.common.ApiResponse;
import com.saltdamage.dto.DelaminationAssessmentRequest;
import com.saltdamage.dto.DelaminationRiskDTO;
import com.saltdamage.service.DelaminationRiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/delamination")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class DelaminationRiskController {

    private final DelaminationRiskService delaminationRiskService;

    @PostMapping("/assess")
    public ApiResponse<DelaminationRiskDTO> assessRisk(@RequestBody DelaminationAssessmentRequest request) {
        log.info("执行起甲风险评估, tombId: {}, chamberId: {}", request.getTombId(), request.getChamberId());
        try {
            DelaminationRiskDTO result = delaminationRiskService.assessRisk(request);
            return ApiResponse.success("评估完成", result);
        } catch (IllegalArgumentException e) {
            log.warn("评估参数错误: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("起甲风险评估失败", e);
            return ApiResponse.error(500, "评估失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse<Page<DelaminationRiskDTO>> getRiskList(
            @RequestParam(required = false) Long tombId,
            @RequestParam(required = false) Long chamberId,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询起甲风险记录, tombId: {}, chamberId: {}, riskLevel: {}, page: {}, size: {}",
                tombId, chamberId, riskLevel, page, size);
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<DelaminationRiskDTO> results = delaminationRiskService.getRiskList(tombId, chamberId, riskLevel, pageable);
            return ApiResponse.success(results);
        } catch (Exception e) {
            log.error("查询起甲风险记录失败", e);
            return ApiResponse.error(500, "查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/latest")
    public ApiResponse<DelaminationRiskDTO> getLatestRisk(
            @RequestParam(required = false) Long tombId,
            @RequestParam(required = false) Long chamberId) {
        log.info("获取最新起甲风险评估, tombId: {}, chamberId: {}", tombId, chamberId);
        try {
            DelaminationRiskDTO result = delaminationRiskService.getLatestRisk(tombId, chamberId);
            if (result != null) {
                return ApiResponse.success(result);
            } else {
                return ApiResponse.error(404, "未找到评估记录");
            }
        } catch (Exception e) {
            log.error("获取最新起甲风险评估失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getRiskStatistics(
            @RequestParam Long tombId,
            @RequestParam(defaultValue = "7") int days) {
        log.info("获取起甲风险统计, tombId: {}, days: {}", tombId, days);
        try {
            Map<String, Object> statistics = delaminationRiskService.getRiskStatistics(tombId, days);
            return ApiResponse.success(statistics);
        } catch (Exception e) {
            log.error("获取起甲风险统计失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }
}
