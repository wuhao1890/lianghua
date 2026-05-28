package com.stock.trade.controller;

import com.stock.trade.dto.*;
import com.stock.trade.entity.TradeOrder;
import com.stock.trade.service.TradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trade")
public class TradeController {

    @Autowired
    private TradeService tradeService;

    /**
     * 买入股票
     * POST /api/trade/buy
     */
    @PostMapping("/buy")
    public ResponseEntity<Map<String, Object>> buy(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody BuyRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            TradeOrder order = tradeService.buy(userId, request);
            result.put("code", 200);
            result.put("message", "买入成功");
            result.put("data", order);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 卖出股票
     * POST /api/trade/sell
     */
    @PostMapping("/sell")
    public ResponseEntity<Map<String, Object>> sell(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody SellRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            TradeOrder order = tradeService.sell(userId, request);
            result.put("code", 200);
            result.put("message", "卖出成功");
            result.put("data", order);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 查询当前持仓
     * GET /api/trade/positions
     */
    @GetMapping("/positions")
    public ResponseEntity<Map<String, Object>> positions(
            @RequestHeader("X-User-Id") Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<PositionDTO> positions = tradeService.getPositions(userId);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", positions);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 查询交易记录
     * GET /api/trade/orders
     */
    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> orders(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<TradeOrder> orders = tradeService.getOrders(userId, status, direction, startDate, endDate);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", orders);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 撤单
     * DELETE /api/trade/order/{id}
     */
    @DeleteMapping("/order/{id}")
    public ResponseEntity<Map<String, Object>> cancelOrder(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            tradeService.cancelOrder(userId, id);
            result.put("code", 200);
            result.put("message", "撤单成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 账户概览
     * GET /api/trade/account
     */
    @GetMapping("/account")
    public ResponseEntity<Map<String, Object>> account(
            @RequestHeader("X-User-Id") Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            AccountOverview overview = tradeService.getAccountOverview(userId);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", overview);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 收益分析
     * GET /api/trade/profit-analysis
     */
    @GetMapping("/profit-analysis")
    public ResponseEntity<Map<String, Object>> profitAnalysis(
            @RequestHeader("X-User-Id") Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            ProfitAnalysis analysis = tradeService.getProfitAnalysis(userId);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", analysis);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 收益记录
     * GET /api/trade/profit-records
     */
    @GetMapping("/profit-records")
    public ResponseEntity<Map<String, Object>> profitRecords(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1m") String range) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> records = tradeService.getProfitRecords(userId, range);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", records);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
