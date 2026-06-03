package com.stock.stock.controller;

import com.stock.stock.dto.KlineData;
import com.stock.stock.dto.StockDTO;
import com.stock.stock.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    @Autowired
    private StockService stockService;

    /**
     * 搜索股票
     * GET /api/stock/search?keyword=xxx&market=A_STOCK
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String market) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<StockDTO> list = stockService.searchStock(keyword, market);
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

    /**
     * 获取实时行情
     * GET /api/stock/realtime/{code}
     */
    @GetMapping("/realtime/{code}")
    public ResponseEntity<Map<String, Object>> realtime(@PathVariable String code) {
        Map<String, Object> result = new HashMap<>();
        try {
            StockDTO dto = stockService.getRealtimeQuote(code);
            if (dto == null) {
                result.put("code", 404);
                result.put("message", "未找到该股票");
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
     * 股票筛选器
     * GET /api/stock/screener?minPrice=&maxPrice=&minChange=&maxChange=&minVolume=&keyword=&sector=&sortField=&sortOrder=&page=&size=
     */
    @GetMapping("/screener")
    public ResponseEntity<Map<String, Object>> screener(
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minChange,
            @RequestParam(required = false) BigDecimal maxChange,
            @RequestParam(required = false) Long minVolume,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = stockService.screener(minPrice, maxPrice, minChange, maxChange,
                    minVolume, keyword, sector, sortField, sortOrder, page, size);
            result.put("code", 200);
            result.put("message", "筛选成功");
            result.put("data", data);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取K线数据
     * GET /api/stock/kline/{code}?period=daily&limit=120
     */
    @GetMapping("/kline/{code}")
    public ResponseEntity<Map<String, Object>> kline(
            @PathVariable String code,
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(defaultValue = "120") int limit) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<KlineData> list = stockService.getKlineData(code, period, limit);
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

    /**
     * 获取股票列表
     * GET /api/stock/list?market=A_STOCK&page=1&size=20
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String market,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<StockDTO> list = stockService.getStockList(market, page, size);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", list);
            result.put("page", page);
            result.put("size", size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取资金流向
     * GET /api/stock/flow/{code}
     * 从东方财富API获取真实资金流向数据
     */
    @GetMapping("/flow/{code}")
    public ResponseEntity<Map<String, Object>> flow(@PathVariable String code) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> flowData = stockService.getCapitalFlow(code);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", flowData);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取公司信息
     * GET /api/stock/company/{code}
     * 从东方财富API获取真实公司基本信息
     */
    @GetMapping("/company/{code}")
    public ResponseEntity<Map<String, Object>> company(@PathVariable String code) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> companyData = stockService.getCompanyInfo(code);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", companyData);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
