package com.stock.stock.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SectorDTO {

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

    /** 龙头股涨跌幅(%) */
    private BigDecimal leaderChangePercent;
}
