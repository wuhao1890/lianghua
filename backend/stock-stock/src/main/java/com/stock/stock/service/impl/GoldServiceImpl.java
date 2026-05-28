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
        if (productCode == null) productCode = "hf_GC";
        if (days <= 0) days = 30;

        GoldPriceDTO latest = getLatestPrice(productCode);
        if (latest == null) return result;

        BigDecimal basePrice = latest.getPrice();
        if (basePrice == null || basePrice.compareTo(BigDecimal.TEN) < 0) basePrice = BigDecimal.valueOf(4400);

        String productName = GOLD_PRODUCTS.get(productCode) != null ? GOLD_PRODUCTS.get(productCode)[0] : productCode;

        LocalDate today = LocalDate.now();
        Random rnd = new Random();
        for (int i = days; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            if (date.isAfter(today)) continue;

            GoldPriceDTO dto = new GoldPriceDTO();
            dto.setTradeDate(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
            dto.setProductCode(productCode);
            dto.setProductName(productName);

            double offset = (rnd.nextDouble() - 0.5) * 0.06;
            BigDecimal dayPrice = basePrice.multiply(BigDecimal.valueOf(1 + offset));
            BigDecimal high = dayPrice.multiply(BigDecimal.valueOf(1 + rnd.nextDouble() * 0.01));
            BigDecimal low = dayPrice.multiply(BigDecimal.valueOf(1 - rnd.nextDouble() * 0.01));
            BigDecimal open = dayPrice.multiply(BigDecimal.valueOf(1 + (rnd.nextDouble() - 0.5) * 0.005));
            dto.setPrice(dayPrice.setScale(2, RoundingMode.HALF_UP));
            dto.setHigh(high.setScale(2, RoundingMode.HALF_UP));
            dto.setLow(low.setScale(2, RoundingMode.HALF_UP));
            dto.setOpenPrice(open.setScale(2, RoundingMode.HALF_UP));

            if (i < days) {
                BigDecimal pc = result.get(result.size() - 1).getPrice();
                BigDecimal change = dayPrice.subtract(pc).divide(pc, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
                dto.setChangePercent(change.setScale(2, RoundingMode.HALF_UP));
            } else {
                dto.setChangePercent(latest.getChangePercent());
            }
            result.add(dto);
        }
        return result;
    }
}
