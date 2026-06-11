package com.saltdamage.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "device", indexes = {
        @Index(name = "idx_device_no", columnList = "device_no", unique = true),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_tomb_id", columnList = "tomb_id")
})
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_no", nullable = false, length = 50, unique = true)
    private String deviceNo;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(length = 100)
    private String manufacturer;

    @Column(length = 50)
    private String model;

    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    private Integer port;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "tomb_id")
    private Long tombId;

    @Column(name = "chamber_id")
    private Long chamberId;

    @Column(length = 200)
    private String location;

    @Column(length = 500)
    private String remark;

    @Column(name = "last_online_time")
    private LocalDateTime lastOnlineTime;

    @Column(name = "install_time")
    private LocalDateTime installTime;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}
