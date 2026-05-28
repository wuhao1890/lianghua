package com.stock.stock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("gold_price")
public class GoldPrice {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 当前价格 */
    private BigDecimal price;

    /** 涨跌幅(%) */
    private BigDecimal changePercent;

    /** 最高价 */
    private BigDecimal high;

    /** 最低价 */
    private BigDecimal low;

    /** 开盘价 */
    private BigDecimal openPrice;

    /** 交易日期 */
    private LocalDate tradeDate;

    /** 创建时间 */
    private LocalDateTime createTime;
}
