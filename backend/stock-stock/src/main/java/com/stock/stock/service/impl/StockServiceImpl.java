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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

            log.warn("股票{}没有可用实时行情，不返回数据库旧行情", code);
            return null;
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
        List<KlineData> live = fetchEastMoneyKline(code, period, limit);
        if (!live.isEmpty()) {
            saveKlineData(code, live);
            return live;
        }
        log.warn("股票{}未获取到真实K线数据，返回空列表，不使用模拟行情", code);
        return new ArrayList<>();
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

    private List<KlineData> fetchEastMoneyKline(String code, String period, int limit) {
        List<KlineData> result = new ArrayList<>();
        try {
            String secid = eastMoneySecId(code);
            String klt = eastMoneyPeriod(period);
            String url = "https://push2his.eastmoney.com/api/qt/stock/kline/get"
                    + "?secid=" + secid
                    + "&fields1=f1,f2,f3,f4,f5,f6"
                    + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"
                    + "&klt=" + klt
                    + "&fqt=1&end=20500101&lmt=" + Math.max(1, limit);
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Referer", "https://quote.eastmoney.com")
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.body() == null || !response.isSuccessful()) {
                    return result;
                }
                JSONObject json = JSON.parseObject(response.body().string());
                JSONObject data = json.getJSONObject("data");
                if (data == null) {
                    return result;
                }
                JSONArray klines = data.getJSONArray("klines");
                if (klines == null) {
                    return result;
                }
                for (int i = 0; i < klines.size(); i++) {
                    String[] fields = klines.getString(i).split(",");
                    if (fields.length < 7) {
                        continue;
                    }
                    KlineData kline = new KlineData();
                    kline.setDate(fields[0]);
                    kline.setOpen(new BigDecimal(fields[1]));
                    kline.setClose(new BigDecimal(fields[2]));
                    kline.setHigh(new BigDecimal(fields[3]));
                    kline.setLow(new BigDecimal(fields[4]));
                    kline.setVolume(Long.parseLong(fields[5]));
                    kline.setTurnover(new BigDecimal(fields[6]));
                    if (fields.length > 8 && !fields[8].isEmpty() && !"-".equals(fields[8])) {
                        kline.setChangePercent(new BigDecimal(fields[8]));
                    }
                    result.add(kline);
                }
            }
        } catch (Exception e) {
            log.warn("获取东方财富K线失败 {}: {}", code, e.getMessage());
        }
        return result;
    }

    private String eastMoneySecId(String code) {
        if (code.startsWith("6") || code.startsWith("5")) {
            return "1." + code;
        }
        return "0." + code;
    }

    private String eastMoneyPeriod(String period) {
        if ("weekly".equalsIgnoreCase(period)) return "102";
        if ("monthly".equalsIgnoreCase(period)) return "103";
        return "101";
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
            if (dto.getCurrentPrice().compareTo(BigDecimal.ZERO) <= 0
                    || dto.getOpenPrice().compareTo(BigDecimal.ZERO) <= 0
                    || dto.getVolume() == null
                    || dto.getVolume() <= 0) {
                return null;
            }

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
