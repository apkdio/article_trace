package com.articleTraceBack.runner;

import com.articleTraceBack.Utils.BcryptUtils;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 在启动时执行，自动检测数据库负责人账户存在情况
@Component
@Order(1)
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserMapper userMapper;
    @Value("${spring.application.admin.enableAutoConfig}")
    private boolean autoConfig;
    @Value("${spring.application.admin.defaultUser}")
    private String adminUsername;
    @Value("${spring.application.admin.defaultPass}")
    private String adminPassword;
    @Value("${spring.application.admin.defaultResetPass}")
    private String adminResetPass;
    @Value("${spring.application.admin.defaultEmail}")
    private String adminEmail;
    @Value("${spring.application.admin.defaultNickName}")
    private String adminNickname;

    public AdminInitializer(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public void run(String... args) throws IllegalStateException {
        // 1. 检查配置功能是否启用
        if (!autoConfig) {
            log.info("auto create administrator is not enabled. skip it.");
            return;
        }

        // 2. 查询用户是否存在
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", adminUsername);
        Long count = userMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            log.info("exist [{}] . skip.", adminUsername);
            return;
        }

        // 3. 参数校验
        if (adminUsername == null || adminUsername.trim().isEmpty() ||
                adminPassword == null || adminPassword.trim().isEmpty() ||
                adminResetPass == null || adminResetPass.trim().isEmpty()) {
            log.warn("default username or password or reset_password are not configured ! " +
                    "skip auto create.");
            throw new IllegalStateException("unable to create default administrator! check your configuration in (application.yaml) !");
        }

        // 4. 创建新用户
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPassword(BcryptUtils.encodePass(adminPassword)); // BCrypt 加密
        admin.setResetPass(BcryptUtils.encodePass(adminResetPass)); // BCrypt 加密
        admin.setCreateTime(LocalDateTime.now());
        admin.setEmail(adminEmail);
        admin.setNickname(adminNickname);
        admin.setType(0);
        userMapper.insert(admin);
        log.info("create administrator success ! username:[{}] ", adminUsername);
    }
}