package com.saltdamage.controller;

import com.saltdamage.common.ApiResponse;
import com.saltdamage.dto.ChamberDTO;
import com.saltdamage.dto.TombDTO;
import com.saltdamage.service.TombService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tomb")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class TombController {

    private final TombService tombService;

    @GetMapping("/list")
    public ApiResponse<List<TombDTO>> getTombList(@RequestParam(required = false) String status) {
        log.info("获取墓葬列表, status: {}", status);
        try {
            List<TombDTO> tombs = tombService.getTombList(status);
            return ApiResponse.success(tombs);
        } catch (Exception e) {
            log.error("获取墓葬列表失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<TombDTO> getTombById(@PathVariable Long id) {
        log.info("获取墓葬详情, id: {}", id);
        try {
            TombDTO tomb = tombService.getTombById(id);
            return ApiResponse.success(tomb);
        } catch (Exception e) {
            log.error("获取墓葬详情失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/{tombId}/chambers")
    public ApiResponse<List<ChamberDTO>> getChambersByTombId(@PathVariable Long tombId) {
        log.info("获取墓葬墓室列表, tombId: {}", tombId);
        try {
            List<ChamberDTO> chambers = tombService.getChambersByTombId(tombId);
            return ApiResponse.success(chambers);
        } catch (Exception e) {
            log.error("获取墓室列表失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/chamber/{id}")
    public ApiResponse<ChamberDTO> getChamberById(@PathVariable Long id) {
        log.info("获取墓室详情, id: {}", id);
        try {
            ChamberDTO chamber = tombService.getChamberById(id);
            return ApiResponse.success(chamber);
        } catch (Exception e) {
            log.error("获取墓室详情失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }
}
