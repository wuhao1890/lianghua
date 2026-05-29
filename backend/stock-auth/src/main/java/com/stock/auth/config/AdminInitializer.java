package com.stock.auth.config;

import com.stock.auth.entity.User;
import com.stock.auth.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminInitializer implements CommandLineRunner {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        try {
            User admin = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getUsername, "admin"));
            if (admin == null) {
                User newAdmin = new User();
                newAdmin.setUsername("admin");
                newAdmin.setPassword(passwordEncoder.encode("admin"));
                newAdmin.setNickname("系统管理员");
                newAdmin.setRole("ADMIN");
                newAdmin.setInitialCapital(java.math.BigDecimal.ZERO);
                newAdmin.setAvailableCash(java.math.BigDecimal.ZERO);
                userMapper.insert(newAdmin);
                log.info("管理员账号初始化完成 - 用户名: admin, 密码: admin");
            } else {
                // 确保已有admin用户的role字段正确
                if (!"ADMIN".equals(admin.getRole())) {
                    admin.setRole("ADMIN");
                    userMapper.updateById(admin);
                    log.info("已更新管理员角色");
                }
            }
        } catch (Exception e) {
            log.warn("管理员账号初始化失败(可能role字段尚未添加): {}", e.getMessage());
        }
    }
}
