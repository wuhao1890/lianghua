package com.stock.stock.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FundInfoDTO {

    /** 基金代码 */
    private String code;

    /** 基金名称 */
    private String name;

    /** 单位净值 */
    private BigDecimal nav;

    /** 累计净值 */
    private BigDecimal accNav;

    /** 净值日期 (yyyy-MM-dd) */
    private String navDate;

    /** 涨跌幅(%) */
    private BigDecimal changePercent;

    /** 基金类型 */
    private String fundType;
}
