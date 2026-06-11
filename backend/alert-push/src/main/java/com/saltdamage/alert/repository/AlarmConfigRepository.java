package com.saltdamage.alert.repository;

import com.saltdamage.entity.AlarmConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlarmConfigRepository extends JpaRepository<AlarmConfig, Long> {

    Optional<AlarmConfig> findFirstByOrderByIdDesc();
}
