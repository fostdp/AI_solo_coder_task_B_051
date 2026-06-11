package com.saltdamage.repository;

import com.saltdamage.entity.BlockchainRecordEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BlockchainRecordRepository extends JpaRepository<BlockchainRecordEntity, Long> {

    Optional<BlockchainRecordEntity> findByTxHash(String txHash);

    List<BlockchainRecordEntity> findByBlockNumber(Long blockNumber);

    Page<BlockchainRecordEntity> findByDataTypeOrderByTimestampDesc(String dataType, Pageable pageable);

    List<BlockchainRecordEntity> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    Long countByDataType(String dataType);

    Page<BlockchainRecordEntity> findAllByOrderByTimestampDesc(Pageable pageable);
}
