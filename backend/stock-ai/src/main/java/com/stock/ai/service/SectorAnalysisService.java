package com.stock.ai.service;

import com.stock.ai.dto.SectorAnalysisResult;

public interface SectorAnalysisService {

    /**
     * 触发AI板块分析
     */
    SectorAnalysisResult analyzeSectors();

    /**
     * 获取最新的板块分析报告
     */
    SectorAnalysisResult getLatestReport();
}
