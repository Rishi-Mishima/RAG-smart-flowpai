package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 微信支付配置
 *
 * @author YiHui
 * @date 2026/3/18
 */
@Data
@Component
@ConfigurationProperties(prefix = "wx.pay")
public class WxPayConfig {
    // 是否启用微信支付
    private boolean enable = false;
    //APPID
    private String appId;
    //mchid
    private String merchantId;
    //商户API私钥
    private String privateKey;
    //商户证书序列号
    private String merchantSerialNumber;
    //商户APIv3密钥
    private String apiV3Key;
    //支付通知地址
    private String payNotifyUrl;
    //退款通知地址
    private String refundNotifyUrl;

    /**
     * 获取私钥信息
     *
     * @return 私钥内容
     */
    public String getPrivateKeyContent() {
        if (privateKey != null && privateKey.contains("-----BEGIN PRIVATE KEY")) {
            // 私钥内容是直接以文本的方式提供的，直接返回
            return privateKey;
        }

        if (privateKey != null && (privateKey.endsWith("=") || privateKey.length() > 200)) {
            // 如果是base64编码的传入方式, 使用base64进行解码
            return new String(Base64.decodeBase64(privateKey), StandardCharsets.UTF_8);
        }

        // 私钥是以文件的方式提供
        try {
            return IOUtils.resourceToString(privateKey, StandardCharsets.UTF_8, this.getClass().getClassLoader());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
