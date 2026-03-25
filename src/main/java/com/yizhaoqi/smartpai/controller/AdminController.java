package com.yizhaoqi.smartpai.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.OrganizationTag;
import com.yizhaoqi.smartpai.model.RechargePackage;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.OrganizationTagRepository;
import com.yizhaoqi.smartpai.repository.RechargePackageRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.service.InviteCodeService;
import com.yizhaoqi.smartpai.service.ModelProviderConfigService;
import com.yizhaoqi.smartpai.service.RateLimitConfigService;
import com.yizhaoqi.smartpai.service.UsageDashboardService;
import com.yizhaoqi.smartpai.service.UserService;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import com.yizhaoqi.smartpai.utils.LogUtils;
import com.yizhaoqi.smartpai.utils.MinioMigrationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员控制器，提供管理知识库、查看系统状态和监控用户活动的接口
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserService userService;
    
    @Autowired
    private OrganizationTagRepository organizationTagRepository;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MinioMigrationUtil migrationUtil;

    @Autowired
    private InviteCodeService inviteCodeService;

    @Autowired
    private UsageDashboardService usageDashboardService;

    @Autowired
    private RateLimitConfigService rateLimitConfigService;

    @Autowired
    private ModelProviderConfigService modelProviderConfigService;

    @Autowired
    private RechargePackageRepository rechargePackageRepository;

    /**
     * 获取所有用户列表
     */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_ALL_USERS");
        String adminUsername = null;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            User admin = validateAdmin(adminUsername);
            
            LogUtils.logBusiness("ADMIN_GET_ALL_USERS", adminUsername, "管理员开始获取所有用户列表");
            
            List<User> users = userRepository.findAll();
            // 移除敏感信息
            users.forEach(user -> user.setPassword(null));
            
            LogUtils.logUserOperation(adminUsername, "ADMIN_GET_ALL_USERS", "user_list", "SUCCESS");
            LogUtils.logBusiness("ADMIN_GET_ALL_USERS", adminUsername, "成功获取用户列表，用户数量: %d", users.size());
            monitor.end("获取用户列表成功");
            
            return ResponseEntity.ok(Map.of("code", 200, "message", "Get all users successful", "data", users));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ALL_USERS", adminUsername, "获取所有用户失败", e);
            monitor.end("获取用户列表失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Failed to get users: " + e.getMessage()));
        }
    }

    /**
     * 添加知识库文档
     */
    @PostMapping("/knowledge/add")
    public ResponseEntity<?> addKnowledgeDocument(
            @RequestHeader("Authorization") String token,
            @RequestParam("file") MultipartFile file,
            @RequestParam("description") String description) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            // 这里应该调用知识库管理服务来处理文档
            // knowledgeService.addDocument(file, description);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "文档已成功添加到知识库");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_ADD_KNOWLEDGE", adminUsername, "添加知识库文档失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "添加文档失败: " + e.getMessage()));
        }
    }

    /**
     * 删除知识库文档
     */
    @DeleteMapping("/knowledge/{documentId}")
    public ResponseEntity<?> deleteKnowledgeDocument(
            @RequestHeader("Authorization") String token,
            @PathVariable("documentId") String documentId) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            // 这里应该调用知识库管理服务来删除文档
            // knowledgeService.deleteDocument(documentId);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "文档已成功从知识库中删除");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_DELETE_KNOWLEDGE", adminUsername, "删除知识库文档失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "删除文档失败: " + e.getMessage()));
        }
    }

    /**
     * 获取系统状态
     */
    @GetMapping("/system/status")
    public ResponseEntity<?> getSystemStatus(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            // 这里应该调用系统监控服务来获取系统状态
            // SystemStatus status = monitoringService.getSystemStatus();
            
            // 模拟系统状态数据
            Map<String, Object> status = new HashMap<>();
            status.put("cpu_usage", "30%");
            status.put("memory_usage", "45%");
            status.put("disk_usage", "60%");
            status.put("active_users", 15);
            status.put("total_documents", 250);
            status.put("total_conversations", 1200);
            
            return ResponseEntity.ok(Map.of("data", status));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATUS", adminUsername, "获取系统状态失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取系统状态失败: " + e.getMessage()));
        }
    }

    /**
     * 获取用户活动日志
     */
    @GetMapping("/user-activities")
    public ResponseEntity<?> getUserActivities(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String start_date,
            @RequestParam(required = false) String end_date) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            // 这里应该调用用户活动监控服务来获取活动日志
            // List<UserActivity> activities = activityService.getUserActivities(username, startDate, endDate);
            
            // 模拟用户活动数据
            List<Map<String, Object>> activities = List.of(
                Map.of(
                    "username", "user1",
                    "action", "LOGIN",
                    "timestamp", "2023-03-01T10:15:30",
                    "ip_address", "192.168.1.100"
                ),
                Map.of(
                    "username", "user2",
                    "action", "UPLOAD_FILE",
                    "timestamp", "2023-03-01T11:20:45",
                    "ip_address", "192.168.1.101"
                )
            );
            
            return ResponseEntity.ok(Map.of("data", activities));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_USER_ACTIVITIES", adminUsername, "获取用户活动失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取用户活动失败: " + e.getMessage()));
        }
    }

    @GetMapping("/usage/overview")
    public ResponseEntity<?> getUsageOverview(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "7") int days) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "获取用量总览成功",
                    "data", usageDashboardService.buildOverview(days)
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_USAGE_OVERVIEW", adminUsername, "获取用量总览失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取用量总览失败: " + e.getMessage()));
        }
    }

    @GetMapping("/rate-limits")
    public ResponseEntity<?> getRateLimits(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "获取限流配置成功",
                    "data", rateLimitConfigService.getCurrentSettings()
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_RATE_LIMITS", adminUsername, "获取限流配置失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取限流配置失败: " + e.getMessage()));
        }
    }

    @PutMapping("/rate-limits")
    public ResponseEntity<?> updateRateLimits(
            @RequestHeader("Authorization") String token,
            @RequestBody RateLimitConfigService.UpdateRateLimitRequest request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "限流配置更新成功",
                    "data", rateLimitConfigService.updateSettings(request, adminUsername)
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_RATE_LIMITS", adminUsername, "更新限流配置失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_RATE_LIMITS", adminUsername, "更新限流配置异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "更新限流配置失败: " + e.getMessage()));
        }
    }

    @GetMapping("/model-providers")
    public ResponseEntity<?> getModelProviders(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "获取模型配置成功",
                    "data", modelProviderConfigService.getCurrentSettings()
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_MODEL_PROVIDERS", adminUsername, "获取模型配置失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取模型配置失败: " + e.getMessage()));
        }
    }

    @PutMapping("/model-providers/{scope}")
    public ResponseEntity<?> updateModelProviders(
            @RequestHeader("Authorization") String token,
            @PathVariable String scope,
            @RequestBody ModelProviderConfigService.UpdateScopeRequest request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "模型配置更新成功",
                    "data", modelProviderConfigService.updateScope(scope, request, adminUsername)
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_MODEL_PROVIDERS", adminUsername, "更新模型配置失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_MODEL_PROVIDERS", adminUsername, "更新模型配置异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "更新模型配置失败: " + e.getMessage()));
        }
    }

    @PostMapping("/model-providers/{scope}/test")
    public ResponseEntity<?> testModelProviderConnection(
            @RequestHeader("Authorization") String token,
            @PathVariable String scope,
            @RequestBody ModelProviderConfigService.ProviderConnectionTestRequest request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "模型连接测试完成",
                    "data", modelProviderConfigService.testConnection(scope, request)
            ));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "模型连接测试失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建管理员用户
     */
    @PostMapping("/users/create-admin")
    public ResponseEntity<?> createAdminUser(
            @RequestHeader("Authorization") String token,
            @RequestBody AdminUserRequest request) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            userService.createAdminUser(request.username(), request.password(), adminUsername);
            return ResponseEntity.ok(Map.of("code", 200, "message", "管理员用户创建成功"));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ADMIN_USER", adminUsername, "创建管理员用户失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ADMIN_USER", adminUsername, "创建管理员用户异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "创建管理员用户失败: " + e.getMessage()));
        }
    }

    /**
     * 创建邀请码
     */
    @PostMapping("/invite-codes")
    public ResponseEntity<?> createInviteCode(
            @RequestHeader("Authorization") String token,
            @RequestBody CreateInviteCodeRequest request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            var created = inviteCodeService.createInviteCodes(
                    adminUsername,
                    request.code(),
                    request.maxUses(),
                    null,
                    request.count()
            );
            return ResponseEntity.ok(Map.of("code", 200, "message", "邀请码创建成功", "data", created));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "创建邀请码失败: " + e.getMessage()));
        }
    }

    /**
     * 分页查询邀请码
     */
    @GetMapping("/invite-codes")
    public ResponseEntity<?> listInviteCodes(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        return ResponseEntity.ok(Map.of("code", 200, "message", "获取邀请码成功", "data", inviteCodeService.list(enabled, page, size)));
    }

    /**
     * 禁用邀请码
     */
    @PatchMapping("/invite-codes/{id}/disable")
    public ResponseEntity<?> disableInviteCode(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            inviteCodeService.disable(id, adminUsername);
            return ResponseEntity.ok(Map.of("code", 200, "message", "邀请码已禁用"));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "禁用邀请码失败: " + e.getMessage()));
        }
    }

    /**
     * 删除邀请码
     */
    @DeleteMapping("/invite-codes/{id}")
    public ResponseEntity<?> deleteInviteCode(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            inviteCodeService.delete(id, adminUsername);
            return ResponseEntity.ok(Map.of("code", 200, "message", "邀请码已删除"));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "删除邀请码失败: " + e.getMessage()));
        }
    }

    /**
     * 编辑邀请码
     */
    @PutMapping("/invite-codes/{id}")
    public ResponseEntity<?> updateInviteCode(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @RequestBody UpdateInviteCodeRequest request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            var updated = inviteCodeService.update(id, adminUsername, request.code(), request.maxUses(), null);
            return ResponseEntity.ok(Map.of("code", 200, "message", "邀请码已更新", "data", updated));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "编辑邀请码失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建组织标签
     */
    @PostMapping("/org-tags")
    public ResponseEntity<?> createOrganizationTag(
            @RequestHeader("Authorization") String token,
            @RequestBody OrgTagRequest request) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            OrganizationTag tag = userService.createOrganizationTag(
                request.tagId(), 
                request.name(), 
                request.description(), 
                request.parentTag(), 
                request.uploadMaxSizeMb(),
                adminUsername
            );
            return ResponseEntity.ok(Map.of("code", 200, "message", "组织标签创建成功", "data", tag));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ORG_TAG", adminUsername, "创建组织标签失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ORG_TAG", adminUsername, "创建组织标签异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "创建组织标签失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取所有组织标签
     */
    @GetMapping("/org-tags")
    public ResponseEntity<?> getAllOrganizationTags(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            List<OrganizationTag> tags = organizationTagRepository.findAll();
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取组织标签成功", "data", tags));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ORG_TAGS", adminUsername, "获取组织标签失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取组织标签失败: " + e.getMessage()));
        }
    }
    
    /**
     * 为用户分配组织标签
     */
    @PutMapping("/users/{userId}/org-tags")
    public ResponseEntity<?> assignOrgTagsToUser(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId,
            @RequestBody AssignOrgTagsRequest request) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            userService.assignOrgTagsToUser(userId, request.orgTags(), adminUsername);
            return ResponseEntity.ok(Map.of("code", 200, "message", "组织标签分配成功"));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_ASSIGN_ORG_TAGS", adminUsername, "分配组织标签失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_ASSIGN_ORG_TAGS", adminUsername, "分配组织标签异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "分配组织标签失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取组织标签树结构
     */
    @GetMapping("/org-tags/tree")
    public ResponseEntity<?> getOrganizationTagTree(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            List<Map<String, Object>> tagTree = userService.getOrganizationTagTree();
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "获取组织标签树成功", 
                "data", tagTree
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ORG_TAG_TREE", adminUsername, "获取组织标签树失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取组织标签树失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新组织标签
     */
    @PutMapping("/org-tags/{tagId}")
    public ResponseEntity<?> updateOrganizationTag(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId,
            @RequestBody OrgTagUpdateRequest request) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            OrganizationTag updatedTag = userService.updateOrganizationTag(
                tagId, 
                request.name(), 
                request.description(), 
                request.parentTag(), 
                request.uploadMaxSizeMb(),
                adminUsername
            );
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "组织标签更新成功", 
                "data", updatedTag
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_ORG_TAG", adminUsername, "更新组织标签失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_ORG_TAG", adminUsername, "更新组织标签异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "更新组织标签失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除组织标签
     */
    @DeleteMapping("/org-tags/{tagId}")
    public ResponseEntity<?> deleteOrganizationTag(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            userService.deleteOrganizationTag(tagId, adminUsername);
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "组织标签删除成功"
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_DELETE_ORG_TAG", adminUsername, "删除组织标签失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_DELETE_ORG_TAG", adminUsername, "删除组织标签异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "删除组织标签失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取用户列表
     */
    @GetMapping("/users/list")
    public ResponseEntity<?> getUserList(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orgTag,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            Map<String, Object> usersData = userService.getUserList(keyword, orgTag, status, page, size);
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "获取用户列表成功", 
                "data", usersData
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_GET_USER_LIST", adminUsername, "获取用户列表失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_USER_LIST", adminUsername, "获取用户列表异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取用户列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 管理员查询所有对话历史
     */
    @GetMapping("/conversation")
    public ResponseEntity<?> getAllConversations(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String userid,
            @RequestParam(required = false) String start_date,
            @RequestParam(required = false) String end_date) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_ALL_CONVERSATIONS");
        String adminUsername = null;
        try {
            // 验证管理员权限
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            User admin = validateAdmin(adminUsername);
            
            LogUtils.logBusiness("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "管理员开始查询对话历史，目标用户ID: %s, 时间范围: %s 到 %s", userid, start_date, end_date);
            
            List<Map<String, Object>> allConversations = new ArrayList<>();
            
            // 如果指定了userid，先验证用户是否存在
            String targetUsername = null;
            if (userid != null && !userid.isEmpty()) {
                try {
                    Long userIdLong = Long.parseLong(userid);
                    Optional<User> targetUser = userRepository.findById(userIdLong);
                    if (targetUser.isPresent()) {
                        targetUsername = targetUser.get().getUsername();
                        LogUtils.logBusiness("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "找到目标用户: ID=%s, 用户名=%s", userid, targetUsername);
                    } else {
                        LogUtils.logBusiness("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "目标用户ID不存在: %s", userid);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("code", 404, "message", "目标用户不存在"));
                    }
                } catch (NumberFormatException e) {
                    LogUtils.logBusiness("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "无效的用户ID格式: %s", userid);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("code", 400, "message", "无效的用户ID格式"));
                }
            }
            
            // 获取所有Redis键中以"user:"开头的键
            Set<String> userKeys = redisTemplate.keys("user:*:current_conversation");
            
            if (userKeys != null && !userKeys.isEmpty()) {
                for (String userKey : userKeys) {
                    String conversationId = redisTemplate.opsForValue().get(userKey);
                    if (conversationId != null) {
                        // 提取用户ID
                        String redisUserId = userKey.replace("user:", "").replace(":current_conversation", "");
                        
                        // 如果指定了userid，只查询该用户的对话
                        if (userid != null && !userid.isEmpty()) {
                            // 检查Redis中的用户ID是否匹配（可能是数字ID或用户名）
                            if (!redisUserId.equals(userid) && !redisUserId.equals(targetUsername)) {
                                continue;
                            }
                        }
                        
                        // 获取对话内容，使用实际的用户名而不是Redis中的ID
                        String conversationKey = "conversation:" + conversationId;
                        String json = redisTemplate.opsForValue().get(conversationKey);
                        if (json != null) {
                            String displayUsername = targetUsername != null ? targetUsername : redisUserId;
                            processRedisConversation(json, allConversations, displayUsername, conversationId, start_date, end_date);
                        }
                    }
                }
            }
            
            LogUtils.logBusiness("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "管理员查询完成，共获取到 %d 条对话记录", allConversations.size());
            LogUtils.logUserOperation(adminUsername, "ADMIN_GET_ALL_CONVERSATIONS", "conversation_history", "SUCCESS");
            monitor.end("管理员查询对话历史成功");
            
            // 构建统一响应格式
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取对话历史成功");  
            response.put("data", allConversations);
            return ResponseEntity.ok().body(response);
            
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "管理员获取对话历史失败: %s", e, e.getMessage());
            monitor.end("管理员获取对话历史失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "管理员获取对话历史异常: %s", e, e.getMessage());
            monitor.end("管理员获取对话历史异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "服务器内部错误: " + e.getMessage()));
        }
    }
    
    /**
     * 处理Redis中的对话数据
     */
    private void processRedisConversation(String json, List<Map<String, Object>> targetList, String username,
                                          String conversationId, String startDate, String endDate) throws JsonProcessingException {
        List<Map<String, Object>> history = objectMapper.readValue(json,
                new TypeReference<List<Map<String, Object>>>() {});
        
        // 解析时间范围
        java.time.LocalDateTime startDateTime = null;
        java.time.LocalDateTime endDateTime = null;
        
        if (startDate != null && !startDate.trim().isEmpty()) {
            try {
                startDateTime = parseDateTime(startDate);
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_ALL_CONVERSATIONS", username, "起始时间解析失败: %s", e, startDate);
            }
        }
        
        if (endDate != null && !endDate.trim().isEmpty()) {
            try {
                endDateTime = parseDateTime(endDate);
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_ALL_CONVERSATIONS", username, "结束时间解析失败: %s", e, endDate);
            }
        }
        
        // 将对话转换为前端需要的格式，使用存储的时间戳并添加用户名
        for (Map<String, Object> message : history) {
            String messageTimestamp = String.valueOf(message.getOrDefault("timestamp", "未知时间"));
            
            // 时间过滤
            if (startDateTime != null || endDateTime != null) {
                if (!"未知时间".equals(messageTimestamp)) {
                    try {
                        java.time.LocalDateTime messageDateTime = java.time.LocalDateTime.parse(messageTimestamp, 
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                        
                        // 检查是否在时间范围内
                        if (startDateTime != null && messageDateTime.isBefore(startDateTime)) {
                            continue; // 跳过早于起始时间的消息
                        }
                        if (endDateTime != null && messageDateTime.isAfter(endDateTime)) {
                            continue; // 跳过晚于结束时间的消息
                        }
                    } catch (Exception e) {
                        // 时间戳格式不正确，跳过过滤（包含所有消息）
                        LogUtils.logBusinessError("ADMIN_GET_ALL_CONVERSATIONS", username, "消息时间戳格式错误: %s", e, messageTimestamp);
                    }
                }
                // 如果是"未知时间"且设置了时间过滤，跳过该消息
                else if (startDateTime != null || endDateTime != null) {
                    continue;
                }
            }
            
            Map<String, Object> messageWithMetadata = new HashMap<>();
            messageWithMetadata.put("role", message.get("role"));
            messageWithMetadata.put("content", message.get("content"));
            messageWithMetadata.put("timestamp", messageTimestamp);
            messageWithMetadata.put("username", username);
            messageWithMetadata.put("conversationId", conversationId);
            if (message.get("referenceMappings") != null) {
                messageWithMetadata.put("referenceMappings", message.get("referenceMappings"));
            }
            targetList.add(messageWithMetadata);
        }
    }
    
    /**
     * 解析日期时间字符串，支持多种格式
     */
    private java.time.LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 尝试标准格式解析 (2023-01-01T12:00:00)
            return java.time.LocalDateTime.parse(dateTimeStr);
        } catch (java.time.format.DateTimeParseException e1) {
            try {
                // 尝试解析不带秒的格式 (2023-01-01T12:00)
                if (dateTimeStr.length() == 16) {
                    return java.time.LocalDateTime.parse(dateTimeStr + ":00");
                }
                
                // 尝试解析不带分钟和秒的格式 (2023-01-01T12)
                if (dateTimeStr.length() == 13) {
                    return java.time.LocalDateTime.parse(dateTimeStr + ":00:00");
                }
                
                // 尝试解析日期格式 (2023-01-01)
                if (dateTimeStr.length() == 10) {
                    return java.time.LocalDateTime.parse(dateTimeStr + "T00:00:00");
                }
                
                // 如果以上都失败，尝试使用自定义格式解析
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                return java.time.LocalDateTime.parse(dateTimeStr, formatter);
            } catch (Exception e2) {
                LogUtils.logBusinessError("PARSE_DATETIME", "system", "无法解析日期时间: %s", e2, dateTimeStr);
                throw new CustomException("无效的日期格式: " + dateTimeStr, HttpStatus.BAD_REQUEST);
            }
        }
    }
    
    /**
     * 验证用户是否为管理员
     */
    private User validateAdmin(String username) {
        if (username == null || username.isEmpty()) {
            throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
        }
        
        User admin = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        
        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Unauthorized access: Admin role required", HttpStatus.FORBIDDEN);
        }

        return admin;
    }

    /**
     * 迁移 MinIO 文件从旧路径到新路径
     * 旧路径: merged/{fileName}
     * 新路径: merged/{fileMd5}
     *
     * @param token JWT token
     * @param adminKey 管理员密钥（简单验证）
     * @return 迁移报告
     */
    @PostMapping("/migrate-minio")
    public ResponseEntity<?> migrateMinioFiles(
            @RequestHeader("Authorization") String token,
            @RequestParam String adminKey) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("MIGRATE_MINIO");
        String adminUsername = null;

        try {
            // 验证管理员权限
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);

            // 简单密钥验证
            if (!"migration2024".equals(adminKey)) {
                Map<String, Object> response = new HashMap<>();
                response.put("code", 403);
                response.put("message", "无效的管理员密钥");
                return ResponseEntity.status(403).body(response);
            }

            LogUtils.logBusiness("MIGRATE_MINIO", adminUsername, "开始MinIO文件迁移");

            MinioMigrationUtil.MigrationReport report = migrationUtil.migrateAllFiles();

            LogUtils.logBusiness("MIGRATE_MINIO", adminUsername,
                "迁移完成: 成功=%d, 跳过=%d, 失败=%d",
                report.successCount, report.skipCount, report.errorCount);

            monitor.end("MinIO文件迁移完成");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "迁移完成");
            response.put("data", Map.of(
                "successCount", report.successCount,
                "skipCount", report.skipCount,
                "errorCount", report.errorCount,
                "errors", report.getErrors()
            ));
            return ResponseEntity.ok(response);

        } catch (CustomException e) {
            LogUtils.logBusinessError("MIGRATE_MINIO", adminUsername, "MinIO文件迁移失败: %s", e, e.getMessage());
            monitor.end("MinIO文件迁移失败: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", e.getStatus().value());
            response.put("message", e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("MIGRATE_MINIO", adminUsername, "MinIO文件迁移异常: %s", e, e.getMessage());
            monitor.end("MinIO文件迁移失败: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("message", "迁移失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 清空所有数据（危险操作，仅用于测试环境）
     *
     * @param token JWT token
     * @param adminKey 管理员密钥
     * @return 操作结果
     */
    @PostMapping("/clear-all-data")
    public ResponseEntity<?> clearAllData(
            @RequestHeader("Authorization") String token,
            @RequestParam String adminKey) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("CLEAR_ALL_DATA");
        String adminUsername = null;

        try {
            // 验证管理员权限
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);

            // 更严格的密钥验证
            if (!"CLEAR_ALL_2024".equals(adminKey)) {
                Map<String, Object> response = new HashMap<>();
                response.put("code", 403);
                response.put("message", "无效的管理员密钥");
                return ResponseEntity.status(403).body(response);
            }

            LogUtils.logBusiness("CLEAR_ALL_DATA", adminUsername, "开始清空所有数据");

            migrationUtil.clearAllData();

            LogUtils.logBusiness("CLEAR_ALL_DATA", adminUsername, "所有数据已清空");

            monitor.end("数据清空完成");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "所有数据已清空");
            return ResponseEntity.ok(response);

        } catch (CustomException e) {
            LogUtils.logBusinessError("CLEAR_ALL_DATA", adminUsername, "清空数据失败: %s", e, e.getMessage());
            monitor.end("数据清空失败: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", e.getStatus().value());
            response.put("message", e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("CLEAR_ALL_DATA", adminUsername, "清空数据异常: %s", e, e.getMessage());
            monitor.end("数据清空失败: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("message", "清空失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // ==================== 充值套餐管理相关接口 ====================

    /**
     * 获取所有充值套餐列表（包含禁用）
     */
    @GetMapping("/recharge-packages")
    public ResponseEntity<?> getAllRechargePackages(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            LogUtils.logBusiness("ADMIN_GET_RECHARGE_PACKAGES", adminUsername, "管理员开始获取充值套餐列表");
            
            List<RechargePackage> packages = rechargePackageRepository.findAllByDeletedFalseOrderBySortOrderAsc();
            
            LogUtils.logBusiness("ADMIN_GET_RECHARGE_PACKAGES", adminUsername, 
                    "成功获取充值套餐列表，套餐数量：%d", packages.size());
            
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "获取充值套餐列表成功",
                    "data", packages
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_RECHARGE_PACKAGES", adminUsername, "获取充值套餐列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取充值套餐列表失败：" + e.getMessage()));
        }
    }

    /**
     * 创建充值套餐
     */
    @PostMapping("/recharge-packages")
    public ResponseEntity<?> createRechargePackage(
            @RequestHeader("Authorization") String token,
            @RequestBody RechargePackageRequest request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            LogUtils.logBusiness("ADMIN_CREATE_RECHARGE_PACKAGE", adminUsername, 
                    "管理员开始创建充值套餐：%s", request.packageName());
            
            // 验证必填字段
            if (request.packageName() == null || request.packageName().isBlank()) {
                throw new CustomException("套餐名称不能为空", HttpStatus.BAD_REQUEST);
            }
            if (request.packagePrice() == null || request.packagePrice() <= 0) {
                throw new CustomException("套餐价格必须大于 0", HttpStatus.BAD_REQUEST);
            }
            if (request.llmToken() == null || request.llmToken() < 0) {
                throw new CustomException("LLM Token 数量不能为负数", HttpStatus.BAD_REQUEST);
            }
            if (request.embeddingToken() == null || request.embeddingToken() < 0) {
                throw new CustomException("Embedding Token 数量不能为负数", HttpStatus.BAD_REQUEST);
            }
            
            RechargePackage pkg = new RechargePackage();
            pkg.setPackageName(request.packageName());
            pkg.setPackagePrice(request.packagePrice());
            pkg.setPackageDesc(request.packageDesc());
            pkg.setPackageBenefit(request.packageBenefit());
            pkg.setLlmToken(request.llmToken());
            pkg.setEmbeddingToken(request.embeddingToken());
            pkg.setEnabled(request.enabled() != null ? request.enabled() : true);
            pkg.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
            pkg.setDeleted(false);
            
            pkg = rechargePackageRepository.save(pkg);
            
            LogUtils.logBusiness("ADMIN_CREATE_RECHARGE_PACKAGE", adminUsername, 
                    "成功创建充值套餐，id=%d", pkg.getId());
            
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "创建充值套餐成功",
                    "data", pkg
            ));
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_CREATE_RECHARGE_PACKAGE", adminUsername, "创建充值套餐失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "创建充值套餐失败：" + e.getMessage()));
        }
    }

    /**
     * 更新充值套餐
     */
    @PutMapping("/recharge-packages/{id}")
    public ResponseEntity<?> updateRechargePackage(
            @RequestHeader("Authorization") String token,
            @PathVariable Integer id,
            @RequestBody RechargePackageRequest request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            LogUtils.logBusiness("ADMIN_UPDATE_RECHARGE_PACKAGE", adminUsername, 
                    "管理员开始更新充值套餐，id=%d", id);
            
            RechargePackage pkg = rechargePackageRepository.findById(id)
                    .orElseThrow(() -> new CustomException("套餐不存在", HttpStatus.BAD_REQUEST));
            
            // 更新字段
            if (request.packageName() != null) {
                pkg.setPackageName(request.packageName());
            }
            if (request.packagePrice() != null) {
                pkg.setPackagePrice(request.packagePrice());
            }
            if (request.packageDesc() != null) {
                pkg.setPackageDesc(request.packageDesc());
            }
            if (request.packageBenefit() != null) {
                pkg.setPackageBenefit(request.packageBenefit());
            }
            if (request.llmToken() != null) {
                pkg.setLlmToken(request.llmToken());
            }
            if (request.embeddingToken() != null) {
                pkg.setEmbeddingToken(request.embeddingToken());
            }
            if (request.enabled() != null) {
                pkg.setEnabled(request.enabled());
            }
            if (request.sortOrder() != null) {
                pkg.setSortOrder(request.sortOrder());
            }
            
            pkg.setUpdatedAt(LocalDateTime.now());
            pkg = rechargePackageRepository.save(pkg);
            
            LogUtils.logBusiness("ADMIN_UPDATE_RECHARGE_PACKAGE", adminUsername, 
                    "成功更新充值套餐，id=%d", id);
            
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "更新充值套餐成功",
                    "data", pkg
            ));
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_RECHARGE_PACKAGE", adminUsername, "更新充值套餐失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "更新充值套餐失败：" + e.getMessage()));
        }
    }

    /**
     * 删除充值套餐（逻辑删除）
     */
    @DeleteMapping("/recharge-packages/{id}")
    public ResponseEntity<?> deleteRechargePackage(
            @RequestHeader("Authorization") String token,
            @PathVariable Integer id) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            LogUtils.logBusiness("ADMIN_DELETE_RECHARGE_PACKAGE", adminUsername, 
                    "管理员开始删除充值套餐，id=%d", id);
            
            RechargePackage pkg = rechargePackageRepository.findById(id)
                    .orElseThrow(() -> new CustomException("套餐不存在", HttpStatus.BAD_REQUEST));
            
            // 逻辑删除：设置 deleted=true
            pkg.setDeleted(true);
            pkg.setUpdatedAt(LocalDateTime.now());
            rechargePackageRepository.save(pkg);
            
            LogUtils.logBusiness("ADMIN_DELETE_RECHARGE_PACKAGE", adminUsername, 
                    "成功删除充值套餐（逻辑删除），id=%d", id);
            
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "删除充值套餐成功"
            ));
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_DELETE_RECHARGE_PACKAGE", adminUsername, "删除充值套餐失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "删除充值套餐失败：" + e.getMessage()));
        }
    }
}

/**
 * 管理员用户请求体
 */
record AdminUserRequest(String username, String password) {}

/**
 * 组织标签请求体
 */
record OrgTagRequest(String tagId, String name, String description, String parentTag, Long uploadMaxSizeMb) {}

/**
 * 分配组织标签请求体
 */
record AssignOrgTagsRequest(List<String> orgTags) {}

// 添加组织标签更新请求记录类
record OrgTagUpdateRequest(String name, String description, String parentTag, Long uploadMaxSizeMb) {}

record CreateInviteCodeRequest(String code, Integer maxUses, Integer count) {}

record UpdateInviteCodeRequest(String code, Integer maxUses) {}

/**
 * 充值套餐请求体
 */
record RechargePackageRequest(
        String packageName,
        Long packagePrice,
        String packageDesc,
        String packageBenefit,
        Long llmToken,
        Long embeddingToken,
        Boolean enabled,
        Integer sortOrder
) {}
