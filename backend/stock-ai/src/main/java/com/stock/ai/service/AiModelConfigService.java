package com.stock.ai.service;

import com.stock.ai.dto.AiModelConfigRequest;
import com.stock.ai.entity.AiModelConfig;

import java.util.List;

public interface AiModelConfigService {

    List<AiModelConfig> listByUserId(Long userId);

    AiModelConfig create(AiModelConfigRequest request, Long userId);

    AiModelConfig update(Long id, AiModelConfigRequest request);

    void delete(Long id, Long userId);

    AiModelConfig getById(Long id);
}
