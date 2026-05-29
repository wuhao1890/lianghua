package com.stock.trade.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Data
public class OrderRequest {

    @NotBlank(message = "股票代码不能为空")
    private String stockCode;

    private String stockName;

    private String market;

    /** 买卖方向: BUY / SELL */
    @NotBlank(message = "买卖方向不能为空")
    private String direction;

    /** 订单类型: MARKET / LIMIT */
    private String orderType = "MARKET";

    /** 委托价格(限价单必填) */
    private BigDecimal price;

    @Min(value = 100, message = "数量至少为100股")
    private Integer quantity;

    /** 止损价 */
    private BigDecimal stopLoss;

    /** 止盈价 */
    private BigDecimal takeProfit;
}
