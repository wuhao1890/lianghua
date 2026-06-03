package com.stock.ai.service;

import com.stock.ai.dto.AiAnalyzeRequest;
import com.stock.ai.dto.AiAnalysisResponse;

import java.util.List;

public interface AiAnalysisService {

    AiAnalysisResponse analyzeStock(AiAnalyzeRequest request, Long userId);

    List<AiAnalysisResponse.NewsItem> getStockNews(String stockCode);
}
