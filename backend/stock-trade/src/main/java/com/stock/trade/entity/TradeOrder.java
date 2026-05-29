package com.stock.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("trade_order")
public class TradeOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String stockCode;

    private String stockName;

    private String market;

    /** 买卖方向: BUY / SELL */
    private String direction;

    /** 订单类型: MARKET / LIMIT */
    private String orderType;

    /** 委托价格 */
    private BigDecimal price;

    /** 委托数量 */
    private Integer quantity;

    /** 委托金额 */
    private BigDecimal amount;

    /** 订单状态: PENDING / FILLED / CANCELLED */
    private String status;

    /** 手续费 */
    private BigDecimal fee;

    /** 止损价 */
    private BigDecimal stopLoss;

    /** 止盈价 */
    private BigDecimal takeProfit;

    /** 是否为模拟交易: true/false */
    private Boolean paperTrade;

    private LocalDateTime filledTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
