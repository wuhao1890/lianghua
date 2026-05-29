package com.stock.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stock.stock.dto.BacktestResult;
import com.stock.stock.dto.BacktestResult.*;
import com.stock.stock.entity.StockDaily;
import com.stock.stock.entity.StockInfo;
import com.stock.stock.mapper.StockDailyMapper;
import com.stock.stock.mapper.StockInfoMapper;
import com.stock.stock.service.BacktestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
public class BacktestServiceImpl implements BacktestService {

    private final StockDailyMapper stockDailyMapper;
    private final StockInfoMapper stockInfoMapper;

    public BacktestServiceImpl(StockDailyMapper stockDailyMapper, StockInfoMapper stockInfoMapper) {
        this.stockDailyMapper = stockDailyMapper;
        this.stockInfoMapper = stockInfoMapper;
    }

    @Override
    public BacktestResult runBacktest(String stockCode, String strategy,
                                       int shortPeriod, int longPeriod,
                                       String startDate, String endDate,
                                       BigDecimal initialCapital) {
        BacktestResult result = new BacktestResult();

        // 1. 参数设置
        BacktestParams params = new BacktestParams();
        params.setStockCode(stockCode);

        // 查股票名称
        StockInfo info = stockInfoMapper.selectOne(
                new LambdaQueryWrapper<StockInfo>().eq(StockInfo::getCode, stockCode));
        params.setStockName(info != null ? info.getName() : stockCode);

        params.setStrategy(strategy != null ? strategy : "ma_cross");
        params.setShortPeriod(shortPeriod > 0 ? shortPeriod : 5);
        params.setLongPeriod(longPeriod > 0 ? longPeriod : 20);
        params.setStartDate(startDate);
        params.setEndDate(endDate);
        params.setInitialCapital(initialCapital != null && initialCapital.compareTo(BigDecimal.ZERO) > 0
                ? initialCapital : new BigDecimal("100000"));
        result.setParams(params);

        // 2. 获取K线数据
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LambdaQueryWrapper<StockDaily> wrapper = new LambdaQueryWrapper<StockDaily>()
                .eq(StockDaily::getStockCode, stockCode)
                .orderByAsc(StockDaily::getTradeDate);

        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(StockDaily::getTradeDate, LocalDate.parse(startDate, fmt));
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(StockDaily::getTradeDate, LocalDate.parse(endDate, fmt));
        }

        List<StockDaily> dailyList = stockDailyMapper.selectList(wrapper);
        if (dailyList == null || dailyList.size() < longPeriod) {
            log.warn("回测数据不足: {} 仅 {} 条, 需要至少 {} 条", stockCode,
                    dailyList == null ? 0 : dailyList.size(), longPeriod);
            BacktestStats emptyStats = new BacktestStats();
            emptyStats.setTotalReturn(BigDecimal.ZERO);
            emptyStats.setTotalTrades(0);
            result.setStats(emptyStats);
            result.setTrades(new ArrayList<>());
            result.setEquityCurve(new ArrayList<>());
            return result;
        }

        // 3. 执行策略
        List<BacktestTrade> trades = new ArrayList<>();
        List<EquityPoint> equityCurve = new ArrayList<>();

        BigDecimal cash = params.getInitialCapital();
        BigDecimal shares = BigDecimal.ZERO;  // 持仓股数
        boolean holding = false;
        BigDecimal buyPrice = BigDecimal.ZERO;
        String buyDate = null;

        // 计算每日MA值（如果表中没有则实时计算）
        List<BigDecimal> closes = new ArrayList<>();
        for (StockDaily d : dailyList) {
            closes.add(d.getClosePrice());
        }

        for (int i = longPeriod - 1; i < dailyList.size(); i++) {
            StockDaily today = dailyList.get(i);
            String dateStr = today.getTradeDate().format(fmt);
            BigDecimal closePrice = today.getClosePrice();

            // 计算短周期MA
            BigDecimal shortMA = calcMA(closes, i, params.getShortPeriod());
            // 计算长周期MA
            BigDecimal longMA = calcMA(closes, i, params.getLongPeriod());

            if ("buy_hold".equals(strategy)) {
                // 买入持有策略：第一天全仓买入，最后一天卖出
                if (i == longPeriod - 1) {
                    // 买入
                    BigDecimal affordable = cash.divide(closePrice, 0, RoundingMode.DOWN);
                    shares = affordable;
                    cash = cash.subtract(affordable.multiply(closePrice));
                    holding = true;
                    buyPrice = closePrice;
                    buyDate = dateStr;

                    BacktestTrade trade = new BacktestTrade();
                    trade.setBuyDate(dateStr);
                    trade.setBuyPrice(closePrice);
                    trades.add(trade);
                }
            } else {
                // MA交叉策略
                BigDecimal prevShortMA = calcMA(closes, i - 1, params.getShortPeriod());
                BigDecimal prevLongMA = calcMA(closes, i - 1, params.getLongPeriod());

                if (prevShortMA != null && prevLongMA != null) {
                    // 金叉：短线上穿长线 → 买入
                    if (!holding && prevShortMA.compareTo(prevLongMA) <= 0
                            && shortMA.compareTo(longMA) > 0) {
                        BigDecimal affordable = cash.divide(closePrice, 0, RoundingMode.DOWN);
                        if (affordable.compareTo(BigDecimal.ZERO) > 0) {
                            shares = affordable;
                            cash = cash.subtract(affordable.multiply(closePrice));
                            holding = true;
                            buyPrice = closePrice;
                            buyDate = dateStr;

                            BacktestTrade trade = new BacktestTrade();
                            trade.setBuyDate(dateStr);
                            trade.setBuyPrice(closePrice);
                            trades.add(trade);
                        }
                    }
                    // 死叉：短线下穿长线 → 卖出
                    else if (holding && prevShortMA.compareTo(prevLongMA) >= 0
                            && shortMA.compareTo(longMA) < 0) {
                        BigDecimal sellValue = shares.multiply(closePrice);
                        cash = cash.add(sellValue);
                        BigDecimal profit = sellValue.subtract(shares.multiply(buyPrice));
                        BigDecimal profitPct = buyPrice.compareTo(BigDecimal.ZERO) > 0
                                ? (closePrice.subtract(buyPrice)).divide(buyPrice, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                                : BigDecimal.ZERO;

                        BacktestTrade lastTrade = trades.get(trades.size() - 1);
                        lastTrade.setSellDate(dateStr);
                        lastTrade.setSellPrice(closePrice);
                        lastTrade.setProfit(profit);
                        lastTrade.setProfitPercent(profitPct);

                        shares = BigDecimal.ZERO;
                        holding = false;
                        buyPrice = BigDecimal.ZERO;
                    }
                }
            }

            // 计算当日总资产
            BigDecimal totalEquity = cash.add(shares.multiply(closePrice));
            EquityPoint ep = new EquityPoint();
            ep.setDate(dateStr);
            ep.setEquity(totalEquity);
            equityCurve.add(ep);
        }

        // 最后一天强制平仓（如果还有持仓）
        if (holding && dailyList.size() > 0) {
            StockDaily last = dailyList.get(dailyList.size() - 1);
            BigDecimal closePrice = last.getClosePrice();
            BigDecimal sellValue = shares.multiply(closePrice);
            cash = cash.add(sellValue);
            BigDecimal profit = sellValue.subtract(shares.multiply(buyPrice));
            BigDecimal profitPct = buyPrice.compareTo(BigDecimal.ZERO) > 0
                    ? (closePrice.subtract(buyPrice)).divide(buyPrice, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                    : BigDecimal.ZERO;

            if (!trades.isEmpty()) {
                BacktestTrade lastTrade = trades.get(trades.size() - 1);
                if (lastTrade.getSellDate() == null) {
                    lastTrade.setSellDate(last.getTradeDate().format(fmt));
                    lastTrade.setSellPrice(closePrice);
                    lastTrade.setProfit(profit);
                    lastTrade.setProfitPercent(profitPct);
                }
            }

            BigDecimal finalEquity = cash;
            if (!equityCurve.isEmpty()) {
                equityCurve.get(equityCurve.size() - 1).setEquity(finalEquity);
            }
        }

        result.setTrades(trades);
        result.setEquityCurve(equityCurve);

        // 4. 计算统计指标
        BacktestStats stats = calculateStats(trades, equityCurve, params);
        result.setStats(stats);

        return result;
    }

    /** 计算移动平均 */
    private BigDecimal calcMA(List<BigDecimal> closes, int index, int period) {
        if (index < period - 1) return null;
        BigDecimal sum = BigDecimal.ZERO;
        for (int j = index - period + 1; j <= index; j++) {
            sum = sum.add(closes.get(j));
        }
        return sum.divide(new BigDecimal(period), 4, RoundingMode.HALF_UP);
    }

    /** 计算回测统计指标 */
    private BacktestStats calculateStats(List<BacktestTrade> trades,
                                          List<EquityPoint> equityCurve,
                                          BacktestParams params) {
        BacktestStats stats = new BacktestStats();

        if (equityCurve.isEmpty()) {
            stats.setTotalReturn(BigDecimal.ZERO);
            stats.setTotalTrades(0);
            stats.setFinalCapital(params.getInitialCapital());
            return stats;
        }

        // 最终资产
        BigDecimal finalEquity = equityCurve.get(equityCurve.size() - 1).getEquity();
        stats.setFinalCapital(finalEquity);

        // 总收益率
        BigDecimal totalReturn = params.getInitialCapital().compareTo(BigDecimal.ZERO) > 0
                ? (finalEquity.subtract(params.getInitialCapital()))
                .divide(params.getInitialCapital(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;
        stats.setTotalReturn(totalReturn);

        // 总交易次数
        int completedTrades = 0;
        for (BacktestTrade t : trades) {
            if (t.getSellDate() != null) completedTrades++;
        }
        stats.setTotalTrades(completedTrades);

        // 胜率和盈亏比
        if (completedTrades > 0) {
            int wins = 0;
            BigDecimal totalProfit = BigDecimal.ZERO;
            BigDecimal totalLoss = BigDecimal.ZERO;
            int lossCount = 0;
            for (BacktestTrade t : trades) {
                if (t.getSellDate() != null && t.getProfit() != null) {
                    if (t.getProfit().compareTo(BigDecimal.ZERO) > 0) {
                        wins++;
                        totalProfit = totalProfit.add(t.getProfit());
                    } else {
                        totalLoss = totalLoss.add(t.getProfit().abs());
                        lossCount++;
                    }
                }
            }
            stats.setWinRate(new BigDecimal(wins).divide(new BigDecimal(completedTrades), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")));

            if (lossCount > 0 && totalLoss.compareTo(BigDecimal.ZERO) > 0) {
                stats.setProfitLossRatio(totalProfit.divide(totalLoss, 4, RoundingMode.HALF_UP));
            } else if (lossCount == 0 && completedTrades > 0) {
                stats.setProfitLossRatio(new BigDecimal("999"));
            } else {
                stats.setProfitLossRatio(BigDecimal.ZERO);
            }
        } else {
            stats.setWinRate(BigDecimal.ZERO);
            stats.setProfitLossRatio(BigDecimal.ZERO);
        }

        // 最大回撤
        BigDecimal peak = params.getInitialCapital();
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (EquityPoint ep : equityCurve) {
            if (ep.getEquity().compareTo(peak) > 0) {
                peak = ep.getEquity();
            }
            BigDecimal dd = peak.subtract(ep.getEquity());
            if (dd.compareTo(maxDrawdown) > 0) {
                maxDrawdown = dd;
            }
        }
        stats.setMaxDrawdown(peak.compareTo(BigDecimal.ZERO) > 0
                ? maxDrawdown.divide(peak, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO);

        // 在 equityCurve 中填充回撤数据
        BigDecimal p = params.getInitialCapital();
        for (EquityPoint ep : equityCurve) {
            if (ep.getEquity().compareTo(p) > 0) p = ep.getEquity();
            BigDecimal dd = p.compareTo(BigDecimal.ZERO) > 0
                    ? p.subtract(ep.getEquity()) : BigDecimal.ZERO;
            ep.setDrawdown(dd);
            ep.setDrawdownPercent(p.compareTo(BigDecimal.ZERO) > 0
                    ? dd.divide(p, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                    : BigDecimal.ZERO);
        }

        // 年化收益率
        if (params.getStartDate() != null && params.getEndDate() != null) {
            try {
                LocalDate sd = LocalDate.parse(params.getStartDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                LocalDate ed = LocalDate.parse(params.getEndDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                long days = ChronoUnit.DAYS.between(sd, ed);
                if (days > 0) {
                    double totalReturnPct = totalReturn.doubleValue() / 100.0;
                    double years = days / 365.0;
                    if (years > 0 && totalReturnPct > -1) {
                        double annualized = Math.pow(1 + totalReturnPct, 1.0 / years) - 1;
                        stats.setAnnualizedReturn(new BigDecimal(annualized * 100)
                                .setScale(2, RoundingMode.HALF_UP));
                    }
                }
            } catch (Exception ignored) {}
        }
        if (stats.getAnnualizedReturn() == null) {
            stats.setAnnualizedReturn(totalReturn);
        }

        // 夏普比率（简化版：平均收益率 / 收益率标准差）
        if (equityCurve.size() > 1) {
            List<BigDecimal> dailyReturns = new ArrayList<>();
            for (int i = 1; i < equityCurve.size(); i++) {
                BigDecimal prev = equityCurve.get(i - 1).getEquity();
                BigDecimal curr = equityCurve.get(i).getEquity();
                if (prev.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal ret = (curr.subtract(prev)).divide(prev, 6, RoundingMode.HALF_UP);
                    dailyReturns.add(ret);
                }
            }
            if (!dailyReturns.isEmpty()) {
                BigDecimal mean = dailyReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(new BigDecimal(dailyReturns.size()), 6, RoundingMode.HALF_UP);
                BigDecimal variance = BigDecimal.ZERO;
                for (BigDecimal r : dailyReturns) {
                    BigDecimal diff = r.subtract(mean);
                    variance = variance.add(diff.multiply(diff));
                }
                variance = variance.divide(new BigDecimal(dailyReturns.size()), 10, RoundingMode.HALF_UP);
                BigDecimal std = new BigDecimal(Math.sqrt(variance.doubleValue()));
                if (std.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal riskFree = new BigDecimal("0.0002"); // 日化无风险利率 ~3% 年化
                    BigDecimal sharpe = (mean.subtract(riskFree)).divide(std, 4, RoundingMode.HALF_UP);
                    stats.setSharpeRatio(sharpe);
                }
            }
        }
        if (stats.getSharpeRatio() == null) {
            stats.setSharpeRatio(BigDecimal.ZERO);
        }

        return stats;
    }
}
