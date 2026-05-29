package com.stock.stock.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 回测结果
 */
@Data
public class BacktestResult {

    /** 回测参数 */
    private BacktestParams params;

    /** 汇总统计 */
    private BacktestStats stats;

    /** 交易记录列表 */
    private List<BacktestTrade> trades;

    /** 每日权益曲线 */
    private List<EquityPoint> equityCurve;

    @Data
    public static class BacktestParams {
        private String stockCode;
        private String stockName;
        private String strategy;       // ma_cross, buy_hold
        private int shortPeriod;
        private int longPeriod;
        private String startDate;
        private String endDate;
        private BigDecimal initialCapital;
    }

    @Data
    public static class BacktestStats {
        private BigDecimal totalReturn;       // 总收益率
        private BigDecimal annualizedReturn;  // 年化收益率
        private BigDecimal maxDrawdown;       // 最大回撤
        private BigDecimal winRate;           // 胜率
        private BigDecimal profitLossRatio;   // 盈亏比
        private int totalTrades;              // 总交易次数
        private BigDecimal sharpeRatio;       // 夏普比率
        private BigDecimal finalCapital;      // 最终资产
    }

    @Data
    public static class BacktestTrade {
        private String buyDate;
        private BigDecimal buyPrice;
        private String sellDate;
        private BigDecimal sellPrice;
        private BigDecimal profit;         // 盈亏金额
        private BigDecimal profitPercent;  // 盈亏百分比
    }

    @Data
    public static class EquityPoint {
        private String date;
        private BigDecimal equity;
        private BigDecimal drawdown;
        private BigDecimal drawdownPercent;
    }
}
