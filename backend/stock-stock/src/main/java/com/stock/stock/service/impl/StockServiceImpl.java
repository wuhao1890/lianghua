package com.stock.stock.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stock.stock.dto.KlineData;
import com.stock.stock.dto.StockDTO;
import com.stock.stock.entity.StockDaily;
import com.stock.stock.entity.StockInfo;
import com.stock.stock.mapper.StockDailyMapper;
import com.stock.stock.mapper.StockInfoMapper;
import com.stock.stock.service.DataCacheService;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class StockServiceImpl extends ServiceImpl<StockInfoMapper, StockInfo> implements StockService {

    private final StockDailyMapper stockDailyMapper;
    private final DataCacheService dataCacheService;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public StockServiceImpl(StockDailyMapper stockDailyMapper, DataCacheService dataCacheService) {
        this.stockDailyMapper = stockDailyMapper;
        this.dataCacheService = dataCacheService;
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

        // 如果数据不足，从新浪拉取真实K线数据
        if (dailyList == null || dailyList.size() < Math.min(limit, 60)) {
            int need = Math.max(limit, 60);
            log.info("股票{} K线数据不足({}条)，从新浪拉取真实数据，目标{}条", code,
                    dailyList == null ? 0 : dailyList.size(), need);
            try {
                List<KlineData> sinaData = dataCacheService.fetchSinaKline(code, need);
                if (sinaData != null && !sinaData.isEmpty()) {
                    saveKlineData(code, sinaData);
                    log.info("从新浪获取K线数据成功: {} 条", sinaData.size());
                } else {
                    log.warn("新浪K线数据为空，不再生成假数据");
                }
            } catch (Exception e) {
                log.warn("获取新浪K线数据失败: {}", e.getMessage());
            }
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
    public Map<String, Object> screener(BigDecimal minPrice, BigDecimal maxPrice,
                                         BigDecimal minChange, BigDecimal maxChange,
                                         Long minVolume, String keyword, String sector,
                                         String sortField, String sortOrder,
                                         int page, int size) {
        QueryWrapper<StockInfo> wrapper = new QueryWrapper<>();

        // 价格范围
        if (minPrice != null) {
            wrapper.ge("current_price", minPrice);
        }
        if (maxPrice != null) {
            wrapper.le("current_price", maxPrice);
        }

        // 涨跌幅范围
        if (minChange != null) {
            wrapper.ge("change_percent", minChange);
        }
        if (maxChange != null) {
            wrapper.le("change_percent", maxChange);
        }

        // 最小成交量
        if (minVolume != null) {
            wrapper.ge("volume", minVolume);
        }

        // 关键字查询（代码或名称LIKE）
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like("code", keyword)
                    .or().like("name", keyword));
        }

        // 板块/市场筛选
        if (sector != null && !sector.isEmpty()) {
            wrapper.eq("market", sector);
        }

        // 排序
        if (sortField != null && !sortField.isEmpty()) {
            // 将驼峰字段名转为下划线
            String dbField = camelToUnderscore(sortField);
            boolean asc = "asc".equalsIgnoreCase(sortOrder);
            if (asc) {
                wrapper.orderByAsc(dbField);
            } else {
                wrapper.orderByDesc(dbField);
            }
        } else {
            wrapper.orderByDesc("change_percent");
        }

        // 分页
        Page<StockInfo> pageParam = new Page<>(page, size);
        Page<StockInfo> result = baseMapper.selectPage(pageParam, wrapper);

        List<StockDTO> dtoList = convertToDTOList(result.getRecords());

        Map<String, Object> map = new HashMap<>();
        map.put("list", dtoList);
        map.put("total", result.getTotal());
        map.put("page", result.getCurrent());
        map.put("size", result.getSize());
        map.put("pages", result.getPages());
        return map;
    }

    /**
     * 将驼峰字段名转为下划线（如 changePercent -> change_percent）
     */
    private String camelToUnderscore(String camel) {
        if (camel == null || camel.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
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

    /**
     * 获取资金流向（东财API）
     * 返回主力净流入、超大单、大单、中单、小单
     */
    @Override
    public Map<String, Object> getCapitalFlow(String code) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // 东财资金流向API - 取当天最后一条（收盘数据）
            String secid = code.startsWith("6") ? "1." + code : "0." + code;
            String url = "https://push2.eastmoney.com/api/qt/stock/fflow/kline/get?lmt=1&klt=1&secid=" + secid
                    + "&fields1=f1,f2,f3,f7&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f62,f63";
            
            String body = fetchUrl(url);
            if (body == null || body.isEmpty()) {
                return result;
            }
            
            JSONObject json = JSONObject.parseObject(body);
            JSONObject data = json.getJSONObject("data");
            if (data != null) {
                Object klines = data.get("klines");
                if (klines instanceof List) {
                    List<?> list = (List<?>) klines;
                    if (!list.isEmpty()) {
                        // kline格式: "时间,主力净流入,?,?,大单净流入,超大单净流入,总成交额"
                        String lastLine = list.get(list.size() - 1).toString();
                        String[] f = lastLine.split(",");
                        if (f.length >= 6) {
                            result.put("mainForce", parseDouble(f[1]) / 10000); // 主力净流入(万元)
                            result.put("superLarge", parseDouble(f[5]) / 10000); // 超大单净流入
                            result.put("large", parseDouble(f[4]) / 10000);      // 大单净流入
                            result.put("medium", parseDouble(f[3]) / 10000);     // 中单净流入
                            result.put("small", parseDouble(f[2]) / 10000);      // 小单净流入
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取资金流向失败 {}: {}", code, e.getMessage());
        }
        return result;
    }

    /**
     * 获取公司信息（东财API）
     * 返回公司简介、主营业务、行业、上市日期等
     */
    @Override
    public Map<String, Object> getCompanyInfo(String code) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // 东财公司信息API
            String secid = code.startsWith("6") ? "1." + code : "0." + code;
            String url = "https://emweb.eastmoney.com/PC_HSF10/CompanySurvey/CompanySurveyAjax?code=" + secid;
            
            String body = fetchUrl(url);
            if (body == null || body.isEmpty()) {
                return result;
            }
            
            JSONObject json = JSONObject.parseObject(body);
            JSONObject data = json.getJSONObject("jbzl");
            if (data != null) {
                result.put("profile", data.getString("gsjj")); // 公司简介
                result.put("business", data.getString("zyyw")); // 主营业务
                result.put("industry", data.getString("sshy")); // 所属行业
                result.put("listDate", data.getString("ssrq")); // 上市日期
                result.put("employees", data.getString("ygzs")); // 员工人数
                result.put("registeredCapital", data.getString("zczb")); // 注册资本
                result.put("chairman", data.getString("frdb")); // 法人代表/董事长
            }
        } catch (Exception e) {
            log.warn("获取公司信息失败 {}: {}", code, e.getMessage());
        }
        return result;
    }

    private String fetchUrl(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", "https://eastmoney.com")
                    .build();
            
            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                log.warn("请求失败 {}: {}", url, response.code());
                return null;
            }
            String body = response.body().string();
            response.close();
            return body;
        } catch (Exception e) {
            log.warn("请求URL失败 {}: {}", url, e.getMessage());
            return null;
        }
    }

    private double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
