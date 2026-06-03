package com.stock.ai.controller;

import com.stock.ai.dto.AiAnalyzeRequest;
import com.stock.ai.dto.AiAnalysisResponse;
import com.stock.ai.dto.AiModelConfigRequest;
import com.stock.ai.entity.AiModelConfig;
import com.stock.ai.entity.AiAnalysisResult;
import com.stock.ai.service.AiAnalysisService;
import com.stock.ai.service.AiModelConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stock.ai.mapper.AiAnalysisResultMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiModelConfigService aiModelConfigService;

    @Autowired
    private AiAnalysisService aiAnalysisService;

    @Autowired
    private AiAnalysisResultMapper aiAnalysisResultMapper;

    /**
     * 获取当前用户的AI模型配置列表
     * GET /api/ai/configs
     */
    @GetMapping("/configs")
    public ResponseEntity<Map<String, Object>> listConfigs(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (userId == null) userId = 1L;
            List<AiModelConfig> list = aiModelConfigService.listByUserId(userId);
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
     * 创建AI模型配置
     * POST /api/ai/configs
     */
    @PostMapping("/configs")
    public ResponseEntity<Map<String, Object>> createConfig(
            @RequestBody AiModelConfigRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (userId == null) userId = 1L;
            AiModelConfig config = aiModelConfigService.create(request, userId);
            result.put("code", 200);
            result.put("message", "创建成功");
            result.put("data", config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 更新AI模型配置
     * PUT /api/ai/configs/{id}
     */
    @PutMapping("/configs/{id}")
    public ResponseEntity<Map<String, Object>> updateConfig(
            @PathVariable Long id,
            @RequestBody AiModelConfigRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            AiModelConfig config = aiModelConfigService.update(id, request);
            result.put("code", 200);
            result.put("message", "更新成功");
            result.put("data", config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 删除AI模型配置
     * DELETE /api/ai/configs/{id}
     */
    @DeleteMapping("/configs/{id}")
    public ResponseEntity<Map<String, Object>> deleteConfig(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (userId == null) userId = 1L;
            aiModelConfigService.delete(id, userId);
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
     * 测试模型连接
     * POST /api/ai/configs/{id}/test
     */
    @PostMapping("/configs/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            AiModelConfig config = aiModelConfigService.getById(id);
            if (config == null) {
                result.put("code", 404);
                result.put("message", "配置不存在");
                return ResponseEntity.ok(result);
            }
            result.put("code", 200);
            result.put("message", "连接测试功能需要实际调用API，请使用分析接口验证");
            result.put("data", config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * AI分析股票
     * POST /api/ai/analyze
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(
            @RequestBody AiAnalyzeRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (userId == null) userId = 1L;
            AiAnalysisResponse response = aiAnalysisService.analyzeStock(request, userId);
            result.put("code", 200);
            result.put("message", "分析完成");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取股票相关新闻
     * GET /api/ai/news?stockCode=300308
     */
    @GetMapping("/news")
    public ResponseEntity<Map<String, Object>> news(@RequestParam String stockCode) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<AiAnalysisResponse.NewsItem> list = aiAnalysisService.getStockNews(stockCode);
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
     * 获取分析历史
     * GET /api/ai/history
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> history(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (userId == null) userId = 1L;
            List<AiAnalysisResult> list = aiAnalysisResultMapper.selectList(
                    new LambdaQueryWrapper<AiAnalysisResult>()
                            .orderByDesc(AiAnalysisResult::getCreateTime));
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
