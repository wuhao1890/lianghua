package com.stock.trade.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProfitAnalysis {

    /** 总收益 */
    private BigDecimal totalProfit;

    /** 总收益率(%) */
    private BigDecimal totalProfitPercent;

    /** 日收益 */
    private BigDecimal dailyProfit;

    /** 日收益率(%) */
    private BigDecimal dailyProfitPercent;

    /** 周收益 */
    private BigDecimal weeklyProfit;

    /** 周收益率(%) */
    private BigDecimal weeklyProfitPercent;

    /** 月收益 */
    private BigDecimal monthlyProfit;

    /** 月收益率(%) */
    private BigDecimal monthlyProfitPercent;

    /** 累计交易次数 */
    private Integer totalTrades;

    /** 盈利次数 */
    private Integer winTrades;

    /** 亏损次数 */
    private Integer loseTrades;

    /** 胜率(%) */
    private BigDecimal winRate;

    /** 累计手续费 */
    private BigDecimal totalFee;
}
