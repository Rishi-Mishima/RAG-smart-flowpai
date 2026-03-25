package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.model.RechargeOrder;
import com.yizhaoqi.smartpai.model.RechargeOrder.OrderStatus;
import com.yizhaoqi.smartpai.service.RechargeService;
import com.yizhaoqi.smartpai.service.WxPayService;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 充值服务控制器
 * @author YiHui
 * @date 2026/3/18
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1/recharge")
@RequiredArgsConstructor
public class RechargeController {

    private final RechargeService rechargeService;
    private final JwtUtils jwtUtils;

    /**
     * 获取充值套餐列表
     */
    @GetMapping("/packages")
    public ResponseEntity<?> getPackages() {
        try {
            List<com.yizhaoqi.smartpai.model.RechargePackage> packages = rechargeService.getAllPackages();
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "success",
                    "data", packages
            ));
        } catch (Exception e) {
            log.error("获取充值套餐列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取充值套餐列表失败：" + e.getMessage()));
        }
    }

    /**
     * 创建充值订单
     *
     * @param token JWT token
     * @param request 充值请求
     * @return 支付唤起信息
     */
    @PostMapping("/create-order")
    public ResponseEntity<?> createRechargeOrder(
            @RequestHeader("Authorization") String token,
            @RequestBody CreateRechargeOrderRequest request) {

        try {
            // 从 token 中提取用户 ID
            String userId = jwtUtils.extractUserIdFromToken(token.replace("Bearer ", ""));
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(401)
                        .body(Map.of("code", 401, "message", "无效的用户 token"));
            }

            // 创建充值订单
            WxPayService.PrePayInfoResBo payInfo = rechargeService.createRechargeOrder(
                    userId,
                    request.packageId(),
                    request.customAmount()
            );

            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "订单创建成功",
                    "data", payInfo
            ));
        } catch (Exception e) {
            log.error("创建充值订单失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "创建充值订单失败：" + e.getMessage()));
        }
    }

    /**
     * 微信支付回调
     */
    @PostMapping("/pay-callback")
    public ResponseEntity<?> payCallback(HttpServletRequest request) {
        try {
            rechargeService.handlePayCallback(request);
            // 返回成功响应给微信
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (Exception e) {
            log.error("处理支付回调失败", e);
            // 返回失败响应给微信，微信会重试
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 查询用户订单列表
     */
    @GetMapping("/orders")
    public ResponseEntity<?> getUserOrders(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String status) {

        try {
            String userId = jwtUtils.extractUserIdFromToken(token.replace("Bearer ", ""));
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(401)
                        .body(Map.of("code", 401, "message", "无效的用户 token"));
            }

            OrderStatus orderStatus = null;
            if (status != null && !status.isBlank()) {
                try {
                    orderStatus = OrderStatus.valueOf(status.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("code", 400, "message", "无效的订单状态"));
                }
            }

            List<RechargeOrder> orders = rechargeService.getUserOrders(userId, orderStatus);
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "success",
                    "data", orders
            ));
        } catch (Exception e) {
            log.error("查询用户订单列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "查询用户订单列表失败：" + e.getMessage()));
        }
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/orders/{tradeNo}")
    public ResponseEntity<?> getOrderDetail(
            @RequestHeader("Authorization") String token,
            @PathVariable String tradeNo) {

        try {
            String userId = jwtUtils.extractUserIdFromToken(token.replace("Bearer ", ""));
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(401)
                        .body(Map.of("code", 401, "message", "无效的用户 token"));
            }

            // 检查订单支付状态
            RechargeOrder order = rechargeService.checkOrderPayStatus(tradeNo);

            // 验证订单是否属于当前用户
            if (!order.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("code", 403, "message", "无权查看该订单"));
            }

            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "success",
                    "data", order
            ));
        } catch (Exception e) {
            log.error("查询订单详情失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "查询订单详情失败：" + e.getMessage()));
        }
    }

    /**
     * 创建充值订单请求
     */
    public record CreateRechargeOrderRequest(
            // 套餐 ID（可选，为空则为自定义充值）
            Integer packageId,
            // 自定义充值金额（单位分，仅在 packageId 为空时有效）
            Long customAmount
    ) {
    }
}
