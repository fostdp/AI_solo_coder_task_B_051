package com.saltdamage.repository;

import com.saltdamage.entity.CycleCountRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCountRecord, Long> {

    Page<CycleCountRecord> findByChamberIdOrderByAnalysisTimeDesc(Long chamberId, Pageable pageable);

    Page<CycleCountRecord> findByTombIdAndPeriodTypeOrderByAnalysisTimeDesc(Long tombId, String periodType, Pageable pageable);

    Optional<CycleCountRecord> findFirstByChamberIdOrderByAnalysisTimeDesc(Long chamberId);

    List<CycleCountRecord> findByTombIdAndAnalysisTimeBetween(Long tombId, LocalDateTime start, LocalDateTime end);
}
