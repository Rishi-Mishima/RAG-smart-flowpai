package com.yizhaoqi.smartpai.config;

import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组织标签授权过滤器
 * 用于实现基于组织标签的数据访问控制
 * 支持多级访问控制：
 * 1. 用户私人空间：仅资源创建者可访问
 * 2. 组织资源：组织成员可访问
 * 3. 公开资源：所有用户可访问
 * 
 * 实现说明：
 * 本过滤器主要解决两类请求的授权需求：
 * 1. 基于资源ID的权限验证：对特定资源的访问需验证用户是否有权限
 * 2. 基于用户身份的简单授权：某些API只需验证用户身份并传递用户ID
 *    - 如上传文件、获取文档列表等接口，不涉及特定资源的权限检查
 *    - 这类API的控制器方法通过@RequestAttribute("userId")获取用户ID
 *    - 由本过滤器负责从JWT令牌中提取用户ID并设置为请求属性
 */
@Component
public class OrgTagAuthorizationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(OrgTagAuthorizationFilter.class);
    private static final String DEFAULT_ORG_TAG = "DEFAULT"; // 默认组织标签
    private static final String PRIVATE_TAG_PREFIX = "PRIVATE_"; // 私人组织标签前缀

    @Autowired
    private JwtUtils jwtUtils;
    
    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String path = request.getRequestURI();
            
            // 需要用户ID但不需要资源权限检查的API路径
            // 这些API只需要用户身份验证，不需要对特定资源进行权限检查
            // 控制器方法通过@RequestAttribute("userId")获取用户ID
            if (path.matches(".*/upload/chunk.*") || 
                path.matches(".*/upload/merge.*") || 
                path.matches(".*/documents/uploads.*") ||
                path.matches(".*/documents/accessible.*") ||
                path.matches(".*/documents/page-preview.*") ||
                path.matches(".*/search/hybrid.*") ||
                (path.matches(".*/documents/[a-fA-F0-9]{32}.*") &&
                        ("DELETE".equals(request.getMethod()) || "POST".equals(request.getMethod())))) {
                
                String operation = "未知操作";
                if (path.contains("/chunk")) {
                    operation = "分片上传";
                } else if (path.contains("/merge")) {
                    operation = "合并分片";
                } else if (path.contains("/uploads")) {
                    operation = "获取用户文档";
                } else if (path.contains("/accessible")) {
                    operation = "获取可访问文档";
                } else if (path.contains("/page-preview")) {
                    operation = "获取 PDF 单页预览";
                } else if (path.contains("/search/hybrid")) {
                    operation = "混合检索";
                } else if ("DELETE".equals(request.getMethod()) && path.matches(".*/documents/[a-fA-F0-9]{32}.*")) {
                    operation = "删除文档";
                } else if ("POST".equals(request.getMethod()) && path.matches(".*/documents/[a-fA-F0-9]{32}/reindex.*")) {
                    operation = "重建文档索引";
                }
                
                logger.info("处理{}请求: {}", operation, path);
                
                // 将用户ID和角色设置为请求属性，供控制器方法使用
                String token = extractToken(request);
                if (token != null) {
                    String userId = jwtUtils.extractUserIdFromToken(token);
                    String role = jwtUtils.extractRoleFromToken(token);
                    String orgTags = jwtUtils.extractOrgTagsFromToken(token);
                    if (userId != null) {
                        request.setAttribute("userId", userId);
                        request.setAttribute("role", role);
                        request.setAttribute("orgTags", orgTags);
                        logger.debug("为{}请求设置userId属性: {}, role: {}, orgTags: {}", operation, userId, role, orgTags);
                    } else {
                        logger.warn("{}请求中无法从token提取userId", operation);
                    }
                } else {
                    logger.warn("{}请求中未找到有效token", operation);
                }
                
                filterChain.doFilter(request, response);
                return;
            }
            
            boolean isChunkUpload = path.matches(".*/upload/chunk.*");
            logger.debug("请求路径: {}, 是否为分片上传: {}", path, isChunkUpload);
            
            // 获取路径中的资源ID
            String resourceId = extractResourceIdFromPath(request);
            
            // 如果URL不含资源ID，直接放行
            if (resourceId == null) {
                logger.debug("未找到资源ID，直接放行");
                filterChain.doFilter(request, response);
                return;
            }
            
            // 获取资源的组织标签
            ResourceInfo resourceInfo = getResourceInfo(resourceId);
            
            // 如果是分片上传并且资源未找到(首次上传)，允许请求通过
            if (isChunkUpload && resourceInfo == null) {
                logger.debug("分片上传 - 首次上传文件(无记录)，放行请求: {}", resourceId);
                filterChain.doFilter(request, response);
                return;
            }
            
            // 如果资源未找到，返回404
            if (resourceInfo == null) {
                logger.debug("资源未找到，返回404: {}", resourceId);
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            
            String resourceOrgTag = resourceInfo.getOrgTag();
            
            // 如果是公开资源、资源没有组织标签、或属于默认组织，直接放行
            if (resourceInfo.isPublic() || 
                resourceOrgTag == null || 
                resourceOrgTag.isEmpty() || 
                DEFAULT_ORG_TAG.equals(resourceOrgTag)) {
                logger.debug("资源是公开的或无组织标签或属于默认组织，放行请求");
                filterChain.doFilter(request, response);
                return;
            }
            
            // 从请求头获取token
            String token = extractToken(request);
            if (token == null) {
                logger.debug("未找到Token，返回401");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            
            // 获取用户名和角色
            String username = jwtUtils.extractUsernameFromToken(token);
            String role = jwtUtils.extractRoleFromToken(token);
            
            // 如果是资源拥有者，直接放行
            if (username != null && username.equals(resourceInfo.getOwner())) {
                logger.debug("用户是资源拥有者，放行请求");
                filterChain.doFilter(request, response);
                return;
            }
            
            // 如果是管理员，直接放行
            if ("ADMIN".equals(role)) {
                logger.debug("用户是管理员，放行请求");
                filterChain.doFilter(request, response);
                return;
            }
            
            // 检查是否为私人组织标签资源
            if (resourceOrgTag.startsWith(PRIVATE_TAG_PREFIX)) {
                // 私人标签资源只允许拥有者访问，此处已排除拥有者和管理员，拒绝访问
                logger.debug("私人资源，且用户不是拥有者或管理员，拒绝访问");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            
            // 获取用户的组织标签
            String userOrgTags = jwtUtils.extractOrgTagsFromToken(token);
            if (userOrgTags == null || userOrgTags.isEmpty()) {
                logger.debug("用户没有组织标签，拒绝访问");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            
            // 检查用户是否有权限访问该资源
            if (isUserAuthorized(userOrgTags, resourceOrgTag)) {
                logger.debug("用户有访问权限，放行请求");
                filterChain.doFilter(request, response);
            } else {
                logger.debug("用户组织标签不匹配资源组织，拒绝访问");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            }
        } catch (Exception e) {
            logger.error("组织标签授权过滤器发生错误: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * 从路径中提取资源ID
     */
    private String extractResourceIdFromPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        logger.debug("提取资源ID，请求路径: {}", path);
        
        // 提取不同类型资源的ID
        // 1. 文件资源: /api/v1/files/{fileMd5}
        if (path.matches(".*/files/[^/]+.*")) {
            String fileId = path.replaceAll(".*/files/([^/]+).*", "$1");
            logger.debug("检测到文件资源请求，提取ID: {}", fileId);
            return fileId;
        }
        
        // 2. 文档删除资源: /api/v1/documents/{file_md5}
        if (path.matches(".*/documents/[a-fA-F0-9]{32}.*")) {
            String fileMd5 = path.replaceAll(".*/documents/([a-fA-F0-9]{32}).*", "$1");
            logger.debug("检测到文档删除请求，提取文件MD5: {}", fileMd5);
            return fileMd5;
        }
        
        // 3. 文档资源: /api/v1/documents/{docId} (数字ID)
        if (path.matches(".*/documents/\\d+.*")) {
            String docId = path.replaceAll(".*/documents/(\\d+).*", "$1");
            logger.debug("检测到文档资源请求，提取ID: {}", docId);
            return docId;
        }
        
        // 4. 上传分片: /api/v1/upload/chunk
        if (path.matches(".*/upload/chunk.*")) {
            String fileMd5 = request.getHeader("X-File-MD5");
            logger.debug("检测到分片上传请求，从请求头提取文件MD5: {}", fileMd5);
            return fileMd5;
        }
        
        // 5. 知识库资源: /api/v1/knowledge/{resourceId}
        if (path.matches(".*/knowledge/[^/]+.*")) {
            String knowledgeId = path.replaceAll(".*/knowledge/([^/]+).*", "$1");
            logger.debug("检测到知识库资源请求，提取ID: {}", knowledgeId);
            return knowledgeId;
        }
        
        logger.debug("未匹配到任何资源类型，返回null");
        return null;
    }
    
    /**
     * 获取资源信息
     * 实际项目中应该根据不同资源类型查询对应的数据库表
     */
    private ResourceInfo getResourceInfo(String resourceId) {
        if (resourceId == null) {
            logger.debug("资源ID为空，无法获取资源信息");
            return null;
        }
        
        logger.debug("尝试获取资源信息，资源ID: {}", resourceId);
        
        // 尝试从文件上传表中获取资源信息
        Optional<FileUpload> fileUpload = fileUploadRepository.findFirstByFileMd5OrderByCreatedAtDesc(resourceId);
        if (fileUpload.isPresent()) {
            FileUpload file = fileUpload.get();
            ResourceInfo info = new ResourceInfo(
                file.getUserId(),
                file.getOrgTag(),
                file.isPublic()
            );
            logger.debug("成功找到文件资源信息 => 资源ID: {}, 拥有者: {}, 组织标签: {}, 是否公开: {}", 
                        resourceId, info.getOwner(), info.getOrgTag(), info.isPublic());
            return info;
        } else {
            logger.debug("在文件上传表中未找到资源 => 资源ID: {}", resourceId);
        }
        
        // TODO: 如果需要支持其他类型的资源，可以在这里添加查询逻辑
        
        // 如果未找到资源，返回null
        logger.debug("未找到任何资源信息 => 资源ID: {}", resourceId);
        return null;
    }
    
    /**
     * 检查资源是否为公开资源
     */
    private boolean isPublicResource(String resourceId) {
        ResourceInfo resourceInfo = getResourceInfo(resourceId);
        return resourceInfo != null && resourceInfo.isPublic();
    }
    
    /**
     * 从请求头中提取 JWT Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    /**
     * 检查用户是否有权限访问该资源
     */
    private boolean isUserAuthorized(String userOrgTags, String resourceOrgTag) {
        // 将用户的组织标签字符串转换为集合
        Set<String> userTags = Arrays.stream(userOrgTags.split(","))
                .collect(Collectors.toSet());
        
        // 检查用户的组织标签是否包含资源的组织标签
        return userTags.contains(resourceOrgTag);
    }
    
    /**
     * 资源信息类，用于封装资源的权限相关信息
     */
    private static class ResourceInfo {
        private final String owner;
        private final String orgTag;
        private final boolean isPublic;
        
        public ResourceInfo(String owner, String orgTag, boolean isPublic) {
            this.owner = owner;
            this.orgTag = orgTag;
            this.isPublic = isPublic;
        }
        
        public String getOwner() {
            return owner;
        }
        
        public String getOrgTag() {
            return orgTag;
        }
        
        public boolean isPublic() {
            return isPublic;
        }
    }
} 
