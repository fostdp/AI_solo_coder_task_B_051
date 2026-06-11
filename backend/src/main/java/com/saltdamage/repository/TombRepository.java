package com.saltdamage.repository;

import com.saltdamage.entity.Tomb;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TombRepository extends JpaRepository<Tomb, Long> {

    List<Tomb> findByStatus(String status);
}
