package com.yizhaoqi.smartpai.config;

import com.yizhaoqi.smartpai.utils.JwtUtils;
import com.yizhaoqi.smartpai.utils.LogUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 日志拦截器
 * 自动记录API调用日志和性能指标
 */
@Component
public class LoggingInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    private static final String START_TIME_ATTRIBUTE = "startTime";
    private static final String REQUEST_ID_ATTRIBUTE = "requestId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 记录请求开始时间
        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME_ATTRIBUTE, startTime);
        
        // 生成请求ID
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        
        // 获取用户信息
        String userId = extractUserId(request);
        String sessionId = request.getSession(false) != null ? request.getSession().getId() : null;
        
        // 设置请求上下文
        LogUtils.setRequestContext(requestId, userId, sessionId);
        
        // 记录请求开始日志（仅对API请求）
        String path = request.getRequestURI();
        if (isApiRequest(path)) {
            LogUtils.logBusiness("REQUEST_START", userId, 
                "开始处理请求 [%s] %s", request.getMethod(), path);
        }
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                              Object handler, Exception ex) {
        try {
            // 计算请求耗时
            Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
            if (startTime != null) {
                long duration = System.currentTimeMillis() - startTime;
                String userId = extractUserId(request);
                String path = request.getRequestURI();
                
                // 记录API调用日志（仅对API请求）
                if (isApiRequest(path)) {
                    LogUtils.logApiCall(request.getMethod(), path, userId, response.getStatus(), duration);
                    
                    // 记录异常信息
                    if (ex != null) {
                        LogUtils.logBusinessError("REQUEST_ERROR", userId, 
                            "请求处理异常 [%s] %s", ex, request.getMethod(), path);
                    }
                    
                    // 记录慢请求
                    if (duration > 3000) { // 超过3秒的请求
                        LogUtils.logPerformance("SLOW_REQUEST", duration, 
                            String.format("[%s] %s [用户:%s]", request.getMethod(), path, userId));
                    }
                }
            }
        } finally {
            // 清除请求上下文
            LogUtils.clearRequestContext();
        }
    }

    /**
     * 从请求中提取用户ID
     */
    private String extractUserId(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            if (token != null) {
                return jwtUtils.extractUserIdFromToken(token);
            }
        } catch (Exception e) {
            // 忽略token解析异常
        }
        return "anonymous";
    }

    /**
     * 从请求头中提取JWT Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 判断是否为API请求
     */
    private boolean isApiRequest(String path) {
        return path.startsWith("/api/") || path.startsWith("/chat/");
    }
} 