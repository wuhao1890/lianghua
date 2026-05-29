package com.stock.analysis.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class TradeSignal {

    /** 股票代码 */
    private String stockCode;

    /** 综合信号: BUY / SELL / HOLD */
    private String signal;

    /** 信号强度: 1-5 */
    private Integer strength;

    /** 信号描述 */
    private String description;

    /** 各指标信号详情 */
    private Map<String, String> indicatorSignals;

    /** 当前价格 */
    private BigDecimal currentPrice;

    /** 各指标值 */
    private Map<String, Object> indicatorValues;

    /** 生成时间 */
    private String timestamp;
}
