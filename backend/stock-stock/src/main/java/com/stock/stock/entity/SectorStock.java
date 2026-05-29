package com.stock.stock.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("sector_stock")
public class SectorStock {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 板块代码 */
    private String sectorCode;

    /** 股票代码 */
    private String stockCode;

    /** 股票名称 */
    private String stockName;

    /** 市值 */
    private BigDecimal marketCap;
}
