package com.stock.stock.dto;

import lombok.Data;

import java.math.BigDecimal;

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
