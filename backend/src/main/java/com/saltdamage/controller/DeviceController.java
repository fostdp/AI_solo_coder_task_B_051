package com.saltdamage.controller;

import com.saltdamage.common.ApiResponse;
import com.saltdamage.dto.DeviceDTO;
import com.saltdamage.dto.DeviceUpdateRequest;
import com.saltdamage.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping("/list")
    public ApiResponse<List<DeviceDTO>> getDeviceList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long tombId,
            @RequestParam(required = false) Long chamberId) {
        log.info("获取设备列表, status: {}, tombId: {}, chamberId: {}", status, tombId, chamberId);
        try {
            List<DeviceDTO> devices = deviceService.getDeviceList(status, tombId, chamberId);
            return ApiResponse.success(devices);
        } catch (Exception e) {
            log.error("获取设备列表失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<DeviceDTO> getDeviceById(@PathVariable Long id) {
        log.info("获取设备详情, id: {}", id);
        try {
            DeviceDTO device = deviceService.getDeviceById(id);
            return ApiResponse.success(device);
        } catch (Exception e) {
            log.error("获取设备详情失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/no/{deviceNo}")
    public ApiResponse<DeviceDTO> getDeviceByNo(@PathVariable String deviceNo) {
        log.info("根据设备编号获取设备, deviceNo: {}", deviceNo);
        try {
            DeviceDTO device = deviceService.getDeviceByNo(deviceNo);
            return ApiResponse.success(device);
        } catch (Exception e) {
            log.error("获取设备失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<DeviceDTO> updateDevice(
            @PathVariable Long id,
            @Valid @RequestBody DeviceUpdateRequest request) {
        log.info("更新设备信息, id: {}", id);
        try {
            DeviceDTO device = deviceService.updateDevice(id, request);
            return ApiResponse.success("设备更新成功", device);
        } catch (Exception e) {
            log.error("更新设备失败", e);
            return ApiResponse.error(500, "更新失败: " + e.getMessage());
        }
    }

    @GetMapping("/online")
    public ApiResponse<Map<String, Object>> getOnlineStatistics() {
        log.info("获取在线设备统计");
        try {
            Map<String, Object> statistics = deviceService.getOnlineStatistics();
            return ApiResponse.success(statistics);
        } catch (Exception e) {
            log.error("获取在线设备统计失败", e);
            return ApiResponse.error(500, "获取失败: " + e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<DeviceDTO> createDevice(@RequestBody DeviceDTO deviceDTO) {
        log.info("创建设备, deviceNo: {}", deviceDTO.getDeviceNo());
        try {
            DeviceDTO device = deviceService.createDevice(deviceDTO);
            return ApiResponse.success("设备创建成功", device);
        } catch (Exception e) {
            log.error("创建设备失败", e);
            return ApiResponse.error(500, "创建失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDevice(@PathVariable Long id) {
        log.info("删除设备, id: {}", id);
        try {
            deviceService.deleteDevice(id);
            return ApiResponse.success("设备删除成功");
        } catch (Exception e) {
            log.error("删除设备失败", e);
            return ApiResponse.error(500, "删除失败: " + e.getMessage());
        }
    }
}
