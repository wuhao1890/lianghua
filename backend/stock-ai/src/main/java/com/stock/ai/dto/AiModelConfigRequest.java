package com.stock.ai.dto;

import lombok.Data;

@Data
public class AiModelConfigRequest {

    private String name;

    private String provider;

    private String apiKey;

    private String baseUrl;

    private String modelName;
}
