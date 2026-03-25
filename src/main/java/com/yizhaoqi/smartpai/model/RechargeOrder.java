package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 充值订单实体
 * @author YiHui
 * @date 2026/3/18
 */
@Data
@Entity
@Table(name = "recharge_orders")
public class RechargeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 订单 ID

    @Column(nullable = false, name = "trade_no", unique = true)
    private String tradeNo; // 业务单号（外部系统唯一）

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId; // 用户 ID（关联 users 表）

    @Column(nullable = false, name = "package_id")
    private Integer packageId; // 套餐 ID（如果是自定义充值，则为 null）

    @Column(nullable = false)
    private Long amount; // 订单金额，单位分

    @Column(nullable = false, name = "llm_token")
    private Long llmToken; // LLM token 数量

    @Column(nullable = false, name = "embedding_token")
    private Long embeddingToken; // Embedding token 数量

    @Column(name = "wx_transaction_id")
    private String wxTransactionId; // 微信交易流水号

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status; // 订单状态

    @Column
    private String description; // 订单描述

    @Column(name = "pay_time")
    private LocalDateTime payTime; // 支付成功时间

    @CreationTimestamp
    private LocalDateTime createdAt; // 创建时间

    @UpdateTimestamp
    private LocalDateTime updatedAt; // 更新时间

    /**
     * 订单状态枚举
     */
    public enum OrderStatus {
        NOT_PAY,      // 待支付
        PAYING,       // 支付中
        SUCCEED,      // 支付成功
        FAIL,         // 支付失败
        CANCELLED     // 已取消
    }
}
