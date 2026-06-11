package com.saltdamage.repository;

import com.saltdamage.entity.MicroClimateControlRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MicroClimateControlRepository extends JpaRepository<MicroClimateControlRecord, Long> {

    Page<MicroClimateControlRecord> findByChamberIdOrderByControlTimestampDesc(Long chamberId, Pageable pageable);

    Optional<MicroClimateControlRecord> findFirstByChamberIdOrderByControlTimestampDesc(Long chamberId);

    List<MicroClimateControlRecord> findByChamberIdAndControlTimestampBetween(
            Long chamberId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COALESCE(SUM(r.energyConsumption), 0) FROM MicroClimateControlRecord r " +
           "WHERE r.chamberId = :chamberId AND r.controlTimestamp BETWEEN :startTime AND :endTime")
    BigDecimal sumEnergyConsumptionByChamberIdAndDateRange(
            @Param("chamberId") Long chamberId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
