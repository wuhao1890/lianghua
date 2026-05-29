package com.stock.trade.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用于trade模块内部引用的StockDTO（与stock模块结构一致）
 */
@Data
public class StockDTO {

    private String code;

    private String name;

    private String market;

    private BigDecimal currentPrice;

    private BigDecimal changePercent;

    private Long volume;

    private BigDecimal turnover;

    private BigDecimal highPrice;

    private BigDecimal lowPrice;

    private BigDecimal openPrice;

    private BigDecimal prevClose;
}
