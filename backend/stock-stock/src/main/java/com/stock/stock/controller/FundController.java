package com.stock.stock.controller;

import com.stock.stock.dto.FundInfoDTO;
import com.stock.stock.service.FundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock/fund")
public class FundController {

    @Autowired
    private FundService fundService;

    /**
     * 获取基金列表（分页、搜索、类型筛选）
     * GET /api/stock/fund/list?keyword=&fundType=&page=1&pageSize=30
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getFundList(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "") String fundType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int pageSize) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = fundService.getFundList(keyword, fundType, page, pageSize);
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

    /**
     * 获取基金详情
     * GET /api/stock/fund/{code}
     */
    @GetMapping("/{code}")
    public ResponseEntity<Map<String, Object>> getFundDetail(@PathVariable String code) {
        Map<String, Object> result = new HashMap<>();
        try {
            FundInfoDTO dto = fundService.getFundDetail(code);
            if (dto == null) {
                result.put("code", 404);
                result.put("message", "基金不存在");
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
     * 获取基金净值历史
     * GET /api/stock/fund/{code}/nav?days=30
     */
    @GetMapping("/{code}/nav")
    public ResponseEntity<Map<String, Object>> getFundNavHistory(
            @PathVariable String code,
            @RequestParam(defaultValue = "30") int days) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<FundInfoDTO> list = fundService.getFundNavHistory(code, days);
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
