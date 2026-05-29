package com.stock.stock.service;

import com.stock.stock.dto.BacktestResult;

public interface BacktestService {

    /**
     * 执行回测
     * @param stockCode 股票代码
     * @param strategy 策略类型: ma_cross, buy_hold
     * @param shortPeriod 短周期
     * @param longPeriod 长周期
     * @param startDate 开始日期 yyyy-MM-dd
     * @param endDate 结束日期 yyyy-MM-dd
     * @param initialCapital 初始资金
     */
    BacktestResult runBacktest(String stockCode, String strategy,
                               int shortPeriod, int longPeriod,
                               String startDate, String endDate,
                               java.math.BigDecimal initialCapital);
}
