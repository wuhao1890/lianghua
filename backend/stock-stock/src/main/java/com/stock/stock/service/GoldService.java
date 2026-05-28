package com.stock.stock.service;

import com.stock.stock.dto.GoldPriceDTO;

import java.util.List;
import java.util.Map;

public interface GoldService {

    /**
     * 获取所有黄金产品列表
     *
     * @return Map<产品代码, 产品名称>
     */
    Map<String, String> getProducts();

    /**
     * 获取指定黄金产品的最新价格
     *
     * @param productCode 产品代码（为空时返回默认COMEX黄金）
     */
    GoldPriceDTO getLatestPrice(String productCode);

    /**
     * 获取指定黄金产品的历史价格
     *
     * @param productCode 产品代码（为空时返回默认COMEX黄金）
     * @param days        天数
     */
    List<GoldPriceDTO> getPriceHistory(String productCode, int days);
}
