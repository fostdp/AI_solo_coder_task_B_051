package com.saltdamage.controller;

import com.saltdamage.common.ApiResponse;
import com.saltdamage.dto.ControlRequest;
import com.saltdamage.dto.EnvironmentStateDTO;
import com.saltdamage.dto.MicroClimateControlDTO;
import com.saltdamage.service.MicroClimateControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/microclimate")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class MicroClimateController {

    private final MicroClimateControlService microClimateControlService;

    @GetMapping("/state/{chamberId}")
    public ApiResponse<EnvironmentStateDTO> getCurrentState(@PathVariable Long chamberId) {
        log.info("获取当前环境状态, chamberId: {}", chamberId);
        try {
            EnvironmentStateDTO state = microClimateControlService.getCurrentState(chamberId);
            return ApiResponse.success(state);
        } catch (Exception e) {
            log.error("获取当前环境状态失败", e);
            return ApiResponse.error(500, "获取状态失败: " + e.getMessage());
        }
    }

    @PostMapping("/control")
    public ApiResponse<MicroClimateControlDTO> executeControl(@RequestBody ControlRequest request) {
        log.info("执行控制操作, chamberId: {}, mode: {}", request.getChamberId(), request.getMode());
        try {
            MicroClimateControlDTO result = microClimateControlService.executeControl(request);
            return ApiResponse.success("控制执行成功", result);
        } catch (Exception e) {
            log.error("执行控制操作失败", e);
            return ApiResponse.error(500, "控制失败: " + e.getMessage());
        }
    }

    @PostMapping("/train")
    public ApiResponse<List<Double>> trainModel(
            @RequestParam(defaultValue = "100") int episodes,
            @RequestParam(required = false) Long chamberId) {
        log.info("训练DQN模型, episodes: {}, chamberId: {}", episodes, chamberId);
        try {
            List<Double> rewards = microClimateControlService.trainModel(episodes, chamberId);
            return ApiResponse.success("训练完成", rewards);
        } catch (Exception e) {
            log.error("训练DQN模型失败", e);
            return ApiResponse.error(500, "训练失败: " + e.getMessage());
        }
    }

    @GetMapping("/history")
    public ApiResponse<List<MicroClimateControlDTO>> getControlHistory(
            @RequestParam Long chamberId,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("获取控制历史, chamberId: {}, limit: {}", chamberId, limit);
        try {
            List<MicroClimateControlDTO> history = microClimateControlService.getControlHistory(chamberId, limit);
            return ApiResponse.success(history);
        } catch (Exception e) {
            log.error("获取控制历史失败", e);
            return ApiResponse.error(500, "获取历史失败: " + e.getMessage());
        }
    }

    @GetMapping("/suggestion/{chamberId}")
    public ApiResponse<MicroClimateControlDTO> getOptimizationSuggestion(@PathVariable Long chamberId) {
        log.info("获取优化建议, chamberId: {}", chamberId);
        try {
            MicroClimateControlDTO suggestion = microClimateControlService.getOptimizationSuggestion(chamberId);
            return ApiResponse.success(suggestion);
        } catch (Exception e) {
            log.error("获取优化建议失败", e);
            return ApiResponse.error(500, "获取建议失败: " + e.getMessage());
        }
    }
}
