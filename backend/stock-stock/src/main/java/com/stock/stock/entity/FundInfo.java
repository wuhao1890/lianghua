package com.stock.stock.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fund_info")
public class FundInfo {

    /** 基金代码 */
    @TableId
    private String code;

    /** 基金名称 */
    private String name;

    /** 单位净值 */
    private BigDecimal nav;

    /** 累计净值 */
    private BigDecimal accNav;

    /** 净值日期 */
    private LocalDate navDate;

    /** 涨跌幅(%) */
    private BigDecimal changePercent;

    /** 基金类型 */
    private String fundType;

    /** 创建时间 */
    private LocalDateTime createTime;
}
