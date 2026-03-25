package com.yizhaoqi.smartpai.service;

import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.exception.ValidationException;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.payments.nativepay.model.SceneInfo;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import com.yizhaoqi.smartpai.config.WxPayConfig;
import com.yizhaoqi.smartpai.utils.HttpRequestUtil;
import com.yizhaoqi.smartpai.utils.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * 微信支付服务
 * @author YiHui
 * @date 2026/3/18
 */
@Slf4j
@Service
public class WxPayService {

    // 订单过期时间，官方默认有效期为2小时，这里我们设置为100分钟
    private final static int PAY_EXPIRE_TIME = 100 * 60 * 1000;

    // 微信支付日期格式
    public static final DateTimeFormatter WX_PAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'+08:00'");
    private final WxPayConfig wxPayConfig;

    private final NativePayService nativePayService;


    public WxPayService(WxPayConfig wxPayConfig) {
        this.wxPayConfig = wxPayConfig;
        if (wxPayConfig.isEnable()) {
            Config config = new RSAAutoCertificateConfig.Builder()
                    .merchantId(wxPayConfig.getMerchantId())
                    .privateKey(wxPayConfig.getPrivateKeyContent())
                    .merchantSerialNumber(wxPayConfig.getMerchantSerialNumber())
                    .apiV3Key(wxPayConfig.getApiV3Key())
                    .build();
            nativePayService = new NativePayService.Builder().config(config).build();
        } else {
            nativePayService = null;
        }
    }


    /**
     * 唤起支付
     * <a href="https://pay.weixin.qq.com/docs/merchant/apis/jsapi-payment/jsapi-transfer-payment.html">JSAPI调起支付</a>
     *
     * @return
     */
    public PrePayInfoResBo createOrder(PayOrderReq payReq) {
        log.info("微信支付 >>>>>>>>>>>>>>>>> 请求：{}", JsonUtil.toStr(payReq));
        // 微信下单
        String prePayId = createPayOrder(payReq);
        return buildPayInfo(payReq, prePayId);
    }

    /**
     * native 支付，生成扫描支付二维码唤起微信支付页面
     *
     * @return 形如 wx://xxx 的支付二维码
     */
    private String createPayOrder(PayOrderReq payReq) {
        PrepayRequest request = new PrepayRequest();
        request.setAppid(wxPayConfig.getAppId());
        request.setMchid(wxPayConfig.getMerchantId());
        request.setDescription(payReq.description());
        request.setNotifyUrl(wxPayConfig.getPayNotifyUrl());
        request.setOutTradeNo(payReq.tradeNo());

        Amount amount = new Amount();
        amount.setTotal(payReq.amount());
        amount.setCurrency("CNY");
        request.setAmount(amount);

        SceneInfo sceneInfo = new SceneInfo();
        sceneInfo.setPayerClientIp(HttpRequestUtil.getClientIp());
        request.setSceneInfo(sceneInfo);

        log.info("微信native下单, 微信请求参数: {}", JsonUtil.toStr(request));
        if (nativePayService == null) {
            throw new ValidationException("微信支付未启用");
        }
        PrepayResponse response = nativePayService.prepay(request);
        log.info("微信支付 >>>>>>>>>>>> 返回: {}", response.getCodeUrl());
        return response.getCodeUrl();
    }

    /**
     * 补齐支付信息
     *
     * @param payReq   支付请求参数
     * @param prePayId 微信返回的支付唤起code
     */
    private PrePayInfoResBo buildPayInfo(PayOrderReq payReq, String prePayId) {
        // 结果封装返回
        PrePayInfoResBo prePay = new PrePayInfoResBo();
        prePay.setOutTradeNo(payReq.tradeNo());
        prePay.setAppId(wxPayConfig.getAppId());
        prePay.setPrePayId(prePayId);
        prePay.setExpireTime(System.currentTimeMillis() + PAY_EXPIRE_TIME);
        return prePay;
    }

    public PayCallbackBo queryOrder(String outTradeNo) {
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setMchid(wxPayConfig.getMerchantId());
        request.setOutTradeNo(outTradeNo);
        Transaction transaction = nativePayService.queryOrderByOutTradeNo(request);
        return toBo(transaction);
    }


    /**
     * 微信支付回调
     *
     * @param request
     * @return
     */
    public PayCallbackBo payCallback(HttpServletRequest request) {
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(request.getHeader("Wechatpay-Serial"))
                .nonce(request.getHeader("Wechatpay-Nonce"))
                .timestamp(request.getHeader("Wechatpay-Timestamp"))
                .signature(request.getHeader("Wechatpay-Signature"))
                .body(HttpRequestUtil.readReqData(request))
                .build();
        log.info("微信回调v3 >>>>>>>>>>>>>>>>> {}", JsonUtil.toStr(requestParam));

        NotificationConfig config = new RSAAutoCertificateConfig.Builder()
                .merchantId(wxPayConfig.getMerchantId())
                .privateKey(wxPayConfig.getPrivateKeyContent())
                .merchantSerialNumber(wxPayConfig.getMerchantSerialNumber())
                .apiV3Key(wxPayConfig.getApiV3Key())
                .build();

        NotificationParser parser = new NotificationParser(config);
        // 验签、解密并转换成 Transaction（返回参数对象）
        Transaction transaction = parser.parse(requestParam, Transaction.class);
        log.info("微信支付回调 成功，解析: {}", JsonUtil.toStr(transaction));
        return toBo(transaction);
    }

    /**
     * 微信退款回调
     * - 技术派目前没有实现退款流程，下面只是实现了回调，没有具体的业务场景
     *
     * @param request
     * @return
     */
    @Transactional
    public <T> ResponseEntity refundCallback(HttpServletRequest request, Function<T, Boolean> refundCallback) {
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(request.getHeader("Wechatpay-Serial"))
                .nonce(request.getHeader("Wechatpay-Nonce"))
                .timestamp(request.getHeader("Wechatpay-Timestamp"))
                .signature(request.getHeader("Wechatpay-Signature"))
                .body(HttpRequestUtil.readReqData(request))
                .build();
        log.info("微信退款回调v3 >>>>>>>>>>>>>>>>> {}", JsonUtil.toStr(requestParam));

        NotificationConfig config = new RSAAutoCertificateConfig.Builder()
                .merchantId(wxPayConfig.getMerchantId())
                .privateKey(wxPayConfig.getPrivateKeyContent())
                .merchantSerialNumber(wxPayConfig.getMerchantSerialNumber())
                .apiV3Key(wxPayConfig.getApiV3Key())
                .build();

        NotificationParser parser = new NotificationParser(config);

        try {
            // 验签、解密并转换成 Transaction（返回参数对象）
            RefundNotification refundNotify = parser.parse(requestParam, RefundNotification.class);
            log.info("微信退款回调 成功，解析: {}", JsonUtil.toStr(refundNotify));
            boolean ans = refundCallback.apply((T) refundNotify);
            if (ans) {
                // 处理成功，返回 200 OK 状态码
                return ResponseEntity.status(HttpStatus.OK).build();
            } else {
                // 处理异常，返回 500 服务器内部异常 状态码
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (ValidationException e) {
            log.error("微信退款回调v3java失败=" + e.getMessage(), e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }


    private PayCallbackBo toBo(Transaction transaction) {
        String outTradeNo = transaction.getOutTradeNo();
        RechargeStatusEnum payStatus = switch (transaction.getTradeState()) {
            case SUCCESS:
                yield RechargeStatusEnum.SUCCEED;
            case NOTPAY:
                yield RechargeStatusEnum.NOT_PAY;
            case USERPAYING:
                yield RechargeStatusEnum.PAYING;
            default:
                yield RechargeStatusEnum.FAIL;
        };
        Long payTime = transaction.getSuccessTime() != null ? wxDayToTimestamp(transaction.getSuccessTime()) : null;
        return new PayCallbackBo(outTradeNo, payTime, transaction.getTransactionId(), payStatus);
    }

    /**
     * 微信的支付时间，转时间戳 "2018-06-08T10:34:56+08:00"
     *
     * @param day
     * @return
     */
    public static Long wxDayToTimestamp(String day) {
        LocalDateTime parse = LocalDateTime.parse(day, WX_PAY_FORMATTER);
        return LocalDateTime.from(parse).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }


    @Getter
    public enum RechargeStatusEnum {
        NOT_PAY(0, "待支付"),
        PAYING(1, "支付中"),
        SUCCEED(2, "支付成功"),
        FAIL(3, "支付失败"),
        ;
        private Integer value;
        private String desc;

        RechargeStatusEnum(Integer value, String desc) {
            this.value = value;
            this.desc = desc;
        }
    }

    public record PayOrderReq(
            /**
             * 业务单号
             */
            String tradeNo,
            /**
             * 订单描述
             */
            String description,
            /**
             * 订单金额，单位分
             */
            int amount) {
    }

    public record PayCallbackBo(
            /**
             * 传递给支付系统的唯一外部单号
             */
            String outTradeNo,
            /**
             * 支付成功时间
             */
            Long successTime,

            /**
             * 三方流水编号
             */
            String thirdTransactionId,

            /**
             * 支付状态
             */
            RechargeStatusEnum payStatus) {
    }

    /**
     * 支付业务对象
     * @author YiHui
     * @date 2026/3/18
     */
    @Data
    @Accessors(chain = true)
    public static class PrePayInfoResBo {
        /**
         * 传递给三方的外部系统编号
         */
        private String outTradeNo;

        /**
         * 应用: appId
         */
        private String appId;

        /**
         * 时间戳信息
         */
        private String nonceStr;

        private String prePackage;

        private String paySign;

        private String timeStamp;

        private String signType;

        /**
         * jsapi：返回的是用于唤起支付的 prePayId
         * h5: 返回的是微信收银台中间页 url，用于访问之后唤起微信客户端的支付页面
         * native: 返回的是形如 weixin:// 的文本，用于生成二维码给微信扫一扫支付
         */
        private String prePayId;

        /**
         * prePayId的失效的时间戳
         */
        private Long expireTime;
    }
}
