package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.RechargeOrder;
import com.yizhaoqi.smartpai.model.RechargeOrder.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 充值订单数据访问层
 * @author YiHui
 * @date 2026/3/18
 */
@Repository
public interface RechargeOrderRepository extends JpaRepository<RechargeOrder, Long> {
    
    /**
     * 根据业务单号查询订单
     */
    Optional<RechargeOrder> findByTradeNo(String tradeNo);
    
    /**
     * 根据用户 ID 查询订单列表
     */
    List<RechargeOrder> findByUserIdOrderByCreatedAtDesc(String userId);
    
    /**
     * 根据用户 ID 和状态查询订单列表
     */
    List<RechargeOrder> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, OrderStatus status);
}
