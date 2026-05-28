package com.stock.auth.controller;

import com.stock.auth.dto.RechargeRequest;
import com.stock.auth.entity.User;
import com.stock.auth.service.RechargeService;
import com.stock.auth.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/recharge")
public class RechargeController {

    @Autowired
    private RechargeService rechargeService;

    @Autowired
    private UserService userService;

    /**
     * 用户提交充值申请（普通用户）
     */
    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> applyRecharge(
            @Valid @RequestBody RechargeRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            request.setUserId(userId);
            rechargeService.applyRecharge(request);
            result.put("code", 200);
            result.put("message", "充值申请已提交");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 管理员给用户充值
     */
    @PostMapping("/admin/recharge")
    public ResponseEntity<Map<String, Object>> adminRecharge(
            @Valid @RequestBody RechargeRequest request,
            @RequestHeader("X-User-Id") Long operatorId,
            @RequestHeader("X-Username") String username) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 验证操作者是否为管理员
            User operator = userService.getById(operatorId);
            if (operator == null || !"ADMIN".equals(operator.getRole())) {
                result.put("code", 403);
                result.put("message", "无权限操作");
                return ResponseEntity.ok(result);
            }
            rechargeService.adminRecharge(request, operatorId);
            result.put("code", 200);
            result.put("message", "充值成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 查询充值记录
     */
    @GetMapping("/records")
    public ResponseEntity<Map<String, Object>> getRecords(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", rechargeService.getRecords(userId, page, pageSize));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 管理员查询所有充值记录
     */
    @GetMapping("/admin/records")
    public ResponseEntity<Map<String, Object>> getAllRecords(
            @RequestHeader("X-User-Id") Long operatorId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status) {
        Map<String, Object> result = new HashMap<>();
        try {
            User operator = userService.getById(operatorId);
            if (operator == null || !"ADMIN".equals(operator.getRole())) {
                result.put("code", 403);
                result.put("message", "无权限操作");
                return ResponseEntity.ok(result);
            }
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", rechargeService.getAllRecords(page, pageSize, status));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 管理员确认充值（用户微信转账后，管理员确认）
     */
    @PostMapping("/admin/confirm/{orderId}")
    public ResponseEntity<Map<String, Object>> confirmRecharge(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long operatorId) {
        Map<String, Object> result = new HashMap<>();
        try {
            User operator = userService.getById(operatorId);
            if (operator == null || !"ADMIN".equals(operator.getRole())) {
                result.put("code", 403);
                result.put("message", "无权限操作");
                return ResponseEntity.ok(result);
            }
            rechargeService.confirmRecharge(orderId, operatorId);
            result.put("code", 200);
            result.put("message", "充值确认成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
