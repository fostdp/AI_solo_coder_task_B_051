package com.saltdamage.repository;

import com.saltdamage.entity.DelaminationRiskRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface DelaminationRiskRepository extends JpaRepository<DelaminationRiskRecord, Long> {

    Page<DelaminationRiskRecord> findByChamberIdOrderByAssessmentTimeDesc(Long chamberId, Pageable pageable);

    Page<DelaminationRiskRecord> findByTombIdAndRiskLevelOrderByAssessmentTimeDesc(Long tombId, String riskLevel, Pageable pageable);

    Optional<DelaminationRiskRecord> findFirstByChamberIdOrderByAssessmentTimeDesc(Long chamberId);

    long countByTombIdAndRiskLevelAndAssessmentTimeAfter(Long tombId, String riskLevel, LocalDateTime time);
}
