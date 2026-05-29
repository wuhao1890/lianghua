package com.stock.stock.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.stock.stock.service.GlobalMarketService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GlobalMarketServiceImpl implements GlobalMarketService {

    private static final String KEY_CODE = "code";
    private static final String KEY_NAME = "name";
    private static final String KEY_PRICE = "price";
    private static final String KEY_CHANGE = "change";
    private static final String KEY_CHANGE_PCT = "changePercent";
    private static final String KEY_HIGH = "high";
    private static final String KEY_LOW = "low";
    private static final String KEY_OPEN = "open";
    private static final String KEY_PREV_CLOSE = "prevClose";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_TURNOVER = "turnover";
    private static final String KEY_MARKET = "market";

    private final OkHttpClient httpClient;

    /** 指数数据缓存（非交易时段返回最后成功获取的值） */
    private static final ConcurrentHashMap<String, List<Map<String, Object>>> indicesCache = new ConcurrentHashMap<>();

    public GlobalMarketServiceImpl() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    // ========== US Stock definitions ==========

    private static final String[] US_CODES = new String[]{
            "aapl", "msft", "googl", "amzn", "meta", "tsla", "nvda", "brk.b", "jpm", "v",
            "jnj", "wmt", "pg", "ma", "unh", "hd", "dis", "pypl", "adbe", "intc",
            "csco", "nflx", "cmcsa", "pfe", "abt", "ko", "pep", "mrk", "tmo", "avgo",
            "acn", "dhr", "nex", "cost", "crm", "lin", "tmc", "txn", "qcom", "sap",
            "amd", "ibm", "ba", "ge", "cat", "mmm", "xom", "cvx", "wfc", "gs"
    };

    private static final Map<String, String> US_NAMES = new LinkedHashMap<>();

    static {
        US_NAMES.put("aapl", "Apple Inc.");
        US_NAMES.put("msft", "Microsoft Corp.");
        US_NAMES.put("googl", "Alphabet Inc.");
        US_NAMES.put("amzn", "Amazon.com Inc.");
        US_NAMES.put("meta", "Meta Platforms Inc.");
        US_NAMES.put("tsla", "Tesla Inc.");
        US_NAMES.put("nvda", "NVIDIA Corp.");
        US_NAMES.put("brk.b", "Berkshire Hathaway B");
        US_NAMES.put("jpm", "JPMorgan Chase");
        US_NAMES.put("v", "Visa Inc.");
        US_NAMES.put("jnj", "Johnson & Johnson");
        US_NAMES.put("wmt", "Walmart Inc.");
        US_NAMES.put("pg", "Procter & Gamble");
        US_NAMES.put("ma", "Mastercard Inc.");
        US_NAMES.put("unh", "UnitedHealth Group");
        US_NAMES.put("hd", "The Home Depot");
        US_NAMES.put("dis", "The Walt Disney Co.");
        US_NAMES.put("pypl", "PayPal Holdings");
        US_NAMES.put("adbe", "Adobe Inc.");
        US_NAMES.put("intc", "Intel Corp.");
        US_NAMES.put("csco", "Cisco Systems");
        US_NAMES.put("nflx", "Netflix Inc.");
        US_NAMES.put("cmcsa", "Comcast Corp.");
        US_NAMES.put("pfe", "Pfizer Inc.");
        US_NAMES.put("abt", "Abbott Laboratories");
        US_NAMES.put("ko", "The Coca-Cola Co.");
        US_NAMES.put("pep", "PepsiCo Inc.");
        US_NAMES.put("mrk", "Merck & Co.");
        US_NAMES.put("tmo", "Thermo Fisher Scientific");
        US_NAMES.put("avgo", "Broadcom Inc.");
        US_NAMES.put("acn", "Accenture plc");
        US_NAMES.put("dhr", "Danaher Corp.");
        US_NAMES.put("nex", "Nextera Energy");
        US_NAMES.put("cost", "Costco Wholesale");
        US_NAMES.put("crm", "Salesforce Inc.");
        US_NAMES.put("lin", "Linde plc");
        US_NAMES.put("tmc", "T-Mobile US");
        US_NAMES.put("txn", "Texas Instruments");
        US_NAMES.put("qcom", "Qualcomm Inc.");
        US_NAMES.put("sap", "SAP SE ADR");
        US_NAMES.put("amd", "Advanced Micro Devices");
        US_NAMES.put("ibm", "IBM Corp.");
        US_NAMES.put("ba", "Boeing Co.");
        US_NAMES.put("ge", "General Electric");
        US_NAMES.put("cat", "Caterpillar Inc.");
        US_NAMES.put("mmm", "3M Co.");
        US_NAMES.put("xom", "Exxon Mobil Corp.");
        US_NAMES.put("cvx", "Chevron Corp.");
        US_NAMES.put("wfc", "Wells Fargo");
        US_NAMES.put("gs", "Goldman Sachs Group");
    }

    // ========== HK Stock definitions ==========

    private static final String[] HK_CODES = new String[]{
            "00700", "09988", "03690", "09618", "01810",
            "02382", "02015", "09961", "01024", "09888",
            "01211", "02269", "02013", "06060", "00388",
            "00005", "00941", "00883", "01398", "02318",
            "02628", "01299", "00322", "00011", "00016",
            "00001", "00002", "00003", "00006", "00012"
    };

    private static final Map<String, String> HK_NAMES = new LinkedHashMap<>();

    static {
        HK_NAMES.put("00700", "腾讯控股");
        HK_NAMES.put("09988", "阿里巴巴-SW");
        HK_NAMES.put("03690", "美团-W");
        HK_NAMES.put("09618", "京东集团-SW");
        HK_NAMES.put("01810", "小米集团-W");
        HK_NAMES.put("02382", "舜宇光学科技");
        HK_NAMES.put("02015", "理想汽车-W");
        HK_NAMES.put("09961", "携程集团-S");
        HK_NAMES.put("01024", "快手-W");
        HK_NAMES.put("09888", "百度集团-SW");
        HK_NAMES.put("01211", "比亚迪股份");
        HK_NAMES.put("02269", "药明生物");
        HK_NAMES.put("02013", "微盟集团");
        HK_NAMES.put("06060", "众安在线");
        HK_NAMES.put("00388", "香港交易所");
        HK_NAMES.put("00005", "汇丰控股");
        HK_NAMES.put("00941", "中国移动");
        HK_NAMES.put("00883", "中国海洋石油");
        HK_NAMES.put("01398", "工商银行");
        HK_NAMES.put("02318", "中国平安");
        HK_NAMES.put("02628", "中国人寿");
        HK_NAMES.put("01299", "友邦保险");
        HK_NAMES.put("00322", "康师傅控股");
        HK_NAMES.put("00011", "恒生银行");
        HK_NAMES.put("00016", "新鸿基地产");
        HK_NAMES.put("00001", "长和");
        HK_NAMES.put("00002", "中电控股");
        HK_NAMES.put("00003", "香港中华煤气");
        HK_NAMES.put("00006", "电能实业");
        HK_NAMES.put("00012", "恒基地产");
    }

    // ========== JP Stock definitions ==========

    private static final String[][] JP_STOCKS = new String[][]{
            {"6861", "Keyence Corp."},
            {"6758", "Sony Group Corp."},
            {"9432", "Nippon Telegraph & Telephone"},
            {"4063", "Shin-Etsu Chemical Co."},
            {"9984", "SoftBank Group Corp."},
            {"7203", "Toyota Motor Corp."},
            {"4502", "Takeda Pharmaceutical Co."},
            {"8306", "Mitsubishi UFJ Financial Group"},
            {"8035", "Tokyo Electron Ltd."},
            {"6098", "Recruit Holdings Co."},
            {"9983", "Fast Retailing Co."},
            {"9434", "SoftBank Corp."},
            {"4519", "Chugai Pharmaceutical Co."},
            {"7267", "Honda Motor Co."},
            {"7751", "Canon Inc."},
            {"6501", "Hitachi Ltd."},
            {"6954", "FANUC Corp."},
            {"6702", "Fujitsu Ltd."},
            {"8001", "Itochu Corp."},
            {"8766", "Tokio Marine Holdings"}
    };

    // ========== KR Stock definitions ==========

    private static final String[][] KR_STOCKS = new String[][]{
            {"005930", "Samsung Electronics Co."},
            {"000660", "SK Hynix Inc."},
            {"207940", "Samsung Biologics Co."},
            {"005380", "Hyundai Motor Co."},
            {"105560", "KB Financial Group Inc."},
            {"068270", "Celltrion Inc."},
            {"035420", "Naver Corp."},
            {"003670", "POSCO Holdings Inc."},
            {"051910", "LG Chem Ltd."},
            {"012330", "Mobis Co."},
            {"055550", "Shinhan Financial Group Co."},
            {"086790", "Hana Financial Group Inc."},
            {"032830", "Samsung Life Insurance Co."},
            {"000270", "Kia Corp."},
            {"018260", "Samsung SDS Co."},
            {"028260", "Samsung C&T Corp."},
            {"034730", "SK Inc."},
            {"096770", "SK Innovation Co."},
            {"030200", "KT Corp."},
            {"036570", "NCsoft Corp."}
    };

    private String todayStr() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
    }

    @Override
    public Map<String, Object> getStockList(String market, int page, int size) {
        switch (market.toUpperCase()) {
            case "US":
                return getUSStockList(page, size);
            case "HK":
                return getHKStockList(page, size);
            case "JP":
                return getJPStockList(page, size);
            case "KR":
                return getKRStockList(page, size);
            default:
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("total", 0);
                result.put("page", page);
                result.put("size", size);
                result.put("list", Collections.emptyList());
                return result;
        }
    }

    private Map<String, Object> getUSStockList(int page, int size) {
        List<Map<String, Object>> allStocks = new ArrayList<>();
        int batchSize = 20;
        try {
            for (int i = 0; i < US_CODES.length; i += batchSize) {
                int end = Math.min(i + batchSize, US_CODES.length);
                StringBuilder sb = new StringBuilder();
                for (int j = i; j < end; j++) {
                    if (j > i) sb.append(",");
                    sb.append("us_").append(US_CODES[j]);
                }
                String url = "https://hq.sinajs.cn/list=" + sb;
                String raw = fetchUrl(url, "GBK");
                if (raw == null || raw.isEmpty()) {
                    log.warn("获取美股行情失败: 响应为空, codes={}", sb);
                    continue;
                }
                for (String line : raw.split("\n")) {
                    line = line.trim();
                    if (!line.startsWith("var hq_str_us_")) continue;
                    int si = line.indexOf("=\"");
                    int ee = line.indexOf("\"", si + 2);
                    if (si < 0 || ee < 0) continue;
                    String data = line.substring(si + 2, ee);
                    if (data.isEmpty()) continue;

                    // hq_str_us_aapl="code, name, ..., lastTrade, ..., time, change, open, high, low, volume, ..."
                    String[] f = data.split(",");
                    if (f.length < 3) continue;

                    // Extract the code from the line
                    String lineCode = line.substring("var hq_str_us_".length());
                    int eqIdx = lineCode.indexOf("=");
                    if (eqIdx > 0) lineCode = lineCode.substring(0, eqIdx).trim();
                    lineCode = lineCode.replace("us_", "");

                    String name = US_NAMES.getOrDefault(lineCode, f[1]);

                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put(KEY_CODE, lineCode.toUpperCase());
                    map.put(KEY_NAME, name);
                    // f[1]=name, f[3]=lastPrice, f[4]=chg, f[5]=chg%, f[6]=open? actually Tencent US format:
                    // name,code,intraday,lastPrice,change,changePercent,open,high,low,volume,turnover,time
                    // Let's index: f[0]=name, f[1]=code(or empty), f[2]=intraday, f[3]=lastPrice, f[4]=change, f[5]=changePercent
                    map.put(KEY_PRICE, parseDouble(f.length > 3 ? f[3] : "0"));
                    map.put(KEY_CHANGE, parseDouble(f.length > 4 ? f[4] : "0"));
                    map.put(KEY_CHANGE_PCT, parseDouble(f.length > 5 ? f[5] : "0"));
                    map.put(KEY_OPEN, parseDouble(f.length > 6 ? f[6] : "0"));
                    map.put(KEY_HIGH, parseDouble(f.length > 7 ? f[7] : "0"));
                    map.put(KEY_LOW, parseDouble(f.length > 8 ? f[8] : "0"));
                    map.put(KEY_VOLUME, parseDouble(f.length > 9 ? f[9] : "0"));
                    map.put(KEY_TURNOVER, parseDouble(f.length > 10 ? f[10] : "0"));
                    map.put(KEY_MARKET, "US");
                    allStocks.add(map);
                }
            }
        } catch (Exception e) {
            log.warn("获取美股列表失败: {}", e.getMessage());
        }

        int total = allStocks.size();
        int fromIndex = (page - 1) * size;
        if (fromIndex >= total) {
            fromIndex = 0;
            page = 1;
        }
        int toIndex = Math.min(fromIndex + size, total);
        List<Map<String, Object>> pageList = (fromIndex < total) ? allStocks.subList(fromIndex, toIndex) : Collections.emptyList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("list", pageList);
        return result;
    }

    private Map<String, Object> getHKStockList(int page, int size) {
        List<Map<String, Object>> allStocks = new ArrayList<>();
        int batchSize = 20;
        try {
            for (int i = 0; i < HK_CODES.length; i += batchSize) {
                int end = Math.min(i + batchSize, HK_CODES.length);
                StringBuilder sb = new StringBuilder();
                for (int j = i; j < end; j++) {
                    if (j > i) sb.append(",");
                    sb.append("rt_hk").append(HK_CODES[j]);
                }
                String url = "https://hq.sinajs.cn/list=" + sb;
                String raw = fetchUrl(url, "GBK");
                if (raw == null || raw.isEmpty()) {
                    log.warn("获取港股行情失败: 响应为空, codes={}", sb);
                    continue;
                }
                for (String line : raw.split("\n")) {
                    line = line.trim();
                    if (!line.contains("hq_str_rt_hk")) continue;
                    int si = line.indexOf("=\"");
                    int ee = line.indexOf("\"", si + 2);
                    if (si < 0 || ee < 0) continue;
                    String data = line.substring(si + 2, ee);
                    if (data.isEmpty()) continue;

                    // rt_hk format: name,open,prevClose,current,high,low,change,changePercent,time,buy,sell,volume,turnover
                    String[] f = data.split(",");
                    if (f.length < 7) continue;

                    // extract code
                    String codeStr = line.substring(line.indexOf("rt_hk") + 5);
                    int eqIdx = codeStr.indexOf("=");
                    if (eqIdx > 0) codeStr = codeStr.substring(0, eqIdx).trim();

                    String name = HK_NAMES.getOrDefault(codeStr, f[0]);

                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put(KEY_CODE, codeStr);
                    map.put(KEY_NAME, name);
                    map.put(KEY_OPEN, parseDouble(f.length > 1 ? f[1] : "0"));
                    map.put(KEY_PREV_CLOSE, parseDouble(f.length > 2 ? f[2] : "0"));
                    map.put(KEY_PRICE, parseDouble(f.length > 3 ? f[3] : "0"));
                    map.put(KEY_HIGH, parseDouble(f.length > 4 ? f[4] : "0"));
                    map.put(KEY_LOW, parseDouble(f.length > 5 ? f[5] : "0"));
                    map.put(KEY_CHANGE, parseDouble(f.length > 6 ? f[6] : "0"));
                    map.put(KEY_CHANGE_PCT, parseDouble(f.length > 7 ? f[7] : "0"));
                    map.put(KEY_VOLUME, parseDouble(f.length > 11 ? f[11] : "0"));
                    map.put(KEY_TURNOVER, parseDouble(f.length > 12 ? f[12] : "0"));
                    map.put(KEY_MARKET, "HK");
                    allStocks.add(map);
                }
            }
        } catch (Exception e) {
            log.warn("获取港股列表失败: {}", e.getMessage());
        }

        int total = allStocks.size();
        int fromIndex = (page - 1) * size;
        if (fromIndex >= total) {
            fromIndex = 0;
            page = 1;
        }
        int toIndex = Math.min(fromIndex + size, total);
        List<Map<String, Object>> pageList = (fromIndex < total) ? allStocks.subList(fromIndex, toIndex) : Collections.emptyList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("list", pageList);
        return result;
    }

    private Map<String, Object> getJPStockList(int page, int size) {
        // JP API not available, return empty list
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", 0);
        result.put("page", page);
        result.put("size", size);
        result.put("list", Collections.emptyList());
        return result;
    }

    private Map<String, Object> getKRStockList(int page, int size) {
        // KR API not available, return empty list
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", 0);
        result.put("page", page);
        result.put("size", size);
        result.put("list", Collections.emptyList());
        return result;
    }

    @Override
    public Map<String, Object> getRealtimeQuote(String code, String market) {
        switch (market.toUpperCase()) {
            case "US":
                return getUSRealtime(code);
            case "HK":
                return getHKRealtime(code);
            case "JP":
                return getJPRealtime(code);
            case "KR":
                return getKRRealtime(code);
            default:
                return null;
        }
    }

    private Map<String, Object> getUSRealtime(String code) {
        code = code.toLowerCase().replace("us_", "");
        try {
            String url = "https://hq.sinajs.cn/list=us_" + code;
            String raw = fetchUrl(url, "GBK");
            if (raw == null || raw.isEmpty()) return null;
            for (String line : raw.split("\n")) {
                line = line.trim();
                if (!line.startsWith("var hq_str_us_")) continue;
                int si = line.indexOf("=\"");
                int ee = line.indexOf("\"", si + 2);
                if (si < 0 || ee < 0) return null;
                String data = line.substring(si + 2, ee);
                if (data.isEmpty()) return null;

                String[] f = data.split(",");
                if (f.length < 3) return null;

                Map<String, Object> map = new LinkedHashMap<>();
                map.put(KEY_CODE, code.toUpperCase());
                map.put(KEY_NAME, US_NAMES.getOrDefault(code, f[0]));
                map.put(KEY_PRICE, parseDouble(f.length > 3 ? f[3] : "0"));
                map.put(KEY_CHANGE, parseDouble(f.length > 4 ? f[4] : "0"));
                map.put(KEY_CHANGE_PCT, parseDouble(f.length > 5 ? f[5] : "0"));
                map.put(KEY_OPEN, parseDouble(f.length > 6 ? f[6] : "0"));
                map.put(KEY_HIGH, parseDouble(f.length > 7 ? f[7] : "0"));
                map.put(KEY_LOW, parseDouble(f.length > 8 ? f[8] : "0"));
                map.put(KEY_VOLUME, parseDouble(f.length > 9 ? f[9] : "0"));
                map.put(KEY_TURNOVER, parseDouble(f.length > 10 ? f[10] : "0"));
                map.put(KEY_MARKET, "US");
                return map;
            }
        } catch (Exception e) {
            log.warn("获取美股实时行情失败 {}: {}", code, e.getMessage());
        }
        return null;
    }

    private Map<String, Object> getHKRealtime(String code) {
        code = code.replace("hk", "");
        try {
            String url = "https://hq.sinajs.cn/list=rt_hk" + code;
            String raw = fetchUrl(url, "GBK");
            if (raw == null || raw.isEmpty()) return null;
            for (String line : raw.split("\n")) {
                line = line.trim();
                if (!line.contains("hq_str_rt_hk")) continue;
                int si = line.indexOf("=\"");
                int ee = line.indexOf("\"", si + 2);
                if (si < 0 || ee < 0) return null;
                String data = line.substring(si + 2, ee);
                if (data.isEmpty()) return null;

                String[] f = data.split(",");
                if (f.length < 7) return null;

                Map<String, Object> map = new LinkedHashMap<>();
                map.put(KEY_CODE, code);
                map.put(KEY_NAME, HK_NAMES.getOrDefault(code, f[0]));
                map.put(KEY_OPEN, parseDouble(f.length > 1 ? f[1] : "0"));
                map.put(KEY_PREV_CLOSE, parseDouble(f.length > 2 ? f[2] : "0"));
                map.put(KEY_PRICE, parseDouble(f.length > 3 ? f[3] : "0"));
                map.put(KEY_HIGH, parseDouble(f.length > 4 ? f[4] : "0"));
                map.put(KEY_LOW, parseDouble(f.length > 5 ? f[5] : "0"));
                map.put(KEY_CHANGE, parseDouble(f.length > 6 ? f[6] : "0"));
                map.put(KEY_CHANGE_PCT, parseDouble(f.length > 7 ? f[7] : "0"));
                map.put(KEY_VOLUME, parseDouble(f.length > 11 ? f[11] : "0"));
                map.put(KEY_TURNOVER, parseDouble(f.length > 12 ? f[12] : "0"));
                map.put(KEY_MARKET, "HK");
                return map;
            }
        } catch (Exception e) {
            log.warn("获取港股实时行情失败 {}: {}", code, e.getMessage());
        }
        return null;
    }

    private Map<String, Object> getJPRealtime(String code) {
        // JP API not available
        return null;
    }

    private Map<String, Object> getKRRealtime(String code) {
        // KR API not available
        return null;
    }

    @Override
    public List<Map<String, Object>> getMarketIndices(String market) {
        String key = market.toUpperCase();
        List<Map<String, Object>> result;
        switch (key) {
            case "US":
                result = getUSIndices(); break;
            case "CN":
                result = getCNIndices(); break;
            case "HK":
                result = getHKIndices(); break;
            case "JP":
                result = getJPIndices(); break;
            case "KR":
                result = getKRIndices(); break;
            default:
                return Collections.emptyList();
        }
        // Cache non-empty results for market-closed fallback
        if (result != null && !result.isEmpty()) {
            indicesCache.put(key, result);
        } else if (indicesCache.containsKey(key)) {
            // Return cached data when API returns empty (market closed)
            List<Map<String, Object>> cached = indicesCache.get(key);
            log.debug("使用缓存的指数数据: {}", key);
            return cached;
        }
        return result != null ? result : Collections.emptyList();
    }

    private List<Map<String, Object>> getUSIndices() {
        List<Map<String, Object>> indices = new ArrayList<>();
        try {
            String url = "https://hq.sinajs.cn/list=gb_ixic,gb_dji,gb_$comp";
            String raw = fetchUrl(url, "GBK");
            if (raw == null || raw.isEmpty()) return indices;

            for (String line : raw.split("\n")) {
                line = line.trim();
                if (!line.startsWith("var hq_str_gb_")) continue;
                int si = line.indexOf("=\"");
                int ee = line.indexOf("\"", si + 2);
                if (si < 0 || ee < 0) continue;
                String data = line.substring(si + 2, ee);
                if (data.isEmpty()) continue;

                // gb format: name,current,changePercent,time,change,open,high,low,prevClose
                String[] f = data.split(",");
                if (f.length < 6) continue;

                String lineCode = line.substring("var hq_str_gb_".length());
                int eqIdx = lineCode.indexOf("=");
                if (eqIdx > 0) lineCode = lineCode.substring(0, eqIdx).trim();
                // normalize: $comp -> COMP, ixic -> IXIC, dji -> DJI
                String idxCode = lineCode.replace("$", "").toUpperCase();

                Map<String, Object> map = new LinkedHashMap<>();
                map.put(KEY_CODE, idxCode);
                map.put(KEY_NAME, f[0]);
                map.put(KEY_PRICE, parseDouble(f[1]));
                map.put(KEY_CHANGE_PCT, parseDouble(f[2]));
                map.put(KEY_CHANGE, f.length > 4 ? parseDouble(f[4]) : 0);
                map.put(KEY_OPEN, f.length > 5 ? parseDouble(f[5]) : 0);
                map.put(KEY_HIGH, f.length > 6 ? parseDouble(f[6]) : 0);
                map.put(KEY_LOW, f.length > 7 ? parseDouble(f[7]) : 0);
                map.put(KEY_PREV_CLOSE, f.length > 8 ? parseDouble(f[8]) : 0);
                map.put(KEY_MARKET, "US");
                indices.add(map);
            }
        } catch (Exception e) {
            log.warn("获取美股指数失败: {}", e.getMessage());
        }
        return indices;
    }

    private List<Map<String, Object>> getCNIndices() {
        List<Map<String, Object>> indices = new ArrayList<>();
        try {
            String url = "https://hq.sinajs.cn/list=s_sh000001,s_sz399001,s_sz399006";
            String raw = fetchUrl(url, "GBK");
            if (raw == null || raw.isEmpty()) return indices;

            for (String line : raw.split("\n")) {
                line = line.trim();
                if (!line.startsWith("var hq_str_s_")) continue;
                int si = line.indexOf("=\"");
                int ee = line.indexOf("\"", si + 2);
                if (si < 0 || ee < 0) continue;
                String data = line.substring(si + 2, ee);
                if (data.isEmpty()) continue;

                String[] f = data.split(",");
                if (f.length < 4) continue;

                String lineCode = line.substring("var hq_str_s_".length());
                int eqIdx = lineCode.indexOf("=");
                if (eqIdx > 0) lineCode = lineCode.substring(0, eqIdx).trim();

                String idxCode;
                String idxName;
                switch (lineCode) {
                    case "sh000001":
                        idxCode = "SSE";
                        idxName = "上证指数";
                        break;
                    case "sz399001":
                        idxCode = "SZI";
                        idxName = "深证成指";
                        break;
                    case "sz399006":
                        idxCode = "CYB";
                        idxName = "创业板指";
                        break;
                    default:
                        idxCode = lineCode;
                        idxName = f[0];
                }

                // Sina CN index format: name,current,change,changePercent,volume,turnover,...
                Map<String, Object> map = new LinkedHashMap<>();
                map.put(KEY_CODE, idxCode);
                map.put(KEY_NAME, idxName);
                map.put(KEY_PRICE, parseDouble(f[1]));
                map.put(KEY_CHANGE, parseDouble(f[2]));
                map.put(KEY_CHANGE_PCT, parseDouble(f[3]));
                map.put(KEY_MARKET, "CN");
                if (f.length > 4) map.put(KEY_VOLUME, parseDouble(f[4]));
                if (f.length > 5) map.put(KEY_TURNOVER, parseDouble(f[5]));
                indices.add(map);
            }
        } catch (Exception e) {
            log.warn("获取A股指数失败: {}", e.getMessage());
        }
        return indices;
    }

    private List<Map<String, Object>> getHKIndices() {
        List<Map<String, Object>> indices = new ArrayList<>();
        try {
            String url = "https://hq.sinajs.cn/list=rt_hkHSI,rt_hkHSCEI,rt_hkHSTECH";
            String raw = fetchUrl(url, "GBK");
            if (raw == null || raw.isEmpty()) return indices;

            for (String line : raw.split("\n")) {
                line = line.trim();
                if (!line.contains("hq_str_rt_hk")) continue;
                int si = line.indexOf("=\"");
                int ee = line.indexOf("\"", si + 2);
                if (si < 0 || ee < 0) continue;
                String data = line.substring(si + 2, ee);
                if (data.isEmpty()) continue;

                // rt_hk indices format: [0]=code, [1]=chname, [2]=prevClose, [3]=current, [4]=high, [5]=low, [6]=?, [7]=?, [8]=?, ...
                // We calculate change ourselves
                String[] f = data.split(",");
                if (f.length < 7) continue;

                String codeStr = line.substring(line.indexOf("rt_hk") + 5);
                int eqIdx = codeStr.indexOf("=");
                if (eqIdx > 0) codeStr = codeStr.substring(0, eqIdx).trim();

                String idxName = f[1].isEmpty() ? codeStr : f[1];
                double curPrice = parseDouble(f[3]);
                double prevClose = parseDouble(f[2]);
                double changeVal = prevClose > 0 ? curPrice - prevClose : 0;
                double changePct = prevClose > 0 ? (curPrice - prevClose) / prevClose * 100 : 0;

                Map<String, Object> map = new LinkedHashMap<>();
                map.put(KEY_CODE, codeStr);
                map.put(KEY_NAME, idxName);
                map.put(KEY_PRICE, curPrice);
                map.put(KEY_CHANGE, round2(changeVal));
                map.put(KEY_CHANGE_PCT, round2(changePct));
                map.put(KEY_HIGH, parseDouble(f[4]));
                map.put(KEY_LOW, parseDouble(f[5]));
                map.put(KEY_PREV_CLOSE, prevClose);
                map.put(KEY_MARKET, "HK");
                indices.add(map);
            }
        } catch (Exception e) {
            log.warn("获取港股指数失败: {}", e.getMessage());
        }
        return indices;
    }

    private List<Map<String, Object>> getJPIndices() {
        List<Map<String, Object>> indices = new ArrayList<>();
        try {
            Map<String, Object> n225 = fetchN225Index();
            if (n225 != null) indices.add(n225);
        } catch (Exception e) {
            log.warn("获取日经指数失败: {}", e.getMessage());
        }
        return indices;
    }

    // (fetchN225Index preserved from original remaining lines)
    private Map<String, Object> fetchN225Index() {
        try {
            String url = "https://hq.sinajs.cn/list=s_n225";
            String raw = fetchUrl(url, "GBK");
            for (String line : raw.split("\n")) {
                line = line.trim();
                if (!line.contains("hq_str_s_n225")) continue;
                int si = line.indexOf("=\"");
                int ee = line.indexOf("\"", si + 2);
                if (si < 0 || ee < 0) return null;
                String data = line.substring(si + 2, ee);
                if (data.isEmpty()) return null;
                String[] f = data.split(",");
                if (f.length < 6) return null;
                Map<String, Object> map = new LinkedHashMap<>();
                map.put(KEY_CODE, "N225");
                map.put(KEY_NAME, f[0]);
                map.put(KEY_PRICE, parseDouble(f[3]));
                map.put(KEY_CHANGE, f.length > 7 ? parseDouble(f[7]) : 0);
                map.put(KEY_CHANGE_PCT, f.length > 8 ? parseDouble(f[8]) : 0);
                map.put(KEY_HIGH, parseDouble(f[4]));
                map.put(KEY_LOW, parseDouble(f[5]));
                map.put(KEY_OPEN, parseDouble(f[1]));
                map.put(KEY_PREV_CLOSE, parseDouble(f[2]));
                map.put(KEY_MARKET, "JP");
                return map;
            }
        } catch (Exception e) {
            log.warn("获取N225指数失败: {}", e.getMessage());
        }
        return null;
    }

    private List<Map<String, Object>> getKRIndices() {
        List<Map<String, Object>> indices = new ArrayList<>();
        try {
            Map<String, Object> ks11 = fetchKS11Index();
            if (ks11 != null) indices.add(ks11);
        } catch (Exception e) {
            log.warn("获取KOSPI失败: {}", e.getMessage());
        }
        return indices;
    }

    private Map<String, Object> fetchKS11Index() {
        try {
            String url = "https://hq.sinajs.cn/list=s_ks11";
            String raw = fetchUrl(url, "GBK");
            for (String line : raw.split("\n")) {
                line = line.trim();
                if (!line.contains("hq_str_s_ks11")) continue;
                int si = line.indexOf("=\"");
                int ee = line.indexOf("\"", si + 2);
                if (si < 0 || ee < 0) return null;
                String data = line.substring(si + 2, ee);
                if (data.isEmpty()) return null;
                String[] f = data.split(",");
                if (f.length < 6) return null;
                Map<String, Object> map = new LinkedHashMap<>();
                map.put(KEY_CODE, "KS11");
                map.put(KEY_NAME, f[0]);
                map.put(KEY_PRICE, parseDouble(f[3]));
                map.put(KEY_CHANGE, f.length > 7 ? parseDouble(f[7]) : 0);
                map.put(KEY_CHANGE_PCT, f.length > 8 ? parseDouble(f[8]) : 0);
                map.put(KEY_HIGH, parseDouble(f[4]));
                map.put(KEY_LOW, parseDouble(f[5]));
                map.put(KEY_OPEN, parseDouble(f[1]));
                map.put(KEY_PREV_CLOSE, parseDouble(f[2]));
                map.put(KEY_MARKET, "KR");
                return map;
            }
        } catch (Exception e) {
            log.warn("获取KS11指数失败: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> search(String market, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String kw = keyword.trim().toLowerCase();
        List<Map<String, Object>> results = new ArrayList<>();

        switch (market.toUpperCase()) {
            case "US":
                for (String code : US_CODES) {
                    String name = US_NAMES.get(code);
                    if (name == null) continue;
                    if (code.contains(kw) || name.toLowerCase().contains(kw)) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put(KEY_CODE, code.toUpperCase());
                        m.put(KEY_NAME, name);
                        m.put(KEY_MARKET, "US");
                        results.add(m);
                    }
                }
                break;
            case "HK":
                for (String code : HK_CODES) {
                    String name = HK_NAMES.get(code);
                    if (name == null) continue;
                    if (code.contains(kw) || name.contains(kw)) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put(KEY_CODE, code);
                        m.put(KEY_NAME, name);
                        m.put(KEY_MARKET, "HK");
                        results.add(m);
                    }
                }
                break;
            case "JP":
                for (String[] stock : JP_STOCKS) {
                    if (stock[0].contains(kw) || stock[1].toLowerCase().contains(kw)) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put(KEY_CODE, stock[0]);
                        m.put(KEY_NAME, stock[1]);
                        m.put(KEY_MARKET, "JP");
                        results.add(m);
                    }
                }
                break;
            case "KR":
                for (String[] stock : KR_STOCKS) {
                    if (stock[0].contains(kw) || stock[1].toLowerCase().contains(kw)) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put(KEY_CODE, stock[0]);
                        m.put(KEY_NAME, stock[1]);
                        m.put(KEY_MARKET, "KR");
                        results.add(m);
                    }
                }
                break;
            default:
                // search all markets
                for (String code : US_CODES) {
                    String name = US_NAMES.get(code);
                    if (name != null && (code.contains(kw) || name.toLowerCase().contains(kw))) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put(KEY_CODE, code.toUpperCase());
                        m.put(KEY_NAME, name);
                        m.put(KEY_MARKET, "US");
                        results.add(m);
                    }
                }
                for (String code : HK_CODES) {
                    String name = HK_NAMES.get(code);
                    if (name != null && (code.contains(kw) || name.contains(kw))) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put(KEY_CODE, code);
                        m.put(KEY_NAME, name);
                        m.put(KEY_MARKET, "HK");
                        results.add(m);
                    }
                }
                for (String[] stock : JP_STOCKS) {
                    if (stock[0].contains(kw) || stock[1].toLowerCase().contains(kw)) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put(KEY_CODE, stock[0]);
                        m.put(KEY_NAME, stock[1]);
                        m.put(KEY_MARKET, "JP");
                        results.add(m);
                    }
                }
                for (String[] stock : KR_STOCKS) {
                    if (stock[0].contains(kw) || stock[1].toLowerCase().contains(kw)) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put(KEY_CODE, stock[0]);
                        m.put(KEY_NAME, stock[1]);
                        m.put(KEY_MARKET, "KR");
                        results.add(m);
                    }
                }
                break;
        }
        return results;
    }

    private double parseDouble(String s) {
        try {
            if (s == null || s.trim().isEmpty()) return 0d;
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    private double round2(double d) {
        return Math.round(d * 100.0) / 100.0;
    }

    private String fetchUrl(String urlStr, String encoding) {
        try {
            Request request = new Request.Builder()
                    .url(urlStr)
                    .header("Referer", "https://finance.sina.com.cn")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .get()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return null;
                }
                byte[] bytes = response.body().bytes();
                return new String(bytes, encoding);
            }
        } catch (Exception e) {
            log.warn("请求失败 {}: {}", urlStr, e.getMessage());
            return null;
        }
    }
}
