package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 充值套餐实体
 * @author YiHui
 * @date 2026/3/18
 */
@Data
@Entity
@Table(name = "recharge_packages")
public class RechargePackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id; // 套餐 ID（自增主键）

    @Column(nullable = false, length = 128, name = "package_name")
    private String packageName; // 套餐名称

    @Column(nullable = false, name = "package_price")
    private Long packagePrice; // 套餐价格，单位分

    @Column(columnDefinition = "TEXT", name = "package_desc")
    private String packageDesc; // 套餐描述

    @Column(columnDefinition = "TEXT", name = "package_benefit")
    private String packageBenefit; // 套餐权益

    @Column(nullable = false, name = "llm_token")
    private Long llmToken; // LLM token 数量

    @Column(nullable = false, name = "embedding_token")
    private Long embeddingToken; // Embedding token 数量

    @Column(nullable = false, name = "enabled")
    private Boolean enabled = true; // 是否启用

    @Column(nullable = false, name = "deleted")
    private Boolean deleted = false; // 是否已删除（逻辑删除）

    @Column(nullable = false, name = "sort_order")
    private Integer sortOrder = 0; // 排序顺序（数字越小越靠前）

    @CreationTimestamp
    private LocalDateTime createdAt; // 创建时间

    @UpdateTimestamp
    private LocalDateTime updatedAt; // 更新时间
}
