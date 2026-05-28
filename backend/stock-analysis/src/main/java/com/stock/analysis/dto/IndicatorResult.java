package com.stock.analysis.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class IndicatorResult {

    /** 股票代码 */
    private String stockCode;

    /** 指标类型 */
    private String indicatorType;

    /** 指标数据列表（日期 -> 值的映射） */
    private List<IndicatorData> data;

    @Data
    public static class IndicatorData {
        private String date;
        private Map<String, BigDecimal> values;

        public IndicatorData() {
        }

        public IndicatorData(String date, Map<String, BigDecimal> values) {
            this.date = date;
            this.values = values;
        }
    }
}
