package com.stock.stock.controller;

import com.stock.stock.dto.GoldPriceDTO;
import com.stock.stock.service.GoldService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock/gold")
public class GoldController {

    @Autowired
    private GoldService goldService;

    /**
     * 获取所有黄金产品列表
     * GET /api/stock/gold/products
     */
    @GetMapping("/products")
    public ResponseEntity<Map<String, Object>> getProducts() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, String> products = goldService.getProducts();
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", products);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取指定黄金产品的最新价格
     * GET /api/stock/gold/latest?code=hf_GC
     */
    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> getLatestPrice(
            @RequestParam(required = false, defaultValue = "hf_GC") String code) {
        Map<String, Object> result = new HashMap<>();
        try {
            GoldPriceDTO dto = goldService.getLatestPrice(code);
            if (dto == null) {
                result.put("code", 404);
                result.put("message", "未找到黄金价格数据");
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

    /**
     * 获取黄金价格历史
     * GET /api/stock/gold/history?code=hf_GC&days=30
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getPriceHistory(
            @RequestParam(required = false, defaultValue = "hf_GC") String code,
            @RequestParam(defaultValue = "30") int days) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<GoldPriceDTO> list = goldService.getPriceHistory(code, days);
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
