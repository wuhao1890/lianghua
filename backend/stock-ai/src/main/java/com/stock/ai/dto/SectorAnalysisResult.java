package com.stock.ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SectorAnalysisResult {

    /** 分析时间 */
    private String analyzeTime;

    /** 排名前5的板块 */
    private List<TopSector> topSectors;

    @Data
    public static class TopSector {
        /** 板块名称 */
        private String sectorName;

        /** 板块代码 */
        private String sectorCode;

        /** 涨跌幅 */
        private BigDecimal changePercent;

        /** AI推荐理由 */
        private String aiReason;

        /** 龙头股列表 */
        private List<LeaderStock> leaderStocks;
    }

    @Data
    public static class LeaderStock {
        /** 股票代码 */
        private String code;

        /** 股票名称 */
        private String name;

        /** 涨跌幅 */
        private BigDecimal changePercent;

        /** AI趋势判断：看涨/看空/中性 */
        private String aiTrend;

        /** AI判断理由 */
        private String aiReason;
    }
}
