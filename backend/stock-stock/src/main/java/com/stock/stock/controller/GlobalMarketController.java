package com.stock.stock.controller;

import com.stock.stock.service.GlobalMarketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/stock/global")
public class GlobalMarketController {

    @Autowired
    private GlobalMarketService globalMarketService;

    // GET /api/stock/global/list?market=US&page=1&size=20
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam String market,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = globalMarketService.getStockList(market, page, size);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", data);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    // GET /api/stock/global/realtime/{code}?market=HK
    @GetMapping("/realtime/{code}")
    public ResponseEntity<Map<String, Object>> realtime(
            @PathVariable String code,
            @RequestParam(defaultValue = "US") String market) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> dto = globalMarketService.getRealtimeQuote(code, market);
            if (dto == null) {
                result.put("code", 404);
                result.put("message", "未找到");
                return ResponseEntity.ok(result);
            }
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", dto);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    // GET /api/stock/global/indices?market=HK
    @GetMapping("/indices")
    public ResponseEntity<Map<String, Object>> indices(@RequestParam String market) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> list = globalMarketService.getMarketIndices(market);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", list);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    // GET /api/stock/global/search?market=HK&keyword=腾讯
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String market,
            @RequestParam String keyword) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> list = globalMarketService.search(market, keyword);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", list);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
