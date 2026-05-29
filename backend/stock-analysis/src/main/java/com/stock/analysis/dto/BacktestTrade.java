package com.stock.analysis.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BacktestTrade {

    /** 交易日期 */
    private String date;

    /** 买卖方向: BUY / SELL */
    private String direction;

    /** 价格 */
    private BigDecimal price;

    /** 数量 */
    private Integer quantity;

    /** 金额 */
    private BigDecimal amount;

    /** 盈亏 */
    private BigDecimal profitLoss;

    /** 收益率(%) */
    private BigDecimal profitLossPercent;
}
