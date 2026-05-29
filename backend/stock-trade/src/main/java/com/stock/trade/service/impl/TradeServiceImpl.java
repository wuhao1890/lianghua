package com.stock.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stock.trade.client.StockClient;
import com.stock.trade.dto.*;
import com.stock.trade.entity.Position;
import com.stock.trade.entity.TradeLog;
import com.stock.trade.entity.TradeOrder;
import com.stock.trade.mapper.PositionMapper;
import com.stock.trade.mapper.TradeLogMapper;
import com.stock.trade.mapper.TradeOrderMapper;
import com.stock.trade.service.TradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TradeServiceImpl extends ServiceImpl<TradeOrderMapper, TradeOrder> implements TradeService {

    @Autowired
    private TradeOrderMapper tradeOrderMapper;

    @Autowired
    private PositionMapper positionMapper;

    @Autowired
    private TradeLogMapper tradeLogMapper;

    @Autowired
    private StockClient stockClient;

    /** 手续费费率: 万三 */
    private static final BigDecimal FEE_RATE = new BigDecimal("0.0003");

    /** 最低手续费: 5元 */
    private static final BigDecimal MIN_FEE = new BigDecimal("5.00");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TradeOrder buy(Long userId, BuyRequest request) {
        // 1. 获取实时价格
        BigDecimal tradePrice = getTradePrice(request.getStockCode(), request.getOrderType(), request.getPrice());
        if (tradePrice == null || tradePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("无法获取股票价格");
        }

        // 2. 获取股票名称
        String stockName = request.getStockName();
        String market = request.getMarket();
        if (stockName == null || stockName.isEmpty()) {
            try {
                StockDTO stockDTO = stockClient.getRealtimeQuote(request.getStockCode());
                if (stockDTO != null) {
                    stockName = stockDTO.getName();
                    market = stockDTO.getMarket();
                }
            } catch (Exception e) {
                log.warn("获取股票信息失败: {}", e.getMessage());
            }
        }
        if (stockName == null) {
            stockName = request.getStockCode();
        }
        if (market == null) {
            market = "A_STOCK";
        }

        // 3. 计算金额和手续费
        BigDecimal amount = tradePrice.multiply(new BigDecimal(request.getQuantity()));
        BigDecimal fee = calculateFee(amount);
        BigDecimal totalCost = amount.add(fee);

        // 4. 创建订单
        TradeOrder order = new TradeOrder();
        order.setUserId(userId);
        order.setStockCode(request.getStockCode());
        order.setStockName(stockName);
        order.setMarket(market);
        order.setDirection("BUY");
        order.setOrderType(request.getOrderType());
        order.setPrice(tradePrice);
        order.setQuantity(request.getQuantity());
        order.setAmount(amount);
        order.setStatus("FILLED");
        order.setFee(fee);
        order.setFilledTime(LocalDateTime.now());
        order.setCreateTime(LocalDateTime.now());
        tradeOrderMapper.insert(order);

        // 5. 更新持仓
        Position position = getPosition(userId, request.getStockCode());
        if (position == null) {
            // 新建持仓
            position = new Position();
            position.setUserId(userId);
            position.setStockCode(request.getStockCode());
            position.setStockName(stockName);
            position.setMarket(market);
            position.setQuantity(request.getQuantity());
            position.setAvgCost(tradePrice);
            position.setCurrentPrice(tradePrice);
            position.setProfitLoss(BigDecimal.ZERO);
            position.setProfitLossPercent(BigDecimal.ZERO);
            position.setUpdateTime(LocalDateTime.now());
            positionMapper.insert(position);
        } else {
            // 加仓: 重新计算平均成本
            BigDecimal totalCostAmount = position.getAvgCost()
                    .multiply(new BigDecimal(position.getQuantity()))
                    .add(amount);
            int newQuantity = position.getQuantity() + request.getQuantity();
            BigDecimal newAvgCost = totalCostAmount.divide(new BigDecimal(newQuantity), 4, RoundingMode.HALF_UP);
            position.setQuantity(newQuantity);
            position.setAvgCost(newAvgCost);
            position.setCurrentPrice(tradePrice);
            position.setProfitLoss(tradePrice.subtract(newAvgCost).multiply(new BigDecimal(newQuantity)));
            position.setProfitLossPercent(tradePrice.subtract(newAvgCost)
                    .divide(newAvgCost, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")));
            position.setUpdateTime(LocalDateTime.now());
            positionMapper.updateById(position);
        }

        // 6. 记录交易日志
        TradeLog tradeLog = new TradeLog();
        tradeLog.setOrderId(order.getId());
        tradeLog.setUserId(userId);
        tradeLog.setStockCode(request.getStockCode());
        tradeLog.setAction("BUY");
        tradeLog.setPrice(tradePrice);
        tradeLog.setQuantity(request.getQuantity());
        tradeLog.setAmount(amount);
        tradeLog.setProfitLoss(BigDecimal.ZERO);
        tradeLog.setFee(fee);
        tradeLog.setCreateTime(LocalDateTime.now());
        tradeLogMapper.insert(tradeLog);

        log.info("用户{}买入{} {}股, 价格={}, 金额={}, 手续费={}",
                userId, request.getStockCode(), request.getQuantity(), tradePrice, amount, fee);

        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TradeOrder sell(Long userId, SellRequest request) {
        // 1. 检查持仓
        Position position = getPosition(userId, request.getStockCode());
        if (position == null || position.getQuantity() < request.getQuantity()) {
            throw new RuntimeException("持仓不足，无法卖出");
        }

        // 2. 获取实时价格
        BigDecimal tradePrice = getTradePrice(request.getStockCode(), request.getOrderType(), request.getPrice());
        if (tradePrice == null || tradePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("无法获取股票价格");
        }

        // 3. 计算金额、手续费和盈亏
        BigDecimal amount = tradePrice.multiply(new BigDecimal(request.getQuantity()));
        BigDecimal fee = calculateFee(amount);
        BigDecimal costAmount = position.getAvgCost().multiply(new BigDecimal(request.getQuantity()));
        BigDecimal profitLoss = amount.subtract(costAmount).subtract(fee);

        // 4. 创建订单
        TradeOrder order = new TradeOrder();
        order.setUserId(userId);
        order.setStockCode(request.getStockCode());
        order.setStockName(position.getStockName());
        order.setMarket(position.getMarket());
        order.setDirection("SELL");
        order.setOrderType(request.getOrderType());
        order.setPrice(tradePrice);
        order.setQuantity(request.getQuantity());
        order.setAmount(amount);
        order.setStatus("FILLED");
        order.setFee(fee);
        order.setFilledTime(LocalDateTime.now());
        order.setCreateTime(LocalDateTime.now());
        tradeOrderMapper.insert(order);

        // 5. 更新持仓
        int remainQuantity = position.getQuantity() - request.getQuantity();
        if (remainQuantity == 0) {
            // 清仓
            positionMapper.deleteById(position.getId());
        } else {
            // 减仓
            position.setQuantity(remainQuantity);
            position.setCurrentPrice(tradePrice);
            position.setProfitLoss(tradePrice.subtract(position.getAvgCost())
                    .multiply(new BigDecimal(remainQuantity)));
            position.setProfitLossPercent(tradePrice.subtract(position.getAvgCost())
                    .divide(position.getAvgCost(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")));
            position.setUpdateTime(LocalDateTime.now());
            positionMapper.updateById(position);
        }

        // 6. 记录交易日志
        TradeLog tradeLog = new TradeLog();
        tradeLog.setOrderId(order.getId());
        tradeLog.setUserId(userId);
        tradeLog.setStockCode(request.getStockCode());
        tradeLog.setAction("SELL");
        tradeLog.setPrice(tradePrice);
        tradeLog.setQuantity(request.getQuantity());
        tradeLog.setAmount(amount);
        tradeLog.setProfitLoss(profitLoss);
        tradeLog.setFee(fee);
        tradeLog.setCreateTime(LocalDateTime.now());
        tradeLogMapper.insert(tradeLog);

        log.info("用户{}卖出{} {}股, 价格={}, 金额={}, 盈亏={}, 手续费={}",
                userId, request.getStockCode(), request.getQuantity(), tradePrice, amount, profitLoss, fee);

        return order;
    }

    @Override
    public List<PositionDTO> getPositions(Long userId) {
        List<Position> positions = positionMapper.selectList(
                new LambdaQueryWrapper<Position>()
                        .eq(Position::getUserId, userId));

        List<PositionDTO> result = new ArrayList<>();
        for (Position pos : positions) {
            PositionDTO dto = new PositionDTO();
            dto.setId(pos.getId());
            dto.setStockCode(pos.getStockCode());
            dto.setStockName(pos.getStockName());
            dto.setMarket(pos.getMarket());
            dto.setQuantity(pos.getQuantity());
            dto.setAvgCost(pos.getAvgCost());
            dto.setCostAmount(pos.getAvgCost().multiply(new BigDecimal(pos.getQuantity())));

            // 获取实时价格
            try {
                StockDTO stockDTO = stockClient.getRealtimeQuote(pos.getStockCode());
                if (stockDTO != null && stockDTO.getCurrentPrice() != null
                        && stockDTO.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal currentPrice = stockDTO.getCurrentPrice();
                    dto.setCurrentPrice(currentPrice);
                    dto.setMarketValue(currentPrice.multiply(new BigDecimal(pos.getQuantity())));
                    dto.setProfitLoss(currentPrice.subtract(pos.getAvgCost())
                            .multiply(new BigDecimal(pos.getQuantity())));
                    dto.setProfitLossPercent(currentPrice.subtract(pos.getAvgCost())
                            .divide(pos.getAvgCost(), 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100")));

                    // 更新数据库中的价格
                    pos.setCurrentPrice(currentPrice);
                    pos.setProfitLoss(dto.getProfitLoss());
                    pos.setProfitLossPercent(dto.getProfitLossPercent());
                    pos.setUpdateTime(LocalDateTime.now());
                    positionMapper.updateById(pos);
                } else {
                    dto.setCurrentPrice(pos.getCurrentPrice());
                    dto.setMarketValue(pos.getCurrentPrice().multiply(new BigDecimal(pos.getQuantity())));
                    dto.setProfitLoss(pos.getProfitLoss());
                    dto.setProfitLossPercent(pos.getProfitLossPercent());
                }
            } catch (Exception e) {
                log.warn("获取实时价格失败 {}: {}", pos.getStockCode(), e.getMessage());
                dto.setCurrentPrice(pos.getCurrentPrice());
                dto.setMarketValue(pos.getCurrentPrice().multiply(new BigDecimal(pos.getQuantity())));
                dto.setProfitLoss(pos.getProfitLoss());
                dto.setProfitLossPercent(pos.getProfitLossPercent());
            }

            result.add(dto);
        }
        return result;
    }

    @Override
    public List<TradeOrder> getOrders(Long userId, String status, String direction, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<TradeOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradeOrder::getUserId, userId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(TradeOrder::getStatus, status);
        }
        if (direction != null && !direction.isEmpty()) {
            wrapper.eq(TradeOrder::getDirection, direction);
        }
        if (startDate != null) {
            wrapper.ge(TradeOrder::getCreateTime, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(TradeOrder::getCreateTime, endDate.atTime(23, 59, 59));
        }
        wrapper.orderByDesc(TradeOrder::getCreateTime);
        wrapper.last("LIMIT 100");
        return tradeOrderMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long userId, Long orderId) {
        TradeOrder order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此订单");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new RuntimeException("只能撤销待成交的订单");
        }

        order.setStatus("CANCELLED");
        order.setUpdateTime(LocalDateTime.now());
        tradeOrderMapper.updateById(order);
        return true;
    }

    @Override
    public AccountOverview getAccountOverview(Long userId) {
        AccountOverview overview = new AccountOverview();

        // 获取持仓列表（含实时价格）
        List<PositionDTO> positions = getPositions(userId);

        // 计算持仓市值
        BigDecimal positionValue = BigDecimal.ZERO;
        for (PositionDTO pos : positions) {
            if (pos.getMarketValue() != null) {
                positionValue = positionValue.add(pos.getMarketValue());
            }
        }

        // TODO: 从auth服务获取用户资金信息（这里使用简化逻辑，直接从交易日志推算）
        // 实际项目中应该通过Feign调用auth服务获取
        BigDecimal initialCapital = new BigDecimal("1000000.00");
        BigDecimal totalFee = getTotalFee(userId);
        BigDecimal sellIncome = getSellIncome(userId);
        BigDecimal buyCost = getBuyCost(userId);
        BigDecimal availableCash = initialCapital.subtract(buyCost).add(sellIncome).subtract(totalFee);

        overview.setInitialCapital(initialCapital);
        overview.setAvailableCash(availableCash);
        overview.setPositionValue(positionValue);
        overview.setTotalAssets(availableCash.add(positionValue));
        overview.setTotalProfitLoss(overview.getTotalAssets().subtract(initialCapital));

        if (initialCapital.compareTo(BigDecimal.ZERO) > 0) {
            overview.setTotalProfitLossPercent(overview.getTotalProfitLoss()
                    .divide(initialCapital, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")));
        }

        overview.setPositionCount(positions.size());
        overview.setTodayProfitLoss(BigDecimal.ZERO); // 简化处理

        return overview;
    }

    @Override
    public ProfitAnalysis getProfitAnalysis(Long userId) {
        ProfitAnalysis analysis = new ProfitAnalysis();

        // 查询所有卖出日志
        List<TradeLog> sellLogs = tradeLogMapper.selectList(
                new LambdaQueryWrapper<TradeLog>()
                        .eq(TradeLog::getUserId, userId)
                        .eq(TradeLog::getAction, "SELL"));

        // 查询所有买入日志
        List<TradeLog> buyLogs = tradeLogMapper.selectList(
                new LambdaQueryWrapper<TradeLog>()
                        .eq(TradeLog::getUserId, userId)
                        .eq(TradeLog::getAction, "BUY"));

        // 总收益
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalFee = BigDecimal.ZERO;
        int winTrades = 0;
        int loseTrades = 0;

        for (TradeLog log : sellLogs) {
            if (log.getProfitLoss() != null) {
                totalProfit = totalProfit.add(log.getProfitLoss());
                if (log.getProfitLoss().compareTo(BigDecimal.ZERO) > 0) {
                    winTrades++;
                } else if (log.getProfitLoss().compareTo(BigDecimal.ZERO) < 0) {
                    loseTrades++;
                }
            }
        }

        for (TradeLog log : buyLogs) {
            if (log.getFee() != null) {
                totalFee = totalFee.add(log.getFee());
            }
        }
        for (TradeLog log : sellLogs) {
            if (log.getFee() != null) {
                totalFee = totalFee.add(log.getFee());
            }
        }

        int totalTrades = sellLogs.size();
        BigDecimal winRate = BigDecimal.ZERO;
        if (totalTrades > 0) {
            winRate = new BigDecimal(winTrades)
                    .divide(new BigDecimal(totalTrades), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        BigDecimal initialCapital = new BigDecimal("1000000.00");
        BigDecimal totalProfitPercent = BigDecimal.ZERO;
        if (initialCapital.compareTo(BigDecimal.ZERO) > 0) {
            totalProfitPercent = totalProfit.divide(initialCapital, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        analysis.setTotalProfit(totalProfit);
        analysis.setTotalProfitPercent(totalProfitPercent);
        analysis.setTotalTrades(totalTrades);
        analysis.setWinTrades(winTrades);
        analysis.setLoseTrades(loseTrades);
        analysis.setWinRate(winRate);
        analysis.setTotalFee(totalFee);

        // 日收益（简化：当天卖出的盈亏之和）
        LocalDateTime todayStart = LocalDateTime.now().with(LocalTime.MIN);
        BigDecimal dailyProfit = BigDecimal.ZERO;
        for (TradeLog log : sellLogs) {
            if (log.getCreateTime() != null && log.getCreateTime().isAfter(todayStart)) {
                if (log.getProfitLoss() != null) {
                    dailyProfit = dailyProfit.add(log.getProfitLoss());
                }
            }
        }
        analysis.setDailyProfit(dailyProfit);
        analysis.setDailyProfitPercent(initialCapital.compareTo(BigDecimal.ZERO) > 0
                ? dailyProfit.divide(initialCapital, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO);

        // 周收益（简化）
        LocalDateTime weekStart = LocalDateTime.now().minusDays(7).with(LocalTime.MIN);
        BigDecimal weeklyProfit = BigDecimal.ZERO;
        for (TradeLog log : sellLogs) {
            if (log.getCreateTime() != null && log.getCreateTime().isAfter(weekStart)) {
                if (log.getProfitLoss() != null) {
                    weeklyProfit = weeklyProfit.add(log.getProfitLoss());
                }
            }
        }
        analysis.setWeeklyProfit(weeklyProfit);
        analysis.setWeeklyProfitPercent(initialCapital.compareTo(BigDecimal.ZERO) > 0
                ? weeklyProfit.divide(initialCapital, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO);

        // 月收益（简化）
        LocalDateTime monthStart = LocalDateTime.now().minusDays(30).with(LocalTime.MIN);
        BigDecimal monthlyProfit = BigDecimal.ZERO;
        for (TradeLog log : sellLogs) {
            if (log.getCreateTime() != null && log.getCreateTime().isAfter(monthStart)) {
                if (log.getProfitLoss() != null) {
                    monthlyProfit = monthlyProfit.add(log.getProfitLoss());
                }
            }
        }
        analysis.setMonthlyProfit(monthlyProfit);
        analysis.setMonthlyProfitPercent(initialCapital.compareTo(BigDecimal.ZERO) > 0
                ? monthlyProfit.divide(initialCapital, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO);

        return analysis;
    }

    @Override
    public List<Map<String, Object>> getProfitRecords(Long userId, String range) {
        List<Map<String, Object>> records = new ArrayList<>();

        // 根据range确定查询时间范围
        LocalDateTime startTime;
        switch (range) {
            case "1w":
                startTime = LocalDateTime.now().minusWeeks(1);
                break;
            case "1m":
                startTime = LocalDateTime.now().minusMonths(1);
                break;
            case "3m":
                startTime = LocalDateTime.now().minusMonths(3);
                break;
            case "1y":
                startTime = LocalDateTime.now().minusYears(1);
                break;
            default:
                startTime = LocalDateTime.now().minusMonths(1);
        }

        // 查询所有卖出日志
        List<TradeLog> sellLogs = tradeLogMapper.selectList(
                new LambdaQueryWrapper<TradeLog>()
                        .eq(TradeLog::getUserId, userId)
                        .eq(TradeLog::getAction, "SELL")
                        .ge(TradeLog::getCreateTime, startTime)
                        .orderByAsc(TradeLog::getCreateTime));

        BigDecimal cumulativeProfit = BigDecimal.ZERO;

        for (TradeLog log : sellLogs) {
            Map<String, Object> record = new HashMap<>();
            record.put("date", log.getCreateTime() != null ? log.getCreateTime().toLocalDate().toString() : "");
            record.put("profit", log.getProfitLoss() != null ? log.getProfitLoss() : BigDecimal.ZERO);
            cumulativeProfit = cumulativeProfit.add(log.getProfitLoss() != null ? log.getProfitLoss() : BigDecimal.ZERO);
            record.put("cumulativeProfit", cumulativeProfit);
            record.put("stockCode", log.getStockCode());
            records.add(record);
        }

        return records;
    }

    /**
     * 获取交易价格
     */
    private BigDecimal getTradePrice(String stockCode, String orderType, BigDecimal limitPrice) {
        if ("LIMIT".equals(orderType) && limitPrice != null) {
            return limitPrice;
        }
        // 市价单：获取实时价格
        try {
            StockDTO stockDTO = stockClient.getRealtimeQuote(stockCode);
            if (stockDTO != null && stockDTO.getCurrentPrice() != null
                    && stockDTO.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
                return stockDTO.getCurrentPrice();
            }
        } catch (Exception e) {
            log.warn("获取实时价格失败: {}", e.getMessage());
        }
        if ("LIMIT".equals(orderType)) {
            return limitPrice;
        }
        throw new RuntimeException("无法获取实时价格，请使用限价单");
    }

    /**
     * 计算手续费（万三，最低5元）
     */
    private BigDecimal calculateFee(BigDecimal amount) {
        BigDecimal fee = amount.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        return fee.compareTo(MIN_FEE) < 0 ? MIN_FEE : fee;
    }

    /**
     * 获取用户持仓
     */
    private Position getPosition(Long userId, String stockCode) {
        return positionMapper.selectOne(
                new LambdaQueryWrapper<Position>()
                        .eq(Position::getUserId, userId)
                        .eq(Position::getStockCode, stockCode));
    }

    /**
     * 获取总手续费
     */
    private BigDecimal getTotalFee(Long userId) {
        List<TradeLog> logs = tradeLogMapper.selectList(
                new LambdaQueryWrapper<TradeLog>().eq(TradeLog::getUserId, userId));
        BigDecimal total = BigDecimal.ZERO;
        for (TradeLog log : logs) {
            if (log.getFee() != null) {
                total = total.add(log.getFee());
            }
        }
        return total;
    }

    /**
     * 获取卖出总收入
     */
    private BigDecimal getSellIncome(Long userId) {
        List<TradeLog> logs = tradeLogMapper.selectList(
                new LambdaQueryWrapper<TradeLog>()
                        .eq(TradeLog::getUserId, userId)
                        .eq(TradeLog::getAction, "SELL"));
        BigDecimal total = BigDecimal.ZERO;
        for (TradeLog log : logs) {
            if (log.getAmount() != null) {
                total = total.add(log.getAmount());
            }
        }
        return total;
    }

    /**
     * 获取买入总成本
     */
    private BigDecimal getBuyCost(Long userId) {
        List<TradeLog> logs = tradeLogMapper.selectList(
                new LambdaQueryWrapper<TradeLog>()
                        .eq(TradeLog::getUserId, userId)
                        .eq(TradeLog::getAction, "BUY"));
        BigDecimal total = BigDecimal.ZERO;
        for (TradeLog log : logs) {
            if (log.getAmount() != null) {
                total = total.add(log.getAmount());
            }
        }
        return total;
    }

    // ===== 增强交易 API 实现 =====

    /** 交易模式存储：userId -> REAL / PAPER */
    private static final java.util.concurrent.ConcurrentHashMap<Long, String> tradeModeMap = new java.util.concurrent.ConcurrentHashMap<>();

    /** 模拟交易账户资金：userId -> availableCash */
    private static final java.util.concurrent.ConcurrentHashMap<Long, BigDecimal> paperAccountMap = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TradeOrder placeOrder(Long userId, OrderRequest request) {
        String mode = tradeModeMap.getOrDefault(userId, "REAL");

        // 根据方向调用已有 buy/sell 逻辑
        TradeOrder order;
        if ("BUY".equals(request.getDirection())) {
            BuyRequest buyReq = new BuyRequest();
            buyReq.setStockCode(request.getStockCode());
            buyReq.setStockName(request.getStockName());
            buyReq.setMarket(request.getMarket());
            buyReq.setOrderType(request.getOrderType());
            buyReq.setPrice(request.getPrice());
            buyReq.setQuantity(request.getQuantity());
            order = buy(userId, buyReq);
        } else if ("SELL".equals(request.getDirection())) {
            SellRequest sellReq = new SellRequest();
            sellReq.setStockCode(request.getStockCode());
            sellReq.setStockName(request.getStockName());
            sellReq.setMarket(request.getMarket());
            sellReq.setOrderType(request.getOrderType());
            sellReq.setPrice(request.getPrice());
            sellReq.setQuantity(request.getQuantity());
            order = sell(userId, sellReq);
        } else {
            throw new RuntimeException("无效的买卖方向: " + request.getDirection());
        }

        // 设置止损止盈
        if (request.getStopLoss() != null) {
            order.setStopLoss(request.getStopLoss());
        }
        if (request.getTakeProfit() != null) {
            order.setTakeProfit(request.getTakeProfit());
        }

        // 标记模拟交易
        order.setPaperTrade("PAPER".equals(mode));
        tradeOrderMapper.updateById(order);

        // 模拟交易：扣减/增加虚拟资金
        if ("PAPER".equals(mode)) {
            BigDecimal cash = paperAccountMap.getOrDefault(userId, new BigDecimal("1000000.00"));
            if ("BUY".equals(request.getDirection())) {
                BigDecimal cost = order.getAmount().add(order.getFee() != null ? order.getFee() : BigDecimal.ZERO);
                cash = cash.subtract(cost);
            } else if ("SELL".equals(request.getDirection())) {
                BigDecimal income = order.getAmount().subtract(order.getFee() != null ? order.getFee() : BigDecimal.ZERO);
                cash = cash.add(income);
            }
            paperAccountMap.put(userId, cash);
            log.info("模拟交易更新 - 用户{} 可用资金: {}", userId, cash);
        }

        return order;
    }

    @Override
    public List<TradeOrder> checkStopConditions(Long userId, Long orderId, BigDecimal currentPrice) {
        List<TradeOrder> triggered = new ArrayList<>();

        LambdaQueryWrapper<TradeOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradeOrder::getUserId, userId);
        if (orderId != null) {
            wrapper.eq(TradeOrder::getId, orderId);
        }
        wrapper.eq(TradeOrder::getStatus, "FILLED");
        wrapper.and(w -> w.isNotNull(TradeOrder::getStopLoss)
                .or().isNotNull(TradeOrder::getTakeProfit));

        List<TradeOrder> orders = tradeOrderMapper.selectList(wrapper);
        for (TradeOrder order : orders) {
            boolean shouldTrigger = false;
            if (order.getStopLoss() != null && currentPrice.compareTo(order.getStopLoss()) <= 0) {
                shouldTrigger = true;
                log.info("止损触发: orderId={}, stockCode={}, stopLoss={}, currentPrice={}",
                        order.getId(), order.getStockCode(), order.getStopLoss(), currentPrice);
            }
            if (order.getTakeProfit() != null && currentPrice.compareTo(order.getTakeProfit()) >= 0) {
                shouldTrigger = true;
                log.info("止盈触发: orderId={}, stockCode={}, takeProfit={}, currentPrice={}",
                        order.getId(), order.getStockCode(), order.getTakeProfit(), currentPrice);
            }
            if (shouldTrigger) {
                triggered.add(order);
            }
        }
        return triggered;
    }

    @Override
    public String getTradeMode(Long userId) {
        return tradeModeMap.getOrDefault(userId, "REAL");
    }

    @Override
    public String switchTradeMode(Long userId, String mode) {
        if (!"REAL".equals(mode) && !"PAPER".equals(mode)) {
            throw new RuntimeException("无效的交易模式，仅支持 REAL 和 PAPER");
        }
        tradeModeMap.put(userId, mode);
        // 初始化模拟账户资金
        if ("PAPER".equals(mode)) {
            paperAccountMap.putIfAbsent(userId, new BigDecimal("1000000.00"));
        }
        log.info("用户{} 切换交易模式为: {}", userId, mode);
        return mode;
    }

    @Override
    public AccountOverview getPaperAccountOverview(Long userId) {
        AccountOverview overview = new AccountOverview();

        BigDecimal availableCash = paperAccountMap.getOrDefault(userId, new BigDecimal("1000000.00"));
        BigDecimal initialCapital = new BigDecimal("1000000.00");

        // 获取模拟交易的持仓
        List<Position> positions = positionMapper.selectList(
                new LambdaQueryWrapper<Position>()
                        .eq(Position::getUserId, userId));

        // 计算持仓市值
        BigDecimal positionValue = BigDecimal.ZERO;
        for (Position pos : positions) {
            try {
                StockDTO stockDTO = stockClient.getRealtimeQuote(pos.getStockCode());
                if (stockDTO != null && stockDTO.getCurrentPrice() != null
                        && stockDTO.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
                    positionValue = positionValue.add(stockDTO.getCurrentPrice().multiply(new BigDecimal(pos.getQuantity())));
                } else {
                    positionValue = positionValue.add(pos.getCurrentPrice().multiply(new BigDecimal(pos.getQuantity())));
                }
            } catch (Exception e) {
                positionValue = positionValue.add(pos.getCurrentPrice().multiply(new BigDecimal(pos.getQuantity())));
            }
        }

        overview.setInitialCapital(initialCapital);
        overview.setAvailableCash(availableCash);
        overview.setPositionValue(positionValue);
        overview.setTotalAssets(availableCash.add(positionValue));
        overview.setTotalProfitLoss(overview.getTotalAssets().subtract(initialCapital));
        if (initialCapital.compareTo(BigDecimal.ZERO) > 0) {
            overview.setTotalProfitLossPercent(overview.getTotalProfitLoss()
                    .divide(initialCapital, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")));
        }
        overview.setPositionCount(positions.size());
        overview.setTodayProfitLoss(BigDecimal.ZERO);

        return overview;
    }
}
