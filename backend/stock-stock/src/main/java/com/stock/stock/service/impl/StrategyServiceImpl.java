package com.stock.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stock.stock.dto.StrategyDto;
import com.stock.stock.dto.StrategyDto.*;
import com.stock.stock.entity.StockDaily;
import com.stock.stock.entity.StockInfo;
import com.stock.stock.mapper.StockDailyMapper;
import com.stock.stock.mapper.StockInfoMapper;
import com.stock.stock.service.StrategyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class StrategyServiceImpl implements StrategyService {

    private final StockDailyMapper stockDailyMapper;
    private final StockInfoMapper stockInfoMapper;

    /** 策略存储 */
    private final ConcurrentHashMap<String, StrategyDto> strategyMap = new ConcurrentHashMap<>();
    /** 策略ID生成 */
    private final AtomicLong idGen = new AtomicLong(0);

    public StrategyServiceImpl(StockDailyMapper stockDailyMapper, StockInfoMapper stockInfoMapper) {
        this.stockDailyMapper = stockDailyMapper;
        this.stockInfoMapper = stockInfoMapper;
    }

    @Override
    public StrategyDto createStrategy(StrategyDto dto) {
        String id = "strat_" + idGen.incrementAndGet();
        dto.setId(id);
        dto.setStatus("IDLE");
        // 查股票名称
        if (dto.getStockCode() != null) {
            StockInfo info = stockInfoMapper.selectOne(
                    new LambdaQueryWrapper<StockInfo>().eq(StockInfo::getCode, dto.getStockCode()));
            if (info != null) dto.setStockName(info.getName());
        }
        strategyMap.put(id, dto);
        return dto;
    }

    @Override
    public StrategyDto runStrategy(String id) {
        StrategyDto dto = strategyMap.get(id);
        if (dto == null) return null;
        dto.setStatus("RUNNING");

        try {
            StrategyResult result = evaluateStrategy(dto);
            dto.setResult(result);
            dto.setStatus("STOPPED");
        } catch (Exception e) {
            StrategyResult err = new StrategyResult();
            err.setMessage(e.getMessage());
            dto.setResult(err);
            dto.setStatus("ERROR");
            log.error("策略执行失败: {} - {}", id, e.getMessage());
        }
        return dto;
    }

    @Override
    public StrategyDto pauseStrategy(String id) {
        StrategyDto dto = strategyMap.get(id);
        if (dto != null) dto.setStatus("PAUSED");
        return dto;
    }

    @Override
    public StrategyDto stopStrategy(String id) {
        StrategyDto dto = strategyMap.get(id);
        if (dto != null) dto.setStatus("STOPPED");
        return dto;
    }

    @Override
    public List<StrategyDto> listStrategies() {
        return new ArrayList<>(strategyMap.values());
    }

    @Override
    public StrategyDto getStrategy(String id) {
        return strategyMap.get(id);
    }

    @Override
    public void deleteStrategy(String id) {
        strategyMap.remove(id);
    }

    /** 执行策略评估 */
    private StrategyResult evaluateStrategy(StrategyDto dto) {
        String code = dto.getStockCode();
        BacktestConfig config = dto.getBacktestConfig();

        // 获取K线数据（回测模式用历史，实盘模式用最近）
        LambdaQueryWrapper<StockDaily> wrapper = new LambdaQueryWrapper<StockDaily>()
                .eq(StockDaily::getStockCode, code)
                .orderByAsc(StockDaily::getTradeDate);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        if (config != null && config.getStartDate() != null && !config.getStartDate().isEmpty()) {
            wrapper.ge(StockDaily::getTradeDate, LocalDate.parse(config.getStartDate(), fmt));
        }
        if (config != null && config.getEndDate() != null && !config.getEndDate().isEmpty()) {
            wrapper.le(StockDaily::getTradeDate, LocalDate.parse(config.getEndDate(), fmt));
        }

        List<StockDaily> dailyList = stockDailyMapper.selectList(wrapper);
        if (dailyList == null || dailyList.size() < 20) {
            throw new RuntimeException("数据不足（需要至少20个交易日），当前仅 " + (dailyList == null ? 0 : dailyList.size()) + " 条");
        }

        // 提取价格数据
        List<BigDecimal> closes = new ArrayList<>();
        List<BigDecimal> highs = new ArrayList<>();
        List<BigDecimal> lows = new ArrayList<>();
        List<Long> volumes = new ArrayList<>();
        for (StockDaily d : dailyList) {
            closes.add(d.getClosePrice());
            highs.add(d.getHighPrice());
            lows.add(d.getLowPrice());
            volumes.add(d.getVolume() != null ? d.getVolume() : 0L);
        }

        // 计算所有指标
        double[] closeArr = closes.stream().mapToDouble(BigDecimal::doubleValue).toArray();
        double[] highArr = highs.stream().mapToDouble(BigDecimal::doubleValue).toArray();
        double[] lowArr = lows.stream().mapToDouble(BigDecimal::doubleValue).toArray();
        long[] volArr = volumes.stream().mapToLong(Long::longValue).toArray();

        double[] ma5 = calcMA(closeArr, 5);
        double[] ma10 = calcMA(closeArr, 10);
        double[] ma20 = calcMA(closeArr, 20);
        double[] ma60 = calcMA(closeArr, 60);
        double[] rsi = calcRSI(closeArr);
        double[][] macd = calcMACD(closeArr);
        double[][] kdj = calcKDJ(closeArr, highArr, lowArr);

        // 条件评估
        List<Condition> conditions = dto.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            throw new RuntimeException("策略无条件");
        }

        // 回测：按日评估条件，统计胜率
        StrategyResult result = new StrategyResult();
        BigDecimal initialCapital = config != null && config.getInitialCapital() != null
                ? config.getInitialCapital() : new BigDecimal("100000");
        BigDecimal cash = initialCapital;
        BigDecimal shares = BigDecimal.ZERO;
        BigDecimal buyPrice = BigDecimal.ZERO;
        boolean holding = false;
        int wins = 0, losses = 0;

        int startIdx = Math.max(20, dailyList.size() - 60); // 至少20天，最多60天预热
        for (int i = startIdx; i < dailyList.size(); i++) {
            int idx = i;
            boolean conditionMet = evaluateConditions(idx, closeArr, ma5, ma10, ma20, ma60, rsi, macd, kdj, volArr, conditions);

            if (conditionMet && !holding) {
                // 买入
                BigDecimal price = closes.get(i);
                BigDecimal affordable = cash.divide(price, 0, RoundingMode.DOWN);
                if (affordable.compareTo(BigDecimal.ZERO) > 0) {
                    shares = affordable;
                    cash = cash.subtract(affordable.multiply(price));
                    holding = true;
                    buyPrice = price;
                }
            } else if (!conditionMet && holding) {
                // 卖出
                BigDecimal price = closes.get(i);
                BigDecimal sellValue = shares.multiply(price);
                cash = cash.add(sellValue);
                if (sellValue.compareTo(shares.multiply(buyPrice)) > 0) wins++;
                else losses++;
                shares = BigDecimal.ZERO;
                holding = false;
            }
        }

        // 最后平仓
        if (holding && !dailyList.isEmpty()) {
            BigDecimal price = closes.get(dailyList.size() - 1);
            cash = cash.add(shares.multiply(price));
            if (shares.multiply(price).compareTo(shares.multiply(buyPrice)) > 0) wins++;
            else losses++;
        }

        int totalTrades = wins + losses;
        BigDecimal finalCapital = cash;

        result.setTotalReturn(initialCapital.compareTo(BigDecimal.ZERO) > 0
                ? (finalCapital.subtract(initialCapital)).divide(initialCapital, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO);
        result.setTotalTrades(totalTrades);
        result.setWinCount(wins);
        result.setLossCount(losses);
        result.setWinRate(totalTrades > 0
                ? new BigDecimal(wins).divide(new BigDecimal(totalTrades), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO);
        result.setFinalCapital(finalCapital);
        result.setMessage("回测完成");

        return result;
    }

    /** 评估条件组 */
    private boolean evaluateConditions(int idx, double[] close, double[] ma5, double[] ma10,
                                        double[] ma20, double[] ma60, double[] rsi,
                                        double[][] macd, double[][] kdj, long[] vol,
                                        List<Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) return false;

        // 取前一个索引用于交叉判断
        int prevIdx = idx - 1;

        boolean result = true;
        boolean first = true;

        for (Condition cond : conditions) {
            boolean condResult = evaluateSingleCondition(idx, prevIdx, close, ma5, ma10, ma20, ma60, rsi, macd, kdj, vol, cond);

            if (first) {
                result = condResult;
                first = false;
            } else if ("OR".equalsIgnoreCase(cond.getConnector())) {
                result = result || condResult;
            } else {
                result = result && condResult;
            }
        }
        return result;
    }

    /** 评估单条条件 */
    private boolean evaluateSingleCondition(int idx, int prevIdx, double[] close,
                                             double[] ma5, double[] ma10, double[] ma20, double[] ma60,
                                             double[] rsi, double[][] macd, double[][] kdj,
                                             long[] vol, Condition cond) {
        double val1 = getIndicatorValue(idx, cond.getIndicator(), close, ma5, ma10, ma20, ma60, rsi, macd, kdj, vol);
        double val2;

        // 交叉条件特殊处理
        if ("CROSS_ABOVE".equals(cond.getOperator()) || "CROSS_BELOW".equals(cond.getOperator())) {
            double prevVal1 = getIndicatorValue(prevIdx, cond.getIndicator(), close, ma5, ma10, ma20, ma60, rsi, macd, kdj, vol);
            double prevVal2;
            try { prevVal2 = Double.parseDouble(cond.getValue()); } catch (Exception e) {
                prevVal2 = getIndicatorValue(prevIdx, cond.getValue(), close, ma5, ma10, ma20, ma60, rsi, macd, kdj, vol);
            }
            try { val2 = Double.parseDouble(cond.getValue()); } catch (Exception e) {
                val2 = getIndicatorValue(idx, cond.getValue(), close, ma5, ma10, ma20, ma60, rsi, macd, kdj, vol);
            }

            if ("CROSS_ABOVE".equals(cond.getOperator())) {
                return prevVal1 <= prevVal2 && val1 > val2;
            } else {
                return prevVal1 >= prevVal2 && val1 < val2;
            }
        }

        try { val2 = Double.parseDouble(cond.getValue()); } catch (Exception e) {
            val2 = getIndicatorValue(idx, cond.getValue(), close, ma5, ma10, ma20, ma60, rsi, macd, kdj, vol);
        }

        switch (cond.getOperator()) {
            case ">": return val1 > val2;
            case "<": return val1 < val2;
            case ">=": return val1 >= val2;
            case "<=": return val1 <= val2;
            case "==": return Math.abs(val1 - val2) < 0.001;
            default: return false;
        }
    }

    /** 获取指标值 */
    private double getIndicatorValue(int idx, String indicator, double[] close,
                                      double[] ma5, double[] ma10, double[] ma20, double[] ma60,
                                      double[] rsi, double[][] macd, double[][] kdj,
                                      long[] vol) {
        if (idx < 0 || idx >= close.length) return 0;
        switch (indicator.toUpperCase()) {
            case "PRICE": return close[idx];
            case "MA5": return ma5[idx];
            case "MA10": return ma10[idx];
            case "MA20": return ma20[idx];
            case "MA60": return ma60[idx];
            case "RSI": return rsi != null && idx < rsi.length ? rsi[idx] : 50;
            case "MACD_DIF": return macd != null && idx < macd[0].length ? macd[0][idx] : 0;
            case "MACD_DEA": return macd != null && idx < macd[1].length ? macd[1][idx] : 0;
            case "MACD": return macd != null && idx < macd[2].length ? macd[2][idx] : 0;
            case "KDJ_K": return kdj != null && idx < kdj[0].length ? kdj[0][idx] : 50;
            case "KDJ_D": return kdj != null && idx < kdj[1].length ? kdj[1][idx] : 50;
            case "KDJ_J": return kdj != null && idx < kdj[2].length ? kdj[2][idx] : 50;
            case "VOLUME": return vol != null && idx < vol.length ? vol[idx] : 0;
            default: return 0;
        }
    }

    // ===== 指标计算（同KlineChart） =====

    private double[] calcMA(double[] data, int period) {
        double[] result = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            if (i < period - 1) { result[i] = 0; continue; }
            double sum = 0;
            for (int j = 0; j < period; j++) sum += data[i - j];
            result[i] = sum / period;
        }
        return result;
    }

    private double[] calcRSI(double[] data) {
        double[] r = new double[data.length];
        r[0] = 50;
        double gain = 0, loss = 0;
        for (int i = 1; i < data.length; i++) {
            double diff = data[i] - data[i-1];
            if (i <= 14) {
                if (diff > 0) gain += diff; else loss -= diff;
                r[i] = (i == 14) ? 100 - 100/(1 + gain/loss) : 50;
            } else {
                gain = ((gain*13) + (diff>0?diff:0))/14;
                loss = ((loss*13) + (diff<0?-diff:0))/14;
                r[i] = 100 - 100/(1 + gain/loss);
            }
        }
        return r;
    }

    private double[][] calcMACD(double[] data) {
        double[] ema12 = new double[data.length];
        double[] ema26 = new double[data.length];
        double[] dif = new double[data.length];
        double[] dea = new double[data.length];
        double[] macd = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            if (i == 0) { ema12[i] = data[i]; ema26[i] = data[i]; }
            else { ema12[i] = ema12[i-1]*11/13 + data[i]*2/13; ema26[i] = ema26[i-1]*25/27 + data[i]*2/27; }
            dif[i] = ema12[i] - ema26[i];
        }
        for (int i = 0; i < data.length; i++) {
            if (i == 0) dea[i] = dif[i];
            else dea[i] = dea[i-1]*8/10 + dif[i]*2/10;
            macd[i] = dif[i] - dea[i];
        }
        return new double[][]{dif, dea, macd};
    }

    private double[][] calcKDJ(double[] close, double[] high, double[] low) {
        double[] k = new double[close.length];
        double[] d = new double[close.length];
        double[] j = new double[close.length];
        double prevK = 50, prevD = 50;
        for (int i = 0; i < close.length; i++) {
            if (i < 8) { k[i] = 50; d[i] = 50; j[i] = 50; continue; }
            double h = -Double.MAX_VALUE, lv = Double.MAX_VALUE;
            for (int t = i-8; t <= i; t++) { h = Math.max(h, high[t]); lv = Math.min(lv, low[t]); }
            double rsv = (h == lv) ? 50 : (close[i] - lv) / (h - lv) * 100;
            double kv = prevK*2/3 + rsv/3;
            double dv = prevD*2/3 + kv/3;
            k[i] = kv; d[i] = dv; j[i] = 3*kv - 2*dv;
            prevK = kv; prevD = dv;
        }
        return new double[][]{k, d, j};
    }
}
