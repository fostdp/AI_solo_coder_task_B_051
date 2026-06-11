package com.saltdamage.alert.repository;

import com.saltdamage.entity.Alarm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlarmRepository extends JpaRepository<Alarm, Long> {

    Page<Alarm> findByStatusOrderByAlarmTimeDesc(String status, Pageable pageable);

    Page<Alarm> findByDeviceNoOrderByAlarmTimeDesc(String deviceNo, Pageable pageable);

    Page<Alarm> findByAlarmTypeOrderByAlarmTimeDesc(String alarmType, Pageable pageable);

    Page<Alarm> findByAlarmLevelOrderByAlarmTimeDesc(String alarmLevel, Pageable pageable);

    List<Alarm> findByStatus(String status);

    Long countByStatus(String status);

    Long countByAlarmLevel(String alarmLevel);

    Long countByAlarmType(String alarmType);

    @Query("SELECT COUNT(a) FROM Alarm a WHERE a.alarmTime >= :startTime")
    Long countByAlarmTimeAfter(@Param("startTime") LocalDateTime startTime);

    @Query("SELECT COUNT(a) FROM Alarm a WHERE a.alarmTime >= :startTime AND a.alarmTime < :endTime")
    Long countByAlarmTimeBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    boolean existsByDeviceNoAndAlarmTypeAndStatusIn(
            String deviceNo, String alarmType, List<String> statuses);
}
