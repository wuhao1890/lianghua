package com.stock.stock.service;

import com.stock.stock.dto.GoldPriceDTO;

import java.util.List;

public interface GoldService {

    /**
     * 获取最新黄金价格
     */
    GoldPriceDTO getLatestPrice();

    /**
     * 获取最近N天的黄金价格历史
     *
     * @param days 天数
     */
    List<GoldPriceDTO> getPriceHistory(int days);
}
