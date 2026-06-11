package com.saltdamage.crystal.repository;

import com.saltdamage.entity.AnalysisResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    Page<AnalysisResult> findByTombIdOrderByAnalysisTimeDesc(Long tombId, Pageable pageable);

    Page<AnalysisResult> findByChamberIdOrderByAnalysisTimeDesc(Long chamberId, Pageable pageable);

    Optional<AnalysisResult> findFirstByTombIdOrderByAnalysisTimeDesc(Long tombId);

    Optional<AnalysisResult> findFirstByChamberIdOrderByAnalysisTimeDesc(Long chamberId);

    List<AnalysisResult> findByAnalysisTypeOrderByAnalysisTimeDesc(String analysisType);
}
