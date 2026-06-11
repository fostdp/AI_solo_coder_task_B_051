package com.saltdamage.repository;

import com.saltdamage.entity.PredictionData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PredictionDataRepository extends JpaRepository<PredictionData, Long> {

    List<PredictionData> findByTombIdAndPredictTimeAfterOrderByPredictTimeDesc(
            Long tombId, LocalDateTime predictTime);

    List<PredictionData> findByChamberIdAndPredictTimeAfterOrderByPredictTimeDesc(
            Long chamberId, LocalDateTime predictTime);

    Optional<PredictionData> findFirstByTombIdOrderByPredictTimeDesc(Long tombId);

    Optional<PredictionData> findFirstByChamberIdOrderByPredictTimeDesc(Long chamberId);
}
