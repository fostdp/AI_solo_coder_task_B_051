package com.saltdamage.repository;

import com.saltdamage.entity.DingtalkConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DingtalkConfigRepository extends JpaRepository<DingtalkConfig, Long> {

    Optional<DingtalkConfig> findByConfigName(String configName);

    List<DingtalkConfig> findByEnabled(Boolean enabled);

    Optional<DingtalkConfig> findFirstByEnabledOrderByIdDesc(Boolean enabled);

    boolean existsByConfigName(String configName);
}
