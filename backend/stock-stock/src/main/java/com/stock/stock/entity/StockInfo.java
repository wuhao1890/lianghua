package com.stock.stock.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("stock_info")
public class StockInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 股票代码 */
    private String code;

    /** 股票名称 */
    private String name;

    /** 市场: A_STOCK / NASDAQ */
    private String market;

    /** 当前价格 */
    private BigDecimal currentPrice;

    /** 涨跌幅(%) */
    private BigDecimal changePercent;

    /** 成交量 */
    private Long volume;

    /** 成交额 */
    private BigDecimal turnover;

    /** 最高价 */
    private BigDecimal highPrice;

    /** 最低价 */
    private BigDecimal lowPrice;

    /** 开盘价 */
    private BigDecimal openPrice;

    /** 昨收价 */
    private BigDecimal prevClose;

    /** 总市值 */
    private BigDecimal marketCap;

    private LocalDateTime updateTime;
}
