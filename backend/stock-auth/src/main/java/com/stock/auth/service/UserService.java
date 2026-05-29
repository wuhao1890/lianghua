package com.stock.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stock.auth.dto.LoginRequest;
import com.stock.auth.dto.LoginResponse;
import com.stock.auth.dto.RegisterRequest;
import com.stock.auth.entity.User;

import java.math.BigDecimal;

public interface UserService extends IService<User> {

    /**
     * 用户注册
     */
    User register(RegisterRequest request);

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);

    /**
     * 根据用户名查询用户
     */
    User getByUsername(String username);

    /**
     * 更新用户可用资金
     */
    boolean updateCash(Long userId, BigDecimal amount);

    /**
     * 增加用户可用资金
     */
    boolean addCash(Long userId, BigDecimal amount);

    /**
     * 扣减用户可用资金
     */
    boolean deductCash(Long userId, BigDecimal amount);
}
