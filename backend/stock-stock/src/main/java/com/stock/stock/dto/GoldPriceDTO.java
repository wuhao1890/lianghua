package com.stock.stock.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GoldPriceDTO {

    /** 产品代码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 当前价格 */
    private BigDecimal price;

    /** 涨跌幅(%) */
    private BigDecimal changePercent;

    /** 最高价 */
    private BigDecimal high;

    /** 最低价 */
    private BigDecimal low;

    /** 开盘价 */
    private BigDecimal openPrice;

    /** 交易日期 (yyyy-MM-dd) */
    private String tradeDate;
}
