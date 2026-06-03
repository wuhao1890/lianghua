package com.stock.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stock.stock.dto.KlineData;
import com.stock.stock.entity.StockDaily;
import com.stock.stock.entity.StockInfo;
import com.stock.stock.mapper.StockDailyMapper;
import com.stock.stock.mapper.StockInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * K线数据缓存服务
 * 定时从新浪API拉取K线数据并持久化到 stock_daily 表
 * 数据来源：http://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData?symbol=sh600519&datalen=120
 */
@Slf4j
@Service
@EnableScheduling
public class DataCacheService {

    private final StockDailyMapper stockDailyMapper;
    private final StockInfoMapper stockInfoMapper;

    /** 缓存统计 */
    private final ConcurrentHashMap<String, Object> cacheStats = new ConcurrentHashMap<>();

    /** 最近同步日期 */
    private String lastSyncDate = "";

    public DataCacheService(StockDailyMapper stockDailyMapper, StockInfoMapper stockInfoMapper) {
        this.stockDailyMapper = stockDailyMapper;
        this.stockInfoMapper = stockInfoMapper;
    }

    @PostConstruct
    public void init() {
        log.info("数据缓存服务已初始化");
    }

    /**
     * 每60分钟执行一次K线数据缓存
     * 检查所有股票的最新K线数据，如果缺失则从新浪拉取
     */
    @Scheduled(fixedRate = 3600000) // 60分钟
    public void cacheKlineData() {
        log.info("开始定时缓存K线数据...");
        LocalDate today = LocalDate.now();
        lastSyncDate = today.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 获取所有A股
        List<StockInfo> stocks = stockInfoMapper.selectList(
                new LambdaQueryWrapper<StockInfo>()
                        .eq(StockInfo::getMarket, "A_STOCK")
                        .last("LIMIT 10")); // 限制每次10只，避免请求过多

        int fetched = 0;
        for (StockInfo stock : stocks) {
            try {
                // 检查最新数据日期
                StockDaily latest = stockDailyMapper.selectOne(
                        new LambdaQueryWrapper<StockDaily>()
                                .eq(StockDaily::getStockCode, stock.getCode())
                                .orderByDesc(StockDaily::getTradeDate)
                                .last("LIMIT 1"));

                // 如果已有最近3天的数据则跳过
                if (latest != null && latest.getTradeDate() != null) {
                    long daysDiff = today.toEpochDay() - latest.getTradeDate().toEpochDay();
                    if (daysDiff <= 3) {
                        continue; // 数据够新，跳过
                    }
                }

                // 从新浪拉取K线数据
                List<KlineData> klineList = fetchSinaKline(stock.getCode(), 120);
                if (klineList != null && !klineList.isEmpty()) {
                    saveKlineBatch(stock.getCode(), klineList);
                    fetched += klineList.size();
                    log.debug("缓存K线: {} ({}) - {}条", stock.getCode(), stock.getName(), klineList.size());
                }

                // 控制请求频率
                Thread.sleep(500);
            } catch (Exception e) {
                log.warn("缓存K线失败: {} - {}", stock.getCode(), e.getMessage());
            }
        }

        log.info("定时缓存完成: 共获取 {} 条K线数据", fetched);
        cacheStats.put("lastSync", lastSyncDate);
        cacheStats.put("fetched", fetched);
    }

    /**
     * 从新浪财经API获取K线数据
     */
    public List<KlineData> fetchSinaKline(String code, int datalen) {
        String sinaSymbol;
        if (code.startsWith("6") || code.startsWith("9")) {
            sinaSymbol = "sh" + code;
        } else {
            sinaSymbol = "sz" + code;
        }

        String url = "https://quotes.sina.cn/cn/api/json_v2.php/"
                + "CN_MarketDataService.getKLineData?symbol=" + sinaSymbol + "&scale=240&datalen=" + datalen;

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            conn.disconnect();

            String json = sb.toString();
            return parseSinaKlineJson(json);
        } catch (Exception e) {
            log.warn("获取新浪K线失败 {}: {}", code, e.getMessage());
            return null;
        }
    }

    /**
     * 解析新浪K线JSON
     * 格式: [{"day":"2026-05-28","open":"96.50","high":"98.00","low":"95.50","close":"97.30","volume":"1234567"}]
     */
    private List<KlineData> parseSinaKlineJson(String json) {
        List<KlineData> result = new ArrayList<>();
        if (json == null || json.isEmpty() || "null".equals(json)) return result;

        try {
            // 简易JSON解析（不依赖fastjson，自己解析）
            json = json.trim();
            if (!json.startsWith("[") || !json.endsWith("]")) return result;
            json = json.substring(1, json.length() - 1);

            int pos = 0;
            while (pos < json.length()) {
                int braceStart = json.indexOf("{", pos);
                int braceEnd = json.indexOf("}", braceStart);
                if (braceStart < 0 || braceEnd < 0) break;

                String item = json.substring(braceStart + 1, braceEnd);
                KlineData kd = new KlineData();

                String[] parts = item.split(",");
                for (String part : parts) {
                    String[] kv = part.split(":", 2);
                    if (kv.length != 2) continue;
                    String key = kv[0].replace("\"", "").trim();
                    String val = kv[1].replace("\"", "").trim();

                    switch (key) {
                        case "day": kd.setDate(val); break;
                        case "open": kd.setOpen(new BigDecimal(val)); break;
                        case "high": kd.setHigh(new BigDecimal(val)); break;
                        case "low": kd.setLow(new BigDecimal(val)); break;
                        case "close": kd.setClose(new BigDecimal(val)); break;
                        case "volume":
                            try { kd.setVolume(Long.parseLong(val)); } catch (Exception ignored) {}
                            break;
                    }
                }

                if (kd.getDate() != null && kd.getClose() != null) {
                    result.add(kd);
                }
                pos = braceEnd + 1;
            }
        } catch (Exception e) {
            log.warn("解析新浪K线JSON失败: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 批量保存K线数据
     */
    private void saveKlineBatch(String stockCode, List<KlineData> klineList) {
        for (KlineData kline : klineList) {
            if (kline.getDate() == null) continue;
            LocalDate tradeDate;
            try {
                tradeDate = LocalDate.parse(kline.getDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (Exception e) {
                continue;
            }

            StockDaily exist = stockDailyMapper.selectOne(
                    new LambdaQueryWrapper<StockDaily>()
                            .eq(StockDaily::getStockCode, stockCode)
                            .eq(StockDaily::getTradeDate, tradeDate));

            if (exist != null) {
                exist.setOpenPrice(kline.getOpen());
                exist.setHighPrice(kline.getHigh());
                exist.setLowPrice(kline.getLow());
                exist.setClosePrice(kline.getClose());
                exist.setVolume(kline.getVolume());
                stockDailyMapper.updateById(exist);
            } else {
                StockDaily daily = new StockDaily();
                daily.setStockCode(stockCode);
                daily.setTradeDate(tradeDate);
                daily.setOpenPrice(kline.getOpen());
                daily.setHighPrice(kline.getHigh());
                daily.setLowPrice(kline.getLow());
                daily.setClosePrice(kline.getClose());
                daily.setVolume(kline.getVolume());
                stockDailyMapper.insert(daily);
            }
        }
    }

    /**
     * 手动触发缓存（API调用）
     */
    public Map<String, Object> triggerCache() {
        cacheKlineData();
        Map<String, Object> result = new HashMap<>();
        result.putAll(cacheStats);
        return result;
    }

    /**
     * 获取缓存状态
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> result = new HashMap<>();
        result.putAll(cacheStats);
        result.put("lastSyncDate", lastSyncDate);
        return result;
    }

    /**
     * 清理无效的K线数据（日期在未来或数据异常）
     * 删除所有tradeDate > 今天 的记录
     */
    public int cleanInvalidKlineData() {
        LocalDate today = LocalDate.now();
        // 删除所有日期在未来的数据
        int deleted = stockDailyMapper.delete(
                new LambdaQueryWrapper<StockDaily>()
                        .gt(StockDaily::getTradeDate, today));
        log.info("清理无效K线数据: 删除{}条(日期>{})", deleted, today);
        return deleted;
    }

    /**
     * 清空所有K线数据并重新从新浪获取真实数据
     */
    public int clearAllKlineData() {
        // 删除所有K线数据
        int deleted = stockDailyMapper.delete(new LambdaQueryWrapper<>());
        log.info("清空所有K线数据: 删除{}条", deleted);
        return deleted;
    }
}
