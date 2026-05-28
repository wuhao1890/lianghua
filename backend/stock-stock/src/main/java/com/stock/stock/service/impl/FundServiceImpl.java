package com.stock.stock.service.impl;

import com.stock.stock.dto.FundInfoDTO;
import com.stock.stock.service.FundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
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
import java.util.stream.Collectors;

@Slf4j
@Service
public class FundServiceImpl implements FundService {

    // Full fund list cache: code -> [name, fullName, type, pinyin]
    private List<String[]> allFunds = new ArrayList<>();
    private volatile boolean initialized = false;
    private long lastFetchTime = 0;
    private static final long CACHE_DURATION = 3600000; // 1 hour cache

    @PostConstruct
    public void init() {
        fetchAllFunds();
    }

    private void fetchAllFunds() {
        try {
            URL url = new URL("http://fund.eastmoney.com/js/fundcode_search.js");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            conn.disconnect();

            String raw = sb.toString();
            int start = raw.indexOf("[");
            int end = raw.lastIndexOf("]");
            if (start < 0 || end < 0) return;

            allFunds.clear();
            String content = raw.substring(start + 1, end);
            // Parse array entries: ["000001","HXCZHH","华夏成长混合","混合型-灵活","HUAXIACHENGZHANGHUNHE"]
            int i = 0;
            while (i < content.length()) {
                int arrStart = content.indexOf("[", i);
                int arrEnd = content.indexOf("]", arrStart);
                if (arrStart < 0 || arrEnd < 0) break;

                String arr = content.substring(arrStart + 1, arrEnd);
                String[] parts = parseArrayString(arr);
                if (parts.length >= 5) {
                    allFunds.add(new String[]{
                            parts[0].trim(),         // code
                            parts[2].trim(),         // name
                            parts[3].trim(),         // type
                            parts[4].trim()          // pinyin
                    });
                }
                i = arrEnd + 1;
            }
            initialized = true;
            lastFetchTime = System.currentTimeMillis();
            log.info("加载基金列表: {} 只", allFunds.size());
        } catch (Exception e) {
            log.error("获取基金列表失败", e);
        }
    }

    private String[] parseArrayString(String s) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (char c : s.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    @Override
    public Map<String, Object> getFundList(String keyword, String fundType, int page, int pageSize) {
        // Refresh cache if needed
        if (!initialized || System.currentTimeMillis() - lastFetchTime > CACHE_DURATION) {
            fetchAllFunds();
        }

        List<FundInfoDTO> filtered = new ArrayList<>();
        for (String[] fund : allFunds) {
            String code = fund[0], name = fund[1], type = fund[2];

            // Filter by keyword
            if (keyword != null && !keyword.isEmpty()) {
                String kw = keyword.toLowerCase();
                if (!code.contains(kw) && !name.toLowerCase().contains(kw)) continue;
            }
            // Filter by type
            if (fundType != null && !fundType.isEmpty() && !fundType.equals("全部")) {
                if (!type.contains(fundType) && !type.startsWith(fundType)) continue;
            }

            FundInfoDTO dto = new FundInfoDTO();
            dto.setCode(code);
            dto.setName(name);
            dto.setFundType(type);
            filtered.add(dto);
        }

        // Sort by code
        filtered.sort((a, b) -> a.getCode().compareTo(b.getCode()));

        int total = filtered.size();
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, total);

        List<FundInfoDTO> pageList = from < total ? filtered.subList(from, to) : new ArrayList<>();

        // Fetch real-time NAV for visible items
        for (FundInfoDTO dto : pageList) {
            try {
                FundInfoDTO realtime = fetchRealtimeNav(dto.getCode());
                if (realtime != null) {
                    dto.setNav(realtime.getNav());
                    dto.setAccNav(realtime.getAccNav());
                    dto.setNavDate(realtime.getNavDate());
                    dto.setChangePercent(realtime.getChangePercent());
                }
                Thread.sleep(80);
            } catch (Exception e) { /* skip */ }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageList);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    private FundInfoDTO fetchRealtimeNav(String code) {
        try {
            String url = "http://fundgz.1234567.com.cn/js/" + code + ".js";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            conn.disconnect();

            String raw = sb.toString();
            int s = raw.indexOf("{"), e = raw.lastIndexOf("}");
            if (s < 0 || e < 0) return null;

            String json = raw.substring(s + 1, e);
            Map<String, String> data = new HashMap<>();
            String[] pairs = json.split(",");
            for (String pair : pairs) {
                int ci = pair.indexOf(":");
                if (ci > 0) {
                    data.put(pair.substring(0, ci).replace("\"", "").trim(),
                            pair.substring(ci + 1).replace("\"", "").trim());
                }
            }

            FundInfoDTO dto = new FundInfoDTO();
            dto.setCode(code);
            dto.setName(data.getOrDefault("name", ""));
            dto.setNavDate(data.getOrDefault("jzrq", ""));
            String gsz = data.get("gsz");
            String dwjz = data.get("dwjz");
            if (gsz != null && !gsz.isEmpty()) {
                dto.setNav(new BigDecimal(gsz));
            } else if (dwjz != null && !dwjz.isEmpty()) {
                dto.setNav(new BigDecimal(dwjz));
            }
            dto.setAccNav(dto.getNav());
            String gszzl = data.get("gszzl");
            if (gszzl != null && !gszzl.isEmpty()) {
                try {
                    dto.setChangePercent(new BigDecimal(gszzl));
                } catch (Exception ignored) {}
            }
            return dto;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public FundInfoDTO getFundDetail(String code) {
        FundInfoDTO dto = fetchRealtimeNav(code);
        if (dto == null) {
            // Fallback to cached name
            for (String[] fund : allFunds) {
                if (fund[0].equals(code)) {
                    dto = new FundInfoDTO();
                    dto.setCode(code);
                    dto.setName(fund[1]);
                    dto.setFundType(fund[2]);
                    break;
                }
            }
        } else {
            // Fill in fund type from cache
            for (String[] fund : allFunds) {
                if (fund[0].equals(code)) {
                    dto.setFundType(fund[2]);
                    break;
                }
            }
        }
        return dto;
    }

    @Override
    public List<FundInfoDTO> getFundNavHistory(String code, int days) {
        // Generate simulated NAV history based on current NAV
        List<FundInfoDTO> result = new ArrayList<>();
        FundInfoDTO current = fetchRealtimeNav(code);
        BigDecimal baseNav = current != null && current.getNav() != null ? current.getNav() : BigDecimal.ONE;

        String fundName = "";
        for (String[] fund : allFunds) {
            if (fund[0].equals(code)) {
                fundName = fund[1];
                break;
            }
        }

        Random rnd = new Random();
        LocalDate today = LocalDate.now();
        for (int i = days; i >= 0; i--) {
            FundInfoDTO dto = new FundInfoDTO();
            dto.setCode(code);
            dto.setName(fundName);
            dto.setNavDate(today.minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE));
            double offset = (rnd.nextDouble() - 0.5) * 0.08;
            BigDecimal nav = baseNav.multiply(BigDecimal.valueOf(1 + offset)).setScale(4, RoundingMode.HALF_UP);
            dto.setNav(nav);
            dto.setAccNav(nav);
            if (i < days) {
                BigDecimal pc = result.get(result.size() - 1).getNav();
                BigDecimal chg = nav.subtract(pc).divide(pc, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
                dto.setChangePercent(chg.setScale(2, RoundingMode.HALF_UP));
            }
            result.add(dto);
        }
        return result;
    }
}
