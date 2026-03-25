package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.AppAuthProperties;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.RegistrationMode;
import com.yizhaoqi.smartpai.model.OrganizationTag;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.OrganizationTagRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.utils.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

/**
 * UserService 类用于处理用户注册和认证相关的业务逻辑。
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    private static final String DEFAULT_ORG_TAG = "DEFAULT";
    private static final String DEFAULT_ORG_NAME = "默认组织";
    private static final String DEFAULT_ORG_DESCRIPTION = "系统默认组织标签，自动分配给所有新用户";
    private static final String PRIVATE_TAG_PREFIX = "PRIVATE_";
    private static final String PRIVATE_ORG_NAME_SUFFIX = "的私人空间";
    private static final String PRIVATE_ORG_DESCRIPTION = "用户的私人组织标签，仅用户本人可访问";
    private static final long BYTES_PER_MB = 1024L * 1024L;
    private static final int MAX_TAG_ID_LENGTH = 255;
    private static final Pattern NON_ALNUM_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final Pattern TRIM_DASH_PATTERN = Pattern.compile("(^-+|-+$)");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*\\d).{6,18}$"
    );

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private OrganizationTagRepository organizationTagRepository;
    
    @Autowired
    private OrgTagCacheService orgTagCacheService;

    @Autowired
    private AppAuthProperties appAuthProperties;

    @Autowired
    private InviteCodeService inviteCodeService;

    @Autowired
    private UsageQuotaService usageQuotaService;

    @Value("${spring.servlet.multipart.max-file-size:50MB}")
    private String globalUploadMaxFileSize;

    /**
     * 注册新用户。
     *
     * @param username 要注册的用户名
     * @param password 要注册的用户密码
     * @throws CustomException 如果用户名已存在，则抛出异常
     */
    @Transactional
    public void registerUser(String username, String password) {
        registerUser(username, password, null);
    }

    @Transactional
    public void registerUser(String username, String password, String inviteCode) {
        validateRegistrationPolicy(username, inviteCode);
        validatePassword(password);

        // 检查数据库中是否已存在该用户名
        if (userRepository.findByUsername(username).isPresent()) {
            // 若用户名已存在，抛出自定义异常，状态码为 400 Bad Request
            throw new CustomException("Username already exists", HttpStatus.BAD_REQUEST);
        }
        
        // 确保默认组织标签存在（系统内部使用）
        ensureDefaultOrgTagExists();
        
        User user = new User();
        user.setUsername(username);
        // 对密码进行加密处理并设置到 User 对象中
        user.setPassword(PasswordUtil.encode(password));
        // 设置用户角色为普通用户
        user.setRole(User.Role.USER);
        
        // 保存用户以生成ID
        userRepository.save(user);
        
        // 创建用户的私人组织标签
        String privateTagId = PRIVATE_TAG_PREFIX + username;
        createPrivateOrgTag(privateTagId, username, user);
        
        // 新用户默认拥有系统默认组织和自己的私人组织
        List<String> assignedOrgTags = List.of(DEFAULT_ORG_TAG, privateTagId);
        user.setOrgTags(String.join(",", assignedOrgTags));
        
        // 设置私人组织标签为主组织标签
        user.setPrimaryOrg(privateTagId);
        
        userRepository.save(user);
        
        // 缓存组织标签信息
        orgTagCacheService.cacheUserOrgTags(username, assignedOrgTags);
        orgTagCacheService.cacheUserPrimaryOrg(username, privateTagId);
        
        logger.info("User registered successfully with default and private organization tags: {}", username);
    }

    private void validateRegistrationPolicy(String username, String inviteCode) {
        RegistrationMode mode = appAuthProperties.getRegistration().getMode();
        boolean inviteRequired = appAuthProperties.getRegistration().isInviteRequired() || mode == RegistrationMode.INVITE_ONLY;

        if (mode == RegistrationMode.CLOSED) {
            logger.warn("Registration blocked because registration mode is CLOSED, username: {}", username);
            throw new CustomException("REGISTRATION_CLOSED", HttpStatus.FORBIDDEN);
        }

        if (inviteRequired) {
            inviteCodeService.consume(inviteCode, username);
        }
    }
    
    /**
     * 创建用户的私人组织标签
     */
    private void createPrivateOrgTag(String privateTagId, String username, User owner) {
        // 检查私人标签是否已存在
        if (!organizationTagRepository.existsByTagId(privateTagId)) {
            logger.info("Creating private organization tag for user: {}", username);
            
            // 创建私人组织标签
            OrganizationTag privateTag = new OrganizationTag();
            privateTag.setTagId(privateTagId);
            privateTag.setName(username + PRIVATE_ORG_NAME_SUFFIX);
            privateTag.setDescription(PRIVATE_ORG_DESCRIPTION);
            privateTag.setCreatedBy(owner);
            
            organizationTagRepository.save(privateTag);
            logger.info("Private organization tag created successfully for user: {}", username);
        }
    }

    /**
     * 确保默认组织标签存在
     */
    private void ensureDefaultOrgTagExists() {
        if (!organizationTagRepository.existsByTagId(DEFAULT_ORG_TAG)) {
            logger.info("Creating default organization tag");
            
            // 寻找一个管理员用户作为创建者
            Optional<User> adminUser = userRepository.findAll().stream()
                    .filter(user -> User.Role.ADMIN.equals(user.getRole()))
                    .findFirst();

            User creator = adminUser.orElseThrow(
                    () -> new CustomException("No admin user exists to initialize default organization tag", HttpStatus.INTERNAL_SERVER_ERROR));
            
            // 创建默认组织标签
            OrganizationTag defaultTag = new OrganizationTag();
            defaultTag.setTagId(DEFAULT_ORG_TAG);
            defaultTag.setName(DEFAULT_ORG_NAME);
            defaultTag.setDescription(DEFAULT_ORG_DESCRIPTION);
            defaultTag.setCreatedBy(creator);
            
            organizationTagRepository.save(defaultTag);
            logger.info("Default organization tag created successfully");
        }
    }

    /**
     * 创建管理员用户。
     *
     * @param username 要注册的管理员用户名
     * @param password 要注册的管理员密码
     * @param creatorUsername 创建者的用户名（必须是已存在的管理员）
     * @throws CustomException 如果用户名已存在或创建者不是管理员，则抛出异常
     */
    public void createAdminUser(String username, String password, String creatorUsername) {
        // 验证创建者是否为管理员
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new CustomException("Creator not found", HttpStatus.NOT_FOUND));
        
        if (creator.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can create admin accounts", HttpStatus.FORBIDDEN);
        }
        
        // 检查数据库中是否已存在该用户名
        if (userRepository.findByUsername(username).isPresent()) {
            throw new CustomException("Username already exists", HttpStatus.BAD_REQUEST);
        }

        validatePassword(password);
        
        User adminUser = new User();
        adminUser.setUsername(username);
        adminUser.setPassword(PasswordUtil.encode(password));
        adminUser.setRole(User.Role.ADMIN);
        userRepository.save(adminUser);
    }

    private void validatePassword(String password) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new CustomException("密码格式不正确，6-18位字符，必须包含字母和数字", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 对用户进行认证。
     *
     * @param username 要认证的用户名
     * @param password 要认证的用户密码
     * @return 认证成功后返回用户的用户名
     * @throws CustomException 如果用户名或密码无效，则抛出异常
     */
    public String authenticateUser(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("Invalid username or password", HttpStatus.UNAUTHORIZED));
        // 比较输入的密码和数据库中存储的加密密码是否匹配
        if (!PasswordUtil.matches(password, user.getPassword())) {
            // 若不匹配，抛出自定义异常，状态码为 401 Unauthorized
            throw new CustomException("Invalid username or password", HttpStatus.UNAUTHORIZED);
        }
        // 认证成功，返回用户的用户名
        return user.getUsername();
    }
    
    /**
     * 创建组织标签
     * 
     * @param tagId 标签唯一标识
     * @param name 标签名称
     * @param description 标签描述
     * @param parentTag 父标签ID（可选）
     * @param creatorUsername 创建者用户名（必须是管理员）
     */
    @Transactional
    public OrganizationTag createOrganizationTag(String tagId, String name, String description, 
                                                String parentTag, Long uploadMaxSizeMb, String creatorUsername) {
        // 验证创建者是否为管理员
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new CustomException("Creator not found", HttpStatus.NOT_FOUND));
        
        if (creator.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can create organization tags", HttpStatus.FORBIDDEN);
        }
        
        String resolvedTagId = resolveOrGenerateTagId(tagId, name);
        
        // 如果指定了父标签，检查父标签是否存在
        if (parentTag != null && !parentTag.isEmpty()) {
            organizationTagRepository.findByTagId(parentTag)
                    .orElseThrow(() -> new CustomException("Parent tag not found", HttpStatus.NOT_FOUND));
        }
        
        OrganizationTag tag = new OrganizationTag();
        tag.setTagId(resolvedTagId);
        tag.setName(name);
        tag.setDescription(description);
        tag.setParentTag(parentTag);
        tag.setUploadMaxSizeBytes(normalizeUploadMaxSizeBytes(uploadMaxSizeMb));
        tag.setCreatedBy(creator);
        
        OrganizationTag savedTag = organizationTagRepository.save(tag);
        
        // 清除标签缓存，因为层级关系可能变化
        orgTagCacheService.invalidateAllEffectiveTagsCache();
        
        return savedTag;
    }
    
    /**
     * 为用户分配组织标签
     * 
     * @param userId 用户ID
     * @param orgTags 组织标签ID列表
     * @param adminUsername 管理员用户名
     */
    @Transactional
    public void assignOrgTagsToUser(Long userId, List<String> orgTags, String adminUsername) {
        // 验证操作者是否为管理员
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.NOT_FOUND));
        
        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can assign organization tags", HttpStatus.FORBIDDEN);
        }
        
        // 查找用户
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        
        // 验证所有标签是否存在
        for (String tagId : orgTags) {
            if (!organizationTagRepository.existsByTagId(tagId)) {
                throw new CustomException("Organization tag " + tagId + " not found", HttpStatus.NOT_FOUND);
            }
        }
        
        // 获取用户的现有组织标签
        Set<String> existingTags = new HashSet<>();
        if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
            existingTags = Arrays.stream(user.getOrgTags().split(",")).collect(Collectors.toSet());
        }
        
        // 找出并保留用户的私人组织标签
        String privateTagId = PRIVATE_TAG_PREFIX + user.getUsername();
        boolean hasPrivateTag = existingTags.contains(privateTagId);
        
        // 确保用户的私人组织标签不会被删除
        Set<String> finalTags = new HashSet<>(orgTags);
        if (hasPrivateTag && !finalTags.contains(privateTagId)) {
            finalTags.add(privateTagId);
        }
        
        // 将标签列表转换为逗号分隔的字符串
        String orgTagsStr = String.join(",", finalTags);
        user.setOrgTags(orgTagsStr);
        
        // 如果用户没有主组织标签且有组织标签，则优先使用私人标签作为主组织
        if ((user.getPrimaryOrg() == null || user.getPrimaryOrg().isEmpty()) && !finalTags.isEmpty()) {
            if (hasPrivateTag) {
                user.setPrimaryOrg(privateTagId);
            } else {
                user.setPrimaryOrg(new ArrayList<>(finalTags).get(0));
            }
        }
        
        userRepository.save(user);
        
        // 更新缓存
        orgTagCacheService.deleteUserOrgTagsCache(user.getUsername());
        orgTagCacheService.cacheUserOrgTags(user.getUsername(), new ArrayList<>(finalTags));
        // 同时清除有效标签缓存
        orgTagCacheService.deleteUserEffectiveTagsCache(user.getUsername());
        
        if (user.getPrimaryOrg() != null && !user.getPrimaryOrg().isEmpty()) {
            orgTagCacheService.cacheUserPrimaryOrg(user.getUsername(), user.getPrimaryOrg());
        }
    }
    
    /**
     * 获取用户的组织标签信息
     * 
     * @param username 用户名
     * @return 包含用户组织标签信息的Map
     */
    public Map<String, Object> getUserOrgTags(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        
        // 尝试从缓存获取
        List<String> orgTags = orgTagCacheService.getUserOrgTags(username);
        String primaryOrg = orgTagCacheService.getUserPrimaryOrg(username);
        
        // 如果缓存中没有，则从数据库获取
        if (orgTags == null || orgTags.isEmpty()) {
            orgTags = Arrays.asList(user.getOrgTags().split(","));
            // 更新缓存
            orgTagCacheService.cacheUserOrgTags(username, orgTags);
        }
        
        if (primaryOrg == null || primaryOrg.isEmpty()) {
            primaryOrg = user.getPrimaryOrg();
            // 更新缓存
            orgTagCacheService.cacheUserPrimaryOrg(username, primaryOrg);
        }
        
        // 获取组织标签的详细信息
        List<Map<String, Object>> orgTagDetails = new ArrayList<>();
        for (String tagId : orgTags) {
            OrganizationTag tag = organizationTagRepository.findByTagId(tagId)
                    .orElse(null);
            if (tag != null) {
                Map<String, Object> tagInfo = new HashMap<>();
                tagInfo.put("tagId", tag.getTagId());
                tagInfo.put("name", tag.getName());
                tagInfo.put("description", tag.getDescription());
                tagInfo.put("uploadMaxSizeBytes", tag.getUploadMaxSizeBytes());
                tagInfo.put("uploadMaxSizeMb", toUploadMaxSizeMb(tag.getUploadMaxSizeBytes()));
                orgTagDetails.add(tagInfo);
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("orgTags", orgTags);
        result.put("primaryOrg", primaryOrg);
        result.put("orgTagDetails", orgTagDetails);
        
        return result;
    }
    
    /**
     * 设置用户的主组织标签
     * 
     * @param username 用户名
     * @param primaryOrg 主组织标签
     */
    public void setUserPrimaryOrg(String username, String primaryOrg) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        
        // 检查该组织标签是否已分配给用户
        Set<String> userTags = Arrays.stream(user.getOrgTags().split(",")).collect(Collectors.toSet());
        if (!userTags.contains(primaryOrg)) {
            throw new CustomException("Organization tag not assigned to user", HttpStatus.BAD_REQUEST);
        }
        
        user.setPrimaryOrg(primaryOrg);
        userRepository.save(user);
        
        // 更新缓存
        orgTagCacheService.cacheUserPrimaryOrg(username, primaryOrg);
    }
    
    /**
     * 获取用户的主组织标签
     * 
     * @param userId 用户ID
     * @return 用户的主组织标签
     */
    public String getUserPrimaryOrg(String userId) {
        // 先通过userId查找用户，然后获取username
        User user = resolveUser(userId);
        
        String username = user.getUsername();
        
        // 尝试从缓存获取
        String primaryOrg = orgTagCacheService.getUserPrimaryOrg(username);
        
        // 如果缓存中没有，则从数据库获取
        if (primaryOrg == null || primaryOrg.isEmpty()) {
            primaryOrg = user.getPrimaryOrg();
            
            // 如果用户没有设置主组织标签，则尝试使用第一个分配的组织标签
            if (primaryOrg == null || primaryOrg.isEmpty()) {
                String[] tags = user.getOrgTags().split(",");
                if (tags.length > 0) {
                    primaryOrg = tags[0];
                    // 更新用户的主组织标签
                    user.setPrimaryOrg(primaryOrg);
                    userRepository.save(user);
                } else {
                    // 如果用户没有任何组织标签，则使用默认标签
                    primaryOrg = DEFAULT_ORG_TAG;
                }
            }
            
            // 更新缓存
            orgTagCacheService.cacheUserPrimaryOrg(username, primaryOrg);
        }
        
        return primaryOrg;
    }

    /**
     * 获取组织标签树结构
     * 
     * @return 组织标签树结构
     */
    public List<Map<String, Object>> getOrganizationTagTree() {
        // 获取所有根节点（parentTag为null的标签）
        List<OrganizationTag> rootTags = organizationTagRepository.findByParentTag(null);
        
        // 递归构建标签树
        return buildTagTreeRecursive(rootTags);
    }
    
    /**
     * 递归构建标签树
     * 
     * @param tags 当前级别的标签列表
     * @return 树形结构
     */
    private List<Map<String, Object>> buildTagTreeRecursive(List<OrganizationTag> tags) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (OrganizationTag tag : tags) {
            Map<String, Object> node = new HashMap<>();
            node.put("tagId", tag.getTagId());
            node.put("name", tag.getName());
            node.put("description", tag.getDescription());
            node.put("parentTag", tag.getParentTag()); // 添加父标签字段
            node.put("uploadMaxSizeBytes", tag.getUploadMaxSizeBytes());
            node.put("uploadMaxSizeMb", toUploadMaxSizeMb(tag.getUploadMaxSizeBytes()));
            
            // 获取子标签
            List<OrganizationTag> children = organizationTagRepository.findByParentTag(tag.getTagId());
            if (!children.isEmpty()) {
                node.put("children", buildTagTreeRecursive(children));
            }
            // 如果没有子节点，不添加children字段，而不是添加空数组
            
            result.add(node);
        }
        
        return result;
    }
    
    /**
     * 更新组织标签
     * 
     * @param tagId 标签ID
     * @param name 新名称
     * @param description 新描述
     * @param parentTag 新父标签ID
     * @param adminUsername 管理员用户名
     * @return 更新后的组织标签
     */
    @Transactional
    public OrganizationTag updateOrganizationTag(String tagId, String name, String description, 
                                                String parentTag, Long uploadMaxSizeMb, String adminUsername) {
        // 验证操作者是否为管理员
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.NOT_FOUND));
        
        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can update organization tags", HttpStatus.FORBIDDEN);
        }
        
        // 获取要更新的标签
        OrganizationTag tag = organizationTagRepository.findByTagId(tagId)
                .orElseThrow(() -> new CustomException("Organization tag not found", HttpStatus.NOT_FOUND));
        
        // 如果指定了父标签，检查父标签是否存在
        if (parentTag != null && !parentTag.isEmpty()) {
            // 检查是否为自身
            if (tagId.equals(parentTag)) {
                throw new CustomException("A tag cannot be its own parent", HttpStatus.BAD_REQUEST);
            }
            
            // 检查是否存在
            organizationTagRepository.findByTagId(parentTag)
                    .orElseThrow(() -> new CustomException("Parent tag not found", HttpStatus.NOT_FOUND));
            
            // 检查是否会形成循环
            if (wouldFormCycle(tagId, parentTag)) {
                throw new CustomException("Setting this parent would create a cycle in the tag hierarchy", HttpStatus.BAD_REQUEST);
            }
        }
        
        // 更新标签
        if (name != null && !name.isEmpty()) {
            tag.setName(name);
        }
        
        if (description != null) {
            tag.setDescription(description);
        }
        
        tag.setParentTag(parentTag);
        tag.setUploadMaxSizeBytes(normalizeUploadMaxSizeBytes(uploadMaxSizeMb));
        
        OrganizationTag updatedTag = organizationTagRepository.save(tag);
        
        // 清除所有标签缓存，因为层级关系可能变化
        orgTagCacheService.invalidateAllEffectiveTagsCache();
        
        return updatedTag;
    }

    public boolean isAdminUser(String userId) {
        return resolveUser(userId).getRole() == User.Role.ADMIN;
    }

    public OrganizationTag getOrganizationTag(String tagId) {
        return organizationTagRepository.findByTagId(tagId)
                .orElseThrow(() -> new CustomException("Organization tag not found", HttpStatus.NOT_FOUND));
    }
    
    /**
     * 检查是否会形成标签层级循环
     * 
     * @param tagId 要设置父标签的标签ID
     * @param newParentId 新的父标签ID
     * @return 是否会形成循环
     */
    private boolean wouldFormCycle(String tagId, String newParentId) {
        String currentParentId = newParentId;
        
        // 检查是否形成循环
        while (currentParentId != null && !currentParentId.isEmpty()) {
            if (tagId.equals(currentParentId)) {
                return true; // 形成循环
            }
            
            // 获取父标签的父标签
            Optional<OrganizationTag> parentTag = organizationTagRepository.findByTagId(currentParentId);
            if (parentTag.isEmpty()) {
                break;
            }
            
            currentParentId = parentTag.get().getParentTag();
        }
        
        return false;
    }
    
    /**
     * 删除组织标签
     * 
     * @param tagId 标签ID
     * @param adminUsername 管理员用户名
     */
    @Transactional
    public void deleteOrganizationTag(String tagId, String adminUsername) {
        // 验证操作者是否为管理员
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.NOT_FOUND));
        
        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can delete organization tags", HttpStatus.FORBIDDEN);
        }
        
        // 获取要删除的标签
        OrganizationTag tag = organizationTagRepository.findByTagId(tagId)
                .orElseThrow(() -> new CustomException("Organization tag not found", HttpStatus.NOT_FOUND));
        
        // 检查是否是特殊标签（如默认标签）
        if (DEFAULT_ORG_TAG.equals(tagId)) {
            throw new CustomException("Cannot delete the default organization tag", HttpStatus.BAD_REQUEST);
        }
        
        // 检查是否有子标签
        List<OrganizationTag> children = organizationTagRepository.findByParentTag(tagId);
        if (!children.isEmpty()) {
            throw new CustomException("Cannot delete a tag with child tags", HttpStatus.BAD_REQUEST);
        }
        
        // 检查是否有用户使用此标签
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
                Set<String> userTags = new HashSet<>(Arrays.asList(user.getOrgTags().split(",")));
                if (userTags.contains(tagId)) {
                    throw new CustomException("Cannot delete a tag that is assigned to users", HttpStatus.CONFLICT);
                }
                
                // 检查是否被用作主组织标签
                if (tagId.equals(user.getPrimaryOrg())) {
                    throw new CustomException("Cannot delete a tag that is used as primary organization", HttpStatus.CONFLICT);
                }
            }
        }
        
        // 检查是否有文档使用此标签（此处应检查file_upload表中的org_tag字段）
        // 由于我们没有直接访问FileUploadRepository，这里采用简化的方式检查
        // 实际实现中，应该注入FileUploadRepository并使用正确的查询方法
        try {
            long fileCount = 0; // 应该是 fileUploadRepository.countByOrgTag(tagId);
            if (fileCount > 0) {
                throw new CustomException("Cannot delete a tag that is associated with documents", HttpStatus.CONFLICT);
            }
        } catch (Exception e) {
            logger.error("Error checking file usage of tag: {}", tagId, e);
            throw new CustomException("Failed to check if tag is used by documents", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        
        // 删除标签
        organizationTagRepository.delete(tag);
        
        // 清除所有标签缓存，因为层级关系可能变化
        orgTagCacheService.invalidateAllEffectiveTagsCache();
        
        logger.info("Organization tag deleted successfully: {}", tagId);
    }
    
    /**
     * 获取用户列表，支持分页和过滤
     * 
     * @param keyword 搜索关键词
     * @param orgTag 组织标签过滤
     * @param status 用户状态过滤
     * @param page 页码
     * @param size 每页大小
     * @return 用户列表数据
     */
    public Map<String, Object> getUserList(String keyword, String orgTag, Integer status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size > 0 ? size : 10;
        int pageIndex = safePage - 1;
        Pageable pageable = PageRequest.of(pageIndex, safeSize, Sort.by("createdAt").descending());

        List<User> filteredUsers = userRepository.findAll(Sort.by("createdAt").descending()).stream()
                .filter(user -> matchesUserListFilters(user, keyword, orgTag, status))
                .toList();

        int start = Math.min((int) pageable.getOffset(), filteredUsers.size());
        int end = Math.min(start + pageable.getPageSize(), filteredUsers.size());
        List<User> pageContent = start < end ? filteredUsers.subList(start, end) : Collections.emptyList();
        Page<User> userPage = new PageImpl<>(pageContent, pageable, filteredUsers.size());
        
        // 转换为前端需要的格式
        Map<String, UsageQuotaService.UserUsageSnapshot> usageSnapshots = usageQuotaService.getSnapshots(
                userPage.getContent().stream()
                        .map(user -> String.valueOf(user.getId()))
                        .toList()
        );

        List<Map<String, Object>> userList = userPage.getContent().stream()
                .map(user -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("userId", user.getId());
                    userMap.put("username", user.getUsername());
                    
                    // 获取用户组织标签的详细信息
                    List<Map<String, String>> orgTagDetails = new ArrayList<>();
                    if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
                        Arrays.stream(user.getOrgTags().split(","))
                                .forEach(tagId -> {
                                    OrganizationTag tag = organizationTagRepository.findByTagId(tagId)
                                            .orElse(null);
                                    if (tag != null) {
                                        Map<String, String> tagInfo = new HashMap<>();
                                        tagInfo.put("tagId", tag.getTagId());
                                        tagInfo.put("name", tag.getName());
                                        orgTagDetails.add(tagInfo);
                                    }
                                });
                    }
                    
                    userMap.put("orgTags", orgTagDetails);
                    userMap.put("primaryOrg", user.getPrimaryOrg());
                    userMap.put("status", user.getRole() == User.Role.USER ? 1 : 0);
                    userMap.put("createdAt", user.getCreatedAt());
                    userMap.put("usage", usageSnapshots.getOrDefault(
                            String.valueOf(user.getId()),
                            usageQuotaService.getSnapshot(String.valueOf(user.getId()))
                    ));
                    
                    return userMap;
                })
                .collect(Collectors.toList());
        
        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("content", userList);
        result.put("totalElements", userPage.getTotalElements());
        result.put("totalPages", userPage.getTotalPages());
        result.put("size", userPage.getSize());
        result.put("number", userPage.getNumber() + 1); // 转换为从1开始的页码
        
        return result;
    }

    private boolean matchesUserListFilters(User user, String keyword, String orgTag, Integer status) {
        if (orgTag != null && !orgTag.isEmpty()) {
            if (user.getOrgTags() == null || user.getOrgTags().isEmpty()) {
                return false;
            }

            Set<String> userTags = new HashSet<>(Arrays.asList(user.getOrgTags().split(",")));
            if (!userTags.contains(orgTag)) {
                return false;
            }
        }

        if (keyword != null && !keyword.isEmpty() && !user.getUsername().contains(keyword)) {
            return false;
        }

        if (status != null) {
            return user.getRole() == (status == 1 ? User.Role.USER : User.Role.ADMIN);
        }

        return true;
    }

    private String resolveOrGenerateTagId(String tagId, String name) {
        String normalizedTagId = tagId == null ? "" : tagId.trim();
        if (!normalizedTagId.isEmpty()) {
            if (normalizedTagId.startsWith(PRIVATE_TAG_PREFIX)) {
                throw new CustomException("Tag ID cannot start with PRIVATE_", HttpStatus.BAD_REQUEST);
            }
            if (organizationTagRepository.existsByTagId(normalizedTagId)) {
                throw new CustomException("Tag ID already exists", HttpStatus.BAD_REQUEST);
            }
            return normalizedTagId;
        }
        return generateUniqueTagId(name);
    }

    private String generateUniqueTagId(String name) {
        String slug = buildTagSlug(name);
        String baseId = truncateTagId("ORG_" + slug);

        if (!organizationTagRepository.existsByTagId(baseId)) {
            return baseId;
        }

        for (int i = 2; i <= 9999; i++) {
            String candidate = appendSuffix(baseId, "_" + i);
            if (!organizationTagRepository.existsByTagId(candidate)) {
                return candidate;
            }
        }

        while (true) {
            String uuidSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String candidate = appendSuffix(baseId, "_" + uuidSuffix);
            if (!organizationTagRepository.existsByTagId(candidate)) {
                return candidate;
            }
        }
    }

    private String buildTagSlug(String name) {
        String raw = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        String slug = NON_ALNUM_PATTERN.matcher(raw).replaceAll("-");
        slug = TRIM_DASH_PATTERN.matcher(slug).replaceAll("");
        return slug.isEmpty() ? "tag" : slug;
    }

    private String appendSuffix(String base, String suffix) {
        if (base.length() + suffix.length() <= MAX_TAG_ID_LENGTH) {
            return base + suffix;
        }
        return base.substring(0, MAX_TAG_ID_LENGTH - suffix.length()) + suffix;
    }

    private String truncateTagId(String tagId) {
        if (tagId.length() <= MAX_TAG_ID_LENGTH) {
            return tagId;
        }
        return tagId.substring(0, MAX_TAG_ID_LENGTH);
    }

    private User resolveUser(String userId) {
        try {
            Long userIdLong = Long.parseLong(userId);
            return userRepository.findById(userIdLong)
                    .orElseThrow(() -> new CustomException("User not found with ID: " + userId, HttpStatus.NOT_FOUND));
        } catch (NumberFormatException e) {
            return userRepository.findByUsername(userId)
                    .orElseThrow(() -> new CustomException("User not found: " + userId, HttpStatus.NOT_FOUND));
        }
    }

    private Long normalizeUploadMaxSizeBytes(Long uploadMaxSizeMb) {
        if (uploadMaxSizeMb == null) {
            return null;
        }
        if (uploadMaxSizeMb <= 0) {
            throw new CustomException("上传大小上限必须大于 0 MB", HttpStatus.BAD_REQUEST);
        }
        long uploadMaxSizeBytes = uploadMaxSizeMb * BYTES_PER_MB;
        long globalUploadMaxBytes = DataSize.parse(globalUploadMaxFileSize).toBytes();
        if (uploadMaxSizeBytes > globalUploadMaxBytes) {
            throw new CustomException(
                    "上传大小上限不能超过系统全局限制 " + (globalUploadMaxBytes / BYTES_PER_MB) + " MB",
                    HttpStatus.BAD_REQUEST
            );
        }
        return uploadMaxSizeBytes;
    }

    private Long toUploadMaxSizeMb(Long uploadMaxSizeBytes) {
        if (uploadMaxSizeBytes == null || uploadMaxSizeBytes <= 0) {
            return null;
        }
        return uploadMaxSizeBytes / BYTES_PER_MB;
    }
}
