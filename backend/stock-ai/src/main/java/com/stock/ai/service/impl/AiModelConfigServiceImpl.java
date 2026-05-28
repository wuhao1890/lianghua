package com.stock.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stock.ai.dto.AiModelConfigRequest;
import com.stock.ai.entity.AiModelConfig;
import com.stock.ai.mapper.AiModelConfigMapper;
import com.stock.ai.service.AiModelConfigService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiModelConfigServiceImpl implements AiModelConfigService {

    private final AiModelConfigMapper aiModelConfigMapper;

    public AiModelConfigServiceImpl(AiModelConfigMapper aiModelConfigMapper) {
        this.aiModelConfigMapper = aiModelConfigMapper;
    }

    @Override
    public List<AiModelConfig> listByUserId(Long userId) {
        return aiModelConfigMapper.selectList(
                new LambdaQueryWrapper<AiModelConfig>()
                        .orderByDesc(AiModelConfig::getCreateTime));
    }

    @Override
    public AiModelConfig create(AiModelConfigRequest request, Long userId) {
        AiModelConfig config = new AiModelConfig();
        BeanUtils.copyProperties(request, config);
        config.setUserId(userId);
        config.setEnabled(true);
        aiModelConfigMapper.insert(config);
        return config;
    }

    @Override
    public AiModelConfig update(Long id, AiModelConfigRequest request) {
        AiModelConfig config = aiModelConfigMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("配置不存在: " + id);
        }
        if (request.getName() != null) {
            config.setName(request.getName());
        }
        if (request.getProvider() != null) {
            config.setProvider(request.getProvider());
        }
        if (request.getApiKey() != null) {
            config.setApiKey(request.getApiKey());
        }
        if (request.getBaseUrl() != null) {
            config.setBaseUrl(request.getBaseUrl());
        }
        if (request.getModelName() != null) {
            config.setModelName(request.getModelName());
        }
        aiModelConfigMapper.updateById(config);
        return config;
    }

    @Override
    public void delete(Long id, Long userId) {
        AiModelConfig config = aiModelConfigMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("配置不存在: " + id);
        }
        if (!config.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除该配置");
        }
        aiModelConfigMapper.deleteById(id);
    }

    @Override
    public AiModelConfig getById(Long id) {
        return aiModelConfigMapper.selectById(id);
    }
}
