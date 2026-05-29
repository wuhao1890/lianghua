package com.stock.stock.controller;

import com.stock.stock.service.SpreadAlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨市场价差监控接口
 */
@RestController
@RequestMapping("/api/spread")
public class SpreadController {

    private final SpreadAlertService spreadAlertService;

    public SpreadController(SpreadAlertService spreadAlertService) {
        this.spreadAlertService = spreadAlertService;
    }

    /**
     * 获取最近价差检查结果
     * GET /api/spread/ah
     */
    @GetMapping("/ah")
    public ResponseEntity<Map<String, Object>> getAhSpreads() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<SpreadAlertService.SpreadResult> list = spreadAlertService.getRecentResults();
            result.put("code", 200);
            result.put("data", list);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
