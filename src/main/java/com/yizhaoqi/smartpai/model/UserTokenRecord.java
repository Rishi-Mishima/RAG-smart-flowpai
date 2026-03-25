package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户 Token 变动记录
 * 用于记录用户每天的 Token 增加和消耗情况
 * 
 * @author YiHui
 * @date 2026/3/18
 */
@Data
@Entity
@Table(name = "user_token_record", 
       indexes = {
           @Index(name = "idx_user_date", columnList = "userId, recordDate")
       })
public class UserTokenRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户 ID
     */
    @Column(nullable = false)
    private String userId;

    /**
     * 记录日期（按天统计）
     */
    @Column(nullable = false)
    private LocalDate recordDate;

    /**
     * Token 类型：LLM 或 EMBEDDING
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TokenType tokenType;

    /**
     * 变动类型：INCREASE（增加）或 CONSUME（消耗）
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ChangeType changeType;

    /**
     * 变动数量
     */
    @Column(nullable = false)
    private Long amount;

    /**
     * 变动前的余额
     */
    private Long balanceBefore;

    /**
     * 变动后的余额
     */
    private Long balanceAfter;

    /**
     * 变动原因描述
     */
    @Column(length = 500)
    private String reason;

    /**
     * 备注信息（如订单号、对话 ID 等）
     */
    @Column(length = 500)
    private String remark;

    /**
     * 请求次数（一次充值或对话可能包含多次 API 请求）
     */
    @Column(nullable = false)
    private Long requestCount = 0L;

    /**
     * 创建时间
     */
    @CreationTimestamp
    private LocalDateTime createdAt;

    /**
     * Token 类型枚举
     */
    public enum TokenType {
        LLM,          // LLM Token
        EMBEDDING     // Embedding Token
    }

    /**
     * 变动类型枚举
     */
    public enum ChangeType {
        INCREASE,     // 增加（充值、赠送等）
        CONSUME       // 消耗（对话使用等）
    }
}
