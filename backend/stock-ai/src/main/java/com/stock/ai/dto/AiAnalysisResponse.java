package com.stock.ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AiAnalysisResponse {

    private String stockCode;
    private String stockName;
    private String signal;
    private BigDecimal score;
    private BigDecimal techScore;
    private BigDecimal sentimentScore;
    private String targetPrice;
    private String analysis;
    private String modelUsed;

    private QuantDecision quantDecision;
    private List<FactorScore> factors;
    private List<Scenario> scenarios;
    private List<String> risks;
    private List<String> actions;
    private Boolean modelAvailable;
    private String failureReason;

    private List<DaVOpinion> daVOpinions;
    private DaVMajorityConsensus daVMajority;
    private List<NewsItem> newsItems;
    private List<CandidateStrategy> candidateStrategies;
    private CandidateStrategy selectedStrategy;
    private StrategyEvolution evolution;

    @Data
    public static class QuantDecision {
        private String signal;
        private Integer confidence;
        private String riskLevel;
        private String trendState;
        private String suggestedPosition;
        private BigDecimal stopLoss;
        private BigDecimal takeProfit;
        private String targetRange;
        private String summary;
    }

    @Data
    public static class FactorScore {
        private String name;
        private Integer score;
        private String direction;
        private Integer weight;
        private String reason;
    }

    @Data
    public static class Scenario {
        private String name;
        private Integer probability;
        private String trigger;
        private String action;
    }

    @Data
    public static class DaVOpinion {
        private String name;
        private String type;
        private String view;
        private String detail;
        private int influence;
        private String publishTime;
    }

    @Data
    public static class DaVMajorityConsensus {
        private String consensus;
        private String summary;
        private int bullishCount;
        private int bearishCount;
        private int neutralCount;
    }

    @Data
    public static class NewsItem {
        private String title;
        private String source;
        private String url;
        private String publishTime;
        private String sentiment;
        private Integer impactScore;
        private String reason;
    }

    @Data
    public static class CandidateStrategy {
        private String name;
        private String style;
        private String signal;
        private Integer score;
        private Integer expectedReturnScore;
        private Integer riskScore;
        private Integer sentimentFitScore;
        private String suggestedPosition;
        private String entryRule;
        private String exitRule;
        private String stopLossRule;
        private String takeProfitRule;
        private String evaluationRule;
        private String rationale;
    }

    @Data
    public static class StrategyEvolution {
        private Integer generation;
        private String status;
        private String lastLearning;
        private String nextMutation;
        private String outcomeJudgement;
        private Integer historySamples;
    }
}
