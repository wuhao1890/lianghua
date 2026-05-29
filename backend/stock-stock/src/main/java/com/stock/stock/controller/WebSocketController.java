package com.stock.stock.controller;

import com.alibaba.fastjson2.JSON;
import com.stock.stock.websocket.QuoteWebSocketHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 管理接口
 * 用于触发推送/查看连接状态
 */
@RestController
@RequestMapping("/api/ws")
public class WebSocketController {

    /**
     * 获取WS连接状态
     * GET /api/ws/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("activeConnections", QuoteWebSocketHandler.getActiveCount());
        return ResponseEntity.ok(result);
    }

    /**
     * 广播行情到指定code
     * POST /api/ws/broadcast/quote
     * Body: { code: "600519", data: { currentPrice: 1300, ... } }
     */
    @PostMapping("/broadcast/quote")
    public ResponseEntity<Map<String, Object>> broadcastQuote(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        Object data = body.get("data");
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "quote");
        msg.put("code", code);
        msg.put("data", data);
        QuoteWebSocketHandler.broadcast("quote", msg);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "已广播");
        return ResponseEntity.ok(result);
    }

    /**
     * 广播预警
     * POST /api/ws/broadcast/alert
     * Body: { code: "600519", name: "贵州茅台", targetPrice: 1300, currentPrice: 1290, direction: "below" }
     */
    @PostMapping("/broadcast/alert")
    public ResponseEntity<Map<String, Object>> broadcastAlert(@RequestBody Map<String, Object> body) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "alert");
        msg.put("data", body);
        QuoteWebSocketHandler.broadcast("alert", msg);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "预警已推送");
        return ResponseEntity.ok(result);
    }
}
