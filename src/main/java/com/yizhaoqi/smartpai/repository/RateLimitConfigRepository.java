package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.RateLimitConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitConfigRepository extends JpaRepository<RateLimitConfig, String> {
}
