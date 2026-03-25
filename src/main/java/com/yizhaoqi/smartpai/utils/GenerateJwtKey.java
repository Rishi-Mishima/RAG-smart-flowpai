package com.yizhaoqi.smartpai.utils;

import io.jsonwebtoken.security.Keys;
import java.util.Base64;

public class GenerateJwtKey {
    public static void main(String[] args) {
        // 生成符合 HS256 要求的 256-bit (32-byte) 密钥
        byte[] keyBytes = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256).getEncoded();
        String base64Key = Base64.getEncoder().encodeToString(keyBytes);

        // 输出 Base64 编码的密钥
        System.out.println("Base64 Encoded Key: " + base64Key);
    }
}

