package com.saltdamage.repository;

import com.saltdamage.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByDeviceNo(String deviceNo);

    List<Device> findByStatus(String status);

    List<Device> findByTombId(Long tombId);

    List<Device> findByChamberId(Long chamberId);

    Long countByStatus(String status);

    @Query("SELECT COUNT(d) FROM Device d")
    Long countTotalDevices();

    boolean existsByDeviceNo(String deviceNo);
}
