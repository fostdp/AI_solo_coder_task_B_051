package com.saltdamage.service;

import com.alibaba.fastjson2.JSON;
import com.saltdamage.dto.BatchSensorDataRequest;
import com.saltdamage.dto.SensorDataRequest;
import com.saltdamage.dto.SensorDataResponse;
import com.saltdamage.entity.Device;
import com.saltdamage.entity.SensorData;
import com.saltdamage.repository.DeviceRepository;
import com.saltdamage.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataService {

    private final SensorDataRepository sensorDataRepository;
    private final DeviceRepository deviceRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final AlarmService alarmService;
    private final WebSocketService webSocketService;

    private static final String REDIS_SENSOR_KEY_PREFIX = "sensor:realtime:";
    private static final String REDIS_SALT_TOTAL_PREFIX = "sensor:salt:total:";

    @Transactional(rollbackFor = Exception.class)
    public SensorDataResponse processSensorData(SensorDataRequest request) {
        log.info("开始处理传感器数据, deviceNo: {}, sensorType: {}", request.getDeviceNo(), request.getSensorType());

        validateRequest(request);

        SensorData sensorData = convertToEntity(request);

        BigDecimal totalSaltAmount = calculateTotalSaltAmount(request.getDeviceNo(), request.getSaltConcentration());
        sensorData.setTotalSaltAmount(totalSaltAmount);

        enrichDeviceInfo(sensorData, request.getDeviceNo());

        if (request.getCollectTime() == null) {
            sensorData.setCollectTime(LocalDateTime.now());
        }

        sensorData = sensorDataRepository.save(sensorData);
        log.info("传感器数据已保存, id: {}", sensorData.getId());

        updateRedisCache(sensorData);

        updateDeviceOnlineStatus(request.getDeviceNo());

        asyncProcessAfterSave(sensorData);

        return convertToResponse(sensorData);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<SensorDataResponse> processBatchSensorData(BatchSensorDataRequest request) {
        log.info("开始批量处理传感器数据, 数据条数: {}", request.getDataList().size());

        List<SensorDataResponse> responses = new ArrayList<>();
        for (SensorDataRequest dataRequest : request.getDataList()) {
            try {
                SensorDataResponse response = processSensorData(dataRequest);
                responses.add(response);
            } catch (Exception e) {
                log.error("处理单条传感器数据失败, deviceNo: {}, error: {}", dataRequest.getDeviceNo(), e.getMessage());
            }
        }

        log.info("批量处理完成, 成功: {} 条, 失败: {} 条", responses.size(), request.getDataList().size() - responses.size());
        return responses;
    }

    private void validateRequest(SensorDataRequest request) {
        if (request.getSaltConcentration().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("盐离子浓度不能为负数");
        }
        if (request.getTemperature() != null && (request.getTemperature().compareTo(new BigDecimal("-40")) < 0
                || request.getTemperature().compareTo(new BigDecimal("85")) > 0)) {
            throw new IllegalArgumentException("温度值超出合理范围");
        }
        if (request.getHumidity() != null && (request.getHumidity().compareTo(BigDecimal.ZERO) < 0
                || request.getHumidity().compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("湿度值超出合理范围");
        }
    }

    private SensorData convertToEntity(SensorDataRequest request) {
        SensorData sensorData = new SensorData();
        sensorData.setDeviceNo(request.getDeviceNo());
        sensorData.setSensorType(request.getSensorType());
        sensorData.setSaltConcentration(request.getSaltConcentration());
        sensorData.setTemperature(request.getTemperature());
        sensorData.setHumidity(request.getHumidity());
        sensorData.setPhValue(request.getPhValue());
        sensorData.setCo2Concentration(request.getCo2Concentration());
        sensorData.setIlluminance(request.getIlluminance());
        sensorData.setPressure(request.getPressure());
        sensorData.setCollectTime(request.getCollectTime());
        return sensorData;
    }

    private void enrichDeviceInfo(SensorData sensorData, String deviceNo) {
        deviceRepository.findByDeviceNo(deviceNo).ifPresent(device -> {
            sensorData.setTombId(device.getTombId());
            sensorData.setChamberId(device.getChamberId());
        });
    }

    private BigDecimal calculateTotalSaltAmount(String deviceNo, BigDecimal currentConcentration) {
        String key = REDIS_SALT_TOTAL_PREFIX + deviceNo;
        String totalStr = stringRedisTemplate.opsForValue().get(key);
        BigDecimal total = totalStr != null ? new BigDecimal(totalStr) : BigDecimal.ZERO;
        total = total.add(currentConcentration);
        stringRedisTemplate.opsForValue().set(key, total.toString(), 24, TimeUnit.HOURS);
        return total;
    }

    private void updateRedisCache(SensorData sensorData) {
        String key = REDIS_SENSOR_KEY_PREFIX + sensorData.getDeviceNo();
        String value = JSON.toJSONString(sensorData);
        stringRedisTemplate.opsForValue().set(key, value, 1, TimeUnit.HOURS);
        log.debug("Redis缓存已更新, key: {}", key);
    }

    private void updateDeviceOnlineStatus(String deviceNo) {
        deviceRepository.findByDeviceNo(deviceNo).ifPresent(device -> {
            device.setStatus("online");
            device.setLastOnlineTime(LocalDateTime.now());
            deviceRepository.save(device);
        });
    }

    @Async
    public void asyncProcessAfterSave(SensorData sensorData) {
        log.info("开始异步处理, sensorDataId: {}", sensorData.getId());

        try {
            alarmService.checkAndTriggerAlarm(sensorData);
            log.info("告警检测完成");
        } catch (Exception e) {
            log.error("告警检测失败", e);
        }

        try {
            webSocketService.pushRealtimeData(sensorData);
            log.info("WebSocket推送完成");
        } catch (Exception e) {
            log.error("WebSocket推送失败", e);
        }
    }

    private SensorDataResponse convertToResponse(SensorData sensorData) {
        SensorDataResponse response = new SensorDataResponse();
        response.setId(sensorData.getId());
        response.setDeviceNo(sensorData.getDeviceNo());
        response.setSensorType(sensorData.getSensorType());
        response.setSaltConcentration(sensorData.getSaltConcentration());
        response.setTemperature(sensorData.getTemperature());
        response.setHumidity(sensorData.getHumidity());
        response.setPhValue(sensorData.getPhValue());
        response.setCo2Concentration(sensorData.getCo2Concentration());
        response.setIlluminance(sensorData.getIlluminance());
        response.setPressure(sensorData.getPressure());
        response.setTotalSaltAmount(sensorData.getTotalSaltAmount());
        response.setCollectTime(sensorData.getCollectTime());
        response.setCreateTime(sensorData.getCreateTime());
        return response;
    }

    public SensorDataResponse getLatestData(String deviceNo) {
        return sensorDataRepository.findFirstByDeviceNoOrderByCollectTimeDesc(deviceNo)
                .map(this::convertToResponse)
                .orElse(null);
    }

    public List<SensorDataResponse> getHistoryData(String deviceNo, LocalDateTime startTime, LocalDateTime endTime) {
        List<SensorData> dataList = sensorDataRepository
                .findByDeviceNoAndCollectTimeBetweenOrderByCollectTimeDesc(deviceNo, startTime, endTime);
        return dataList.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
}
