package com.stock.stock.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stock.stock.dto.KlineData;
import com.stock.stock.dto.StockDTO;
import com.stock.stock.entity.StockDaily;
import com.stock.stock.entity.StockInfo;
import com.stock.stock.mapper.StockDailyMapper;
import com.stock.stock.mapper.StockInfoMapper;
import com.stock.stock.service.StockService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class StockServiceImpl extends ServiceImpl<StockInfoMapper, StockInfo> implements StockService {

    private final StockDailyMapper stockDailyMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public StockServiceImpl(StockDailyMapper stockDailyMapper) {
        this.stockDailyMapper = stockDailyMapper;
    }

    @Override
    public List<StockDTO> searchStock(String keyword, String market) {
        LambdaQueryWrapper<StockInfo> wrapper = new LambdaQueryWrapper<>();
        if (market != null && !market.isEmpty()) {
            wrapper.eq(StockInfo::getMarket, market);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(StockInfo::getCode, keyword)
                    .or().like(StockInfo::getName, keyword));
        }
        wrapper.last("LIMIT 50");

        List<StockInfo> list = list(wrapper);
        return convertToDTOList(list);
    }

    @Override
    public StockDTO getRealtimeQuote(String code) {
        // 先从数据库查询
        StockInfo stockInfo = getOne(new LambdaQueryWrapper<StockInfo>()
                .eq(StockInfo::getCode, code));

        if (stockInfo != null) {
            // 尝试获取实时行情
            try {
                StockDTO realtime = null;
                if ("A_STOCK".equals(stockInfo.getMarket())) {
                    realtime = getAStockQuote(code);
                } else if ("NASDAQ".equals(stockInfo.getMarket())) {
                    realtime = getUSStockQuote(code);
                }
                if (realtime != null) {
                    saveOrUpdateQuote(realtime);
                    return realtime;
                }
            } catch (Exception e) {
                log.warn("获取实时行情失败, 使用数据库数据: {}", e.getMessage());
            }

            return convertToDTO(stockInfo);
        }

        // 数据库中没有，尝试从API获取
        try {
            StockDTO realtime = getAStockQuote(code);
            if (realtime == null) {
                realtime = getUSStockQuote(code);
            }
            if (realtime != null) {
                saveOrUpdateQuote(realtime);
            }
            return realtime;
        } catch (Exception e) {
            log.error("获取实时行情失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<KlineData> getKlineData(String code, String period, int limit) {
        // 先从数据库查询
        LambdaQueryWrapper<StockDaily> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StockDaily::getStockCode, code);
        wrapper.orderByDesc(StockDaily::getTradeDate);
        wrapper.last("LIMIT " + limit);

        List<StockDaily> dailyList = stockDailyMapper.selectList(wrapper);

        // 如果数据不足，尝试生成模拟数据
        if (dailyList == null || dailyList.size() < Math.min(limit, 60)) {
            int need = Math.max(limit, 60);
            log.info("股票{} K线数据不足({}条)，生成模拟数据，目标{}条", code,
                    dailyList == null ? 0 : dailyList.size(), need);
            generateMockKlineData(code, need);
            // 重新查询
            dailyList = stockDailyMapper.selectList(wrapper);
            log.info("重新查询股票{}的K线数据，共{}条", code, dailyList.size());
        }

        List<KlineData> result = new ArrayList<>();
        for (StockDaily daily : dailyList) {
            KlineData kline = new KlineData();
            kline.setDate(daily.getTradeDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            kline.setOpen(daily.getOpenPrice());
            kline.setHigh(daily.getHighPrice());
            kline.setLow(daily.getLowPrice());
            kline.setClose(daily.getClosePrice());
            kline.setVolume(daily.getVolume());
            kline.setTurnover(daily.getTurnover());

            if (daily.getPrevClose() != null && daily.getPrevClose().compareTo(BigDecimal.ZERO) > 0) {
                kline.setChangePercent(daily.getClosePrice()
                        .subtract(daily.getPrevClose())
                        .divide(daily.getPrevClose(), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")));
            }
            result.add(kline);
        }

        // 反转使日期从旧到新
        java.util.Collections.reverse(result);
        return result;
    }

    @Override
    public List<StockDTO> getStockList(String market, int page, int size) {
        Page<StockInfo> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<StockInfo> wrapper = new LambdaQueryWrapper<>();
        if (market != null && !market.isEmpty()) {
            wrapper.eq(StockInfo::getMarket, market);
        }
        wrapper.orderByAsc(StockInfo::getCode);

        Page<StockInfo> result = page(pageParam, wrapper);
        return convertToDTOList(result.getRecords());
    }

    @Override
    public StockDTO getAStockQuote(String code) {
        try {
            // 转换代码格式: 600519 -> sh600519, 000001 -> sz000001, 002594 -> sz002594
            String sinaCode;
            if (code.startsWith("6")) {
                sinaCode = "sh" + code;
            } else if (code.startsWith("0") || code.startsWith("3")) {
                sinaCode = "sz" + code;
            } else {
                sinaCode = code;
            }

            String url = "https://hq.sinajs.cn/list=" + sinaCode;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Referer", "https://finance.sina.com.cn")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.body() != null) {
                    String body = response.body().string();
                    return parseSinaQuote(body, code);
                }
            }
        } catch (Exception e) {
            log.warn("获取A股行情失败 {}: {}", code, e.getMessage());
        }
        return null;
    }

    @Override
    public StockDTO getUSStockQuote(String code) {
        try {
            // 使用腾讯财经API获取美股行情
            String url = "https://qt.gtimg.cn/q=us" + code.toLowerCase();
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Referer", "https://finance.qq.com")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.body() != null) {
                    String body = response.body().string();
                    return parseTencentUSQuote(body, code);
                }
            }
        } catch (Exception e) {
            log.warn("获取美股行情失败 {}: {}", code, e.getMessage());
        }
        return null;
    }

    @Override
    public void saveOrUpdateQuote(StockDTO dto) {
        StockInfo exist = getOne(new LambdaQueryWrapper<StockInfo>()
                .eq(StockInfo::getCode, dto.getCode()));

        if (exist != null) {
            exist.setCurrentPrice(dto.getCurrentPrice());
            exist.setChangePercent(dto.getChangePercent());
            exist.setVolume(dto.getVolume());
            exist.setTurnover(dto.getTurnover());
            exist.setHighPrice(dto.getHighPrice());
            exist.setLowPrice(dto.getLowPrice());
            exist.setOpenPrice(dto.getOpenPrice());
            exist.setPrevClose(dto.getPrevClose());
            exist.setUpdateTime(LocalDateTime.now());
            updateById(exist);
        } else {
            StockInfo info = new StockInfo();
            info.setCode(dto.getCode());
            info.setName(dto.getName());
            info.setMarket(dto.getMarket());
            info.setCurrentPrice(dto.getCurrentPrice());
            info.setChangePercent(dto.getChangePercent());
            info.setVolume(dto.getVolume());
            info.setTurnover(dto.getTurnover());
            info.setHighPrice(dto.getHighPrice());
            info.setLowPrice(dto.getLowPrice());
            info.setOpenPrice(dto.getOpenPrice());
            info.setPrevClose(dto.getPrevClose());
            info.setUpdateTime(LocalDateTime.now());
            save(info);
        }
    }

    @Override
    public void saveKlineData(String stockCode, List<KlineData> klineDataList) {
        for (KlineData kline : klineDataList) {
            StockDaily exist = stockDailyMapper.selectOne(
                    new LambdaQueryWrapper<StockDaily>()
                            .eq(StockDaily::getStockCode, stockCode)
                            .eq(StockDaily::getTradeDate, LocalDate.parse(kline.getDate())));

            if (exist != null) {
                exist.setOpenPrice(kline.getOpen());
                exist.setHighPrice(kline.getHigh());
                exist.setLowPrice(kline.getLow());
                exist.setClosePrice(kline.getClose());
                exist.setVolume(kline.getVolume());
                exist.setTurnover(kline.getTurnover());
                stockDailyMapper.updateById(exist);
            } else {
                StockDaily daily = new StockDaily();
                daily.setStockCode(stockCode);
                daily.setTradeDate(LocalDate.parse(kline.getDate()));
                daily.setOpenPrice(kline.getOpen());
                daily.setHighPrice(kline.getHigh());
                daily.setLowPrice(kline.getLow());
                daily.setClosePrice(kline.getClose());
                daily.setVolume(kline.getVolume());
                daily.setTurnover(kline.getTurnover());
                stockDailyMapper.insert(daily);
            }
        }
    }

    /**
     * 当数据库无K线数据时，使用随机游走算法生成模拟K线数据
     * 从limit个交易日之前开始，跳过周末，生成每日OHLCV数据并写入stock_daily表
     */
    private void generateMockKlineData(String code, int limit) {
        // 尝试获取当前实时价格作为基准
        BigDecimal basePrice = BigDecimal.valueOf(50.00);
        try {
            StockDTO quote = getAStockQuote(code);
            if (quote != null && quote.getCurrentPrice() != null
                    && quote.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
                basePrice = quote.getCurrentPrice();
            }
        } catch (Exception e) {
            log.warn("获取股票{}实时价格失败，使用默认基准价50.00", code);
        }

        Random rand = new Random(code.hashCode()); // 固定种子使每次生成一致
        LocalDate today = LocalDate.now();
        BigDecimal price = basePrice;
        int tradingDays = 0;
        int daysBack = 0;

        while (tradingDays < limit) {
            LocalDate date = today.minusDays(daysBack);
            daysBack++;

            // 跳过周末（非交易日）
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }

            // 检查该日数据是否已存在
            StockDaily exist = stockDailyMapper.selectOne(
                    new LambdaQueryWrapper<StockDaily>()
                            .eq(StockDaily::getStockCode, code)
                            .eq(StockDaily::getTradeDate, date));
            if (exist != null) {
                tradingDays++;
                continue;
            }

            // 随机游走: -4.5% ~ +4.5% 的日涨跌幅
            double change = (rand.nextDouble() - 0.5) * 0.09;
            BigDecimal close = price.multiply(BigDecimal.valueOf(1 + change));

            // 生成OHLCV
            BigDecimal open = price;
            BigDecimal high = open.max(close).multiply(BigDecimal.valueOf(1 + rand.nextDouble() * 0.015));
            BigDecimal low = open.min(close).multiply(BigDecimal.valueOf(1 - rand.nextDouble() * 0.015));
            long volume = (long) (rand.nextDouble() * 8000000 + 2000000);
            BigDecimal turnover = close.multiply(BigDecimal.valueOf(volume));

            // 保存到数据库
            StockDaily daily = new StockDaily();
            daily.setStockCode(code);
            daily.setTradeDate(date);
            daily.setOpenPrice(open.setScale(2, RoundingMode.HALF_UP));
            daily.setHighPrice(high.setScale(2, RoundingMode.HALF_UP));
            daily.setLowPrice(low.setScale(2, RoundingMode.HALF_UP));
            daily.setClosePrice(close.setScale(2, RoundingMode.HALF_UP));
            daily.setVolume(volume);
            daily.setTurnover(turnover.setScale(2, RoundingMode.HALF_UP));
            daily.setPrevClose(price.setScale(2, RoundingMode.HALF_UP));
            stockDailyMapper.insert(daily);

            price = close;
            tradingDays++;
        }

        log.info("为股票{}生成了{}条模拟K线数据，基准价={}", code, limit, basePrice);
    }

    /**
     * 解析新浪A股行情数据
     */
    private StockDTO parseSinaQuote(String body, String code) {
        try {
            // 格式: var hq_str_sh600519="贵州茅台,开盘价,昨收,当前价,最高,最低,买一,卖一,成交量,成交额,..."
            int start = body.indexOf("\"");
            int end = body.lastIndexOf("\"");
            if (start < 0 || end <= start) {
                return null;
            }
            String data = body.substring(start + 1, end);
            String[] fields = data.split(",");

            if (fields.length < 32) {
                return null;
            }

            StockDTO dto = new StockDTO();
            dto.setCode(code);
            dto.setName(fields[0]);
            dto.setMarket("A_STOCK");
            dto.setOpenPrice(new BigDecimal(fields[1]));
            dto.setPrevClose(new BigDecimal(fields[2]));
            dto.setCurrentPrice(new BigDecimal(fields[3]));
            dto.setHighPrice(new BigDecimal(fields[4]));
            dto.setLowPrice(new BigDecimal(fields[5]));
            dto.setVolume(Long.parseLong(fields[8]));
            dto.setTurnover(new BigDecimal(fields[9]));

            // 计算涨跌幅
            if (dto.getPrevClose().compareTo(BigDecimal.ZERO) > 0) {
                dto.setChangePercent(dto.getCurrentPrice()
                        .subtract(dto.getPrevClose())
                        .divide(dto.getPrevClose(), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")));
            }

            return dto;
        } catch (Exception e) {
            log.warn("解析新浪行情数据失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析腾讯美股行情数据
     */
    private StockDTO parseTencentUSQuote(String body, String code) {
        try {
            // 腾讯美股数据格式较为复杂，这里做简化解析
            String[] lines = body.split(";");
            for (String line : lines) {
                if (line.contains("~")) {
                    String[] fields = line.split("~");
                    if (fields.length > 10) {
                        StockDTO dto = new StockDTO();
                        dto.setCode(code.toUpperCase());
                        dto.setName(fields[1]);
                        dto.setMarket("NASDAQ");
                        dto.setCurrentPrice(new BigDecimal(fields[3]));
                        dto.setPrevClose(new BigDecimal(fields[4]));
                        dto.setOpenPrice(new BigDecimal(fields[5]));
                        dto.setHighPrice(new BigDecimal(fields[33]));
                        dto.setLowPrice(new BigDecimal(fields[34]));
                        dto.setVolume(Long.parseLong(fields[6]));
                        dto.setTurnover(new BigDecimal(fields[37]));

                        if (dto.getPrevClose().compareTo(BigDecimal.ZERO) > 0) {
                            dto.setChangePercent(dto.getCurrentPrice()
                                    .subtract(dto.getPrevClose())
                                    .divide(dto.getPrevClose(), 4, RoundingMode.HALF_UP)
                                    .multiply(new BigDecimal("100")));
                        }
                        return dto;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析腾讯美股行情数据失败: {}", e.getMessage());
        }
        return null;
    }

    private List<StockDTO> convertToDTOList(List<StockInfo> list) {
        List<StockDTO> result = new ArrayList<>();
        for (StockInfo info : list) {
            result.add(convertToDTO(info));
        }
        return result;
    }

    private StockDTO convertToDTO(StockInfo info) {
        StockDTO dto = new StockDTO();
        dto.setCode(info.getCode());
        dto.setName(info.getName());
        dto.setMarket(info.getMarket());
        dto.setCurrentPrice(info.getCurrentPrice());
        dto.setChangePercent(info.getChangePercent());
        dto.setVolume(info.getVolume());
        dto.setTurnover(info.getTurnover());
        dto.setHighPrice(info.getHighPrice());
        dto.setLowPrice(info.getLowPrice());
        dto.setOpenPrice(info.getOpenPrice());
        dto.setPrevClose(info.getPrevClose());
        return dto;
    }
}
