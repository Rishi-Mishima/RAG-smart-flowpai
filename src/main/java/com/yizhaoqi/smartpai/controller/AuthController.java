package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import com.yizhaoqi.smartpai.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 刷新Token接口
     * 用于前端主动刷新token机制的后备方案
     */
    @PostMapping("/refreshToken")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("REFRESH_TOKEN");
        String username = null;
        try {
            if (request.refreshToken() == null || request.refreshToken().isEmpty()) {
                LogUtils.logUserOperation("anonymous", "REFRESH_TOKEN", "validation", "FAILED_EMPTY_REFRESH_TOKEN");
                monitor.end("刷新token失败：refreshToken为空");
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Refresh token cannot be empty"));
            }

            // 验证refreshToken是否有效（这里我们用相同的验证逻辑）
            if (!jwtUtils.validateRefreshToken(request.refreshToken())) {
                LogUtils.logUserOperation("anonymous", "REFRESH_TOKEN", "validation", "FAILED_INVALID_REFRESH_TOKEN");
                monitor.end("刷新token失败：refreshToken无效");
                return ResponseEntity.status(401).body(Map.of("code", 401, "message", "Invalid refresh token"));
            }

            // 从refreshToken中提取用户名
            username = jwtUtils.extractUsernameFromToken(request.refreshToken());
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "REFRESH_TOKEN", "extraction", "FAILED_NO_USERNAME");
                monitor.end("刷新token失败：无法提取用户名");
                return ResponseEntity.status(401).body(Map.of("code", 401, "message", "Cannot extract username from refresh token"));
            }

            // 生成新的token和refreshToken
            String newToken = jwtUtils.generateToken(username);
            String newRefreshToken = jwtUtils.generateRefreshToken(username);

            LogUtils.logUserOperation(username, "REFRESH_TOKEN", "token_generation", "SUCCESS");
            monitor.end("刷新token成功");

            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "Token refreshed successfully", 
                "data", Map.of(
                    "token", newToken,
                    "refreshToken", newRefreshToken
                )
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("REFRESH_TOKEN", username, "刷新token失败: %s", e, e.getMessage());
            monitor.end("刷新token失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("REFRESH_TOKEN", username, "刷新token异常: %s", e, e.getMessage());
            monitor.end("刷新token异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    /**
     * 自定义后端错误接口（用于测试）
     */
    @GetMapping("/error")
    public ResponseEntity<?> customBackendError(@RequestParam String code, @RequestParam String msg) {
        return ResponseEntity.status(Integer.parseInt(code)).body(Map.of("code", Integer.parseInt(code), "message", msg));
    }
}

// 刷新Token请求记录类
record RefreshTokenRequest(String refreshToken) {}