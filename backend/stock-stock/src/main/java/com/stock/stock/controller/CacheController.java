package com.stock.stock.controller;

import com.stock.stock.service.DataCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据缓存管理接口
 */
@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final DataCacheService dataCacheService;

    public CacheController(DataCacheService dataCacheService) {
        this.dataCacheService = dataCacheService;
    }

    /**
     * 获取缓存状态
     * GET /api/cache/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", dataCacheService.getCacheStats());
        return ResponseEntity.ok(result);
    }

    /**
     * 手动触发K线缓存
     * POST /api/cache/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> stats = dataCacheService.triggerCache();
            result.put("code", 200);
            result.put("message", "缓存刷新完成");
            result.put("data", stats);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 清理错误的K线数据并重新同步
     * POST /api/cache/clean-kline
     */
    @PostMapping("/clean-kline")
    public ResponseEntity<Map<String, Object>> cleanKline() {
        Map<String, Object> result = new HashMap<>();
        try {
            int deleted = dataCacheService.cleanInvalidKlineData();
            result.put("code", 200);
            result.put("message", "清理完成，已删除" + deleted + "条错误数据");
            result.put("deleted", deleted);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 清空所有K线数据并重新从新浪获取真实数据
     * POST /api/cache/reset-kline
     */
    @PostMapping("/reset-kline")
    public ResponseEntity<Map<String, Object>> resetKline() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 清空所有数据
            int deleted = dataCacheService.clearAllKlineData();
            // 2. 立即从新浪重新获取
            Map<String, Object> syncResult = dataCacheService.triggerCache();
            result.put("code", 200);
            result.put("message", "重置完成");
            result.put("deleted", deleted);
            result.put("syncResult", syncResult);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}
