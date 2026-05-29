package com.stock.ai.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.stock.ai.dto.AiAnalyzeRequest;
import com.stock.ai.dto.AiAnalysisResponse;
import com.stock.ai.entity.AiAnalysisResult;
import com.stock.ai.entity.AiModelConfig;
import com.stock.ai.mapper.AiAnalysisResultMapper;
import com.stock.ai.service.AiAnalysisService;
import com.stock.ai.service.AiModelConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private static final String STOCK_SERVICE_URL = "http://localhost:8082";
    private static final String ANALYSIS_SERVICE_URL = "http://localhost:8084";

    private final RestTemplate restTemplate;
    private final AiModelConfigService aiModelConfigService;
    private final AiAnalysisResultMapper aiAnalysisResultMapper;

    public AiAnalysisServiceImpl(RestTemplate restTemplate,
                                 AiModelConfigService aiModelConfigService,
                                 AiAnalysisResultMapper aiAnalysisResultMapper) {
        this.restTemplate = restTemplate;
        this.aiModelConfigService = aiModelConfigService;
        this.aiAnalysisResultMapper = aiAnalysisResultMapper;
    }

    @Override
    public AiAnalysisResponse analyzeStock(AiAnalyzeRequest request, Long userId) {
        // 1. 加载模型配置
        AiModelConfig config = aiModelConfigService.getById(request.getConfigId());
        if (config == null) {
            throw new RuntimeException("模型配置不存在: " + request.getConfigId());
        }

        String code = request.getStockCode();

        // 2. 获取股票实时行情
        Map<String, Object> realtime = fetchRealtime(code);
        log.info("获取实时行情成功: {}", realtime);

        // 3. 获取K线数据
        List<Map<String, Object>> klineList = fetchKline(code);
        log.info("获取K线数据成功, 共 {} 条", klineList.size());

        // 4. 获取技术指标信号
        Map<String, Object> signalData = fetchTradeSignal(code);
        log.info("获取交易信号成功: {}", signalData);

        // 5. 获取大盘指数
        List<Map<String, Object>> indices = fetchIndices();
        log.info("获取大盘指数成功, 共 {} 个", indices.size());

        // 6. 构建Prompt
        String prompt = buildPrompt(code, realtime, klineList, signalData, indices, request.getCustomPrompt());

        // 7. 调用LLM API
        String llmResponse = callLLM(config, prompt);
        log.info("LLM响应: {}", llmResponse);

        // 8. 解析LLM响应
        AiAnalysisResponse response = parseLLMResponse(llmResponse, code, config.getModelName());

        // 9. 保存结果
        saveResult(config.getId(), code, response);

        return response;
    }

    /**
     * 获取股票实时行情
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchRealtime(String code) {
        // 判断是沪市还是深市
        String prefix = code.startsWith("6") || code.startsWith("5") ? "sh" : "sz";
        String url = STOCK_SERVICE_URL + "/api/stock/sina/realtime?codes=" + prefix + code;
        try {
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (result != null && (Integer) result.get("code") == 200) {
                List<Map<String, Object>> dataList = (List<Map<String, Object>>) result.get("data");
                if (dataList != null && !dataList.isEmpty()) {
                    return dataList.get(0);
                }
            }
        } catch (Exception e) {
            log.warn("获取实时行情失败: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    /**
     * 获取K线数据
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchKline(String code) {
        String url = STOCK_SERVICE_URL + "/api/stock/kline/" + code + "?period=daily&limit=60";
        try {
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (result != null && (Integer) result.get("code") == 200) {
                Object data = result.get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
            }
        } catch (Exception e) {
            log.warn("获取K线数据失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 获取交易信号
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchTradeSignal(String code) {
        String url = ANALYSIS_SERVICE_URL + "/api/analysis/signal/" + code;
        try {
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (result != null && (Integer) result.get("code") == 200) {
                Object data = result.get("data");
                if (data instanceof Map) {
                    return (Map<String, Object>) data;
                }
            }
        } catch (Exception e) {
            log.warn("获取交易信号失败: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    /**
     * 获取大盘指数
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchIndices() {
        String url = STOCK_SERVICE_URL + "/api/stock/sina/indices";
        try {
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (result != null && (Integer) result.get("code") == 200) {
                return (List<Map<String, Object>>) result.get("data");
            }
        } catch (Exception e) {
            log.warn("获取大盘指数失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 构建Prompt
     */
    private String buildPrompt(String code, Map<String, Object> realtime,
                               List<Map<String, Object>> klineList,
                               Map<String, Object> signalData,
                               List<Map<String, Object>> indices,
                               String customPrompt) {
        StringBuilder sb = new StringBuilder();

        // 大盘指数
        sb.append("【大盘指数】\n");
        for (Map<String, Object> idx : indices) {
            sb.append("- ").append(idx.getOrDefault("name", "")).append(": ")
                    .append(idx.getOrDefault("current", "")).append("  (")
                    .append(idx.getOrDefault("changePercent", "")).append("%)\n");
        }

        // 个股实时行情
        sb.append("\n【个股实时行情】\n");
        sb.append("- 代码: ").append(code).append("\n");
        sb.append("- 名称: ").append(realtime.getOrDefault("name", "")).append("\n");
        sb.append("- 当前价: ").append(realtime.getOrDefault("current", "")).append("\n");
        sb.append("- 开盘: ").append(realtime.getOrDefault("open", "")).append(" / 最高: ")
                .append(realtime.getOrDefault("high", "")).append(" / 最低: ")
                .append(realtime.getOrDefault("low", "")).append(" / 昨收: ")
                .append(realtime.getOrDefault("prevClose", "")).append("\n");
        sb.append("- 涨跌幅: ").append(realtime.getOrDefault("changePercent", "")).append("%\n");
        sb.append("- 成交量: ").append(realtime.getOrDefault("volume", "")).append(" / 成交额: ")
                .append(realtime.getOrDefault("amount", "")).append("\n");

        // K线数据（最近10条）
        sb.append("\n【近60日K线数据（最近10条）】\n");
        sb.append("日期|开盘|最高|最低|收盘|涨跌幅\n");
        int start = Math.max(0, klineList.size() - 10);
        for (int i = start; i < klineList.size(); i++) {
            Map<String, Object> k = klineList.get(i);
            sb.append(k.getOrDefault("date", "")).append("|")
                    .append(k.getOrDefault("open", "")).append("|")
                    .append(k.getOrDefault("high", "")).append("|")
                    .append(k.getOrDefault("low", "")).append("|")
                    .append(k.getOrDefault("close", "")).append("|")
                    .append(k.getOrDefault("changePercent", "")).append("%\n");
        }

        // 技术指标
        sb.append("\n【技术指标信号】\n");
        sb.append("综合信号: ").append(signalData.getOrDefault("signal", "")).append("\n");
        sb.append("信号强度: ").append(signalData.getOrDefault("strength", "")).append("\n");
        sb.append("信号描述: ").append(signalData.getOrDefault("description", "")).append("\n\n");

        @SuppressWarnings("unchecked")
        Map<String, String> indicatorSignals = (Map<String, String>) signalData.getOrDefault("indicatorSignals", Collections.emptyMap());
        for (Map.Entry<String, String> entry : indicatorSignals.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        // 自定义Prompt
        if (customPrompt != null && !customPrompt.isEmpty()) {
            sb.append("\n【用户附加要求】\n").append(customPrompt).append("\n");
        }

        // ====== 核心：分析要求 ======
        sb.append("\n========================================\n");
        sb.append("请严格按以下要求进行分析：\n\n");
        sb.append("⚠️ 重要：你需要付出极大的分析力度，输出必须详细、深入、有理有据。\n");
        sb.append("每个分析部分不得少于500字。\n\n");

        sb.append("【第一部分：技术面深度分析（占综合评分50%）】\n");
        sb.append("请从以下多个维度进行深度剖析：\n");
        sb.append("1. 趋势分析：从K线形态（头肩顶/底、双顶/底、旗形等）、均线排列（多头/空头排列、金叉/死叉）、\n");
        sb.append("   MACD（DIF/DEA位置、柱体变化趋势）综合判断当前趋势\n");
        sb.append("2. 支撑压力位：通过前高前低、均线支撑、布林带上下轨精确计算关键价位\n");
        sb.append("3. 量价关系：成交量变化与价格走势的配合关系（放量突破/缩量回调/量价背离等）\n");
        sb.append("4. 多周期对比：短期（5日）、中期（20日）、长期（60日）趋势一致性分析\n");
        sb.append("5. 技术评分：综合以上分析给出0-100的技术面评分\n\n");

        sb.append("【第二部分：大V舆情深度分析（占综合评分50%）】\n");
        sb.append("请你模拟市场上知名的证券分析师、财经大V对该股票的深入分析。\n");
        sb.append("请输出至少8位不同风格、不同立场的大V观点，包含：\n");
        sb.append("1. 大V名称（使用真实存在的知名财经人物/机构名称，如但斌、林园、李大霄风格等）\n");
        sb.append("2. 观点类型（看多/看空/中性）\n");
        sb.append("3. 观点摘要（30字以内，一针见血）\n");
        sb.append("4. 详细论述（100-150字，呈现该大V的完整推理逻辑和论据）\n");
        sb.append("5. 影响力（1-10分）\n\n");
        sb.append("然后综合所有大V观点，给出多数意见共识：\n");
        sb.append("- 整体倾向（看多/看空/中性）\n");
        sb.append("- 观点总结（不少于100字）\n");
        sb.append("- 看多人数/看空人数/中性人数\n\n");

        sb.append("=========================\n\n");

        sb.append("请严格按照以下格式输出（不要遗漏任何字段）：\n\n");

        sb.append("===TECH_SCORE: 0-100\n");
        sb.append("===SENTIMENT_SCORE: 0-100\n");
        sb.append("===SIGNAL: BUY/SELL/HOLD\n");
        sb.append("===SCORE: 0-100（综合评分 = TECH_SCORE*0.5 + SENTIMENT_SCORE*0.5）\n");
        sb.append("===TARGET_PRICE: xxx-xxx\n\n");

        sb.append("===TECH_ANALYSIS:\n");
        sb.append("（技术面详细分析，不少于300字。请用Markdown格式，包含趋势判断、支撑压力、量能分析等节）\n\n");

        sb.append("===SENTIMENT_ANALYSIS:\n");
        sb.append("（大V舆情详细分析，不少于300字。请列出每位大V的观点，然后给出综合判断）\n\n");

        sb.append("===DAV_LIST:\n");
        sb.append("大V名称 | 观点类型(bullish/bearish/neutral) | 观点摘要 | 详细论述 | 影响力 | 发布时间\n");
        sb.append("（每行一个，用|分隔。发布时间格式：MM-DD HH:mm，最近一周内）\n\n");

        sb.append("===DAV_CONSENSUS:\n");
        sb.append("多数意见: bullish/bearish/neutral\n");
        sb.append("观点总结: xxx\n");
        sb.append("看多人数: X\n");
        sb.append("看空人数: X\n");
        sb.append("中性人数: X\n\n");

        sb.append("===COMPREHENSIVE_ADVICE:\n");
        sb.append("（综合技术面和大V舆情，给出最终操作建议，不少于800字。包含：\n");
        sb.append("- 多空博弈综合分析\n");
        sb.append("- 短期（1-5日）/ 中期（1-4周）/ 长期（1-6月）分时段展望\n");
        sb.append("- 具体操作策略（精确入场点、止损位、止盈位）\n");
        sb.append("- 关键催化剂/风险事件\n");
        sb.append("- 仓位管理建议\n");
        sb.append("- 风险提示）\n");

        return sb.toString();
    }

    /**
     * 调用LLM API（OpenAI兼容格式）
     */
    private String callLLM(AiModelConfig config, String prompt) {
        String url = config.getBaseUrl();
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += "chat/completions";

        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", config.getModelName());
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 4096);

        JSONArray messages = new JSONArray();

        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是一位顶级的A股股票分析师，精通技术分析和市场情绪分析。"
                + "你的工作分为两大部分：\n"
                + "1. 技术面分析（权重50%）：基于K线、均线、MACD、RSI、KDJ、布林带等指标，判断趋势和买卖点\n"
                + "2. 大V舆情分析（权重50%）：模拟真实市场上的知名财经博主/分析师对该股票的看法\n"
                + "你必须严格按照用户要求的格式输出，不要遗漏任何字段。"
                + "输出必须包含TECH_SCORE、SENTIMENT_SCORE、SIGNAL、SCORE、TARGET_PRICE、TECH_ANALYSIS、SENTIMENT_ANALYSIS、DAV_LIST、DAV_CONSENSUS、COMPREHENSIVE_ADVICE。");
        messages.add(systemMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);

        requestBody.put("messages", messages);

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody.toJSONString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException("LLM API调用失败, HTTP状态码: " + response.getStatusCodeValue());
            }
        } catch (Exception e) {
            log.error("调用LLM API失败: {}", e.getMessage());
            throw new RuntimeException("调用AI模型失败: " + e.getMessage());
        }
    }

    /**
     * 解析LLM响应
     */
    private AiAnalysisResponse parseLLMResponse(String llmResponse, String stockCode, String modelName) {
        AiAnalysisResponse response = new AiAnalysisResponse();
        response.setStockCode(stockCode);
        response.setModelUsed(modelName);

        try {
            JSONObject json = JSONObject.parseObject(llmResponse);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("LLM返回结果为空");
            }

            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice.getJSONObject("message");
            String content = message.getString("content");

            if (content == null || content.isEmpty()) {
                throw new RuntimeException("LLM返回内容为空");
            }

            // 解析技术面评分
            String techScore = extractField(content, "TECH_SCORE");
            try {
                response.setTechScore(new BigDecimal(techScore != null ? techScore.trim() : "50"));
            } catch (Exception e) {
                response.setTechScore(new BigDecimal("50"));
            }

            // 解析舆情评分
            String sentimentScore = extractField(content, "SENTIMENT_SCORE");
            try {
                response.setSentimentScore(new BigDecimal(sentimentScore != null ? sentimentScore.trim() : "50"));
            } catch (Exception e) {
                response.setSentimentScore(new BigDecimal("50"));
            }

            // 解析综合信号
            String signal = extractField(content, "SIGNAL");
            response.setSignal(signal != null ? signal.trim() : "HOLD");

            // 解析综合评分
            String score = extractField(content, "SCORE");
            try {
                response.setScore(new BigDecimal(score != null ? score.trim() : "50"));
            } catch (Exception e) {
                response.setScore(new BigDecimal("50"));
            }

            // 解析目标价
            String targetPrice = extractField(content, "TARGET_PRICE");
            response.setTargetPrice(targetPrice != null ? targetPrice.trim() : "");

            // 解析技术面分析
            String techAnalysis = extractSection(content, "TECH_ANALYSIS", "SENTIMENT_ANALYSIS");
            // 解析舆情分析
            String sentimentAnalysis = extractSection(content, "SENTIMENT_ANALYSIS", "DAV_LIST");
            // 解析大V列表
            List<AiAnalysisResponse.DaVOpinion> davList = parseDaVList(extractSection(content, "DAV_LIST", "DAV_CONSENSUS"));
            response.setDaVOpinions(davList);
            // 解析大V共识
            String consensusSection = extractSection(content, "DAV_CONSENSUS", "COMPREHENSIVE_ADVICE");
            response.setDaVMajority(parseDaVConsensus(consensusSection));
            // 解析综合建议
            String comprehensiveAdvice = extractSection(content, "COMPREHENSIVE_ADVICE", null);

            // 组装最终分析内容
            StringBuilder finalAnalysis = new StringBuilder();
            if (techAnalysis != null && !techAnalysis.isEmpty()) {
                finalAnalysis.append("## 📊 技术面分析\n\n").append(techAnalysis.trim()).append("\n\n");
            }
            if (sentimentAnalysis != null && !sentimentAnalysis.isEmpty()) {
                finalAnalysis.append("## 🗣️ 大V舆情分析\n\n").append(sentimentAnalysis.trim()).append("\n\n");
            }
            if (comprehensiveAdvice != null && !comprehensiveAdvice.isEmpty()) {
                finalAnalysis.append("## 🎯 综合操作建议\n\n").append(comprehensiveAdvice.trim());
            }

            response.setAnalysis(finalAnalysis.length() > 0 ? finalAnalysis.toString() : content);

        } catch (Exception e) {
            log.error("解析LLM响应失败: {}", e.getMessage());
            response.setSignal("HOLD");
            response.setScore(new BigDecimal("50"));
            response.setTechScore(new BigDecimal("50"));
            response.setSentimentScore(new BigDecimal("50"));
            response.setTargetPrice("");
            response.setAnalysis("AI分析结果解析失败: " + e.getMessage());
        }

        // 获取股票名称
        String stockName = extractStockName(stockCode);
        response.setStockName(stockName != null ? stockName : stockCode);

        return response;
    }

    /**
     * 提取两个标记之间的内容
     */
    private String extractSection(String content, String startMarker, String endMarker) {
        int startIdx = content.indexOf("===" + startMarker);
        if (startIdx < 0) {
            startIdx = content.indexOf(startMarker + ":");
            if (startIdx < 0) return "";
        }
        // 跳过标记行
        startIdx = content.indexOf("\n", startIdx);
        if (startIdx < 0) return "";

        if (endMarker == null) {
            return content.substring(startIdx).trim();
        }

        int endIdx = content.indexOf("===" + endMarker, startIdx);
        if (endIdx < 0) endIdx = content.indexOf(endMarker + ":", startIdx);
        if (endIdx < 0) return content.substring(startIdx).trim();

        return content.substring(startIdx, endIdx).trim();
    }

    /**
     * 解析大V列表
     */
    private List<AiAnalysisResponse.DaVOpinion> parseDaVList(String section) {
        List<AiAnalysisResponse.DaVOpinion> list = new ArrayList<>();
        if (section == null || section.isEmpty()) return list;

        String[] lines = section.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("大V名称") || line.startsWith("===")) continue;

            String[] parts = line.split("\\|");
            if (parts.length < 3) continue;

            try {
                AiAnalysisResponse.DaVOpinion op = new AiAnalysisResponse.DaVOpinion();
                op.setName(parts[0].trim());
                op.setType(parts[1].trim().toLowerCase());
                op.setView(parts.length > 2 ? parts[2].trim() : "");
                op.setDetail(parts.length > 3 ? parts[3].trim() : "");
                if (parts.length > 4) {
                    try {
                        op.setInfluence(Integer.parseInt(parts[4].trim()));
                    } catch (Exception e) {
                        op.setInfluence(5);
                    }
                } else {
                    op.setInfluence(5);
                }
                op.setPublishTime(parts.length > 5 ? parts[5].trim() : "");
                list.add(op);
            } catch (Exception e) {
                log.warn("解析大V行失败: {}", line);
            }
        }
        return list;
    }

    /**
     * 解析大V共识
     */
    private AiAnalysisResponse.DaVMajorityConsensus parseDaVConsensus(String section) {
        AiAnalysisResponse.DaVMajorityConsensus consensus = new AiAnalysisResponse.DaVMajorityConsensus();
        if (section == null || section.isEmpty()) return consensus;

        for (String line : section.split("\n")) {
            line = line.trim();
            if (line.startsWith("多数意见") || line.startsWith("多数意")) {
                int idx = line.indexOf(":");
                if (idx > 0) consensus.setConsensus(line.substring(idx + 1).trim());
                else if (line.contains("bullish")) consensus.setConsensus("bullish");
                else if (line.contains("bearish")) consensus.setConsensus("bearish");
                else consensus.setConsensus("neutral");
            } else if (line.startsWith("观点总结") || line.startsWith("观点")) {
                int idx = line.indexOf(":");
                if (idx > 0) consensus.setSummary(line.substring(idx + 1).trim());
            } else if (line.contains("看多")) {
                try {
                    consensus.setBullishCount(extractNumber(line));
                } catch (Exception e) { /* ignore */ }
            } else if (line.contains("看空")) {
                try {
                    consensus.setBearishCount(extractNumber(line));
                } catch (Exception e) { /* ignore */ }
            } else if (line.contains("中性")) {
                try {
                    consensus.setNeutralCount(extractNumber(line));
                } catch (Exception e) { /* ignore */ }
            }
        }
        return consensus;
    }

    private int extractNumber(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(text);
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }

    /**
     * 提取LLM响应中的字段值
     */
    private String extractField(String content, String fieldName) {
        // 尝试多种格式: FIELD: value 或 FIELD:value 或 **FIELD:** value
        String[] patterns = {
                fieldName + ":\\s*(.*?)(?:\\n|$)",
                "\\*\\*" + fieldName + "\\*\\*:?\\s*(.*?)(?:\\n|$)",
                "(?i)" + fieldName + "[:：]\\s*(.*?)(?:\\n|$)"
        };

        for (String pattern : patterns) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    pattern, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(content);
            if (matcher.find()) {
                String value = matcher.group(1).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * 提取分析内容（SIGNAL/SCORE/TARGET_PRICE之后的部分）
     */
    private String extractAnalysis(String content) {
        String[] markers = {"ANALYSIS:", "ANALYSIS：", "分析："};
        for (String marker : markers) {
            int idx = content.indexOf(marker);
            if (idx >= 0) {
                return content.substring(idx + marker.length()).trim();
            }
        }
        // 如果找不到标记，返回全部内容
        return content;
    }

    /**
     * 获取股票名称
     */
    @SuppressWarnings("unchecked")
    private String extractStockName(String code) {
        String prefix = code.startsWith("6") || code.startsWith("5") ? "sh" : "sz";
        String url = STOCK_SERVICE_URL + "/api/stock/sina/realtime?codes=" + prefix + code;
        try {
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (result != null && (Integer) result.get("code") == 200) {
                List<Map<String, Object>> dataList = (List<Map<String, Object>>) result.get("data");
                if (dataList != null && !dataList.isEmpty()) {
                    return (String) dataList.get(0).get("name");
                }
            }
        } catch (Exception e) {
            log.warn("获取股票名称失败: {}", e.getMessage());
        }
        return code;
    }

    /**
     * 保存分析结果
     */
    private void saveResult(Long configId, String stockCode, AiAnalysisResponse response) {
        AiAnalysisResult result = new AiAnalysisResult();
        result.setConfigId(configId);
        result.setStockCode(stockCode);
        result.setStockName(response.getStockName());
        result.setSignalType(response.getSignal());
        result.setScore(response.getScore());
        result.setTargetPrice(response.getTargetPrice());
        result.setAnalysis(response.getAnalysis());
        aiAnalysisResultMapper.insert(result);
    }
}
