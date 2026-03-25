package com.yizhaoqi.smartpai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.yizhaoqi.smartpai.model.OrganizationTag;
import com.yizhaoqi.smartpai.repository.OrganizationTagRepository;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 组织标签缓存服务
 * 用于缓存用户组织标签信息，提高权限验证效率
 */
@Service
public class OrgTagCacheService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrgTagCacheService.class);
    
    private static final String USER_ORG_TAGS_KEY_PREFIX = "user:org_tags:";
    private static final String USER_PRIMARY_ORG_KEY_PREFIX = "user:primary_org:";
    private static final String USER_EFFECTIVE_TAGS_KEY_PREFIX = "user:effective_org_tags:";
    private static final long CACHE_TTL_HOURS = 24;
    private static final String DEFAULT_ORG_TAG = "DEFAULT";
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private OrganizationTagRepository organizationTagRepository;
    
    /**
     * 缓存用户的组织标签
     * 
     * @param username 用户名
     * @param orgTags 组织标签列表
     */
    public void cacheUserOrgTags(String username, List<String> orgTags) {
        try {
            String key = USER_ORG_TAGS_KEY_PREFIX + username;
            redisTemplate.opsForList().rightPushAll(key, orgTags.toArray());
            redisTemplate.expire(key, CACHE_TTL_HOURS, TimeUnit.HOURS);
            logger.debug("Cached organization tags for user: {}", username);
        } catch (Exception e) {
            logger.error("Failed to cache organization tags for user: {}", username, e);
        }
    }
    
    /**
     * 获取用户的组织标签
     * 
     * @param username 用户名
     * @return 组织标签列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getUserOrgTags(String username) {
        try {
            String key = USER_ORG_TAGS_KEY_PREFIX + username;
            List<Object> result = redisTemplate.opsForList().range(key, 0, -1);
            if (result != null && !result.isEmpty()) {
                return result.stream()
                        .map(obj -> (String) obj)
                        .toList();
            }
        } catch (Exception e) {
            logger.error("Failed to get organization tags for user: {}", username, e);
        }
        return null;
    }
    
    /**
     * 缓存用户的主组织标签
     * 
     * @param username 用户名
     * @param primaryOrg 主组织标签
     */
    public void cacheUserPrimaryOrg(String username, String primaryOrg) {
        try {
            String key = USER_PRIMARY_ORG_KEY_PREFIX + username;
            redisTemplate.opsForValue().set(key, primaryOrg);
            redisTemplate.expire(key, CACHE_TTL_HOURS, TimeUnit.HOURS);
            logger.debug("Cached primary organization for user: {}", username);
        } catch (Exception e) {
            logger.error("Failed to cache primary organization for user: {}", username, e);
        }
    }
    
    /**
     * 获取用户的主组织标签
     * 
     * @param username 用户名
     * @return 主组织标签
     */
    public String getUserPrimaryOrg(String username) {
        try {
            String key = USER_PRIMARY_ORG_KEY_PREFIX + username;
            return (String) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            logger.error("Failed to get primary organization for user: {}", username, e);
            return null;
        }
    }
    
    /**
     * 删除用户的组织标签缓存
     * 
     * @param username 用户名
     */
    public void deleteUserOrgTagsCache(String username) {
        try {
            String orgTagsKey = USER_ORG_TAGS_KEY_PREFIX + username;
            String primaryOrgKey = USER_PRIMARY_ORG_KEY_PREFIX + username;
            redisTemplate.delete(orgTagsKey);
            redisTemplate.delete(primaryOrgKey);
            logger.debug("Deleted organization tags cache for user: {}", username);
        } catch (Exception e) {
            logger.error("Failed to delete organization tags cache for user: {}", username, e);
        }
    }
    
    /**
     * 获取用户的有效标签权限集合（包含用户直接拥有的标签及其所有父标签）
     * 
     * @param username 用户名
     * @return 用户的有效标签集合
     */
    public List<String> getUserEffectiveOrgTags(String username) {
        try {
            // 从缓存获取
            String cacheKey = USER_EFFECTIVE_TAGS_KEY_PREFIX + username;
            List<Object> cachedTags = redisTemplate.opsForList().range(cacheKey, 0, -1);
            
            if (cachedTags != null && !cachedTags.isEmpty()) {
                List<String> effectiveTags = cachedTags.stream()
                        .map(Object::toString)
                        .collect(Collectors.toList());
                
                // 确保默认标签在结果中（从缓存读取的情况）
                if (!effectiveTags.contains(DEFAULT_ORG_TAG)) {
                    effectiveTags.add(DEFAULT_ORG_TAG);
                }
                
                return effectiveTags;
            }
            
            // 缓存未命中，计算有效标签集合
            List<String> userTags = getUserOrgTags(username);
            Set<String> allEffectiveTags = new HashSet<>();
            
            // 如果用户有标签，添加到集合中并查找父标签
            if (userTags != null && !userTags.isEmpty()) {
                allEffectiveTags.addAll(userTags);
            
            // 查找所有父标签
            for (String tagId : userTags) {
                collectParentTags(tagId, allEffectiveTags);
                }
            }
            
            // 确保默认标签在结果中
            allEffectiveTags.add(DEFAULT_ORG_TAG);
            
            List<String> result = new ArrayList<>(allEffectiveTags);
            
            // 缓存结果
            if (!result.isEmpty()) {
                redisTemplate.opsForList().rightPushAll(cacheKey, result.toArray());
                redisTemplate.expire(cacheKey, CACHE_TTL_HOURS, TimeUnit.HOURS);
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Failed to get effective organization tags for user: {}", username, e);
            // 错误情况下至少返回默认标签
            return Collections.singletonList(DEFAULT_ORG_TAG);
        }
    }
    
    /**
     * 递归收集标签的所有父标签
     */
    private void collectParentTags(String tagId, Set<String> result) {
        try {
            OrganizationTag tag = organizationTagRepository.findByTagId(tagId).orElse(null);
            if (tag != null && tag.getParentTag() != null && !tag.getParentTag().isEmpty()) {
                String parentTagId = tag.getParentTag();
                result.add(parentTagId);
                collectParentTags(parentTagId, result);
            }
        } catch (Exception e) {
            logger.error("Error collecting parent tags for tag: {}", tagId, e);
        }
    }
    
    /**
     * 删除用户有效标签缓存
     */
    public void deleteUserEffectiveTagsCache(String username) {
        try {
            String key = USER_EFFECTIVE_TAGS_KEY_PREFIX + username;
            redisTemplate.delete(key);
            logger.debug("Deleted effective organization tags cache for user: {}", username);
        } catch (Exception e) {
            logger.error("Failed to delete effective organization tags cache for user: {}", username, e);
        }
    }
    
    /**
     * 清除所有用户的有效标签缓存
     * 在组织标签结构变更时调用
     */
    public void invalidateAllEffectiveTagsCache() {
        try {
            Set<String> keys = redisTemplate.keys(USER_EFFECTIVE_TAGS_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.info("Invalidated all effective organization tags cache");
            }
        } catch (Exception e) {
            logger.error("Failed to invalidate effective organization tags cache", e);
        }
    }
} 