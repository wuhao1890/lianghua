package com.stock.trade.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PositionDTO {

    private Long id;

    private String stockCode;

    private String stockName;

    private String market;

    private Integer quantity;

    private BigDecimal avgCost;

    private BigDecimal currentPrice;

    private BigDecimal profitLoss;

    private BigDecimal profitLossPercent;

    private BigDecimal marketValue;

    private BigDecimal costAmount;
}
