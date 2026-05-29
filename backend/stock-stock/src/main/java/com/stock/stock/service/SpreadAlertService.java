package com.stock.stock.service;

import com.alibaba.fastjson2.JSON;
import com.stock.stock.websocket.QuoteWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 跨市场价差监控服务
 * 定期检查A+H股的价差，超过阈值时通过WebSocket推送预警
 */
@Slf4j
@Service
public class SpreadAlertService {

    /** A+H 股对照表 */
    private static final Map<String, AhPair> AH_MAP = new LinkedHashMap<>();
    static {
        AH_MAP.put("601318", new AhPair("601318", "中国平安", "sh601318", "sz02318", "02318", 0.92));
        AH_MAP.put("002594", new AhPair("002594", "比亚迪", "sz002594", "sz01211", "01211", 0.92));
        AH_MAP.put("600585", new AhPair("600585", "海螺水泥", "sh600585", "sz00914", "00914", 0.92));
    }

    /** 溢价率阈值 */
    private static final double PREMIUM_THRESHOLD_HIGH = 20.0;  // 溢价超过20%预警
    private static final double PREMIUM_THRESHOLD_LOW = -10.0;  // 折价超过10%预警

    /** 最近一次检查结果 */
    private final Map<String, SpreadResult> lastResults = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        log.info("跨市场价差监控服务已初始化，监控 {} 对 A+H 股", AH_MAP.size());
    }

    /**
     * 每15分钟检查一次价差
     */
    @Scheduled(fixedRate = 900000)
    public void checkSpreads() {
        log.debug("开始跨市场价差检查...");
        for (AhPair pair : AH_MAP.values()) {
            try {
                SpreadResult result = checkSingleSpread(pair);
                if (result != null) {
                    lastResults.put(pair.aCode, result);
                    // 超过阈值时广播预警
                    if (result.premium > PREMIUM_THRESHOLD_HIGH) {
                        broadcastSpreadAlert(result, "溢价过高", result.premium);
                    } else if (result.premium < PREMIUM_THRESHOLD_LOW) {
                        broadcastSpreadAlert(result, "折价过大", result.premium);
                    }
                }
                Thread.sleep(300);
            } catch (Exception e) {
                log.warn("价差检查失败 {}: {}", pair.aCode, e.getMessage());
            }
        }
        log.debug("跨市场价差检查完成");
    }

    /**
     * 检查单个A+H股的价差
     */
    private SpreadResult checkSingleSpread(AhPair pair) {
        // 获取A股价格
        BigDecimal aPrice = fetchPrice(pair.aSinaCode);
        if (aPrice == null) return null;

        // 获取港股价格
        BigDecimal hkPrice = fetchHkPrice(pair.hkCode);
        if (hkPrice == null) return null;

        // 计算溢价率： (A股 - 港股*汇率) / (港股*汇率) * 100
        BigDecimal hkCny = hkPrice.multiply(BigDecimal.valueOf(pair.rate))
                .setScale(4, RoundingMode.HALF_UP);
        if (hkCny.compareTo(BigDecimal.ZERO) <= 0) return null;

        BigDecimal premium = aPrice.subtract(hkCny)
                .divide(hkCny, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);

        SpreadResult result = new SpreadResult();
        result.aCode = pair.aCode;
        result.aName = pair.aName;
        result.hkCode = pair.hkCode;
        result.aPrice = aPrice;
        result.hkPrice = hkPrice;
        result.hkCnyPrice = hkCny;
        result.premium = premium.doubleValue();
        result.checkTime = new Date();

        return result;
    }

    /**
     * 获取A股实时价格（新浪API）
     */
    private BigDecimal fetchPrice(String sinaCode) {
        try {
            String url = "https://hq.sinajs.cn/list=" + sinaCode;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            reader.close();
            conn.disconnect();

            if (line == null || line.isEmpty()) return null;
            // 格式: var hq_str_sh601318="中国平安,42.50,42.60,...
            String[] parts = line.split(",");
            if (parts.length >= 4) {
                return new BigDecimal(parts[3].trim()); // 当前价
            }
        } catch (Exception e) {
            log.debug("获取A股价格失败 {}: {}", sinaCode, e.getMessage());
        }
        return null;
    }

    /**
     * 获取港股实时价格（新浪API）
     */
    private BigDecimal fetchHkPrice(String code) {
        try {
            String url = "https://hq.sinajs.cn/list=rt_hk" + code;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            reader.close();
            conn.disconnect();

            if (line == null || line.isEmpty()) return null;
            // 格式: var hq_str_rt_hk02318="中国平安,42.50,42.60
            String[] parts = line.split(",");
            if (parts.length >= 3) {
                return new BigDecimal(parts[2].trim()); // 当前价
            }
        } catch (Exception e) {
            log.debug("获取港股价格失败 {}: {}", code, e.getMessage());
        }
        return null;
    }

    /**
     * 广播价差预警到WebSocket
     */
    private void broadcastSpreadAlert(SpreadResult result, String type, double premium) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "spread_alert");
            Map<String, Object> data = new HashMap<>();
            data.put("aCode", result.aCode);
            data.put("aName", result.aName);
            data.put("hkCode", result.hkCode);
            data.put("aPrice", result.aPrice);
            data.put("hkPrice", result.hkPrice);
            data.put("hkCnyPrice", result.hkCnyPrice);
            data.put("premium", result.premium);
            data.put("alertType", type);
            data.put("checkTime", result.checkTime.toString());
            msg.put("data", data);

            QuoteWebSocketHandler.broadcast("spread_alert", msg);
            log.info("价差预警: {} {} 溢价={}%", result.aName, type, String.format("%.2f", premium));
        } catch (Exception e) {
            log.warn("广播价差预警失败: {}", e.getMessage());
        }
    }

    /**
     * 获取最近价差检查结果
     */
    public List<SpreadResult> getRecentResults() {
        return new ArrayList<>(lastResults.values());
    }

    // ===== DTOs =====

    public static class AhPair {
        public String aCode;
        public String aName;
        public String aSinaCode;
        public String hkSinaCode;
        public String hkCode;
        public double rate;

        public AhPair(String aCode, String aName, String aSinaCode, String hkSinaCode, String hkCode, double rate) {
            this.aCode = aCode;
            this.aName = aName;
            this.aSinaCode = aSinaCode;
            this.hkSinaCode = hkSinaCode;
            this.hkCode = hkCode;
            this.rate = rate;
        }
    }

    public static class SpreadResult {
        public String aCode;
        public String aName;
        public String hkCode;
        public BigDecimal aPrice;
        public BigDecimal hkPrice;
        public BigDecimal hkCnyPrice;
        public double premium;
        public Date checkTime;

        public String getAlertLevel() {
            if (premium > 20) return "danger";
            if (premium > 10) return "warning";
            if (premium < -10) return "danger";
            if (premium < -5) return "warning";
            return "normal";
        }
    }
}
