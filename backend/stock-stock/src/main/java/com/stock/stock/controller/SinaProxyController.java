package com.stock.stock.controller;

import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/stock")
public class SinaProxyController {

    private static final Logger log = LoggerFactory.getLogger(SinaProxyController.class);
    
    // ========== API地址 ==========
    private static final String SINA_API = "https://hq.sinajs.cn/list=";
    private static final String TENCENT_API = "http://qt.gtimg.cn/q=";
    private static final String EASTMONEY_API = "http://push2.eastmoney.com/api/qt/stock/get?secid=%s&fields=f43,f44,f45,f46,f47,f48,f50,f51,f57,f58";

    // ========== 缓存 ==========
    private static volatile String[] cachedACodes = null;
    private static volatile long cacheTimestamp = 0;
    private static final long CACHE_TTL_MS = 30 * 60 * 1000; // 缓存30分钟

    @PostConstruct
    public void init() {
        // 服务启动时预热A股代码缓存
        log.info("预热A股代码列表...");
        getACodes();
        log.info("A股代码列表预热完成，共 {} 只", cachedACodes != null ? cachedACodes.length : 0);
    }

    // ========== 统一字段名 ==========
    private static final String KEY_CODE = "code";
    private static final String KEY_NAME = "name";
    private static final String KEY_OPEN = "open";
    private static final String KEY_PREV_CLOSE = "prevClose";
    private static final String KEY_CURRENT = "current";
    private static final String KEY_HIGH = "high";
    private static final String KEY_LOW = "low";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_CHANGE = "change";
    private static final String KEY_CHANGE_PCT = "changePercent";

    // ================================================================
    //  实时行情 - 多API轮询
    // ================================================================

    /**
     * GET /api/stock/sina/realtime?codes=sh600519,sz000001
     * 多源获取实时行情：新浪 → 腾讯 → 东方财富
     */
    @GetMapping("/sina/realtime")
    public ResponseEntity<Map<String, Object>> sinaRealtime(@RequestParam String codes) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> stocks = fetchRealtimeBatch(codes);
            result.put("code", 200);
            result.put("data", stocks);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("批量获取实时行情失败: {}", e.getMessage());
            result.put("code", 500);
            result.put("message", e.getMessage());
            result.put("data", new ArrayList<>());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 多API轮流拉取实时行情
     */
    private List<Map<String, Object>> fetchRealtimeBatch(String codes) {
        // 1. 尝试新浪
        try {
            String raw = fetchUrl(SINA_API + codes, "GBK");
            List<Map<String, Object>> list = parseSinaResponse(raw);
            if (!list.isEmpty()) return list;
        } catch (Exception e) {
            log.warn("新浪行情API失败: {}", e.getMessage());
        }

        // 2. 尝试腾讯
        try {
            String raw = fetchUrl(TENCENT_API + codes, "UTF-8");
            List<Map<String, Object>> list = parseTencentResponse(raw);
            if (!list.isEmpty()) return list;
        } catch (Exception e) {
            log.warn("腾讯行情API失败: {}", e.getMessage());
        }

        // 3. 逐个用东方财富（不支持批量）
        List<Map<String, Object>> fallback = new ArrayList<>();
        String[] items = codes.split(",");
        for (String code : items) {
            try {
                Map<String, Object> stock = fetchEastMoneySingle(code.trim());
                if (stock != null) fallback.add(stock);
            } catch (Exception e) {
                log.warn("东方财富行情失败 {}: {}", code, e.getMessage());
            }
        }
        return fallback;
    }

    /** 只获取单只股票（用于小批量场景） */
    private Map<String, Object> fetchRealtimeSingle(String sinaCode) {
        return fetchRealtimeBatch(sinaCode).stream().findFirst().orElse(null);
    }

    // ================================================================
    //  新浪解析
    // ================================================================
    private List<Map<String, Object>> parseSinaResponse(String raw) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String line : raw.split("\n")) {
            if (!line.contains("hq_str_")) continue;
            int si = line.indexOf("hq_str_");
            int ei = line.indexOf("=\"", si);
            int ee = line.indexOf("\"", ei + 2);
            if (ei < 0 || ee < 0) continue;
            String code = line.substring(si + 7, ei);
            String data = line.substring(ei + 2, ee);
            if (data.isEmpty()) continue;
            String[] f = data.split(",");
            if (f.length < 32) continue;

            Map<String, Object> s = new LinkedHashMap<>();
            s.put(KEY_CODE, cleanCode(code));
            s.put(KEY_NAME, f[0]);
            s.put(KEY_OPEN, parseD(f[1]));
            s.put(KEY_PREV_CLOSE, parseD(f[2]));
            s.put(KEY_CURRENT, parseD(f[3]));
            if ((double) s.get(KEY_CURRENT) <= 0 || f[0] == null || f[0].trim().isEmpty()) {
                continue;
            }
            s.put(KEY_HIGH, parseD(f[4]));
            s.put(KEY_LOW, parseD(f[5]));
            s.put(KEY_VOLUME, parseD(f[8]));
            s.put(KEY_AMOUNT, parseD(f[9]));
            if ((double) s.get(KEY_OPEN) <= 0 || (double) s.get(KEY_VOLUME) <= 0) {
                continue;
            }
            addChange(s);
            list.add(s);
        }
        return list;
    }

    // ================================================================
    //  腾讯解析
    // ================================================================
    private List<Map<String, Object>> parseTencentResponse(String raw) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String line : raw.split("\n")) {
            line = line.trim();
            if (!line.startsWith("v_")) continue;
            int eq = line.indexOf("=\"");
            int end = line.lastIndexOf("\"");
            if (eq < 0 || end <= eq) continue;
            String data = line.substring(eq + 2, end);
            String[] f = data.split("~");
            if (f.length < 10) continue;

            // 提取新浪格式代码
            String rawCode = line.substring(line.indexOf("v_") + 2, eq);
            double current = parseD(f[3]);
            if (current <= 0 || f[1] == null || f[1].trim().isEmpty()) {
                continue;
            }
            double prevClose = parseD(f[4]);
            double open = parseD(f[5]);
            double volume = parseD(f[6]);
            if (open <= 0 || volume <= 0) {
                continue;
            }
            double amount = parseD(f[7]);
            double high = parseD(f[33] != null && !f[33].isEmpty() ? f[33] : "0");
            double low = parseD(f[34] != null && !f[34].isEmpty() ? f[34] : "0");

            Map<String, Object> s = new LinkedHashMap<>();
            s.put(KEY_CODE, cleanCode(rawCode));
            s.put(KEY_NAME, f[1]);
            s.put(KEY_OPEN, open);
            s.put(KEY_PREV_CLOSE, prevClose);
            s.put(KEY_CURRENT, current);
            s.put(KEY_HIGH, high > 0 ? high : current * 1.02);
            s.put(KEY_LOW, low > 0 ? low : current * 0.98);
            s.put(KEY_VOLUME, volume);
            s.put(KEY_AMOUNT, amount);
            addChange(s);
            list.add(s);
        }
        return list;
    }

    // ================================================================
    //  东方财富解析（单只）
    // ================================================================
    private Map<String, Object> fetchEastMoneySingle(String sinaCode) {
        String code = cleanCode(sinaCode);
        int market = code.startsWith("6") || code.startsWith("5") ? 1 : 0;
        String url = String.format(EASTMONEY_API, market + "." + code);
        try {
            String raw = fetchUrl(url, "UTF-8");
            JSONObject jo = JSONObject.parseObject(raw);
            JSONObject data = jo.getJSONObject("data");
            if (data == null) return null;

            double current = data.getDoubleValue("f43");
            String name = data.getString("f58");
            if (current <= 0 || name == null || name.trim().isEmpty()) {
                return null;
            }
            double prevClose = data.getDoubleValue("f44");
            double open = data.getDoubleValue("f45");
            double volume = data.getDoubleValue("f48");
            if (open <= 0 || volume <= 0) {
                return null;
            }
            double high = data.getDoubleValue("f46");
            double low = data.getDoubleValue("f47");
            double amount = data.getDoubleValue("f50");

            Map<String, Object> s = new LinkedHashMap<>();
            s.put(KEY_CODE, code);
            s.put(KEY_NAME, name);
            s.put(KEY_OPEN, open);
            s.put(KEY_PREV_CLOSE, prevClose);
            s.put(KEY_CURRENT, current);
            s.put(KEY_HIGH, high);
            s.put(KEY_LOW, low);
            s.put(KEY_VOLUME, volume);
            s.put(KEY_AMOUNT, amount);
            addChange(s);
            return s;
        } catch (Exception e) {
            log.warn("东方财富单只查询失败 {}: {}", code, e.getMessage());
            return null;
        }
    }

    // ================================================================
    //  大盘指数
    // ================================================================
    @GetMapping("/sina/indices")
    public ResponseEntity<Map<String, Object>> sinaIndices() {
        Map<String, Object> result = new HashMap<>();
        try {
            String codes = "sh000001,sz399001,sz399006,sh000300";
            List<Map<String, Object>> indices = fetchRealtimeBatch(codes);
            // 覆盖中文名称
            Map<String, String> nameMap = new HashMap<>();
            nameMap.put("000001", "上证指数");
            nameMap.put("399001", "深证成指");
            nameMap.put("399006", "创业板指");
            nameMap.put("000300", "沪深300");
            for (Map<String, Object> idx : indices) {
                String c = (String) idx.get(KEY_CODE);
                if (nameMap.containsKey(c)) idx.put(KEY_NAME, nameMap.get(c));
            }
            result.put("code", 200);
            result.put("data", indices);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            result.put("data", new ArrayList<>());
            return ResponseEntity.ok(result);
        }
    }

    // ================================================================
    //  A股列表（分页）
    // ================================================================
    @GetMapping("/sina/a-stocks")
    public ResponseEntity<Map<String, Object>> sinaAStocks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Map<String, Object> result = new HashMap<>();
        try {
            String[] allCodes = getACodes();
            int targetStart = Math.max(0, (page - 1) * pageSize);
            int validSeen = 0;
            int cursor = 0;
            List<Map<String, Object>> stocks = new ArrayList<>();
            while (cursor < allCodes.length && stocks.size() < pageSize) {
                int end = Math.min(cursor + 80, allCodes.length);
                StringBuilder sb = new StringBuilder();
                for (int i = cursor; i < end; i++) {
                    if (i > cursor) sb.append(",");
                    sb.append(allCodes[i]);
                }
                for (Map<String, Object> stock : fetchRealtimeBatch(sb.toString())) {
                    if (validSeen++ >= targetStart && stocks.size() < pageSize) {
                        stocks.add(stock);
                    }
                }
                cursor = end;
            }

            result.put("code", 200);
            result.put("data", stocks);
            result.put("total", allCodes.length);
            result.put("page", page);
            result.put("pageSize", pageSize);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取A股列表失败: {}", e.getMessage());
            result.put("code", 500);
            result.put("message", e.getMessage());
            result.put("data", new ArrayList<>());
            return ResponseEntity.ok(result);
        }
    }

    // ================================================================
    //  美股列表（分页）
    // ================================================================
    @GetMapping("/sina/us-stocks")
    public ResponseEntity<Map<String, Object>> sinaUSStocks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Map<String, Object> result = new HashMap<>();
        try {
            String[] allCodes = {
                "gb_aapl","gb_msft","gb_googl","gb_amzn","gb_nvda",
                "gb_tsla","gb_meta","gb_brk.b","gb_jpm","gb_v",
                "gb_unh","gb_hd","gb_pg","gb_ma","gb_dis",
                "gb_pfe","gb_ko","gb_pep","gb_abt","gb_t",
                "gb_xom","gb_cvx","gb_wmt","gb_bac","gb_c",
                "gb_ms","gb_gs","gb_nke","gb_mcd","gb_intc"
            };

            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, allCodes.length);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (i > start) sb.append(",");
                sb.append(allCodes[i]);
            }

            List<Map<String, Object>> stocks = fetchRealtimeBatch(sb.toString());

            result.put("code", 200);
            result.put("data", stocks);
            result.put("total", allCodes.length);
            result.put("page", page);
            result.put("pageSize", pageSize);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            result.put("data", new ArrayList<>());
            return ResponseEntity.ok(result);
        }
    }

    // ================================================================
    //  A股代码列表（本地生成 + 缓存，不需要外部API）
    // ================================================================
    private String[] getACodes() {
        if (cachedACodes != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS) {
            return cachedACodes;
        }
        List<String> codes = generateAllACodes();
        cachedACodes = codes.toArray(new String[0]);
        cacheTimestamp = System.currentTimeMillis();
        log.info("A股代码列表已生成，共 {} 只", cachedACodes.length);
        return cachedACodes;
    }

    /**
     * 本地生成全量A股代码（不依赖任何外部API）
     * 覆盖所有主流前缀区间
     */
    private List<String> generateAllACodes() {
        Set<String> set = new LinkedHashSet<>();
        // 上海主板: 600000-605999, 700000-700999(配股)
        for (int i = 0; i < 6000; i++) set.add(String.format("sh6%05d", i));
        // 上海科创板: 688000-689999
        for (int i = 0; i < 2000; i++) set.add(String.format("sh68%04d", i));
        // 深圳主板: 000000-001999, 200000-200999(B股)
        for (int i = 0; i < 2000; i++) set.add(String.format("sz%06d", i));
        // 深圳中小板: 002000-004999
        for (int i = 0; i < 3000; i++) set.add(String.format("sz00%04d", 2000 + i));
        // 深圳创业板: 300000-301999
        for (int i = 0; i < 2000; i++) set.add(String.format("sz30%04d", i));
        // 北交所: 920000-921999, 430000-439999
        for (int i = 0; i < 2000; i++) set.add(String.format("bj92%04d", i));
        return new ArrayList<>(set);
    }

    // ================================================================
    //  工具方法
    // ================================================================

    private void addChange(Map<String, Object> s) {
        double prev = (double) s.getOrDefault(KEY_PREV_CLOSE, 0.0);
        double curr = (double) s.getOrDefault(KEY_CURRENT, 0.0);
        s.put(KEY_CHANGE, curr - prev);
        s.put(KEY_CHANGE_PCT, prev > 0 ? (curr - prev) / prev * 100 : 0);
    }

    /** 清理代码前缀: sh600519 -> 600519, sz000001 -> 000001, gb_aapl -> aapl */
    private String cleanCode(String raw) {
        return raw.replace("sh", "").replace("sz", "").replace("gb_", "").replace("bj", "");
    }

    /** 通用HTTP GET请求 */
    private String fetchUrl(String urlStr, String encoding) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Referer", "https://finance.sina.com.cn");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(8000);

        if ("GBK".equalsIgnoreCase(encoding)) {
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
            is.close();
            return new String(baos.toByteArray(), "GBK");
        } else {
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l);
            r.close();
            return sb.toString();
        }
    }

    private double parseD(String val) {
        try { return Double.parseDouble(val); } catch (Exception e) { return 0.0; }
    }
}
