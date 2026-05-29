package com.stock.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stock.trade.dto.*;
import com.stock.trade.entity.Position;
import com.stock.trade.entity.TradeOrder;

import java.math.BigDecimal;
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

    /**
     * 增强下单（含止损止盈）
     */
    TradeOrder placeOrder(Long userId, OrderRequest request);

    /**
     * 检查止损止盈是否触发
     */
    List<TradeOrder> checkStopConditions(Long userId, Long orderId, BigDecimal currentPrice);

    /**
     * 获取交易模式
     */
    String getTradeMode(Long userId);

    /**
     * 切换交易模式
     */
    String switchTradeMode(Long userId, String mode);

    /**
     * 获取模拟交易账户概览
     */
    AccountOverview getPaperAccountOverview(Long userId);
}
