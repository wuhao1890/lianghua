package com.stock.stock.controller;

import com.stock.stock.dto.BacktestResult;
import com.stock.stock.service.BacktestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/stock")
public class BacktestController {

    private final BacktestService backtestService;

    /** 回测运行状态 tracking */
    private static final ConcurrentHashMap<String, String> backtestStatus = new ConcurrentHashMap<>();
    private static final AtomicLong backtestIdGen = new AtomicLong(0);

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    /**
     * 执行策略回测
     */
    @GetMapping("/backtest")
    public ResponseEntity<Map<String, Object>> backtest(
            @RequestParam String code,
            @RequestParam(defaultValue = "ma_cross") String strategy,
            @RequestParam(defaultValue = "5") int shortPeriod,
            @RequestParam(defaultValue = "20") int longPeriod,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "100000") BigDecimal capital) {
        Map<String, Object> result = new HashMap<>();
        String btId = "bt_" + backtestIdGen.incrementAndGet();
        try {
            backtestStatus.put(btId, "RUNNING");
            BacktestResult btResult = backtestService.runBacktest(
                    code, strategy, shortPeriod, longPeriod,
                    startDate, endDate, capital);
            backtestStatus.put(btId, "COMPLETED");

            result.put("code", 200);
            result.put("message", "回测完成");
            result.put("data", btResult);
            result.put("backtestId", btId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            backtestStatus.put(btId, "ERROR");
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取回测状态
     * GET /api/stock/backtest/status/{id}
     */
    @GetMapping("/backtest/status/{id}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        String status = backtestStatus.getOrDefault(id, "NOT_FOUND");
        result.put("code", 200);
        result.put("data", status);
        return ResponseEntity.ok(result);
    }
}
