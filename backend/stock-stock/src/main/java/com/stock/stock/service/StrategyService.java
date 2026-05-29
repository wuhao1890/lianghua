package com.stock.stock.service;

import com.stock.stock.dto.StrategyDto;

import java.util.List;

public interface StrategyService {

    /**
     * 创建策略
     */
    StrategyDto createStrategy(StrategyDto dto);

    /**
     * 运行策略（回测或实盘）
     */
    StrategyDto runStrategy(String id);

    /**
     * 暂停策略
     */
    StrategyDto pauseStrategy(String id);

    /**
     * 停止策略
     */
    StrategyDto stopStrategy(String id);

    /**
     * 获取策略列表
     */
    List<StrategyDto> listStrategies();

    /**
     * 获取策略详情
     */
    StrategyDto getStrategy(String id);

    /**
     * 删除策略
     */
    void deleteStrategy(String id);
}
