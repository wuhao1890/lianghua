package com.stock.stock.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class KlineData {

    /** 交易日期 */
    private String date;

    /** 开盘价 */
    private BigDecimal open;

    /** 最高价 */
    private BigDecimal high;

    /** 最低价 */
    private BigDecimal low;

    /** 收盘价 */
    private BigDecimal close;

    /** 成交量 */
    private Long volume;

    /** 成交额 */
    private BigDecimal turnover;

    /** 涨跌幅 */
    private BigDecimal changePercent;
}
