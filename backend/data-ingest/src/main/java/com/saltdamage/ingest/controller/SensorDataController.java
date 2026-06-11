package com.saltdamage.ingest.controller;

import com.saltdamage.common.ApiResponse;
import com.saltdamage.common.message.KafkaTopics;
import com.saltdamage.common.message.SensorDataMessage;
import com.saltdamage.dto.BatchSensorDataRequest;
import com.saltdamage.dto.SensorDataRequest;
import com.saltdamage.ingest.producer.SensorDataKafkaProducer;
import com.saltdamage.ingest.repository.SaltDataRepository;
import cn.hutool.core.util.IdUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/sensor")
@RequiredArgsConstructor
public class SensorDataController {

    private final SensorDataKafkaProducer kafkaProducer;
    private final SaltDataRepository saltDataRepository;

    @PostMapping("/data")
    public ApiResponse<Void> receiveData(@Valid @RequestBody SensorDataRequest request) {
        log.info("接收传感器数据, deviceCode: {}, sensorType: {}", request.getDeviceCode(), request.getSensorType());

        SensorDataMessage message = convertToMessage(request);
        kafkaProducer.publishSensorData(message);

        return ApiResponse.success();
    }

    @PostMapping("/data/batch")
    public ApiResponse<Void> receiveBatchData(@Valid @RequestBody BatchSensorDataRequest request) {
        log.info("接收批量传感器数据, count: {}", request.getDataList().size());

        List<SensorDataMessage> messages = new ArrayList<>();
        for (SensorDataRequest item : request.getDataList()) {
            messages.add(convertToMessage(item));
        }
        kafkaProducer.publishBatch(messages);

        return ApiResponse.success();
    }

    @GetMapping("/data/latest/{deviceNo}")
    public ApiResponse<Object> getLatestData(@PathVariable String deviceNo) {
        log.info("查询设备最新数据, deviceNo: {}", deviceNo);

        Object latestData = saltDataRepository.findLatestByDeviceId(Long.valueOf(deviceNo));
        return ApiResponse.success(latestData);
    }

    private SensorDataMessage convertToMessage(SensorDataRequest request) {
        SensorDataMessage message = new SensorDataMessage();
        message.setMessageId(IdUtil.fastSimpleUUID());
        message.setTimestamp(System.currentTimeMillis());
        message.setDeviceId(request.getDeviceCode());
        message.setSensorType(request.getSensorType());

        Map<String, Object> data = request.getData();
        if (data != null) {
            extractDouble(data, "naPlus").ifPresent(message::setNaPlus);
            extractDouble(data, "ca2Plus").ifPresent(message::setCa2Plus);
            extractDouble(data, "so42Minus").ifPresent(message::setSo42Minus);
            extractDouble(data, "clMinus").ifPresent(message::setClMinus);
            extractDouble(data, "totalSalt").ifPresent(message::setTotalSalt);
            extractDouble(data, "temperature").ifPresent(message::setTemperature);
            extractDouble(data, "humidity").ifPresent(message::setHumidity);
            extractDouble(data, "windSpeed").ifPresent(message::setWindSpeed);
            extractDouble(data, "positionX").ifPresent(message::setPositionX);
            extractDouble(data, "positionY").ifPresent(message::setPositionY);
            extractDouble(data, "positionZ").ifPresent(message::setPositionZ);
            if (data.containsKey("tombId")) {
                message.setTombId(String.valueOf(data.get("tombId")));
            }
            if (data.containsKey("chamberId")) {
                message.setChamberId(String.valueOf(data.get("chamberId")));
            }
        }

        return message;
    }

    private java.util.Optional<Double> extractDouble(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return java.util.Optional.of(((Number) value).doubleValue());
        }
        return java.util.Optional.empty();
    }
}
