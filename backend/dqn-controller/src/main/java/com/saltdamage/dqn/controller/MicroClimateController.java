package com.saltdamage.dqn.controller;

import com.saltdamage.dqn.dto.ControlRequest;
import com.saltdamage.dqn.dto.EnvironmentStateDTO;
import com.saltdamage.dqn.dto.MicroClimateControlDTO;
import com.saltdamage.dqn.service.MicroClimateControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/microclimate")
@RequiredArgsConstructor
public class MicroClimateController {

    private final MicroClimateControlService microClimateControlService;

    @GetMapping("/state")
    public ResponseEntity<EnvironmentStateDTO> getState(
            @RequestParam Long chamberId,
            @RequestParam(required = false) BigDecimal rh) {
        log.info("获取环境状态, chamberId: {}, rh: {}", chamberId, rh);
        EnvironmentStateDTO state = microClimateControlService.getCurrentState(chamberId, rh);
        return ResponseEntity.ok(state);
    }

    @PostMapping("/control")
    public ResponseEntity<MicroClimateControlDTO> control(@RequestBody ControlRequest request) {
        log.info("执行控制, chamberId: {}, mode: {}", request.getChamberId(), request.getMode());
        MicroClimateControlDTO result = microClimateControlService.executeControl(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/train/{episodes}")
    public ResponseEntity<Map<String, Object>> train(
            @PathVariable int episodes,
            @RequestParam(required = false) Long chamberId) {
        log.info("训练模型, episodes: {}, chamberId: {}", episodes, chamberId);
        List<Double> rewards = microClimateControlService.trainModel(episodes, chamberId);
        return ResponseEntity.ok(Map.of(
                "episodes", episodes,
                "rewards", rewards,
                "message", "训练完成"
        ));
    }

    @GetMapping("/history")
    public ResponseEntity<List<MicroClimateControlDTO>> getHistory(
            @RequestParam Long chamberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("获取控制历史, chamberId: {}, page: {}, size: {}", chamberId, page, size);
        List<MicroClimateControlDTO> history = microClimateControlService.getControlHistory(chamberId, page, size);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/suggestion")
    public ResponseEntity<MicroClimateControlDTO> getSuggestion(
            @RequestParam Long chamberId,
            @RequestParam(required = false) BigDecimal rh) {
        log.info("获取优化建议, chamberId: {}, rh: {}", chamberId, rh);
        MicroClimateControlDTO suggestion = microClimateControlService.getOptimizationSuggestion(chamberId, rh);
        return ResponseEntity.ok(suggestion);
    }
}
