package com.yizhaoqi.smartpai.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 日志工具类
 * 提供统一的日志记录方法和格式
 */
public class LogUtils {
    
    // 业务日志记录器
    private static final Logger BUSINESS_LOGGER = LoggerFactory.getLogger("com.yizhaoqi.smartpai.business");
    
    // 性能日志记录器
    private static final Logger PERFORMANCE_LOGGER = LoggerFactory.getLogger("com.yizhaoqi.smartpai.performance");
    
    // MDC键名常量
    public static final String USER_ID = "userId";
    public static final String REQUEST_ID = "requestId";
    public static final String SESSION_ID = "sessionId";
    public static final String OPERATION = "operation";
    
    /**
     * 记录业务日志
     */
    public static void logBusiness(String operation, String userId, String message, Object... args) {
        try {
            MDC.put(OPERATION, operation);
            MDC.put(USER_ID, userId);
            BUSINESS_LOGGER.info("[{}] [用户:{}] {}", operation, userId, formatMessage(message, args));
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * 记录业务错误日志
     */
    public static void logBusinessError(String operation, String userId, String message, Throwable throwable, Object... args) {
        try {
            MDC.put(OPERATION, operation);
            MDC.put(USER_ID, userId);
            BUSINESS_LOGGER.error("[{}] [用户:{}] {}", operation, userId, formatMessage(message, args), throwable);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * 记录性能日志
     */
    public static void logPerformance(String operation, long duration, String details) {
        try {
            MDC.put(OPERATION, operation);
            PERFORMANCE_LOGGER.info("[性能] [{}] 耗时:{}ms {}", operation, duration, details);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * 记录用户操作日志
     */
    public static void logUserOperation(String userId, String operation, String resource, String result) {
        try {
            MDC.put(USER_ID, userId);
            MDC.put(OPERATION, operation);
            BUSINESS_LOGGER.info("[用户操作] [用户:{}] [操作:{}] [资源:{}] [结果:{}]", userId, operation, resource, result);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * 记录API调用日志
     */
    public static void logApiCall(String method, String path, String userId, int statusCode, long duration) {
        try {
            MDC.put(USER_ID, userId);
            MDC.put(OPERATION, "API_CALL");
            BUSINESS_LOGGER.info("[API] [{}] {} [用户:{}] [状态:{}] [耗时:{}ms]", method, path, userId, statusCode, duration);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * 记录文件操作日志
     */
    public static void logFileOperation(String userId, String operation, String fileName, String fileMd5, String result) {
        try {
            MDC.put(USER_ID, userId);
            MDC.put(OPERATION, "FILE_" + operation);
            BUSINESS_LOGGER.info("[文件操作] [用户:{}] [操作:{}] [文件:{}] [MD5:{}] [结果:{}]", 
                    userId, operation, fileName, fileMd5, result);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * 记录聊天日志
     */
    public static void logChat(String userId, String sessionId, String messageType, int messageLength) {
        try {
            MDC.put(USER_ID, userId);
            MDC.put(SESSION_ID, sessionId);
            MDC.put(OPERATION, "CHAT");
            BUSINESS_LOGGER.info("[聊天] [用户:{}] [会话:{}] [类型:{}] [长度:{}]", 
                    userId, sessionId, messageType, messageLength);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * 记录系统启动日志
     */
    public static void logSystemStart(String component, String status, String details) {
        BUSINESS_LOGGER.info("[系统启动] [组件:{}] [状态:{}] {}", component, status, details);
    }
    
    /**
     * 记录系统错误日志
     */
    public static void logSystemError(String component, String error, Throwable throwable) {
        BUSINESS_LOGGER.error("[系统错误] [组件:{}] [错误:{}]", component, error, throwable);
    }
    
    /**
     * 设置请求上下文
     */
    public static void setRequestContext(String requestId, String userId, String sessionId) {
        MDC.put(REQUEST_ID, requestId);
        if (userId != null) {
            MDC.put(USER_ID, userId);
        }
        if (sessionId != null) {
            MDC.put(SESSION_ID, sessionId);
        }
    }
    
    /**
     * 清除请求上下文
     */
    public static void clearRequestContext() {
        MDC.clear();
    }
    
    /**
     * 格式化消息
     */
    private static String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        try {
            return String.format(message, args);
        } catch (Exception e) {
            return message + " [格式化参数失败: " + e.getMessage() + "]";
        }
    }
    
    /**
     * 性能监控装饰器
     */
    public static class PerformanceMonitor {
        private final String operation;
        private final long startTime;
        
        public PerformanceMonitor(String operation) {
            this.operation = operation;
            this.startTime = System.currentTimeMillis();
        }
        
        public void end() {
            end("");
        }
        
        public void end(String details) {
            long duration = System.currentTimeMillis() - startTime;
            logPerformance(operation, duration, details);
        }
    }
    
    /**
     * 创建性能监控器
     */
    public static PerformanceMonitor startPerformanceMonitor(String operation) {
        return new PerformanceMonitor(operation);
    }
} 