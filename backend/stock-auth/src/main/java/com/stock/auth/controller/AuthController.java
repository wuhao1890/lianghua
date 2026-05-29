package com.stock.auth.controller;

import com.stock.auth.dto.LoginRequest;
import com.stock.auth.dto.LoginResponse;
import com.stock.auth.dto.RegisterRequest;
import com.stock.auth.entity.User;
import com.stock.auth.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            User user = userService.register(request);
            result.put("code", 200);
            result.put("message", "注册成功");
            result.put("data", user);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            LoginResponse response = userService.login(request);
            result.put("code", 200);
            result.put("message", "登录成功");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 401);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取用户信息
     * GET /api/auth/info
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getUserInfo(
            @RequestHeader("X-User-Id") Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            User user = userService.getById(userId);
            if (user == null) {
                result.put("code", 404);
                result.put("message", "用户不存在");
                return ResponseEntity.ok(result);
            }
            // 不返回密码
            user.setPassword(null);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", user);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
