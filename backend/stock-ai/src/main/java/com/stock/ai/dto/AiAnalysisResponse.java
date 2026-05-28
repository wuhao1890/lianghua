package com.stock.ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class AiAnalysisResponse {

    private String stockCode;

    private String stockName;

    private String signal;        // BUY/SELL/HOLD

    private BigDecimal score;     // 综合评分 0-100

    private BigDecimal techScore;       // 技术面评分占比50%

    private BigDecimal sentimentScore;  // 大V舆情评分占比50%

    private String targetPrice;   // 目标价

    private String analysis;      // 详细Markdown分析内容

    private String modelUsed;     // 使用的模型

    // 大V舆情模块
    private List<DaVOpinion> daVOpinions; // 各"大V"观点列表
    private DaVMajorityConsensus daVMajority; // 多数意见综合

    @Data
    public static class DaVOpinion {
        private String name;        // 大V名称
        private String type;        // bullish/bearish/neutral
        private String view;        // 观点摘要
        private String detail;      // 详细论述
        private int influence;      // 影响力权重 1-10
        private String publishTime; // 发布时间 MM-DD HH:mm
    }

    @Data
    public static class DaVMajorityConsensus {
        private String consensus;   // 多数意见: bullish/bearish/neutral
        private String summary;     // 多数观点总结
        private int bullishCount;   // 看多人数
        private int bearishCount;   // 看空人数
        private int neutralCount;   // 中性人数
    }
}
