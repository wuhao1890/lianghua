package com.stock.trade.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountOverview {

    /** 总资产 */
    private BigDecimal totalAssets;

    /** 初始资金 */
    private BigDecimal initialCapital;

    /** 可用资金 */
    private BigDecimal availableCash;

    /** 持仓市值 */
    private BigDecimal positionValue;

    /** 总盈亏 */
    private BigDecimal totalProfitLoss;

    /** 总收益率(%) */
    private BigDecimal totalProfitLossPercent;

    /** 当日盈亏 */
    private BigDecimal todayProfitLoss;

    /** 持仓数量 */
    private Integer positionCount;
}
