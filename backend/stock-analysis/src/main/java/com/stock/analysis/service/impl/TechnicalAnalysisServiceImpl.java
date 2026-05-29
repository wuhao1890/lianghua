package com.stock.analysis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stock.analysis.dto.*;
import com.stock.analysis.entity.StockDaily;
import com.stock.analysis.mapper.StockDailyMapper;
import com.stock.analysis.service.TechnicalAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TechnicalAnalysisServiceImpl implements TechnicalAnalysisService {

    private final StockDailyMapper stockDailyMapper;

    public TechnicalAnalysisServiceImpl(StockDailyMapper stockDailyMapper) {
        this.stockDailyMapper = stockDailyMapper;
    }

    @Override
    public IndicatorResult calculateMA(String stockCode, int... periods) {
        List<StockDaily> dailyList = getDailyData(stockCode, 120);
        if (dailyList.size() < 2) {
            return new IndicatorResult();
        }

        IndicatorResult result = new IndicatorResult();
        result.setStockCode(stockCode);
        result.setIndicatorType("MA");

        List<IndicatorResult.IndicatorData> dataList = new ArrayList<>();
        for (int i = 0; i < dailyList.size(); i++) {
            StockDaily daily = dailyList.get(i);
            Map<String, BigDecimal> values = new LinkedHashMap<>();

            for (int period : periods) {
                if (i >= period - 1) {
                    BigDecimal sum = BigDecimal.ZERO;
                    for (int j = i - period + 1; j <= i; j++) {
                        sum = sum.add(dailyList.get(j).getClosePrice());
                    }
                    BigDecimal ma = sum.divide(new BigDecimal(period), 4, RoundingMode.HALF_UP);
                    values.put("MA" + period, ma);
                }
            }

            if (!values.isEmpty()) {
                dataList.add(new IndicatorResult.IndicatorData(
                        daily.getTradeDate().format(DateTimeFormatter.ISO_LOCAL_DATE), values));
            }
        }

        result.setData(dataList);
        return result;
    }

    @Override
    public IndicatorResult calculateMACD(String stockCode, int shortPeriod, int longPeriod, int signalPeriod) {
        List<StockDaily> dailyList = getDailyData(stockCode, 120);
        if (dailyList.size() < longPeriod + signalPeriod) {
            return new IndicatorResult();
        }

        IndicatorResult result = new IndicatorResult();
        result.setStockCode(stockCode);
        result.setIndicatorType("MACD");

        // 计算EMA
        List<BigDecimal> closePrices = dailyList.stream()
                .map(StockDaily::getClosePrice)
                .collect(Collectors.toList());

        List<BigDecimal> emaShort = calculateEMA(closePrices, shortPeriod);
        List<BigDecimal> emaLong = calculateEMA(closePrices, longPeriod);

        // DIF = EMA(short) - EMA(long)
        List<BigDecimal> difList = new ArrayList<>();
        for (int i = 0; i < emaShort.size(); i++) {
            difList.add(emaShort.get(i).subtract(emaLong.get(i)));
        }

        // DEA = EMA(DIF, signalPeriod)
        List<BigDecimal> deaList = calculateEMA(difList, signalPeriod);

        // MACD柱 = (DIF - DEA) * 2
        List<IndicatorResult.IndicatorData> dataList = new ArrayList<>();
        for (int i = 0; i < dailyList.size(); i++) {
            if (i >= longPeriod - 1) {
                Map<String, BigDecimal> values = new LinkedHashMap<>();
                values.put("DIF", difList.get(i));
                values.put("DEA", deaList.get(i));
                values.put("MACD", difList.get(i).subtract(deaList.get(i))
                        .multiply(new BigDecimal("2")).setScale(4, RoundingMode.HALF_UP));
                dataList.add(new IndicatorResult.IndicatorData(
                        dailyList.get(i).getTradeDate().format(DateTimeFormatter.ISO_LOCAL_DATE), values));
            }
        }

        result.setData(dataList);
        return result;
    }

    @Override
    public IndicatorResult calculateRSI(String stockCode, int... periods) {
        List<StockDaily> dailyList = getDailyData(stockCode, 120);
        if (dailyList.size() < 2) {
            return new IndicatorResult();
        }

        IndicatorResult result = new IndicatorResult();
        result.setStockCode(stockCode);
        result.setIndicatorType("RSI");

        // 计算价格变动
        List<BigDecimal> changes = new ArrayList<>();
        for (int i = 1; i < dailyList.size(); i++) {
            changes.add(dailyList.get(i).getClosePrice().subtract(dailyList.get(i - 1).getClosePrice()));
        }

        List<IndicatorResult.IndicatorData> dataList = new ArrayList<>();
        for (int i = 0; i < dailyList.size(); i++) {
            if (i == 0) continue;

            Map<String, BigDecimal> values = new LinkedHashMap<>();
            for (int period : periods) {
                if (i >= period) {
                    BigDecimal avgGain = BigDecimal.ZERO;
                    BigDecimal avgLoss = BigDecimal.ZERO;
                    for (int j = i - period; j < i; j++) {
                        BigDecimal change = changes.get(j);
                        if (change.compareTo(BigDecimal.ZERO) > 0) {
                            avgGain = avgGain.add(change);
                        } else {
                            avgLoss = avgLoss.add(change.abs());
                        }
                    }
                    avgGain = avgGain.divide(new BigDecimal(period), 4, RoundingMode.HALF_UP);
                    avgLoss = avgLoss.divide(new BigDecimal(period), 4, RoundingMode.HALF_UP);

                    BigDecimal rsi;
                    if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
                        rsi = new BigDecimal("100");
                    } else {
                        BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
                        rsi = new BigDecimal("100").subtract(
                                new BigDecimal("100").divide(rs.add(BigDecimal.ONE), 4, RoundingMode.HALF_UP));
                    }
                    values.put("RSI" + period, rsi);
                }
            }

            if (!values.isEmpty()) {
                dataList.add(new IndicatorResult.IndicatorData(
                        dailyList.get(i).getTradeDate().format(DateTimeFormatter.ISO_LOCAL_DATE), values));
            }
        }

        result.setData(dataList);
        return result;
    }

    @Override
    public IndicatorResult calculateKDJ(String stockCode, int n, int m1, int m2) {
        List<StockDaily> dailyList = getDailyData(stockCode, 120);
        if (dailyList.size() < n) {
            return new IndicatorResult();
        }

        IndicatorResult result = new IndicatorResult();
        result.setStockCode(stockCode);
        result.setIndicatorType("KDJ");

        BigDecimal prevK = new BigDecimal("50");
        BigDecimal prevD = new BigDecimal("50");

        List<IndicatorResult.IndicatorData> dataList = new ArrayList<>();
        for (int i = 0; i < dailyList.size(); i++) {
            int start = Math.max(0, i - n + 1);
            BigDecimal highestHigh = BigDecimal.ZERO;
            BigDecimal lowestLow = new BigDecimal("999999");

            for (int j = start; j <= i; j++) {
                if (dailyList.get(j).getHighPrice().compareTo(highestHigh) > 0) {
                    highestHigh = dailyList.get(j).getHighPrice();
                }
                if (dailyList.get(j).getLowPrice().compareTo(lowestLow) < 0) {
                    lowestLow = dailyList.get(j).getLowPrice();
                }
            }

            BigDecimal range = highestHigh.subtract(lowestLow);
            BigDecimal rsv;
            if (range.compareTo(BigDecimal.ZERO) == 0) {
                rsv = new BigDecimal("50");
            } else {
                rsv = dailyList.get(i).getClosePrice().subtract(lowestLow)
                        .divide(range, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }

            BigDecimal k = prevK.multiply(new BigDecimal(m1 - 1))
                    .divide(new BigDecimal(m1), 4, RoundingMode.HALF_UP)
                    .add(rsv.divide(new BigDecimal(m1), 4, RoundingMode.HALF_UP));
            BigDecimal d = prevD.multiply(new BigDecimal(m2 - 1))
                    .divide(new BigDecimal(m2), 4, RoundingMode.HALF_UP)
                    .add(k.divide(new BigDecimal(m2), 4, RoundingMode.HALF_UP));
            BigDecimal j = k.multiply(new BigDecimal("3")).subtract(d.multiply(new BigDecimal("2")));

            Map<String, BigDecimal> values = new LinkedHashMap<>();
            values.put("K", k.setScale(2, RoundingMode.HALF_UP));
            values.put("D", d.setScale(2, RoundingMode.HALF_UP));
            values.put("J", j.setScale(2, RoundingMode.HALF_UP));

            dataList.add(new IndicatorResult.IndicatorData(
                    dailyList.get(i).getTradeDate().format(DateTimeFormatter.ISO_LOCAL_DATE), values));

            prevK = k;
            prevD = d;
        }

        result.setData(dataList);
        return result;
    }

    @Override
    public IndicatorResult calculateBOLL(String stockCode, int period, int multiplier) {
        List<StockDaily> dailyList = getDailyData(stockCode, 120);
        if (dailyList.size() < period) {
            return new IndicatorResult();
        }

        IndicatorResult result = new IndicatorResult();
        result.setStockCode(stockCode);
        result.setIndicatorType("BOLL");

        List<IndicatorResult.IndicatorData> dataList = new ArrayList<>();
        for (int i = period - 1; i < dailyList.size(); i++) {
            // 计算中轨(MA)
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = i - period + 1; j <= i; j++) {
                sum = sum.add(dailyList.get(j).getClosePrice());
            }
            BigDecimal middle = sum.divide(new BigDecimal(period), 4, RoundingMode.HALF_UP);

            // 计算标准差
            BigDecimal variance = BigDecimal.ZERO;
            for (int j = i - period + 1; j <= i; j++) {
                BigDecimal diff = dailyList.get(j).getClosePrice().subtract(middle);
                variance = variance.add(diff.multiply(diff));
            }
            variance = variance.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
            double stdDev = Math.sqrt(variance.doubleValue());
            BigDecimal stdDevBd = new BigDecimal(stdDev).setScale(4, RoundingMode.HALF_UP);

            BigDecimal mult = new BigDecimal(multiplier);
            BigDecimal upper = middle.add(stdDevBd.multiply(mult));
            BigDecimal lower = middle.subtract(stdDevBd.multiply(mult));

            Map<String, BigDecimal> values = new LinkedHashMap<>();
            values.put("UPPER", upper);
            values.put("MIDDLE", middle);
            values.put("LOWER", lower);

            dataList.add(new IndicatorResult.IndicatorData(
                    dailyList.get(i).getTradeDate().format(DateTimeFormatter.ISO_LOCAL_DATE), values));
        }

        result.setData(dataList);
        return result;
    }

    @Override
    public List<IndicatorResult> getIndicators(String stockCode, String types) {
        List<IndicatorResult> results = new ArrayList<>();
        String[] typeArray = types.split(",");

        for (String type : typeArray) {
            type = type.trim().toUpperCase();
            switch (type) {
                case "MA":
                    results.add(calculateMA(stockCode, 5, 10, 20, 60));
                    break;
                case "MACD":
                    results.add(calculateMACD(stockCode, 12, 26, 9));
                    break;
                case "RSI":
                    results.add(calculateRSI(stockCode, 6, 12, 24));
                    break;
                case "KDJ":
                    results.add(calculateKDJ(stockCode, 9, 3, 3));
                    break;
                case "BOLL":
                    results.add(calculateBOLL(stockCode, 20, 2));
                    break;
                default:
                    log.warn("不支持的指标类型: {}", type);
            }
        }

        return results;
    }

    @Override
    public TradeSignal generateSignal(String stockCode) {
        TradeSignal signal = new TradeSignal();
        signal.setStockCode(stockCode);
        signal.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 检查是否有K线数据
        List<StockDaily> checkData = getDailyData(stockCode, 5);
        if (checkData == null || checkData.size() < 2) {
            signal.setSignal("HOLD");
            signal.setStrength(0);
            signal.setDescription("该标的无足够K线数据，无法生成技术信号");
            signal.setIndicatorSignals(new LinkedHashMap<>());
            signal.setIndicatorValues(new HashMap<>());
            return signal;
        }

        Map<String, String> indicatorSignals = new LinkedHashMap<>();
        Map<String, Object> indicatorValues = new HashMap<>();
        int buyScore = 0;
        int sellScore = 0;

        // 1. MA分析
        IndicatorResult maResult = calculateMA(stockCode, 5, 10, 20, 60);
        List<IndicatorResult.IndicatorData> maData = maResult.getData();
        if (maData.size() >= 2) {
            IndicatorResult.IndicatorData latest = maData.get(maData.size() - 1);
            IndicatorResult.IndicatorData prev = maData.get(maData.size() - 2);

            BigDecimal ma5 = latest.getValues().get("MA5");
            BigDecimal ma10 = latest.getValues().get("MA10");
            BigDecimal ma20 = latest.getValues().get("MA20");
            BigDecimal ma60 = latest.getValues().get("MA60");

            indicatorValues.put("MA", latest.getValues());

            if (ma5 != null && ma10 != null && ma20 != null) {
                if (ma5.compareTo(ma10) > 0 && ma10.compareTo(ma20) > 0) {
                    indicatorSignals.put("MA", "多头排列 - BUY");
                    buyScore += 2;
                } else if (ma5.compareTo(ma10) < 0 && ma10.compareTo(ma20) < 0) {
                    indicatorSignals.put("MA", "空头排列 - SELL");
                    sellScore += 2;
                } else {
                    // 检查金叉/死叉
                    BigDecimal prevMa5 = prev.getValues().get("MA5");
                    BigDecimal prevMa10 = prev.getValues().get("MA10");
                    if (prevMa5 != null && prevMa10 != null) {
                        if (prevMa5.compareTo(prevMa10) <= 0 && ma5.compareTo(ma10) > 0) {
                            indicatorSignals.put("MA", "MA5上穿MA10金叉 - BUY");
                            buyScore += 2;
                        } else if (prevMa5.compareTo(prevMa10) >= 0 && ma5.compareTo(ma10) < 0) {
                            indicatorSignals.put("MA", "MA5下穿MA10死叉 - SELL");
                            sellScore += 2;
                        } else {
                            indicatorSignals.put("MA", "震荡 - HOLD");
                        }
                    }
                }
            }
        }

        // 2. MACD分析
        IndicatorResult macdResult = calculateMACD(stockCode, 12, 26, 9);
        List<IndicatorResult.IndicatorData> macdData = macdResult.getData();
        if (macdData.size() >= 2) {
            IndicatorResult.IndicatorData latest = macdData.get(macdData.size() - 1);
            IndicatorResult.IndicatorData prev = macdData.get(macdData.size() - 2);

            BigDecimal dif = latest.getValues().get("DIF");
            BigDecimal dea = latest.getValues().get("DEA");
            BigDecimal macd = latest.getValues().get("MACD");

            indicatorValues.put("MACD", latest.getValues());

            BigDecimal prevDif = prev.getValues().get("DIF");
            BigDecimal prevDea = prev.getValues().get("DEA");

            if (prevDif != null && prevDea != null && dif != null && dea != null) {
                if (prevDif.compareTo(prevDea) <= 0 && dif.compareTo(dea) > 0) {
                    indicatorSignals.put("MACD", "DIF上穿DEA金叉 - BUY");
                    buyScore += 2;
                } else if (prevDif.compareTo(prevDea) >= 0 && dif.compareTo(dea) < 0) {
                    indicatorSignals.put("MACD", "DIF下穿DEA死叉 - SELL");
                    sellScore += 2;
                } else if (dif.compareTo(BigDecimal.ZERO) > 0 && macd.compareTo(BigDecimal.ZERO) > 0) {
                    indicatorSignals.put("MACD", "多头区域 - BUY");
                    buyScore += 1;
                } else if (dif.compareTo(BigDecimal.ZERO) < 0 && macd.compareTo(BigDecimal.ZERO) < 0) {
                    indicatorSignals.put("MACD", "空头区域 - SELL");
                    sellScore += 1;
                } else {
                    indicatorSignals.put("MACD", "震荡 - HOLD");
                }
            }
        }

        // 3. RSI分析
        IndicatorResult rsiResult = calculateRSI(stockCode, 6, 12, 24);
        List<IndicatorResult.IndicatorData> rsiData = rsiResult.getData();
        if (!rsiData.isEmpty()) {
            IndicatorResult.IndicatorData latest = rsiData.get(rsiData.size() - 1);
            BigDecimal rsi6 = latest.getValues().get("RSI6");
            BigDecimal rsi12 = latest.getValues().get("RSI12");

            indicatorValues.put("RSI", latest.getValues());

            if (rsi6 != null) {
                if (rsi6.compareTo(new BigDecimal("20")) < 0) {
                    indicatorSignals.put("RSI", "超卖区域 - BUY");
                    buyScore += 2;
                } else if (rsi6.compareTo(new BigDecimal("80")) > 0) {
                    indicatorSignals.put("RSI", "超买区域 - SELL");
                    sellScore += 2;
                } else if (rsi6.compareTo(new BigDecimal("50")) > 0) {
                    indicatorSignals.put("RSI", "偏强 - BUY");
                    buyScore += 1;
                } else {
                    indicatorSignals.put("RSI", "偏弱 - SELL");
                    sellScore += 1;
                }
            }
        }

        // 4. KDJ分析
        IndicatorResult kdjResult = calculateKDJ(stockCode, 9, 3, 3);
        List<IndicatorResult.IndicatorData> kdjData = kdjResult.getData();
        if (kdjData.size() >= 2) {
            IndicatorResult.IndicatorData latest = kdjData.get(kdjData.size() - 1);
            IndicatorResult.IndicatorData prev = kdjData.get(kdjData.size() - 2);

            BigDecimal k = latest.getValues().get("K");
            BigDecimal d = latest.getValues().get("D");
            BigDecimal j = latest.getValues().get("J");

            indicatorValues.put("KDJ", latest.getValues());

            BigDecimal prevK = prev.getValues().get("K");
            BigDecimal prevD = prev.getValues().get("D");

            if (prevK != null && prevD != null && k != null && d != null) {
                if (prevK.compareTo(prevD) <= 0 && k.compareTo(d) > 0) {
                    indicatorSignals.put("KDJ", "K上穿D金叉 - BUY");
                    buyScore += 2;
                } else if (prevK.compareTo(prevD) >= 0 && k.compareTo(d) < 0) {
                    indicatorSignals.put("KDJ", "K下穿D死叉 - SELL");
                    sellScore += 2;
                } else if (k.compareTo(new BigDecimal("80")) > 0) {
                    indicatorSignals.put("KDJ", "超买区域 - SELL");
                    sellScore += 1;
                } else if (k.compareTo(new BigDecimal("20")) < 0) {
                    indicatorSignals.put("KDJ", "超卖区域 - BUY");
                    buyScore += 1;
                } else {
                    indicatorSignals.put("KDJ", "中性 - HOLD");
                }
            }
        }

        // 5. 布林带分析
        IndicatorResult bollResult = calculateBOLL(stockCode, 20, 2);
        List<IndicatorResult.IndicatorData> bollData = bollResult.getData();
        if (!bollData.isEmpty()) {
            IndicatorResult.IndicatorData latest = bollData.get(bollData.size() - 1);

            List<StockDaily> dailyList = getDailyData(stockCode, 120);
            BigDecimal currentPrice = dailyList.get(dailyList.size() - 1).getClosePrice();

            BigDecimal upper = latest.getValues().get("UPPER");
            BigDecimal middle = latest.getValues().get("MIDDLE");
            BigDecimal lower = latest.getValues().get("LOWER");

            indicatorValues.put("BOLL", latest.getValues());
            signal.setCurrentPrice(currentPrice);

            if (currentPrice.compareTo(lower) <= 0) {
                indicatorSignals.put("BOLL", "触及下轨 - BUY");
                buyScore += 1;
            } else if (currentPrice.compareTo(upper) >= 0) {
                indicatorSignals.put("BOLL", "触及上轨 - SELL");
                sellScore += 1;
            } else {
                indicatorSignals.put("BOLL", "中轨附近 - HOLD");
            }
        }

        // 综合判断
        signal.setIndicatorSignals(indicatorSignals);
        signal.setIndicatorValues(indicatorValues);

        int scoreDiff = buyScore - sellScore;
        if (scoreDiff >= 3) {
            signal.setSignal("BUY");
            signal.setStrength(Math.min(5, scoreDiff));
            signal.setDescription("多个指标发出买入信号，建议关注");
        } else if (scoreDiff <= -3) {
            signal.setSignal("SELL");
            signal.setStrength(Math.min(5, -scoreDiff));
            signal.setDescription("多个指标发出卖出信号，建议减仓");
        } else {
            signal.setSignal("HOLD");
            signal.setStrength(1);
            signal.setDescription("指标信号不明确，建议持有观望");
        }

        return signal;
    }

    @Override
    public BacktestResult backtest(String stockCode, String strategy, Map<String, Integer> params) {
        List<StockDaily> dailyList = getDailyData(stockCode, 250);
        if (dailyList.size() < 60) {
            throw new RuntimeException("数据不足，无法回测");
        }

        BacktestResult result = new BacktestResult();
        result.setStockCode(stockCode);
        result.setStrategy(strategy);
        result.setInitialCapital(new BigDecimal("100000.00"));

        if ("ma_cross".equals(strategy)) {
            return backtestMACross(dailyList, params, result);
        }

        throw new RuntimeException("不支持的策略: " + strategy);
    }

    /**
     * MA金叉死叉回测策略
     */
    private BacktestResult backtestMACross(List<StockDaily> dailyList, Map<String, Integer> params,
                                           BacktestResult result) {
        int shortPeriod = params.getOrDefault("shortPeriod", 5);
        int longPeriod = params.getOrDefault("longPeriod", 20);

        List<BigDecimal> closePrices = dailyList.stream()
                .map(StockDaily::getClosePrice)
                .collect(Collectors.toList());

        List<BigDecimal> maShort = calculateMAList(closePrices, shortPeriod);
        List<BigDecimal> maLong = calculateMAList(closePrices, longPeriod);

        BigDecimal capital = result.getInitialCapital();
        int position = 0; // 持仓数量
        BigDecimal avgCost = BigDecimal.ZERO;
        List<BacktestTrade> trades = new ArrayList<>();
        List<BigDecimal> equityCurve = new ArrayList<>();
        int winTrades = 0;
        int loseTrades = 0;
        BigDecimal maxEquity = capital;

        for (int i = longPeriod; i < dailyList.size(); i++) {
            BigDecimal shortMA = maShort.get(i);
            BigDecimal longMA = maLong.get(i);
            BigDecimal prevShortMA = maShort.get(i - 1);
            BigDecimal prevLongMA = maLong.get(i - 1);
            BigDecimal price = closePrices.get(i);

            // 金叉买入
            if (prevShortMA.compareTo(prevLongMA) <= 0 && shortMA.compareTo(longMA) > 0 && position == 0) {
                int buyQuantity = capital.divide(price, 0, RoundingMode.DOWN).intValue();
                buyQuantity = (buyQuantity / 100) * 100; // A股按手(100股)交易
                if (buyQuantity >= 100) {
                    BigDecimal amount = price.multiply(new BigDecimal(buyQuantity));
                    capital = capital.subtract(amount);
                    position = buyQuantity;
                    avgCost = price;

                    BacktestTrade trade = new BacktestTrade();
                    trade.setDate(dailyList.get(i).getTradeDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
                    trade.setDirection("BUY");
                    trade.setPrice(price);
                    trade.setQuantity(buyQuantity);
                    trade.setAmount(amount);
                    trades.add(trade);
                }
            }
            // 死叉卖出
            else if (prevShortMA.compareTo(prevLongMA) >= 0 && shortMA.compareTo(longMA) < 0 && position > 0) {
                BigDecimal amount = price.multiply(new BigDecimal(position));
                BigDecimal profitLoss = price.subtract(avgCost).multiply(new BigDecimal(position));
                capital = capital.add(amount);
                position = 0;

                BacktestTrade trade = new BacktestTrade();
                trade.setDate(dailyList.get(i).getTradeDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
                trade.setDirection("SELL");
                trade.setPrice(price);
                trade.setQuantity(position);
                trade.setAmount(amount);
                trade.setProfitLoss(profitLoss);
                if (avgCost.compareTo(BigDecimal.ZERO) > 0) {
                    trade.setProfitLossPercent(price.subtract(avgCost)
                            .divide(avgCost, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100")));
                }
                trades.add(trade);

                if (profitLoss.compareTo(BigDecimal.ZERO) > 0) {
                    winTrades++;
                } else if (profitLoss.compareTo(BigDecimal.ZERO) < 0) {
                    loseTrades++;
                }
            }

            // 计算当前权益
            BigDecimal equity = capital.add(price.multiply(new BigDecimal(position)));
            equityCurve.add(equity);
            if (equity.compareTo(maxEquity) > 0) {
                maxEquity = equity;
            }
        }

        // 如果还有持仓，按最后价格清仓
        if (position > 0) {
            BigDecimal lastPrice = closePrices.get(closePrices.size() - 1);
            capital = capital.add(lastPrice.multiply(new BigDecimal(position)));
        }

        // 计算回测结果
        result.setFinalCapital(capital);
        result.setTotalReturn(capital.subtract(result.getInitialCapital())
                .divide(result.getInitialCapital(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")));

        // 年化收益率（简化）
        int tradingDays = dailyList.size() - longPeriod;
        double annualizedReturn = Math.pow(capital.doubleValue() / result.getInitialCapital().doubleValue(),
                252.0 / tradingDays) - 1;
        result.setAnnualizedReturn(new BigDecimal(annualizedReturn * 100).setScale(2, RoundingMode.HALF_UP));

        // 最大回撤
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (BigDecimal equity : equityCurve) {
            BigDecimal drawdown = maxEquity.subtract(equity)
                    .divide(maxEquity, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }
        result.setMaxDrawdown(maxDrawdown);

        int totalTrades = winTrades + loseTrades;
        result.setTotalTrades(totalTrades);
        result.setWinTrades(winTrades);
        result.setLoseTrades(loseTrades);
        result.setWinRate(totalTrades > 0
                ? new BigDecimal(winTrades).divide(new BigDecimal(totalTrades), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                : BigDecimal.ZERO);

        result.setSharpeRatio(BigDecimal.ZERO); // 简化
        result.setTrades(trades);
        result.setEquityCurve(equityCurve);

        return result;
    }

    /**
     * 获取日K数据
     */
    private List<StockDaily> getDailyData(String stockCode, int limit) {
        return stockDailyMapper.selectList(
                new LambdaQueryWrapper<StockDaily>()
                        .eq(StockDaily::getStockCode, stockCode)
                        .orderByAsc(StockDaily::getTradeDate)
                        .last("LIMIT " + limit));
    }

    /**
     * 计算EMA（指数移动平均）
     */
    private List<BigDecimal> calculateEMA(List<BigDecimal> data, int period) {
        List<BigDecimal> ema = new ArrayList<>();
        BigDecimal multiplier = new BigDecimal("2.0")
                .divide(new BigDecimal(period + 1), 8, RoundingMode.HALF_UP);

        // 第一个EMA值使用SMA
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(data.get(i));
            ema.add(data.get(i)); // 前period-1个值先用原始值
        }
        BigDecimal firstEma = sum.divide(new BigDecimal(period), 4, RoundingMode.HALF_UP);
        ema.set(period - 1, firstEma);

        for (int i = period; i < data.size(); i++) {
            BigDecimal emaValue = data.get(i).subtract(ema.get(i - 1))
                    .multiply(multiplier)
                    .add(ema.get(i - 1))
                    .setScale(4, RoundingMode.HALF_UP);
            ema.add(emaValue);
        }

        return ema;
    }

    /**
     * 计算简单移动平均列表
     */
    private List<BigDecimal> calculateMAList(List<BigDecimal> data, int period) {
        List<BigDecimal> ma = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            if (i < period - 1) {
                ma.add(data.get(i));
            } else {
                BigDecimal sum = BigDecimal.ZERO;
                for (int j = i - period + 1; j <= i; j++) {
                    sum = sum.add(data.get(j));
                }
                ma.add(sum.divide(new BigDecimal(period), 4, RoundingMode.HALF_UP));
            }
        }
        return ma;
    }
}
