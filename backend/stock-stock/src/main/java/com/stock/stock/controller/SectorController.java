package com.stock.stock.controller;

import com.stock.stock.dto.SectorDTO;
import com.stock.stock.dto.StockDTO;
import com.stock.stock.service.SectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock/sectors")
public class SectorController {

    @Autowired
    private SectorService sectorService;

    /**
     * 获取所有板块列表
     * GET /api/stock/sectors
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllSectors() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<SectorDTO> list = sectorService.getAllSectors();
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
     * 获取板块详情
     * GET /api/stock/sectors/{code}
     */
    @GetMapping("/{code}")
    public ResponseEntity<Map<String, Object>> getSectorDetail(@PathVariable String code) {
        Map<String, Object> result = new HashMap<>();
        try {
            SectorDTO dto = sectorService.getSectorDetail(code);
            if (dto == null) {
                result.put("code", 404);
                result.put("message", "板块不存在");
                return ResponseEntity.ok(result);
            }

            // 同时查询板块下的前5只股票
            List<StockDTO> stocks = sectorService.getSectorStocks(code);
            // 只取前5
            List<StockDTO> topStocks = stocks.size() > 5 ? stocks.subList(0, 5) : stocks;

            Map<String, Object> data = new HashMap<>();
            data.put("sector", dto);
            data.put("topStocks", topStocks);

            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", data);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取板块内所有股票
     * GET /api/stock/sectors/{code}/stocks
     */
    @GetMapping("/{code}/stocks")
    public ResponseEntity<Map<String, Object>> getSectorStocks(@PathVariable String code) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<StockDTO> list = sectorService.getSectorStocks(code);
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
