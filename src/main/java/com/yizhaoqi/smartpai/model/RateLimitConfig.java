package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "rate_limit_configs")
public class RateLimitConfig {

    @Id
    @Column(name = "config_key", nullable = false, length = 64)
    private String configKey;

    @Column(name = "single_max")
    private Integer singleMax;

    @Column(name = "single_window_seconds")
    private Long singleWindowSeconds;

    @Column(name = "minute_max")
    private Long minuteMax;

    @Column(name = "minute_window_seconds")
    private Long minuteWindowSeconds;

    @Column(name = "day_max")
    private Long dayMax;

    @Column(name = "day_window_seconds")
    private Long dayWindowSeconds;

    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
