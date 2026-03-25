package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.RechargeOrder;
import com.yizhaoqi.smartpai.model.RechargeOrder.OrderStatus;
import com.yizhaoqi.smartpai.model.RechargePackage;
import com.yizhaoqi.smartpai.repository.RechargeOrderRepository;
import com.yizhaoqi.smartpai.repository.RechargePackageRepository;
import com.yizhaoqi.smartpai.utils.PriceUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

/**
 * 充值服务
 * @author YiHui
 * @date 2026/3/18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeService {

    private final WxPayService wxPayService;
    private final RechargePackageRepository packageRepository;
    private final RechargeOrderRepository orderRepository;
    private final UserTokenService userTokenService;

    /**
     * 获取所有启用的充值套餐
     */
    public List<RechargePackage> getAllPackages() {
        return packageRepository.findAllByEnabledTrueAndDeletedFalseAndPackagePriceGreaterThanOrderBySortOrderAsc(1L);
    }

    /**
     * 根据套餐 ID 获取套餐信息
     */
    public RechargePackage getPackageById(Integer id) {
        return packageRepository.findById(id)
                .orElseThrow(() -> new CustomException("套餐不存在", HttpStatus.BAD_REQUEST));
    }

    /**
     * 创建充值订单
     *
     * @param userId 用户 ID
     * @param packageId 套餐 ID（可选，如果为空则为自定义充值）
     * @param customAmount 自定义充值金额（单位分，仅在 packageId 为空时有效）
     * @return 支付唤起信息
     */
    @Transactional(rollbackFor = Exception.class)
    public WxPayService.PrePayInfoResBo createRechargeOrder(
            String userId,
            Integer packageId,
            Long customAmount) {

        // 1. 确定充值金额和 token 数量
        RechargePackage rechargePackage = null;
        Long amount;
        Long llmToken;
        Long embeddingToken;
        String description;

        if (packageId != null && packageId > 0) {
            // 使用套餐充值
            rechargePackage = getPackageById(packageId);
            amount = rechargePackage.getPackagePrice();
            llmToken = rechargePackage.getLlmToken();
            embeddingToken = rechargePackage.getEmbeddingToken();
            description = "【派聪明】充值套餐：" + rechargePackage.getPackageName();
        } else {
            // 自定义充值
            if (customAmount == null || customAmount <= 0) {
                throw new CustomException("充值金额无效", HttpStatus.BAD_REQUEST);
            }
            amount = customAmount;
            rechargePackage = packageRepository.findByPackagePriceAndEnabledIsTrueAndDeletedFalse(1)
                    .orElseThrow(() -> new CustomException("套餐不存在", HttpStatus.BAD_REQUEST));
            // 自定义充值按内部 1 分钱基准套餐折算 token 数量。
            llmToken = amount * rechargePackage.getLlmToken();
            embeddingToken = amount * rechargePackage.getEmbeddingToken();
            description = "【派聪明】自定义充值￥" + PriceUtil.toYuanPrice(amount) + "元";
        }

        // 2. 生成业务单号
        String tradeNo = generateTradeNo(userId);

        // 3. 创建订单记录
        RechargeOrder order = new RechargeOrder();
        order.setTradeNo(tradeNo);
        order.setUserId(userId);
        order.setPackageId(packageId != null ? packageId : 0); // 0 表示自定义充值
        order.setAmount(amount);
        order.setLlmToken(llmToken);
        order.setEmbeddingToken(embeddingToken);
        order.setStatus(OrderStatus.NOT_PAY);
        order.setDescription(description);
        order.setWxTransactionId("");

        order = orderRepository.save(order);
        log.info("创建充值订单成功，order={}", order);

        // 4. 调用微信支付下单
        WxPayService.PayOrderReq payReq = new WxPayService.PayOrderReq(tradeNo, description, amount.intValue());
        return wxPayService.createOrder(payReq);
    }

    /**
     * 处理微信支付回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void handlePayCallback(HttpServletRequest request) {
        // 1. 解析支付回调
        WxPayService.PayCallbackBo callbackBo = wxPayService.payCallback(request);

        // 2. 根据回调信息更新订单状态
        String tradeNo = callbackBo.outTradeNo();
        RechargeOrder order = orderRepository.findByTradeNo(tradeNo)
                .orElseThrow(() -> new CustomException("订单不存在", HttpStatus.BAD_REQUEST));

        // 3. 更新订单状态
        if (callbackBo.payStatus() == WxPayService.RechargeStatusEnum.SUCCEED) {
            order.setStatus(OrderStatus.SUCCEED);
            order.setWxTransactionId(callbackBo.thirdTransactionId());
            order.setPayTime(java.time.LocalDateTime.ofEpochSecond(
                    callbackBo.successTime() / 1000, 0, java.time.ZoneOffset.of("+8")));
            log.info("充值订单支付成功，tradeNo={}, wxTransactionId={}", tradeNo, callbackBo.thirdTransactionId());
        } else if (callbackBo.payStatus() == WxPayService.RechargeStatusEnum.FAIL) {
            order.setStatus(OrderStatus.FAIL);
            log.warn("充值订单支付失败，tradeNo={}", tradeNo);
        } else {
            order.setStatus(OrderStatus.PAYING);
            log.info("充值订单支付中，tradeNo={}", tradeNo);
        }

        orderRepository.save(order);
        // 4. 支付成功，增加用户的剩余token数量
        if (callbackBo.payStatus() == WxPayService.RechargeStatusEnum.SUCCEED) {
            this.paySuccessCallback(order);
        }
    }

    /**
     * 查询用户订单列表
     */
    public List<RechargeOrder> getUserOrders(String userId, OrderStatus status) {
        if (status != null) {
            return orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        }
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 查询订单详情
     */
    public RechargeOrder getOrderDetail(String tradeNo) {
        return orderRepository.findByTradeNo(tradeNo)
                .orElseThrow(() -> new CustomException("订单不存在", HttpStatus.BAD_REQUEST));
    }

    @Transactional(rollbackFor = Exception.class)
    public RechargeOrder checkOrderPayStatus(String tradeNo) {
        // 校验订单的支付状态
        WxPayService.PayCallbackBo bo = wxPayService.queryOrder(tradeNo);
        if (bo.payStatus() == WxPayService.RechargeStatusEnum.SUCCEED) {
            RechargeOrder order = orderRepository.findByTradeNo(tradeNo)
                    .orElseThrow(() -> new CustomException("订单不存在", HttpStatus.BAD_REQUEST));
            order.setStatus(OrderStatus.SUCCEED);
            order.setWxTransactionId(bo.thirdTransactionId());
            order.setPayTime(java.time.LocalDateTime.ofEpochSecond(
                    bo.successTime() / 1000, 0, java.time.ZoneOffset.of("+8")));
            order.setUpdatedAt(java.time.LocalDateTime.now());
            orderRepository.save(order);
            paySuccessCallback(order);
            return order;
        }

        return getOrderDetail(tradeNo);
    }


    /**
     * 支付成功回调
     */
    private void paySuccessCallback(RechargeOrder rechargeOrder) {
        // 增加用户的剩余token数量
        userTokenService.addLlmTokens(rechargeOrder.getUserId(), rechargeOrder.getLlmToken());
        userTokenService.addEmbeddingTokens(rechargeOrder.getUserId(), rechargeOrder.getEmbeddingToken());
    }

    /**
     * 生成业务单号
     * 格式：R + 时间戳 + 随机数
     */
    private String generateTradeNo(String userId) {
        // 按照年月日时分秒 + 8 位随机数生成唯一的TradeId
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"));
        String u = String.format("%06d", Long.parseLong(userId)).substring(0, 6);
        String random = String.format("%04d", new Random().nextInt(10000));
        String tradeNo = "R" + date + time + u + random;
        return tradeNo;
    }
}
