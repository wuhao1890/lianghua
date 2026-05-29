package com.stock.stock.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("stock_daily")
public class StockDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 股票代码 */
    private String stockCode;

    /** 交易日期 */
    private LocalDate tradeDate;

    /** 开盘价 */
    private BigDecimal openPrice;

    /** 最高价 */
    private BigDecimal highPrice;

    /** 最低价 */
    private BigDecimal lowPrice;

    /** 收盘价 */
    private BigDecimal closePrice;

    /** 成交量 */
    private Long volume;

    /** 成交额 */
    private BigDecimal turnover;

    /** 5日均线 */
    private BigDecimal ma5;

    /** 10日均线 */
    private BigDecimal ma10;

    /** 20日均线 */
    private BigDecimal ma20;

    /** 60日均线 */
    private BigDecimal ma60;

    /** 昨收价 */
    private BigDecimal prevClose;
}
