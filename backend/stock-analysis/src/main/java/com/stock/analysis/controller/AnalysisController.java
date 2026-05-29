package com.stock.analysis.controller;

import com.stock.analysis.dto.BacktestResult;
import com.stock.analysis.dto.IndicatorResult;
import com.stock.analysis.dto.TradeSignal;
import com.stock.analysis.service.TechnicalAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    @Autowired
    private TechnicalAnalysisService technicalAnalysisService;

    /**
     * 获取技术指标
     * GET /api/analysis/indicators/{code}?types=MA,MACD,RSI,KDJ,BOLL
     */
    @GetMapping("/indicators/{code}")
    public ResponseEntity<Map<String, Object>> getIndicators(
            @PathVariable String code,
            @RequestParam(defaultValue = "MA,MACD,RSI,KDJ,BOLL") String types) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<IndicatorResult> indicators = technicalAnalysisService.getIndicators(code, types);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", indicators);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取综合买卖信号
     * GET /api/analysis/signal/{code}
     */
    @GetMapping("/signal/{code}")
    public ResponseEntity<Map<String, Object>> getSignal(@PathVariable String code) {
        Map<String, Object> result = new HashMap<>();
        try {
            TradeSignal signal = technicalAnalysisService.generateSignal(code);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", signal);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 回测策略
     * GET /api/analysis/backtest/{code}?strategy=ma_cross&shortPeriod=5&longPeriod=20
     */
    @GetMapping("/backtest/{code}")
    public ResponseEntity<Map<String, Object>> backtest(
            @PathVariable String code,
            @RequestParam(defaultValue = "ma_cross") String strategy,
            @RequestParam(defaultValue = "5") Integer shortPeriod,
            @RequestParam(defaultValue = "20") Integer longPeriod) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Integer> params = new LinkedHashMap<>();
            params.put("shortPeriod", shortPeriod);
            params.put("longPeriod", longPeriod);

            BacktestResult backtestResult = technicalAnalysisService.backtest(code, strategy, params);
            result.put("code", 200);
            result.put("message", "回测完成");
            result.put("data", backtestResult);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
