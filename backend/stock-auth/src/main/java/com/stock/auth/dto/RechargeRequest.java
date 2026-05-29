package com.stock.auth.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

@Data
public class RechargeRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "充值金额不能为空")
    @Positive(message = "充值金额必须大于0")
    private BigDecimal amount;

    private String remark;
}
