package com.stock.ai.controller;

import com.stock.ai.dto.SectorAnalysisResult;
import com.stock.ai.service.SectorAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/sector")
public class SectorAiController {

    @Autowired
    private SectorAnalysisService sectorAnalysisService;

    /**
     * 触发AI板块分析
     * POST /api/ai/sector/analyze
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeSectors() {
        Map<String, Object> result = new HashMap<>();
        try {
            SectorAnalysisResult analysis = sectorAnalysisService.analyzeSectors();
            if (analysis == null) {
                result.put("code", 500);
                result.put("message", "板块分析失败，未获取到板块数据");
                return ResponseEntity.ok(result);
            }
            result.put("code", 200);
            result.put("message", "板块分析完成");
            result.put("data", analysis);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取最新的板块分析报告
     * GET /api/ai/sector/report
     */
    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getLatestReport() {
        Map<String, Object> result = new HashMap<>();
        try {
            SectorAnalysisResult report = sectorAnalysisService.getLatestReport();
            if (report == null) {
                result.put("code", 404);
                result.put("message", "暂无板块分析报告，请先触发分析");
                return ResponseEntity.ok(result);
            }
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", report);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
