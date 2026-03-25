package com.yizhaoqi.smartpai.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.entity.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Repository
public class RedisRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRepository(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public String getCurrentConversationId(String userId) {
        return (String) redisTemplate.opsForValue().get("user:" + userId + ":current_conversation");
    }

    public List<Message> getConversationHistory(String conversationId) {
        String json = (String) redisTemplate.opsForValue().get("conversation:" + conversationId);
        try {
            return json == null ? new ArrayList<>() : objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, Message.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse conversation history", e);
        }
    }

    public void saveConversationHistory(String conversationId, List<Message> messages) throws JsonProcessingException {
        redisTemplate.opsForValue().set("conversation:" + conversationId, objectMapper.writeValueAsString(messages), Duration.ofDays(7));
    }
}
