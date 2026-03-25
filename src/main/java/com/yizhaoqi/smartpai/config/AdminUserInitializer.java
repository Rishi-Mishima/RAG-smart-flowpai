package com.yizhaoqi.smartpai.config;

import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.utils.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 管理员账号初始化器
 * 在应用启动时自动创建管理员账号（如果不存在）
 */
@Component
@Order(1) // 设置优先级，确保在其他初始化器之前运行
public class AdminUserInitializer implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(AdminUserInitializer.class);
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "admin123", "admin", "password", "123456", "12345678", "qwerty"
    );

    @Autowired
    private UserRepository userRepository;

    @Value("${admin.bootstrap.enabled:false}")
    private boolean bootstrapEnabled;

    @Value("${admin.bootstrap.username:}")
    private String adminUsername;

    @Value("${admin.bootstrap.password:}")
    private String adminPassword;

    @Value("${admin.bootstrap.primary-org:default}")
    private String adminPrimaryOrg;

    @Value("${admin.bootstrap.org-tags:default,admin}")
    private String adminOrgTags;

    @Override
    public void run(String... args) throws Exception {
        if (!bootstrapEnabled) {
            logger.info("管理员引导创建已禁用（admin.bootstrap.enabled=false）");
            return;
        }

        validateBootstrapConfig();

        logger.info("检查管理员账号是否存在: {}", adminUsername);
        Optional<User> existingAdmin = userRepository.findByUsername(adminUsername);

        if (existingAdmin.isPresent()) {
            logger.info("管理员账号 '{}' 已存在，跳过创建步骤", adminUsername);
            return;
        }

        try {
            logger.info("开始创建管理员账号: {}", adminUsername);
            User adminUser = new User();
            adminUser.setUsername(adminUsername);
            adminUser.setPassword(PasswordUtil.encode(adminPassword));
            adminUser.setRole(User.Role.ADMIN);
            adminUser.setPrimaryOrg(adminPrimaryOrg);
            adminUser.setOrgTags(adminOrgTags);

            userRepository.save(adminUser);
            logger.info("管理员账号 '{}' 创建成功", adminUsername);
        } catch (Exception e) {
            logger.error("创建管理员账号失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法创建管理员账号", e);
        }
    }

    private void validateBootstrapConfig() {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalStateException("admin.bootstrap.username 不能为空");
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("admin.bootstrap.password 不能为空");
        }
        if (adminPassword.length() < 12) {
            throw new IllegalStateException("admin.bootstrap.password 长度必须 >= 12");
        }
        if (WEAK_PASSWORDS.contains(adminPassword.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("admin.bootstrap.password 不能使用弱口令");
        }
    }
}
