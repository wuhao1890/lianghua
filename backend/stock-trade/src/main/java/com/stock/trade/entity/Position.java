package com.stock.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("position")
public class Position {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String stockCode;

    private String stockName;

    private String market;

    /** 持仓数量 */
    private Integer quantity;

    /** 平均成本 */
    private BigDecimal avgCost;

    /** 当前价格 */
    private BigDecimal currentPrice;

    /** 浮动盈亏 */
    private BigDecimal profitLoss;

    /** 收益率(%) */
    private BigDecimal profitLossPercent;

    private LocalDateTime updateTime;
}
