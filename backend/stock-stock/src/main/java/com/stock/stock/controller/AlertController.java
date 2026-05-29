package com.stock.stock.controller;

import com.stock.stock.websocket.QuoteWebSocketHandler;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/stock/alert")
public class AlertController {

    /** 内存存储：id -> Alert */
    private static final ConcurrentHashMap<Long, Alert> alertMap = new ConcurrentHashMap<>();
    private static final AtomicLong idGenerator = new AtomicLong(1);

    /**
     * 获取所有告警列表
     * GET /api/stock/alert/list
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Alert> alerts = new ArrayList<>(alertMap.values());
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", alerts);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 添加告警
     * POST /api/stock/alert/add
     * Body: { code, name, targetPrice, direction }
     */
    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> add(@RequestBody AlertRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            Alert alert = new Alert();
            alert.setId(idGenerator.getAndIncrement());
            alert.setCode(request.getCode());
            alert.setName(request.getName());
            alert.setTargetPrice(request.getTargetPrice());
            alert.setDirection(request.getDirection());
            alert.setEnabled(true);
            alertMap.put(alert.getId(), alert);

            result.put("code", 200);
            result.put("message", "添加成功");
            result.put("data", alert);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 删除告警
     * DELETE /api/stock/alert/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            Alert removed = alertMap.remove(id);
            if (removed == null) {
                result.put("code", 404);
                result.put("message", "告警不存在");
                return ResponseEntity.ok(result);
            }
            result.put("code", 200);
            result.put("message", "删除成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 切换告警启用/禁用
     * PUT /api/stock/alert/{id}/toggle
     */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggle(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            Alert alert = alertMap.get(id);
            if (alert == null) {
                result.put("code", 404);
                result.put("message", "告警不存在");
                return ResponseEntity.ok(result);
            }
            alert.setEnabled(!alert.getEnabled());
            result.put("code", 200);
            result.put("message", alert.getEnabled() ? "已启用" : "已禁用");
            result.put("data", alert);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 检查某股票当前价格是否触发告警
     * GET /api/stock/alert/check/{code}?currentPrice=xxx
     */
    @GetMapping("/check/{code}")
    public ResponseEntity<Map<String, Object>> check(
            @PathVariable String code,
            @RequestParam BigDecimal currentPrice) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> triggered = new ArrayList<>();
            for (Alert alert : alertMap.values()) {
                if (!alert.getCode().equals(code) || !alert.getEnabled()) {
                    continue;
                }
                boolean triggeredFlag = false;
                String triggerDesc;
                if ("below".equals(alert.getDirection())) {
                    // 当前价低于目标价时触发
                    if (currentPrice.compareTo(alert.getTargetPrice()) <= 0) {
                        triggeredFlag = true;
                    }
                    triggerDesc = "价格下跌至" + alert.getTargetPrice() + "以下";
                } else if ("above".equals(alert.getDirection())) {
                    // 当前价高于目标价时触发
                    if (currentPrice.compareTo(alert.getTargetPrice()) >= 0) {
                        triggeredFlag = true;
                    }
                    triggerDesc = "价格上涨至" + alert.getTargetPrice() + "以上";
                } else {
                    triggerDesc = "未知方向";
                }

                if (triggeredFlag) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", alert.getId());
                    item.put("code", alert.getCode());
                    item.put("name", alert.getName());
                    item.put("targetPrice", alert.getTargetPrice());
                    item.put("direction", alert.getDirection());
                    item.put("currentPrice", currentPrice);
                    item.put("triggerDesc", triggerDesc);
                    triggered.add(item);
                }
            }

            result.put("code", 200);
            result.put("message", "检查完成");
            result.put("data", triggered);

            // 如果有触发预警，通过WebSocket广播
            if (!triggered.isEmpty()) {
                for (Map<String, Object> item : triggered) {
                    Map<String, Object> wsMsg = new HashMap<>();
                    wsMsg.put("type", "alert");
                    wsMsg.put("data", item);
                    QuoteWebSocketHandler.broadcast("alert", wsMsg);
                }
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    // ===== DTO Classes =====

    @Data
    public static class Alert {
        private Long id;
        private String code;
        private String name;
        private BigDecimal targetPrice;
        /** below / above */
        private String direction;
        private Boolean enabled;
    }

    @Data
    public static class AlertRequest {
        private String code;
        private String name;
        private BigDecimal targetPrice;
        /** below / above */
        private String direction;
    }
}
