package com.saltdamage.controller;

import com.saltdamage.common.ApiResponse;
import com.saltdamage.dto.AnalysisResultDTO;
import com.saltdamage.dto.AnalysisRunRequest;
import com.saltdamage.dto.PredictionDTO;
import com.saltdamage.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/result")
    public ApiResponse<Page<AnalysisResultDTO>> getAnalysisResults(
            @RequestParam(required = false) Long tombId,
            @RequestParam(required = false) Long chamberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询分析结果, tombId: {}, chamberId: {}, page: {}, size: {}", tombId, chamberId, page, size);
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<AnalysisResultDTO> results = analysisService.getAnalysisResults(tombId, chamberId, pageable);
            return ApiResponse.success(results);
        } catch (Exception e) {
            log.error("查询分析结果失败", e);
            return ApiResponse.error(500, "查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/result/latest")
    public ApiResponse<AnalysisResultDTO> getLatestAnalysisResult(
            @RequestParam(required = false) Long tombId,
            @RequestParam(required = false) Long chamberId) {
        log.info("获取最新分析结果, tombId: {}, chamberId: {}", tombId, chamberId);
        try {
            AnalysisResultDTO result = analysisService.getLatestAnalysisResult(tombId, chamberId);
            if (result != null) {
                return ApiResponse.success(result);
            } else {
                return ApiResponse.error(404, "未找到分析结果");
            }
        } catch (Exception e) {
            log.error("获取最新分析结果失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @PostMapping("/run")
    public ApiResponse<AnalysisResultDTO> runAnalysis(@RequestBody AnalysisRunRequest request) {
        log.info("执行盐害分析, tombId: {}, chamberId: {}", request.getTombId(), request.getChamberId());
        try {
            AnalysisResultDTO result = analysisService.runAnalysis(request);
            return ApiResponse.success("分析执行成功", result);
        } catch (Exception e) {
            log.error("执行盐害分析失败", e);
            return ApiResponse.error(500, "分析失败: " + e.getMessage());
        }
    }

    @GetMapping("/prediction")
    public ApiResponse<List<PredictionDTO>> getPredictions(
            @RequestParam(required = false) Long tombId,
            @RequestParam(required = false) Long chamberId,
            @RequestParam(defaultValue = "7") int days) {
        log.info("获取预测数据, tombId: {}, chamberId: {}, days: {}", tombId, chamberId, days);
        try {
            List<PredictionDTO> predictions = analysisService.getPredictions(tombId, chamberId, days);
            return ApiResponse.success(predictions);
        } catch (Exception e) {
            log.error("获取预测数据失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/prediction/latest")
    public ApiResponse<PredictionDTO> getLatestPrediction(
            @RequestParam(required = false) Long tombId,
            @RequestParam(required = false) Long chamberId) {
        log.info("获取最新预测数据, tombId: {}, chamberId: {}", tombId, chamberId);
        try {
            PredictionDTO prediction = analysisService.getLatestPrediction(tombId, chamberId);
            if (prediction != null) {
                return ApiResponse.success(prediction);
            } else {
                return ApiResponse.error(404, "未找到预测数据");
            }
        } catch (Exception e) {
            log.error("获取最新预测数据失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }
}
