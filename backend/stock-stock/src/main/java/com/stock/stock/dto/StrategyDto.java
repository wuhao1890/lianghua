package com.stock.stock.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 策略定义和执行结果
 */
@Data
public class StrategyDto {

    /** 策略ID */
    private String id;

    /** 策略名称 */
    private String name;

    /** 策略类型: simple_condition / js_script */
    private String type;

    /** 股票代码 */
    private String stockCode;

    /** 股票名称 */
    private String stockName;

    /** 条件列表（simple_condition模式） */
    private List<Condition> conditions;

    /** JS脚本（js_script模式） */
    private String script;

    /** 运行模式: backtest / live */
    private String mode;

    /** 回测参数 */
    private BacktestConfig backtestConfig;

    /** 运行状态: IDLE / RUNNING / PAUSED / STOPPED / ERROR */
    private String status;

    /** 执行结果 */
    private StrategyResult result;

    @Data
    public static class Condition {
        /** 指标: MA5 / MA10 / MA20 / MA60 / RSI / MACD / KDJ_K / KDJ_D / VOLUME / PRICE */
        private String indicator;
        /** 比较符: > / < / >= / <= / == / CROSS_ABOVE / CROSS_BELOW */
        private String operator;
        /** 值 */
        private String value;
        /** 连接符: AND / OR */
        private String connector;
    }

    @Data
    public static class BacktestConfig {
        private String startDate;
        private String endDate;
        private BigDecimal initialCapital;
    }

    @Data
    public static class StrategyResult {
        private BigDecimal totalReturn;
        private BigDecimal annualizedReturn;
        private BigDecimal maxDrawdown;
        private BigDecimal winRate;
        private BigDecimal sharpeRatio;
        private int totalTrades;
        private int winCount;
        private int lossCount;
        private BigDecimal finalCapital;
        private String message;
    }
}
