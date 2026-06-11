package com.saltdamage.repository;

import com.saltdamage.entity.Chamber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChamberRepository extends JpaRepository<Chamber, Long> {

    List<Chamber> findByTombId(Long tombId);

    List<Chamber> findByTombIdAndStatus(Long tombId, String status);
}
