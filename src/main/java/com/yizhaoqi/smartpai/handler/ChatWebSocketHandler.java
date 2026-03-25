package com.yizhaoqi.smartpai.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.service.ChatHandler;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final String HEARTBEAT_PING = "__chat_ping__";
    private static final String HEARTBEAT_PONG = "__chat_pong__";
    private final ChatHandler chatHandler;
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtUtils jwtUtils;
    
    // 内部指令令牌 - 可以从配置文件读取
    private static final String INTERNAL_CMD_TOKEN = "WSS_STOP_CMD_" + System.currentTimeMillis() % 1000000;

    public ChatWebSocketHandler(ChatHandler chatHandler, JwtUtils jwtUtils) {
        this.chatHandler = chatHandler;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String jwtToken;
        try {
            jwtToken = extractToken(session);
            if (!jwtUtils.validateToken(jwtToken)) {
                logger.debug("拒绝无效WebSocket连接，会话ID: {}", session.getId());
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
        } catch (Exception exception) {
            logger.warn("拒绝非法WebSocket连接，会话ID: {}, 原因: {}", session.getId(), exception.getMessage());
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception closeException) {
                logger.error("关闭无效WebSocket连接失败: {}", closeException.getMessage(), closeException);
            }
            return;
        }

        String userId = extractUserId(jwtToken);
        sessions.put(userId, session);
        logger.info("WebSocket连接已建立，用户ID: {}，会话ID: {}，URI路径: {}",
                userId, session.getId(), session.getUri().getPath());

        // 发送会话ID到前端
        try {
            Map<String, String> connectionMessage = Map.of(
                "type", "connection",
                "sessionId", session.getId(),
                "message", "WebSocket连接已建立"
            );
            String jsonMessage = objectMapper.writeValueAsString(connectionMessage);
            session.sendMessage(new TextMessage(jsonMessage));
            logger.info("已发送会话ID到前端: sessionId={}", session.getId());
        } catch (Exception e) {
            logger.error("发送会话ID失败: {}", e.getMessage(), e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = extractUserId(extractToken(session));
        try {
            String payload = message.getPayload();

            // 心跳消息只用于保活连接，不进入聊天处理链路。
            if (HEARTBEAT_PING.equals(payload)) {
                session.sendMessage(new TextMessage(HEARTBEAT_PONG));
                return;
            }

            logger.info("接收到消息，用户ID: {}，会话ID: {}，消息长度: {}", 
                       userId, session.getId(), payload.length());
            
            // 检查是否是JSON格式的系统指令
            if (payload.trim().startsWith("{")) {
                try {
                    Map<String, Object> jsonMessage = objectMapper.readValue(payload, Map.class);
                    String messageType = (String) jsonMessage.get("type");
                    String internalToken = (String) jsonMessage.get("_internal_cmd_token");
                    
                    // 只有包含正确内部令牌的停止指令才处理
                    if ("stop".equals(messageType) && INTERNAL_CMD_TOKEN.equals(internalToken)) {
                        // 处理停止指令
                        logger.info("收到有效的停止按钮指令，用户ID: {}，会话ID: {}", userId, session.getId());
                        chatHandler.stopResponse(userId, session);
                        return;
                    }
                    
                    // 其他JSON消息当作普通消息处理
                    logger.debug("收到JSON格式的聊天消息，当作普通消息处理");
                } catch (Exception jsonParseError) {
                    // JSON解析失败，当作普通文本消息处理
                    logger.debug("JSON解析失败，当作普通消息处理: {}", jsonParseError.getMessage());
                }
            }
            
            // 普通聊天消息处理（保持向下兼容）
            chatHandler.processMessage(userId, payload, session);
            
        } catch (Exception e) {
            logger.error("处理消息出错，用户ID: {}，会话ID: {}，错误: {}", 
                        userId, session.getId(), e.getMessage(), e);
            sendErrorMessage(session, "消息处理失败：" + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = "unknown";
        try {
            userId = extractUserId(extractToken(session));
            sessions.remove(userId);
        } catch (Exception e) {
            logger.debug("关闭连接时无法解析用户信息，会话ID: {}", session.getId());
        }

        if (CloseStatus.POLICY_VIOLATION.equals(status)) {
            logger.debug("WebSocket连接因策略校验失败被关闭，用户ID: {}，会话ID: {}，状态: {}",
                    userId, session.getId(), status);
        } else {
            logger.info("WebSocket连接已关闭，用户ID: {}，会话ID: {}，状态: {}",
                    userId, session.getId(), status);
        }

        // 清理会话的引用映射
        chatHandler.clearSessionReferenceMapping(session.getId());
    }

    private String extractUserId(String jwtToken) {
        String userId = jwtUtils.extractUserIdFromToken(jwtToken);
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("无法从JWT令牌中提取用户ID");
        }

        logger.debug("从JWT令牌中提取的用户ID: {}", userId);
        return userId;
    }

    private String extractToken(WebSocketSession session) {
        if (session.getUri() == null || session.getUri().getPath() == null) {
            throw new IllegalArgumentException("WebSocket URI is missing");
        }
        String path = session.getUri().getPath();
        String[] segments = path.split("/");
        return segments[segments.length - 1];
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            Map<String, String> error = Map.of("error", errorMessage);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
            logger.info("已发送错误消息到会话: {}, 错误: {}", session.getId(), errorMessage);
        } catch (Exception e) {
            logger.error("发送错误消息失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取内部指令令牌 - 供前端调用
     */
    public static String getInternalCmdToken() {
        return INTERNAL_CMD_TOKEN;
    }
} 
