package com.saltdamage.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "dingtalk_config")
public class DingtalkConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_name", nullable = false, length = 100)
    private String configName;

    @Column(name = "webhook_url", nullable = false, length = 500)
    private String webhookUrl;

    @Column(name = "secret", length = 200)
    private String secret;

    @Column(name = "at_mobiles", length = 500)
    private String atMobiles;

    @Column(name = "at_user_ids", length = 500)
    private String atUserIds;

    @Column(name = "is_at_all")
    private Boolean isAtAll;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}
