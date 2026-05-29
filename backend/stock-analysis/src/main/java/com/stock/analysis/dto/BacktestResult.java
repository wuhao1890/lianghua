package com.stock.analysis.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BacktestResult {

    /** 策略名称 */
    private String strategy;

    /** 股票代码 */
    private String stockCode;

    /** 初始资金 */
    private BigDecimal initialCapital;

    /** 最终资金 */
    private BigDecimal finalCapital;

    /** 总收益率(%) */
    private BigDecimal totalReturn;

    /** 年化收益率(%) */
    private BigDecimal annualizedReturn;

    /** 最大回撤(%) */
    private BigDecimal maxDrawdown;

    /** 夏普比率 */
    private BigDecimal sharpeRatio;

    /** 胜率(%) */
    private BigDecimal winRate;

    /** 总交易次数 */
    private Integer totalTrades;

    /** 盈利次数 */
    private Integer winTrades;

    /** 亏损次数 */
    private Integer loseTrades;

    /** 交易记录 */
    private List<BacktestTrade> trades;

    /** 资金曲线 */
    private List<BigDecimal> equityCurve;
}
