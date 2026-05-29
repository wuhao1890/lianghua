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
}
