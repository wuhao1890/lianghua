package com.stock.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("trade_log")
public class TradeLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long userId;

    private String stockCode;

    /** 操作: BUY / SELL */
    private String action;

    /** 成交价格 */
    private BigDecimal price;

    /** 成交数量 */
    private Integer quantity;

    /** 成交金额 */
    private BigDecimal amount;

    /** 盈亏 */
    private BigDecimal profitLoss;

    /** 手续费 */
    private BigDecimal fee;

    private LocalDateTime createTime;
}
