package com.stock.stock.service;

import com.stock.stock.dto.SectorDTO;
import com.stock.stock.dto.StockDTO;

import java.util.List;

public interface SectorService {

    /**
     * 获取所有板块列表，按涨跌幅降序排列
     */
    List<SectorDTO> getAllSectors();

    /**
     * 获取板块详情，包含前5只成分股
     */
    SectorDTO getSectorDetail(String sectorCode);

    /**
     * 获取板块内所有股票
     */
    List<StockDTO> getSectorStocks(String sectorCode);
}
