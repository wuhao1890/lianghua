package com.stock.analysis.service;

import com.stock.analysis.dto.*;

import java.util.List;
import java.util.Map;

public interface TechnicalAnalysisService {

    /**
     * 计算MA均线
     */
    IndicatorResult calculateMA(String stockCode, int... periods);

    /**
     * 计算MACD
     */
    IndicatorResult calculateMACD(String stockCode, int shortPeriod, int longPeriod, int signalPeriod);

    /**
     * 计算RSI
     */
    IndicatorResult calculateRSI(String stockCode, int... periods);

    /**
     * 计算KDJ
     */
    IndicatorResult calculateKDJ(String stockCode, int n, int m1, int m2);

    /**
     * 计算布林带
     */
    IndicatorResult calculateBOLL(String stockCode, int period, int multiplier);

    /**
     * 获取指定类型的指标
     */
    List<IndicatorResult> getIndicators(String stockCode, String types);

    /**
     * 生成综合买卖信号
     */
    TradeSignal generateSignal(String stockCode);

    /**
     * 回测策略
     */
    BacktestResult backtest(String stockCode, String strategy, Map<String, Integer> params);
}
