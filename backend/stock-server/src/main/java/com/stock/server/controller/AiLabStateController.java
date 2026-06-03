package com.stock.server.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/ai/lab")
public class AiLabStateController implements InitializingBean {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterPropertiesSet() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ai_lab_state (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "user_id BIGINT NOT NULL UNIQUE," +
                "generation INT NOT NULL DEFAULT 0," +
                "iteration_count INT NOT NULL DEFAULT 0," +
                "capital DECIMAL(18,2) NOT NULL DEFAULT 100000," +
                "interval_minutes INT NOT NULL DEFAULT 5," +
                "state_json LONGTEXT NOT NULL," +
                "last_run_at DATETIME NULL," +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ai_lab_iteration (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "user_id BIGINT NOT NULL," +
                "generation INT NOT NULL DEFAULT 0," +
                "champion_json LONGTEXT NULL," +
                "experiments_json LONGTEXT NULL," +
                "capital DECIMAL(18,2) NOT NULL DEFAULT 100000," +
                "interval_minutes INT NOT NULL DEFAULT 5," +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "INDEX idx_ai_lab_iteration_user_time (user_id, created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    @GetMapping("/state")
    public Map<String, Object> state(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Long safeUserId = safeUserId(userId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT state_json FROM ai_lab_state WHERE user_id = ? LIMIT 1",
                safeUserId
        );
        if (rows.isEmpty()) {
            return ok(defaultState());
        }
        try {
            return ok(objectMapper.readValue(String.valueOf(rows.get(0).get("state_json")), Map.class));
        } catch (Exception e) {
            return ok(defaultState());
        }
    }

    @PostMapping("/state")
    public Map<String, Object> saveState(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody Map<String, Object> body) throws JsonProcessingException {
        Map<String, Object> state = normalizeState(body);
        upsertState(safeUserId(userId), state);
        return ok(state, "AI实验室状态已保存");
    }

    @PostMapping("/iteration")
    public Map<String, Object> saveIteration(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody Map<String, Object> body) throws JsonProcessingException {
        Long safeUserId = safeUserId(userId);
        Map<String, Object> state = normalizeState(body);
        Integer generation = intValue(state.get("generation"), 0);
        jdbcTemplate.update(
                "INSERT INTO ai_lab_iteration (user_id, generation, champion_json, experiments_json, capital, interval_minutes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                safeUserId,
                generation,
                objectMapper.writeValueAsString(state.get("champion")),
                objectMapper.writeValueAsString(state.getOrDefault("experiments", Collections.emptyList())),
                numberValue(state.get("capital"), 100000),
                intValue(state.get("intervalMinutes"), 5),
                LocalDateTime.now()
        );
        state.put("iterationCount", Math.max(intValue(state.get("iterationCount"), 0), generation));
        state.put("lastRunAt", new Date().toInstant().toString());
        upsertState(safeUserId, state);
        Map<String, Object> data = new HashMap<>();
        data.put("state", state);
        data.put("generation", generation);
        return ok(data, "AI实验室迭代已入库");
    }

    @GetMapping("/iterations")
    public Map<String, Object> iterations(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, generation, champion_json, experiments_json, capital, interval_minutes, created_at " +
                        "FROM ai_lab_iteration WHERE user_id = ? ORDER BY created_at DESC LIMIT 200",
                safeUserId(userId)
        );
        return ok(rows);
    }

    private void upsertState(Long userId, Map<String, Object> state) throws JsonProcessingException {
        jdbcTemplate.update(
                "INSERT INTO ai_lab_state (user_id, generation, iteration_count, capital, interval_minutes, state_json, last_run_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, NOW()) " +
                        "ON DUPLICATE KEY UPDATE generation = VALUES(generation), iteration_count = VALUES(iteration_count), " +
                        "capital = VALUES(capital), interval_minutes = VALUES(interval_minutes), state_json = VALUES(state_json), last_run_at = NOW()",
                userId,
                intValue(state.get("generation"), 0),
                intValue(state.get("iterationCount"), intValue(state.get("generation"), 0)),
                numberValue(state.get("capital"), 100000),
                intValue(state.get("intervalMinutes"), 5),
                objectMapper.writeValueAsString(state)
        );
    }

    private Map<String, Object> normalizeState(Map<String, Object> body) {
        Map<String, Object> state = new HashMap<>(body == null ? Collections.emptyMap() : body);
        state.putIfAbsent("generation", 0);
        state.putIfAbsent("iterationCount", state.get("generation"));
        state.putIfAbsent("capital", 100000);
        state.putIfAbsent("intervalMinutes", 5);
        state.putIfAbsent("assets", Collections.emptyList());
        state.putIfAbsent("experiments", Collections.emptyList());
        state.putIfAbsent("evolutionLog", Collections.emptyList());
        state.put("updatedAt", new Date().toInstant().toString());
        return state;
    }

    private Map<String, Object> defaultState() {
        Map<String, Object> state = new HashMap<>();
        state.put("generation", 0);
        state.put("iterationCount", 0);
        state.put("capital", 100000);
        state.put("intervalMinutes", 5);
        state.put("assets", Collections.emptyList());
        state.put("experiments", Collections.emptyList());
        state.put("evolutionLog", Collections.emptyList());
        state.put("champion", null);
        state.put("lastRunAt", null);
        state.put("updatedAt", null);
        return state;
    }

    private Long safeUserId(Long userId) {
        return userId == null || userId <= 0 ? 1L : userId;
    }

    private Integer intValue(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private Double numberValue(Object value, double fallback) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private Map<String, Object> ok(Object data) {
        return ok(data, "success");
    }

    private Map<String, Object> ok(Object data, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", data);
        return result;
    }
}
