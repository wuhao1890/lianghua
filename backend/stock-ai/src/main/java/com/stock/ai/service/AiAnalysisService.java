package com.stock.ai.service;

import com.stock.ai.dto.AiAnalyzeRequest;
import com.stock.ai.dto.AiAnalysisResponse;

public interface AiAnalysisService {

    AiAnalysisResponse analyzeStock(AiAnalyzeRequest request, Long userId);
}
