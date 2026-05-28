package com.stock.stock.service.impl;

import com.stock.stock.dto.FundInfoDTO;
import com.stock.stock.service.FundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FundServiceImpl implements FundService {

    // 30只热门基金 (混合型/股票型/指数型)
    private static final String[] HOT_FUNDS = {
        "110011", "005827", "161725", "000001", "003834",
        "110022", "001230", "163402", "519772", "000697",
        "008888", "005669", "162605", "008286", "010186",
        "007465", "001714", "005911", "006327", "110005",
        "161726", "501188", "270002", "519069", "020010",
        "166002", "519704", "040008", "340007", "070003"
    };

    // 基金名称缓存 (因为实时API不返回名称)
    private static final Map<String, String[]> FUND_META = new HashMap<>();

    static {
        FUND_META.put("110011", new String[]{"易方达优质精选混合", "混合型"});
        FUND_META.put("005827", new String[]{"易方达蓝筹精选混合", "混合型"});
        FUND_META.put("161725", new String[]{"招商中证白酒指数", "指数型"});
        FUND_META.put("000001", new String[]{"华夏成长混合", "混合型"});
        FUND_META.put("003834", new String[]{"华夏能源革新股票", "股票型"});
        FUND_META.put("110022", new String[]{"易方达消费行业股票", "股票型"});
        FUND_META.put("001230", new String[]{"鹏华医药科技股票", "股票型"});
        FUND_META.put("163402", new String[]{"兴全趋势投资混合", "混合型"});
        FUND_META.put("519772", new String[]{"交银新生活力混合", "混合型"});
        FUND_META.put("000697", new String[]{"汇添富移动互联股票", "股票型"});
        FUND_META.put("008888", new String[]{"华夏半导体龙头混合", "混合型"});
        FUND_META.put("005669", new String[]{"前海开源公用事业股票", "股票型"});
        FUND_META.put("162605", new String[]{"景顺长城鼎益混合", "混合型"});
        FUND_META.put("008286", new String[]{"易方达研究精选股票", "股票型"});
        FUND_META.put("010186", new String[]{"嘉实核心成长混合", "混合型"});
        FUND_META.put("007465", new String[]{"国泰中证生物医药ETF联接", "指数型"});
        FUND_META.put("001714", new String[]{"工银文体产业股票", "股票型"});
        FUND_META.put("005911", new String[]{"广发双擎升级混合", "混合型"});
        FUND_META.put("006327", new String[]{"易方达中证海外互联ETF联接", "指数型"});
        FUND_META.put("110005", new String[]{"易方达积极成长混合", "混合型"});
        FUND_META.put("161726", new String[]{"国泰中证煤炭ETF联接", "指数型"});
        FUND_META.put("501188", new String[]{"添富科创板", "混合型"});
        FUND_META.put("270002", new String[]{"广发稳健增长混合", "混合型"});
        FUND_META.put("519069", new String[]{"汇添富价值精选混合", "混合型"});
        FUND_META.put("020010", new String[]{"国泰金牛创新混合", "混合型"});
        FUND_META.put("166002", new String[]{"中欧新蓝筹混合", "混合型"});
        FUND_META.put("519704", new String[]{"交银先进制造混合", "混合型"});
        FUND_META.put("040008", new String[]{"华安策略优选混合", "混合型"});
        FUND_META.put("340007", new String[]{"兴全社会责任混合", "混合型"});
        FUND_META.put("070003", new String[]{"嘉实稳健混合", "混合型"});
    }

    @Override
    public List<FundInfoDTO> getFundList() {
        List<FundInfoDTO> result = new ArrayList<>();
        for (String code : HOT_FUNDS) {
            FundInfoDTO dto = fetchFundRealtime(code);
            if (dto != null) {
                result.add(dto);
            }
            // Rate limiting
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        if (result.isEmpty()) {
            log.warn("所有基金实时数据获取失败，返回空列表");
        }
        return result;
    }

    @Override
    public FundInfoDTO getFundDetail(String code) {
        return fetchFundRealtime(code);
    }

    private FundInfoDTO fetchFundRealtime(String code) {
        try {
            String url = "http://fundgz.1234567.com.cn/js/" + code + ".js";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            conn.disconnect();

            String raw = sb.toString();
            // Parse: jsonpgz({"fundcode":"110011","name":"...","jzrq":"2026-05-27","dwjz":"4.4523","gsz":"4.3952","gszzl":"-1.28","gztime":"..."});
            int start = raw.indexOf("{");
            int end = raw.lastIndexOf("}");
            if (start < 0 || end < 0) return null;

            String json = raw.substring(start + 1, end);
            Map<String, String> data = new HashMap<>();
            // Extract key-value pairs (simple parser without dependencies)
            String[] pairs = json.split(",");
            for (String pair : pairs) {
                int colonIdx = pair.indexOf(":");
                if (colonIdx > 0) {
                    String key = pair.substring(0, colonIdx).replace("\"", "").trim();
                    String val = pair.substring(colonIdx + 1).replace("\"", "").trim();
                    data.put(key, val);
                }
            }

            FundInfoDTO dto = new FundInfoDTO();
            dto.setCode(code);

            String name = data.get("name");
            if (name == null || name.isEmpty()) {
                String[] meta = FUND_META.get(code);
                dto.setName(meta != null ? meta[0] : code);
            } else {
                dto.setName(name);
            }

            if (data.containsKey("dwjz") && !data.get("dwjz").isEmpty()) {
                dto.setNav(new BigDecimal(data.get("dwjz")));
            }
            if (data.containsKey("gsz") && !data.get("gsz").isEmpty()) {
                // 估算净值当作当前价（也可显示为"实时估算"）
                dto.setNav(new BigDecimal(data.get("gsz")));
            }
            dto.setNavDate(data.getOrDefault("jzrq", ""));
            dto.setAccNav(dto.getNav()); // 累计净值用单位净值近似

            if (data.containsKey("gszzl")) {
                try {
                    dto.setChangePercent(new BigDecimal(data.get("gszzl")));
                } catch (Exception ignored) {}
            }

            String[] meta = FUND_META.get(code);
            dto.setFundType(meta != null ? meta[1] : "混合型");

            return dto;
        } catch (Exception e) {
            log.warn("获取基金[{}]实时数据失败: {}", code, e.getMessage());
            return null;
        }
    }
}
