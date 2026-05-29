package com.stock.stock.controller;

import com.stock.stock.dto.StrategyDto;
import com.stock.stock.service.StrategyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock/strategy")
public class StrategyController {

    private final StrategyService strategyService;

    public StrategyController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    /**
     * 创建策略
     * POST /api/stock/strategy/create
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> create(@RequestBody StrategyDto dto) {
        Map<String, Object> result = new HashMap<>();
        try {
            StrategyDto saved = strategyService.createStrategy(dto);
            result.put("code", 200);
            result.put("message", "策略创建成功");
            result.put("data", saved);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 运行策略
     * POST /api/stock/strategy/run/{id}
     */
    @PostMapping("/run/{id}")
    public ResponseEntity<Map<String, Object>> run(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        try {
            StrategyDto dto = strategyService.runStrategy(id);
            if (dto == null) { result.put("code", 404); result.put("message", "策略不存在"); }
            else { result.put("code", 200); result.put("message", "策略执行完成"); result.put("data", dto); }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 暂停策略
     * POST /api/stock/strategy/pause/{id}
     */
    @PostMapping("/pause/{id}")
    public ResponseEntity<Map<String, Object>> pause(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        StrategyDto dto = strategyService.pauseStrategy(id);
        if (dto == null) { result.put("code", 404); result.put("message", "策略不存在"); }
        else { result.put("code", 200); result.put("data", dto); }
        return ResponseEntity.ok(result);
    }

    /**
     * 停止策略
     * POST /api/stock/strategy/stop/{id}
     */
    @PostMapping("/stop/{id}")
    public ResponseEntity<Map<String, Object>> stop(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        StrategyDto dto = strategyService.stopStrategy(id);
        if (dto == null) { result.put("code", 404); result.put("message", "策略不存在"); }
        else { result.put("code", 200); result.put("data", dto); }
        return ResponseEntity.ok(result);
    }

    /**
     * 获取策略列表
     * GET /api/stock/strategy/list
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list() {
        Map<String, Object> result = new HashMap<>();
        List<StrategyDto> list = strategyService.listStrategies();
        result.put("code", 200);
        result.put("data", list);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取策略详情
     * GET /api/stock/strategy/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        StrategyDto dto = strategyService.getStrategy(id);
        if (dto == null) { result.put("code", 404); result.put("message", "策略不存在"); }
        else { result.put("code", 200); result.put("data", dto); }
        return ResponseEntity.ok(result);
    }

    /**
     * 删除策略
     * DELETE /api/stock/strategy/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        strategyService.deleteStrategy(id);
        result.put("code", 200);
        result.put("message", "删除成功");
        return ResponseEntity.ok(result);
    }
}
