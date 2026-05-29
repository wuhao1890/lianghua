package com.stock.stock.service;

import java.util.List;
import java.util.Map;

public interface GlobalMarketService {

    /**
     * 获取全球市场股票列表（分页）
     */
    Map<String, Object> getStockList(String market, int page, int size);

    /**
     * 获取实时行情
     */
    Map<String, Object> getRealtimeQuote(String code, String market);

    /**
     * 获取市场指数
     */
    List<Map<String, Object>> getMarketIndices(String market);

    /**
     * 搜索股票
     */
    List<Map<String, Object>> search(String market, String keyword);
}
