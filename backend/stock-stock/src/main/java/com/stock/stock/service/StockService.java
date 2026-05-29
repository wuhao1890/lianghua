package com.stock.stock.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stock.stock.dto.KlineData;
import com.stock.stock.dto.StockDTO;
import com.stock.stock.entity.StockDaily;
import com.stock.stock.entity.StockInfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface StockService extends IService<StockInfo> {

    /**
     * 搜索股票
     */
    List<StockDTO> searchStock(String keyword, String market);

    /**
     * 获取实时行情
     */
    StockDTO getRealtimeQuote(String code);

    /**
     * 获取K线数据
     */
    List<KlineData> getKlineData(String code, String period, int limit);

    /**
     * 获取股票列表（分页）
     */
    List<StockDTO> getStockList(String market, int page, int size);

    /**
     * 股票筛选器（分页+条件+排序）
     */
    Map<String, Object> screener(BigDecimal minPrice, BigDecimal maxPrice,
                                  BigDecimal minChange, BigDecimal maxChange,
                                  Long minVolume, String keyword, String sector,
                                  String sortField, String sortOrder,
                                  int page, int size);

    /**
     * 获取A股实时行情（新浪API）
     */
    StockDTO getAStockQuote(String code);

    /**
     * 获取美股行情
     */
    StockDTO getUSStockQuote(String code);

    /**
     * 保存或更新行情数据
     */
    void saveOrUpdateQuote(StockDTO dto);

    /**
     * 保存K线数据
     */
    void saveKlineData(String stockCode, List<KlineData> klineDataList);
}
