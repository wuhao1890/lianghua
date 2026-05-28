package com.stock.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stock.trade.dto.*;
import com.stock.trade.entity.Position;
import com.stock.trade.entity.TradeOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface TradeService extends IService<TradeOrder> {

    /**
     * 买入股票
     */
    TradeOrder buy(Long userId, BuyRequest request);

    /**
     * 卖出股票
     */
    TradeOrder sell(Long userId, SellRequest request);

    /**
     * 查询持仓列表
     */
    List<PositionDTO> getPositions(Long userId);

    /**
     * 查询交易记录
     */
    List<TradeOrder> getOrders(Long userId, String status, String direction, LocalDate startDate, LocalDate endDate);

    /**
     * 撤单
     */
    boolean cancelOrder(Long userId, Long orderId);

    /**
     * 获取账户资产概览
     */
    AccountOverview getAccountOverview(Long userId);

    /**
     * 收益分析
     */
    ProfitAnalysis getProfitAnalysis(Long userId);

    /**
     * 获取收益记录
     */
    List<Map<String, Object>> getProfitRecords(Long userId, String range);
}
