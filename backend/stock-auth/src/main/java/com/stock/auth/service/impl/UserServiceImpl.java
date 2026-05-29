package com.stock.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stock.auth.dto.LoginRequest;
import com.stock.auth.dto.LoginResponse;
import com.stock.auth.dto.RegisterRequest;
import com.stock.auth.entity.User;
import com.stock.auth.mapper.UserMapper;
import com.stock.auth.service.UserService;
import com.stock.auth.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User register(RegisterRequest request) {
        // 检查用户名是否已存在
        User existUser = getByUsername(request.getUsername());
        if (existUser != null) {
            throw new RuntimeException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole("USER");

        // 设置初始资金，默认100万
        BigDecimal initialCapital = request.getInitialCapital();
        if (initialCapital == null || initialCapital.compareTo(BigDecimal.ZERO) <= 0) {
            initialCapital = new BigDecimal("1000000.00");
        }
        user.setInitialCapital(initialCapital);
        user.setAvailableCash(initialCapital);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        save(user);
        log.info("用户注册成功: {}", user.getUsername());
        return user;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = getByUsername(request.getUsername());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setAvailableCash(user.getAvailableCash());
        response.setInitialCapital(user.getInitialCapital());
        response.setRole(user.getRole());

        log.info("用户登录成功: {}", user.getUsername());
        return response;
    }

    @Override
    public User getByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCash(Long userId, BigDecimal amount) {
        User user = getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setAvailableCash(amount);
        user.setUpdateTime(LocalDateTime.now());
        return updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addCash(Long userId, BigDecimal amount) {
        User user = getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setAvailableCash(user.getAvailableCash().add(amount));
        user.setUpdateTime(LocalDateTime.now());
        return updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductCash(Long userId, BigDecimal amount) {
        User user = getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getAvailableCash().compareTo(amount) < 0) {
            throw new RuntimeException("可用资金不足");
        }
        user.setAvailableCash(user.getAvailableCash().subtract(amount));
        user.setUpdateTime(LocalDateTime.now());
        return updateById(user);
    }
}
