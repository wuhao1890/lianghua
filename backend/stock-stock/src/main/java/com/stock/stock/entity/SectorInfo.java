package com.stock.stock.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sector_info")
public class SectorInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 板块名称 */
    private String sectorName;

    /** 板块代码 */
    private String sectorCode;

    /** 涨跌幅(%) */
    private BigDecimal changePercent;

    /** 龙头股代码 */
    private String leaderStock;

    /** 龙头股名称 */
    private String leaderName;

    /** 成分股数量 */
    private Integer stockCount;

    /** 平均涨跌幅(%) */
    private BigDecimal avgChange;

    private LocalDateTime updateTime;
}
