package com.saltdamage.dqn.repository;

import com.saltdamage.dqn.entity.MicroClimateControlRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MicroClimateControlRepository extends JpaRepository<MicroClimateControlRecord, Long> {

    List<MicroClimateControlRecord> findByChamberIdOrderByControlTimeDesc(Long chamberId, Pageable pageable);

    Optional<MicroClimateControlRecord> findFirstByChamberIdOrderByControlTimeDesc(Long chamberId);

    long countByControlTimeBetween(LocalDateTime start, LocalDateTime end);
}
