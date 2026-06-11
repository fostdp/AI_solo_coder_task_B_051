package com.saltdamage.service;

import com.saltdamage.dto.DeviceDTO;
import com.saltdamage.dto.DeviceUpdateRequest;
import com.saltdamage.entity.Device;
import com.saltdamage.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public List<DeviceDTO> getDeviceList(String status, Long tombId, Long chamberId) {
        log.info("获取设备列表, status: {}, tombId: {}, chamberId: {}", status, tombId, chamberId);

        List<Device> devices;
        if (status != null && !status.isEmpty()) {
            devices = deviceRepository.findByStatus(status);
        } else if (chamberId != null) {
            devices = deviceRepository.findByChamberId(chamberId);
        } else if (tombId != null) {
            devices = deviceRepository.findByTombId(tombId);
        } else {
            devices = deviceRepository.findAll();
        }

        return devices.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public DeviceDTO getDeviceById(Long id) {
        log.info("获取设备详情, id: {}", id);
        return deviceRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));
    }

    public DeviceDTO getDeviceByNo(String deviceNo) {
        log.info("根据设备编号获取设备, deviceNo: {}", deviceNo);
        return deviceRepository.findByDeviceNo(deviceNo)
                .map(this::convertToDTO)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceDTO updateDevice(Long id, DeviceUpdateRequest request) {
        log.info("更新设备信息, id: {}, deviceName: {}", id, request.getDeviceName());

        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));

        device.setDeviceName(request.getDeviceName());
        device.setLocation(request.getLocation());
        device.setRemark(request.getDeviceName());

        device = deviceRepository.save(device);
        log.info("设备信息已更新, id: {}", id);

        return convertToDTO(device);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceDTO updateDeviceStatus(String deviceNo, String status) {
        log.info("更新设备状态, deviceNo: {}, status: {}", deviceNo, status);

        Device device = deviceRepository.findByDeviceNo(deviceNo)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));

        device.setStatus(status);
        if ("online".equals(status)) {
            device.setLastOnlineTime(LocalDateTime.now());
        }

        device = deviceRepository.save(device);
        log.info("设备状态已更新, deviceNo: {}, status: {}", deviceNo, status);

        return convertToDTO(device);
    }

    public Map<String, Object> getOnlineStatistics() {
        log.info("获取在线设备统计");

        Long total = deviceRepository.countTotalDevices();
        Long online = deviceRepository.countByStatus("online");
        Long offline = deviceRepository.countByStatus("offline");
        Long fault = deviceRepository.countByStatus("fault");
        Long maintenance = deviceRepository.countByStatus("maintenance");

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalCount", total);
        statistics.put("onlineCount", online);
        statistics.put("offlineCount", offline);
        statistics.put("faultCount", fault);
        statistics.put("maintenanceCount", maintenance);

        double onlineRate = total > 0 ? (double) online / total * 100 : 0;
        statistics.put("onlineRate", Math.round(onlineRate * 100) / 100.0);

        return statistics;
    }

    public boolean deviceExists(String deviceNo) {
        return deviceRepository.existsByDeviceNo(deviceNo);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceDTO createDevice(DeviceDTO deviceDTO) {
        log.info("创建设备, deviceNo: {}", deviceDTO.getDeviceNo());

        if (deviceRepository.existsByDeviceNo(deviceDTO.getDeviceNo())) {
            throw new IllegalArgumentException("设备编号已存在");
        }

        Device device = new Device();
        device.setDeviceNo(deviceDTO.getDeviceNo());
        device.setDeviceName(deviceDTO.getDeviceName());
        device.setDeviceType(deviceDTO.getDeviceType());
        device.setManufacturer(deviceDTO.getManufacturer());
        device.setModel(deviceDTO.getModel());
        device.setFirmwareVersion(deviceDTO.getFirmwareVersion());
        device.setIpAddress(deviceDTO.getIpAddress());
        device.setPort(deviceDTO.getPort());
        device.setStatus(deviceDTO.getStatus() != null ? deviceDTO.getStatus() : "offline");
        device.setTombId(deviceDTO.getTombId());
        device.setChamberId(deviceDTO.getChamberId());
        device.setLocation(deviceDTO.getLocation());
        device.setInstallTime(deviceDTO.getInstallTime());

        device = deviceRepository.save(device);
        log.info("设备已创建, id: {}, deviceNo: {}", device.getId(), device.getDeviceNo());

        return convertToDTO(device);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDevice(Long id) {
        log.info("删除设备, id: {}", id);

        if (!deviceRepository.existsById(id)) {
            throw new IllegalArgumentException("设备不存在");
        }

        deviceRepository.deleteById(id);
        log.info("设备已删除, id: {}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateLastOnlineTime(String deviceNo) {
        log.debug("更新设备最后在线时间, deviceNo: {}", deviceNo);

        deviceRepository.findByDeviceNo(deviceNo).ifPresent(device -> {
            device.setLastOnlineTime(LocalDateTime.now());
            deviceRepository.save(device);
        });
    }

    private DeviceDTO convertToDTO(Device device) {
        DeviceDTO dto = new DeviceDTO();
        dto.setId(device.getId());
        dto.setDeviceNo(device.getDeviceNo());
        dto.setDeviceName(device.getDeviceName());
        dto.setDeviceType(device.getDeviceType());
        dto.setManufacturer(device.getManufacturer());
        dto.setModel(device.getModel());
        dto.setFirmwareVersion(device.getFirmwareVersion());
        dto.setIpAddress(device.getIpAddress());
        dto.setPort(device.getPort());
        dto.setStatus(device.getStatus());
        dto.setTombId(device.getTombId());
        dto.setChamberId(device.getChamberId());
        dto.setLocation(device.getLocation());
        dto.setLastOnlineTime(device.getLastOnlineTime());
        dto.setInstallTime(device.getInstallTime());
        dto.setCreateTime(device.getCreateTime());
        dto.setUpdateTime(device.getUpdateTime());
        return dto;
    }
}
