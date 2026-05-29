package com.stock.stock.service.impl;

import com.stock.stock.dto.GoldPriceDTO;
import com.stock.stock.service.GoldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class GoldServiceImpl implements GoldService {

    // Gold products mapping: code -> {name, sina prefix}
    private static final Map<String, String[]> GOLD_PRODUCTS = new LinkedHashMap<>();
    static {
        GOLD_PRODUCTS.put("hf_GC", new String[]{"COMEX黄金期货", "hf"});
        GOLD_PRODUCTS.put("sh518880", new String[]{"华安黄金ETF", "sh"});
        GOLD_PRODUCTS.put("sz159934", new String[]{"易方达黄金ETF", "sz"});
        GOLD_PRODUCTS.put("sz159937", new String[]{"博时黄金ETF", "sz"});
        GOLD_PRODUCTS.put("sz159812", new String[]{"黄金基金ETF", "sz"});
    }

    @Override
    public Map<String, String> getProducts() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : GOLD_PRODUCTS.entrySet()) {
            map.put(e.getKey(), e.getValue()[0]);
        }
        return map;
    }

    @Override
    public GoldPriceDTO getLatestPrice(String productCode) {
        if (productCode == null || productCode.isEmpty()) productCode = "hf_GC";
        try {
            String sinaCode;
            if (productCode.startsWith("hf_")) {
                sinaCode = productCode;
            } else {
                // ETF codes like sh518880, sz159934
                sinaCode = productCode;
            }

            HttpURLConnection conn = (HttpURLConnection) new URL("https://hq.sinajs.cn/list=" + sinaCode).openConnection();
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            conn.disconnect();

            String raw = sb.toString();
            if (raw == null || raw.isEmpty() || !raw.contains("\"")) return null;
            String data = raw.substring(raw.indexOf("\"") + 1, raw.lastIndexOf("\""));
            String[] fields = data.split(",");
            if (fields.length < 8) return null;

            GoldPriceDTO dto = new GoldPriceDTO();
            if (productCode.startsWith("hf_")) {
                // COMEX futures format: price,buy,sell,high,low,time,prevClose,open,...
                dto.setPrice(new BigDecimal(fields[0]));
                dto.setHigh(new BigDecimal(fields[4]));
                dto.setLow(new BigDecimal(fields[5]));
                dto.setOpenPrice(new BigDecimal(fields[8]));
                if (new BigDecimal(fields[7]).compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal pct = new BigDecimal(fields[0]).subtract(new BigDecimal(fields[7]))
                            .divide(new BigDecimal(fields[7]), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
                    dto.setChangePercent(pct.setScale(2, RoundingMode.HALF_UP));
                }
                dto.setTradeDate(fields[12]);
            } else {
                // ETF format: name,open,prevClose,currentPrice,high,low,...
                dto.setOpenPrice(new BigDecimal(fields[1]));
                dto.setPrice(new BigDecimal(fields[3]));
                dto.setHigh(new BigDecimal(fields[4]));
                dto.setLow(new BigDecimal(fields[5]));
                if (new BigDecimal(fields[2]).compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal pct = new BigDecimal(fields[3]).subtract(new BigDecimal(fields[2]))
                            .divide(new BigDecimal(fields[2]), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
                    dto.setChangePercent(pct.setScale(2, RoundingMode.HALF_UP));
                }
                dto.setTradeDate(fields[30]);
            }
            dto.setProductCode(productCode);
            String[] meta = GOLD_PRODUCTS.get(productCode);
            if (meta != null) {
                dto.setProductName(meta[0]);
            } else {
                // Try to extract name from the data itself for unknown codes
                if (!productCode.startsWith("hf_") && fields.length > 0) {
                    dto.setProductName(fields[0]);
                } else {
                    dto.setProductName(productCode);
                }
            }
            return dto;
        } catch (Exception e) {
            log.error("获取黄金[{}]行情失败", productCode, e);
            return null;
        }
    }

    @Override
    public List<GoldPriceDTO> getPriceHistory(String productCode, int days) {
        List<GoldPriceDTO> result = new ArrayList<>();
        try {
            if (productCode == null || productCode.isEmpty()) productCode = "hf_GC";

            // For gold ETFs: use Tencent K-line API
            if (!productCode.startsWith("hf_")) {
                fetchFromTencent(productCode, days, result);
            } else {
                // For COMEX gold futures: use ETF sh518880 as proxy, scale to COMEX price level
                List<GoldPriceDTO> etfHistory = new ArrayList<>();
                fetchFromTencent("sh518880", days, etfHistory);

                if (!etfHistory.isEmpty()) {
                    // Calculate scaling factor from latest realtime prices
                    BigDecimal comexPrice = null;
                    BigDecimal etfPrice = null;
                    try {
                        GoldPriceDTO comexLatest = getLatestPrice("hf_GC");
                        GoldPriceDTO etfLatest = getLatestPrice("sh518880");
                        if (comexLatest != null && etfLatest != null
                                && comexLatest.getPrice() != null && etfLatest.getPrice() != null
                                && etfLatest.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                            comexPrice = comexLatest.getPrice();
                            etfPrice = etfLatest.getPrice();
                        }
                    } catch (Exception e) {
                        log.warn("获取缩放基准失败, 使用默认倍数: {}", e.getMessage());
                    }

                    BigDecimal scaleFactor;
                    if (comexPrice != null && etfPrice != null) {
                        scaleFactor = comexPrice.divide(etfPrice, 4, RoundingMode.HALF_UP);
                    } else {
                        scaleFactor = new BigDecimal("480"); // approximate: COMEX ~= 480 * ETF price
                    }

                    String[] meta = GOLD_PRODUCTS.get(productCode);
                    String productName = meta != null ? meta[0] : productCode;

                    for (GoldPriceDTO etf : etfHistory) {
                        GoldPriceDTO dto = new GoldPriceDTO();
                        dto.setProductCode(productCode);
                        dto.setProductName(productName);
                        dto.setTradeDate(etf.getTradeDate());
                        if (etf.getPrice() != null) {
                            dto.setPrice(etf.getPrice().multiply(scaleFactor).setScale(2, RoundingMode.HALF_UP));
                        }
                        if (etf.getOpenPrice() != null) {
                            dto.setOpenPrice(etf.getOpenPrice().multiply(scaleFactor).setScale(2, RoundingMode.HALF_UP));
                        }
                        if (etf.getHigh() != null) {
                            dto.setHigh(etf.getHigh().multiply(scaleFactor).setScale(2, RoundingMode.HALF_UP));
                        }
                        if (etf.getLow() != null) {
                            dto.setLow(etf.getLow().multiply(scaleFactor).setScale(2, RoundingMode.HALF_UP));
                        }
                        if (dto.getPrice() != null && dto.getOpenPrice() != null
                                && dto.getOpenPrice().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal pct = dto.getPrice().subtract(dto.getOpenPrice())
                                    .divide(dto.getOpenPrice(), 4, RoundingMode.HALF_UP)
                                    .multiply(new BigDecimal("100"));
                            dto.setChangePercent(pct.setScale(2, RoundingMode.HALF_UP));
                        }
                        result.add(dto);
                    }
                }
            }
            log.info("获取黄金历史K线: {} - {} 条", productCode, result.size());
        } catch (Exception e) {
            log.warn("获取黄金历史K线失败 {}: {}", productCode, e.getMessage());
        }
        return result;
    }

    /**
     * 从腾讯财经获取ETF K线数据
     */
    private void fetchFromTencent(String code, int days, List<GoldPriceDTO> result) {
        try {
            String url = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=" + code + ",day,,," + days + ",qfq";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Referer", "https://finance.qq.com");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            conn.disconnect();

            String json = sb.toString();
            if (json == null || json.isEmpty()) return;

            // Parse Tencent format: {"data":{"sh518880":{"qfqday":[["2026-05-28","open","close","high","low","volume"],...]}}}
            // Find "qfqday" array
            int qfqStart = json.indexOf("\"qfqday\"");
            if (qfqStart < 0) return;
            int arrStart = json.indexOf("[", qfqStart);
            if (arrStart < 0) return;
            int arrEnd = json.lastIndexOf("]");
            if (arrEnd < arrStart) return;

            json = json.substring(arrStart + 1, arrEnd);

            int pos = 0;
            while (pos < json.length()) {
                int bs = json.indexOf("[", pos);
                int be = json.indexOf("]", bs);
                if (bs < 0 || be < 0) break;

                String item = json.substring(bs + 1, be);
                String[] fields = item.split(",");
                for (int i = 0; i < fields.length; i++) {
                    fields[i] = fields[i].replace("\"", "").trim();
                }

                if (fields.length >= 5) {
                    GoldPriceDTO dto = new GoldPriceDTO();
                    dto.setTradeDate(fields[0]);
                    try {
                        dto.setOpenPrice(new BigDecimal(fields[1]));
                        dto.setPrice(new BigDecimal(fields[2]));
                        dto.setHigh(new BigDecimal(fields[3]));
                        dto.setLow(new BigDecimal(fields[4]));
                        if (dto.getPrice() != null && dto.getOpenPrice() != null
                                && dto.getOpenPrice().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal pct = dto.getPrice().subtract(dto.getOpenPrice())
                                    .divide(dto.getOpenPrice(), 4, RoundingMode.HALF_UP)
                                    .multiply(new BigDecimal("100"));
                            dto.setChangePercent(pct.setScale(2, RoundingMode.HALF_UP));
                        }
                        result.add(dto);
                    } catch (Exception ignored) {}
                }
                pos = be + 1;
            }
        } catch (Exception e) {
            log.warn("从腾讯获取K线失败 {}: {}", code, e.getMessage());
        }
    }
}
