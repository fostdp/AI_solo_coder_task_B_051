package com.saltdamage.service;

import com.saltdamage.dto.MonitorDataDTO;
import com.saltdamage.dto.StatisticsDTO;
import com.saltdamage.entity.SensorData;
import com.saltdamage.repository.AlarmRepository;
import com.saltdamage.repository.DeviceRepository;
import com.saltdamage.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {

    private final SensorDataRepository sensorDataRepository;
    private final DeviceRepository deviceRepository;
    private final AlarmRepository alarmRepository;

    public List<MonitorDataDTO> getSaltData(Long tombId, Long chamberId, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("查询盐离子数据, tombId: {}, chamberId: {}, startTime: {}, endTime: {}", tombId, chamberId, startTime, endTime);

        LocalDateTime start = startTime != null ? startTime : LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();

        List<SensorData> dataList;
        if (chamberId != null) {
            dataList = sensorDataRepository.findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(chamberId, start, end);
        } else if (tombId != null) {
            dataList = sensorDataRepository.findByTombIdAndCollectTimeBetweenOrderByCollectTimeDesc(tombId, start, end);
        } else {
            throw new IllegalArgumentException("tombId和chamberId不能同时为空");
        }

        return dataList.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<MonitorDataDTO> getEnvData(Long tombId, Long chamberId, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("查询微环境数据, tombId: {}, chamberId: {}, startTime: {}, endTime: {}", tombId, chamberId, startTime, endTime);
        return getSaltData(tombId, chamberId, startTime, endTime);
    }

    public MonitorDataDTO getRealtimeData(String deviceNo) {
        log.info("获取实时数据, deviceNo: {}", deviceNo);
        return sensorDataRepository.findFirstByDeviceNoOrderByCollectTimeDesc(deviceNo)
                .map(this::convertToDTO)
                .orElse(null);
    }

    public List<MonitorDataDTO> getRealtimeDataList(Long tombId) {
        log.info("获取墓葬所有设备实时数据, tombId: {}", tombId);
        List<SensorData> latestDataList = deviceRepository.findByTombId(tombId).stream()
                .map(device -> sensorDataRepository.findFirstByDeviceNoOrderByCollectTimeDesc(device.getDeviceNo()))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .collect(Collectors.toList());

        return latestDataList.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public StatisticsDTO getStatistics() {
        log.info("获取统计数据");
        StatisticsDTO statistics = new StatisticsDTO();

        Long totalDevices = deviceRepository.countTotalDevices();
        Long onlineDevices = deviceRepository.countByStatus("online");
        Long offlineDevices = totalDevices - onlineDevices;

        statistics.setDeviceCount(totalDevices);
        statistics.setOnlineDeviceCount(onlineDevices);
        statistics.setOfflineDeviceCount(offlineDevices);

        Long totalAlarms = alarmRepository.count();
        Long unprocessedAlarms = alarmRepository.countByStatus("unprocessed");
        statistics.setAlarmCount(totalAlarms);
        statistics.setUnprocessedAlarmCount(unprocessedAlarms);

        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.now();

        Long todayDataCount = sensorDataRepository.countTodayData();
        statistics.setDataCountToday(todayDataCount);

        try {
            List<SensorData> todayData = sensorDataRepository.findAll().stream()
                    .filter(d -> d.getCollectTime() != null
                            && !d.getCollectTime().isBefore(todayStart)
                            && !d.getCollectTime().isAfter(todayEnd))
                    .limit(1000)
                    .toList();

            if (!todayData.isEmpty()) {
                List<BigDecimal> saltValues = todayData.stream()
                        .filter(d -> d.getSaltConcentration() != null)
                        .map(SensorData::getSaltConcentration)
                        .toList();

                if (!saltValues.isEmpty()) {
                    BigDecimal avgSalt = saltValues.stream()
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(saltValues.size()), 4, BigDecimal.ROUND_HALF_UP);
                    BigDecimal maxSalt = saltValues.stream()
                            .max(BigDecimal::compareTo)
                            .orElse(BigDecimal.ZERO);

                    statistics.setAvgSaltConcentration(avgSalt);
                    statistics.setMaxSaltConcentration(maxSalt);
                } else {
                    statistics.setAvgSaltConcentration(BigDecimal.ZERO);
                    statistics.setMaxSaltConcentration(BigDecimal.ZERO);
                }
            } else {
                statistics.setAvgSaltConcentration(BigDecimal.ZERO);
                statistics.setMaxSaltConcentration(BigDecimal.ZERO);
            }
        } catch (Exception e) {
            log.warn("计算盐离子统计数据失败", e);
            statistics.setAvgSaltConcentration(BigDecimal.ZERO);
            statistics.setMaxSaltConcentration(BigDecimal.ZERO);
        }

        try {
            List<SensorData> recentData = sensorDataRepository.findAll().stream()
                    .limit(100)
                    .collect(Collectors.toList());

            if (!recentData.isEmpty()) {
                BigDecimal avgTemp = recentData.stream()
                        .filter(d -> d.getTemperature() != null)
                        .map(SensorData::getTemperature)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(recentData.stream().filter(d -> d.getTemperature() != null).count()), 4, BigDecimal.ROUND_HALF_UP);

                BigDecimal avgHumidity = recentData.stream()
                        .filter(d -> d.getHumidity() != null)
                        .map(SensorData::getHumidity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(recentData.stream().filter(d -> d.getHumidity() != null).count()), 4, BigDecimal.ROUND_HALF_UP);

                statistics.setAvgTemperature(avgTemp);
                statistics.setAvgHumidity(avgHumidity);
            }
        } catch (Exception e) {
            log.warn("计算温湿度统计数据失败", e);
        }

        return statistics;
    }

    private MonitorDataDTO convertToDTO(SensorData sensorData) {
        MonitorDataDTO dto = new MonitorDataDTO();
        dto.setId(sensorData.getId());
        dto.setDeviceNo(sensorData.getDeviceNo());
        dto.setTombId(sensorData.getTombId());
        dto.setChamberId(sensorData.getChamberId());
        dto.setSaltConcentration(sensorData.getSaltConcentration());
        dto.setTemperature(sensorData.getTemperature());
        dto.setHumidity(sensorData.getHumidity());
        dto.setPhValue(sensorData.getPhValue());
        dto.setCo2Concentration(sensorData.getCo2Concentration());
        dto.setIlluminance(sensorData.getIlluminance());
        dto.setPressure(sensorData.getPressure());
        dto.setTotalSaltAmount(sensorData.getTotalSaltAmount());
        dto.setCollectTime(sensorData.getCollectTime());
        return dto;
    }
}
