package com.stock.ai.dto;

import lombok.Data;

@Data
public class AiAnalyzeRequest {

    private String stockCode;

    private Long configId;

    private String customPrompt;
}
