package com.stock.stock.websocket;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 实时行情推送处理器
 * 客户端连接后自行选择订阅的数据，服务器数据变化时广播
 */
public class QuoteWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(QuoteWebSocketHandler.class);

    /** 所有活跃连接 */
    private static final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** 客户端订阅映射: sessionId -> { codes: [600519,...], type: quote/kline } */
    private static final ConcurrentHashMap<String, Map<String, Object>> subscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket 连接: {} (当前连接数: {})", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        try {
            Map<String, Object> msg = JSON.parseObject(payload);
            String action = (String) msg.getOrDefault("action", "");
            if ("subscribe".equals(action)) {
                // 订阅行情: { action: "subscribe", codes: ["600519","000001"], type: "quote" }
                subscriptions.put(session.getId(), msg);
                log.info("订阅: session={}, codes={}, type={}", session.getId(), msg.get("codes"), msg.get("type"));
            } else if ("unsubscribe".equals(action)) {
                subscriptions.remove(session.getId());
                log.info("取消订阅: session={}", session.getId());
            } else if ("ping".equals(action)) {
                // 心跳回复
                sendMessage(session, "{\"action\":\"pong\"}");
            }
        } catch (Exception e) {
            log.warn("WS 消息解析失败: {}", payload);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        subscriptions.remove(session.getId());
        log.info("WebSocket 断开: {} (当前连接数: {})", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        subscriptions.remove(session.getId());
        log.warn("WebSocket 传输错误: {} - {}", session.getId(), exception.getMessage());
    }

    /**
     * 广播行情数据到所有订阅了相关code的客户端
     * @param code 股票代码
     * @param data 行情JSON
     */
    public static void broadcastQuote(String code, Object data) {
        if (sessions.isEmpty()) return;
        String json = JSON.toJSONString(data);
        TextMessage msg = new TextMessage(json);
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            Map<String, Object> sub = subscriptions.get(entry.getKey());
            if (sub != null && "quote".equals(sub.get("type"))) {
                Object codes = sub.get("codes");
                if (codes == null || codes.toString().contains(code)) {
                    sendMessage(entry.getValue(), msg);
                }
            }
        }
    }

    /**
     * 广播行情数据到所有连接（用于预警/通知等场景）
     * @param type 消息类型: alert/quote/system
     * @param data 数据
     */
    public static void broadcast(String type, Object data) {
        if (sessions.isEmpty()) return;
        try {
            String json = JSON.toJSONString(data);
            TextMessage msg = new TextMessage(json);
            for (WebSocketSession session : sessions.values()) {
                sendMessage(session, msg);
            }
        } catch (Exception e) {
            log.warn("WS 广播失败: {}", e.getMessage());
        }
    }

    private static void sendMessage(WebSocketSession session, TextMessage msg) {
        try {
            if (session.isOpen()) {
                session.sendMessage(msg);
            }
        } catch (IOException e) {
            log.warn("WS 发送失败: {}", e.getMessage());
        }
    }

    private static void sendMessage(WebSocketSession session, String msg) {
        sendMessage(session, new TextMessage(msg));
    }

    public static int getActiveCount() {
        return sessions.size();
    }
}
