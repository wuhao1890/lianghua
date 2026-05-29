package com.stock.stock.controller;

import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/risk")
public class RiskController {

    /** 内存存储风险设置：userId -> RiskSettings */
    private static final ConcurrentHashMap<Long, RiskSettings> riskSettingsMap = new ConcurrentHashMap<>();

    /** 默认风险设置 */
    private static RiskSettings getDefaultSettings() {
        RiskSettings settings = new RiskSettings();
        settings.setMaxSingleAmount(new BigDecimal("1000000"));
        settings.setMaxPositionRatio(new BigDecimal("50"));
        settings.setDailyLossLimit(new BigDecimal("50000"));
        settings.setDrawdownLimit(new BigDecimal("20"));
        settings.setCircuitBreakerEnabled(true);
        settings.setCircuitBreakerLoss(new BigDecimal("100000"));
        return settings;
    }

    /**
     * 获取用户风险设置
     * GET /api/risk/settings?userId=1
     */
    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings(
            @RequestParam(defaultValue = "1") Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            RiskSettings settings = riskSettingsMap.get(userId);
            if (settings == null) {
                settings = getDefaultSettings();
            }
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", settings);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 保存用户风险设置
     * POST /api/risk/settings?userId=1
     */
    @PostMapping("/settings")
    public ResponseEntity<Map<String, Object>> saveSettings(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestBody RiskSettings settings) {
        Map<String, Object> result = new HashMap<>();
        try {
            riskSettingsMap.put(userId, settings);
            result.put("code", 200);
            result.put("message", "保存成功");
            result.put("data", settings);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 检查交易是否符合风控规则
     * POST /api/risk/check
     * Body: { userId, stockCode, price, quantity, amount, direction }
     */
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> checkRisk(
            @RequestBody RiskCheckRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = request.getUserId() != null ? request.getUserId() : 1L;
            RiskSettings settings = riskSettingsMap.get(userId);
            if (settings == null) {
                settings = getDefaultSettings();
            }

            Map<String, Object> checkResult = new HashMap<>();
            boolean passed = true;
            StringBuilder message = new StringBuilder();

            // 检查单笔最大金额
            if (request.getAmount() != null && settings.getMaxSingleAmount() != null) {
                if (request.getAmount().compareTo(settings.getMaxSingleAmount()) > 0) {
                    passed = false;
                    message.append("单笔金额超过限额(")
                            .append(settings.getMaxSingleAmount()).append("); ");
                }
            }

            // 检查熔断
            if (settings.getCircuitBreakerEnabled() != null && settings.getCircuitBreakerEnabled()) {
                // 如果启用熔断，检查方向为BUY时是否触发
                if ("BUY".equals(request.getDirection())) {
                    // 简单逻辑：单笔超过熔断阈值
                    if (request.getAmount() != null && settings.getCircuitBreakerLoss() != null
                            && request.getAmount().compareTo(settings.getCircuitBreakerLoss()) > 0) {
                        passed = false;
                        message.append("触发熔断(金额超").append(settings.getCircuitBreakerLoss()).append("); ");
                    }
                }
            }

            checkResult.put("passed", passed);
            checkResult.put("message", passed ? "风控检查通过" : message.toString().replaceAll("; $", ""));
            checkResult.put("settings", settings);

            result.put("code", 200);
            result.put("message", "检查完成");
            result.put("data", checkResult);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    // ===== DTO Classes =====

    @Data
    public static class RiskSettings {
        /** 最大单笔金额 */
        private BigDecimal maxSingleAmount;
        /** 最大仓位百分比 0-100 */
        private BigDecimal maxPositionRatio;
        /** 日亏损限额 */
        private BigDecimal dailyLossLimit;
        /** 最大回撤阈值 0-100 */
        private BigDecimal drawdownLimit;
        /** 是否启用熔断 */
        private Boolean circuitBreakerEnabled;
        /** 熔断触发亏损额 */
        private BigDecimal circuitBreakerLoss;
    }

    @Data
    public static class RiskCheckRequest {
        private Long userId;
        private String stockCode;
        private BigDecimal price;
        private Integer quantity;
        private BigDecimal amount;
        private String direction;
    }
}
