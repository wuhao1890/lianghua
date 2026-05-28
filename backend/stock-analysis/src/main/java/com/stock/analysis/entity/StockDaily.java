package com.stock.analysis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("stock_daily")
public class StockDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String stockCode;

    private LocalDate tradeDate;

    private BigDecimal openPrice;

    private BigDecimal highPrice;

    private BigDecimal lowPrice;

    private BigDecimal closePrice;

    private Long volume;

    private BigDecimal turnover;

    private BigDecimal ma5;

    private BigDecimal ma10;

    private BigDecimal ma20;

    private BigDecimal ma60;
}
