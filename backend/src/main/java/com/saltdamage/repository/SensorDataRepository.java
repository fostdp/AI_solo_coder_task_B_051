package com.saltdamage.repository;

import com.saltdamage.entity.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SensorDataRepository extends JpaRepository<SensorData, Long> {

    List<SensorData> findByDeviceNoOrderByCollectTimeDesc(String deviceNo);

    List<SensorData> findByDeviceNoAndCollectTimeBetweenOrderByCollectTimeDesc(
            String deviceNo, LocalDateTime startTime, LocalDateTime endTime);

    List<SensorData> findByTombIdAndCollectTimeBetweenOrderByCollectTimeDesc(
            Long tombId, LocalDateTime startTime, LocalDateTime endTime);

    List<SensorData> findByChamberIdAndCollectTimeBetweenOrderByCollectTimeDesc(
            Long chamberId, LocalDateTime startTime, LocalDateTime endTime);

    Optional<SensorData> findFirstByDeviceNoOrderByCollectTimeDesc(String deviceNo);

    @Query("SELECT s FROM SensorData s WHERE s.deviceNo = :deviceNo " +
           "AND s.collectTime >= :startTime ORDER BY s.collectTime DESC")
    List<SensorData> findRecentDataByDeviceNo(
            @Param("deviceNo") String deviceNo,
            @Param("startTime") LocalDateTime startTime);

    @Query("SELECT AVG(s.saltConcentration) FROM SensorData s " +
           "WHERE s.chamberId = :chamberId AND s.collectTime BETWEEN :startTime AND :endTime")
    BigDecimal findAvgSaltConcentrationByChamberIdAndTimeRange(
            @Param("chamberId") Long chamberId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT MAX(s.saltConcentration) FROM SensorData s " +
           "WHERE s.chamberId = :chamberId AND s.collectTime BETWEEN :startTime AND :endTime")
    BigDecimal findMaxSaltConcentrationByChamberIdAndTimeRange(
            @Param("chamberId") Long chamberId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(s) FROM SensorData s WHERE DATE(s.collectTime) = CURRENT_DATE")
    Long countTodayData();

    @Query("SELECT s FROM SensorData s WHERE s.deviceNo = :deviceNo " +
           "AND s.humidity >= :threshold AND s.collectTime >= :startTime " +
           "ORDER BY s.collectTime DESC")
    List<SensorData> findHumidityDataAboveThreshold(
            @Param("deviceNo") String deviceNo,
            @Param("threshold") BigDecimal threshold,
            @Param("startTime") LocalDateTime startTime);
}
