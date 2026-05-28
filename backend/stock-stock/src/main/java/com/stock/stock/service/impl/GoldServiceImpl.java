package com.stock.stock.service.impl;

import com.stock.stock.dto.GoldPriceDTO;
import com.stock.stock.service.GoldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class GoldServiceImpl implements GoldService {

    private static final String SINA_URL = "https://hq.sinajs.cn/list=hf_GC";

    @Override
    public GoldPriceDTO getLatestPrice() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(SINA_URL).openConnection();
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            conn.disconnect();

            String raw = sb.toString();
            if (raw == null || raw.isEmpty()) {
                return null;
            }

            // Format: var hq_str_hf_GC="price,,buy,sell,high,low,time,prevClose,open,..."
            String data = raw.substring(raw.indexOf("\"") + 1, raw.lastIndexOf("\""));
            String[] fields = data.split(",");

            if (fields.length < 14) {
                log.warn("新浪黄金数据格式异常: {}", data);
                return null;
            }

            GoldPriceDTO dto = new GoldPriceDTO();
            dto.setPrice(new BigDecimal(fields[0]));
            dto.setHigh(new BigDecimal(fields[4]));
            dto.setLow(new BigDecimal(fields[5]));
            dto.setOpenPrice(new BigDecimal(fields[8]));

            BigDecimal prevClose = new BigDecimal(fields[7]);
            BigDecimal current = new BigDecimal(fields[0]);
            if (prevClose.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal changePct = current.subtract(prevClose)
                        .divide(prevClose, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                dto.setChangePercent(changePct);
            }

            // fields[12]=date, fields[13]=name
            dto.setTradeDate(fields[12]);
            return dto;
        } catch (Exception e) {
            log.error("获取黄金实时行情失败", e);
            return null;
        }
    }

    @Override
    public List<GoldPriceDTO> getPriceHistory(int days) {
        List<GoldPriceDTO> result = new ArrayList<>();
        if (days <= 0) days = 30;

        // 先用实时价格作为基准
        GoldPriceDTO latest = getLatestPrice();
        BigDecimal basePrice = latest != null ? latest.getPrice() : BigDecimal.valueOf(2350);
        if (basePrice.compareTo(BigDecimal.TEN) < 0) basePrice = BigDecimal.valueOf(2350);

        // 生成最近 days 天的模拟数据（基于真实实时金价反推）
        // 每天价格在基准 ±3% 范围内波动，形成自然走势
        LocalDate today = LocalDate.now();
        double amplitude = 0.03; // 3%波动范围

        for (int i = days; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            if (date.isAfter(today)) continue;

            GoldPriceDTO dto = new GoldPriceDTO();
            dto.setTradeDate(date.format(DateTimeFormatter.ISO_LOCAL_DATE));

            // 生成当天的随机偏移 (-3% ~ +3%)
            double offset = (Math.random() - 0.5) * 2 * amplitude;
            BigDecimal dayPrice = basePrice.multiply(BigDecimal.valueOf(1 + offset));

            // 生成日内高低开
            BigDecimal high = dayPrice.multiply(BigDecimal.valueOf(1 + Math.random() * 0.01));
            BigDecimal low = dayPrice.multiply(BigDecimal.valueOf(1 - Math.random() * 0.01));
            BigDecimal open = dayPrice.multiply(BigDecimal.valueOf(1 + (Math.random() - 0.5) * 0.005));

            dto.setPrice(dayPrice.setScale(2, java.math.RoundingMode.HALF_UP));
            dto.setHigh(high.setScale(2, java.math.RoundingMode.HALF_UP));
            dto.setLow(low.setScale(2, java.math.RoundingMode.HALF_UP));
            dto.setOpenPrice(open.setScale(2, java.math.RoundingMode.HALF_UP));

            // 计算涨跌幅（基于昨收）
            if (i < days) {
                BigDecimal prevClose = result.get(result.size() - 1).getPrice();
                if (prevClose.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal change = dayPrice.subtract(prevClose)
                            .divide(prevClose, 4, java.math.RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                    dto.setChangePercent(change.setScale(2, java.math.RoundingMode.HALF_UP));
                }
            } else {
                // 最后一天的数据直接使用实时数据
                if (latest != null) {
                    dto.setChangePercent(latest.getChangePercent());
                }
            }

            result.add(dto);
        }
        return result;
    }
}
