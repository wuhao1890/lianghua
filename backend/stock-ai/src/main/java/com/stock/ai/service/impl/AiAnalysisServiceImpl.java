package com.stock.ai.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stock.ai.dto.AiAnalyzeRequest;
import com.stock.ai.dto.AiAnalysisResponse;
import com.stock.ai.entity.AiAnalysisResult;
import com.stock.ai.entity.AiModelConfig;
import com.stock.ai.mapper.AiAnalysisResultMapper;
import com.stock.ai.service.AiAnalysisService;
import com.stock.ai.service.AiModelConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private static final String STOCK_SERVICE_URL = "http://localhost:8080";
    private static final String ANALYSIS_SERVICE_URL = "http://localhost:8080";

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
        AiModelConfig config = aiModelConfigService.getById(request.getConfigId());
        if (config == null) {
            throw new RuntimeException("模型配置不存在: " + request.getConfigId());
        }

        String code = request.getStockCode();
        Map<String, Object> realtime = fetchRealtime(code);
        List<Map<String, Object>> klineList = fetchKline(code);
        Map<String, Object> signalData = fetchTradeSignal(code);
        List<Map<String, Object>> indices = fetchIndices();
        List<AiAnalysisResponse.NewsItem> newsItems = fetchNews(code, String.valueOf(realtime.getOrDefault("name", "")));

        AiAnalysisResponse quantResponse = buildQuantResponse(code, realtime, klineList, signalData, indices, newsItems, config.getModelName());

        try {
            String prompt = buildPrompt(code, realtime, klineList, signalData, indices, newsItems, request.getCustomPrompt(), quantResponse);
            String llmResponse = callLLM(config, prompt);
            AiAnalysisResponse modelResponse = parseLLMResponse(llmResponse, code, config.getModelName());
            mergeQuantFields(modelResponse, quantResponse);
            modelResponse.setModelAvailable(true);
            saveResult(config.getId(), code, modelResponse);
            return modelResponse;
        } catch (Exception e) {
            log.warn("AI模型调用失败，已切换到本地量化引擎: {}", e.getMessage());
            quantResponse.setModelAvailable(false);
            quantResponse.setFailureReason(shortReason(e.getMessage()));
            quantResponse.setModelUsed("本地量化引擎");
            saveResult(config.getId(), code, quantResponse);
            return quantResponse;
        }
    }

    @Override
    public List<AiAnalysisResponse.NewsItem> getStockNews(String stockCode) {
        String code = stockCode == null ? "" : stockCode.trim();
        if (code.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> realtime = fetchRealtime(code);
        return fetchNews(code, String.valueOf(realtime.getOrDefault("name", "")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchRealtime(String code) {
        String prefix = code.startsWith("6") || code.startsWith("5") ? "sh" : "sz";
        String url = STOCK_SERVICE_URL + "/api/stock/sina/realtime?codes=" + prefix + code;
        try {
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (ok(result)) {
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchKline(String code) {
        String url = STOCK_SERVICE_URL + "/api/stock/kline/" + code + "?period=daily&limit=80";
        try {
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (ok(result) && result.get("data") instanceof List) {
                List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("data");
                Collections.sort(list, new Comparator<Map<String, Object>>() {
                    @Override
                    public int compare(Map<String, Object> a, Map<String, Object> b) {
                        return String.valueOf(a.get("date")).compareTo(String.valueOf(b.get("date")));
                    }
                });
                return list;
            }
        } catch (Exception e) {
            log.warn("获取K线数据失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchTradeSignal(String code) {
        String url = ANALYSIS_SERVICE_URL + "/api/analysis/signal/" + code;
        try {
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (ok(result) && result.get("data") instanceof Map) {
                return (Map<String, Object>) result.get("data");
            }
        } catch (Exception e) {
            log.warn("获取交易信号失败: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchIndices() {
        String url = STOCK_SERVICE_URL + "/api/stock/sina/indices";
        try {
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (ok(result) && result.get("data") instanceof List) {
                return (List<Map<String, Object>>) result.get("data");
            }
        } catch (Exception e) {
            log.warn("获取大盘指数失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<AiAnalysisResponse.NewsItem> fetchNews(String code, String stockName) {
        List<AiAnalysisResponse.NewsItem> list = new ArrayList<>();
        String safeName = stockName == null ? "" : stockName.trim();
        List<String> keywords = new ArrayList<>();
        if (!safeName.isEmpty() && !safeName.equals(code)) keywords.add(safeName + " 股票");
        if (!safeName.isEmpty() && !safeName.equals(code)) keywords.add(code + " " + safeName);
        keywords.add(code + " 股票");
        for (String keyword : keywords) {
            try {
                String url = "https://news.google.com/rss/search?q="
                        + URLEncoder.encode(keyword, StandardCharsets.UTF_8.name())
                        + "&hl=zh-CN&gl=CN&ceid=CN:zh-Hans";
                String body = fetchNewsRss(url);
                if (body == null || !body.contains("<item>")) continue;
                Matcher itemMatcher = Pattern.compile("<item>([\\s\\S]*?)</item>").matcher(body);
                while (itemMatcher.find() && list.size() < 8) {
                    String item = itemMatcher.group(1);
                    String title = xmlValue(item, "title");
                    String link = xmlValue(item, "link");
                    if (title == null || title.trim().isEmpty()) continue;
                    String cleanTitle = cleanXml(title);
                    if (!isRelevantNews(cleanTitle, code, safeName)) continue;
                    AiAnalysisResponse.NewsItem news = new AiAnalysisResponse.NewsItem();
                    news.setTitle(cleanTitle);
                    news.setUrl(cleanXml(link));
                    news.setPublishTime(cleanXml(xmlValue(item, "pubDate")));
                    news.setSource(cleanXml(xmlValue(item, "source")));
                    enrichNewsSentiment(news);
                    list.add(news);
                }
                if (!list.isEmpty()) return list;
            } catch (Exception e) {
                log.warn("获取真实新闻失败[{}]: {}", keyword, e.getMessage());
            }
        }
        return list;
    }

    private String fetchNewsRss(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        } finally {
            conn.disconnect();
        }
        return sb.toString();
    }

    private boolean isRelevantNews(String title, String code, String stockName) {
        if (title == null) return false;
        if (code != null && !code.isEmpty() && title.contains(code)) return true;
        return stockName != null && !stockName.isEmpty() && !stockName.equals(code) && title.contains(stockName);
    }

    private String xmlValue(String xml, String tag) {
        Matcher matcher = Pattern.compile("<" + tag + "(?: [^>]*)?>([\\s\\S]*?)</" + tag + ">").matcher(xml);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String cleanXml(String text) {
        if (text == null) return "";
        return text.replace("<![CDATA[", "")
                .replace("]]>", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .trim();
    }

    private void enrichNewsSentiment(AiAnalysisResponse.NewsItem news) {
        String title = news.getTitle() == null ? "" : news.getTitle();
        int score = 50;
        String sentiment = "neutral";
        String reason = "新闻标题未出现明显方向性词汇，作为中性信息纳入风控。";
        if (containsAny(title, "增长", "突破", "中标", "盈利", "上调", "创新高", "回购", "增持", "扩产", "订单")) {
            score = 72;
            sentiment = "bullish";
            reason = "标题包含偏利好关键词，提升新闻面评分。";
        } else if (containsAny(title, "下滑", "亏损", "减持", "处罚", "调查", "风险", "跌", "下调", "诉讼", "违约")) {
            score = 28;
            sentiment = "bearish";
            reason = "标题包含偏利空或风险关键词，降低新闻面评分。";
        }
        news.setImpactScore(score);
        news.setSentiment(sentiment);
        news.setReason(reason);
    }

    private boolean containsAny(String text, String... words) {
        if (text == null) return false;
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    private AiAnalysisResponse buildQuantResponse(String code,
                                                  Map<String, Object> realtime,
                                                  List<Map<String, Object>> klineList,
                                                  Map<String, Object> signalData,
                                                  List<Map<String, Object>> indices,
                                                  List<AiAnalysisResponse.NewsItem> newsItems,
                                                  String modelName) {
        List<Double> closes = values(klineList, "close");
        List<Double> volumes = values(klineList, "volume");
        double current = firstPositive(number(realtime.get("current")), last(closes), number(realtime.get("currentPrice")));
        double ma5 = ma(closes, 5);
        double ma20 = ma(closes, 20);
        double ma60 = ma(closes, 60);
        double ret5 = returnPct(closes, 5);
        double ret20 = returnPct(closes, 20);
        double volRatio = average(volumes, 5) / Math.max(1D, average(volumes, 20));
        double volatility = stdDevReturn(closes, 20);
        double drawdown = maxDrawdown(closes, 40);
        double marketChange = averageChange(indices);

        int trendScore = clampScore(50 + scoreBy(current, ma20, 12) + scoreBy(ma5, ma20, 10) + scoreBy(ma20, ma60, 8) + (ret20 > 0 ? 8 : -8));
        int momentumScore = clampScore(50 + bounded(ret5 * 5, -18, 18) + bounded(ret20 * 2, -22, 22));
        int volumeScore = clampScore(50 + bounded((volRatio - 1D) * 45D, -20, 20) + (ret5 > 0 && volRatio > 1.05D ? 10 : 0));
        int riskScore = clampScore(82 - bounded(volatility * 9D, 0, 32) - bounded(drawdown * 2.2D, 0, 30));
        int marketScore = clampScore(50 + bounded(marketChange * 12D, -18, 18));
        int signalScore = scoreSignal(signalData.get("signal"));
        int voiceScore = scoreMarketVoice(trendScore, momentumScore, volumeScore, riskScore, marketScore, ret20, volRatio);
        int newsScore = scoreNews(newsItems);
        int sentimentComposite = clampScore(voiceScore * 0.58D + newsScore * 0.42D);
        int technicalComposite = clampScore(trendScore * 0.34D
                + momentumScore * 0.25D
                + volumeScore * 0.16D
                + riskScore * 0.17D
                + signalScore * 0.08D);

        int total = clampScore(technicalComposite * 0.58D + sentimentComposite * 0.42D);

        String signal = total >= 68 ? "BUY" : total <= 42 ? "SELL" : "HOLD";
        int confidence = clampScore(Math.abs(total - 50) * 2 + (Math.abs(ret20) > 6 ? 8 : 0));
        String riskLevel = riskScore >= 68 ? "LOW" : riskScore >= 48 ? "MEDIUM" : "HIGH";
        String trendState = trendState(current, ma5, ma20, ma60, ret20);
        String position = suggestedPosition(signal, total, riskLevel);

        double stopPct = "HIGH".equals(riskLevel) ? 0.06D : "MEDIUM".equals(riskLevel) ? 0.075D : 0.09D;
        double rewardPct = "BUY".equals(signal) ? ("HIGH".equals(riskLevel) ? 0.09D : 0.13D) : 0.06D;
        BigDecimal stopLoss = money(current * (1D - stopPct));
        BigDecimal takeProfit = money(current * (1D + rewardPct));
        String targetRange = money(current * (1D + Math.max(0.02D, rewardPct * 0.45D))) + " - " + takeProfit;

        List<AiAnalysisResponse.FactorScore> factors = new ArrayList<>();
        factors.add(factor("趋势结构", trendScore, "trend", 30, "MA5/MA20/MA60与20日收益共同判断，当前为" + trendState));
        factors.add(factor("动量强度", momentumScore, "momentum", 22, "5日收益" + pct(ret5) + "，20日收益" + pct(ret20)));
        factors.add(factor("量价确认", volumeScore, "volume", 16, "近5日成交量约为20日均量的" + round(volRatio, 2) + "倍"));
        factors.add(factor("风险回撤", riskScore, "risk", 17, "20日波动率约" + pct(volatility) + "，阶段回撤约" + pct(drawdown)));
        factors.add(factor("市场环境", marketScore, "market", 10, "主要指数平均涨跌幅约" + pct(marketChange)));
        factors.add(factor("技术信号", signalScore, "signal", 5, String.valueOf(signalData.getOrDefault("description", "系统指标信号中性"))));

        factors.add(factor("大V舆情", voiceScore, "sentiment", 22, "基于主流交易风格画像、趋势热度、量价关注度与风险分歧合成"));
        factors.add(factor("新闻事件", newsScore, "news", 20, newsItems == null || newsItems.isEmpty()
                ? "未获取到可验证新闻，新闻因子按中性处理"
                : "基于真实新闻标题、来源与方向性关键词合成，新闻条数" + newsItems.size()));
        List<AiAnalysisResponse.DaVOpinion> voiceOpinions = buildDaVOpinions(trendScore, momentumScore, volumeScore, riskScore, marketScore, voiceScore, signal, trendState);
        AiAnalysisResponse.DaVMajorityConsensus consensus = buildDaVConsensus(voiceOpinions, sentimentComposite, signal, riskLevel);
        List<AiAnalysisResponse.CandidateStrategy> strategies = buildCandidateStrategies(signal, total, technicalComposite, sentimentComposite, riskScore, trendState, position, stopLoss, takeProfit, newsItems);
        AiAnalysisResponse.CandidateStrategy selectedStrategy = chooseStrategy(strategies);
        AiAnalysisResponse.StrategyEvolution evolution = buildEvolution(code, selectedStrategy, total);

        AiAnalysisResponse.QuantDecision decision = new AiAnalysisResponse.QuantDecision();
        decision.setSignal(signal);
        decision.setConfidence(confidence);
        decision.setRiskLevel(riskLevel);
        decision.setTrendState(trendState);
        decision.setSuggestedPosition(position);
        decision.setStopLoss(stopLoss);
        decision.setTakeProfit(takeProfit);
        decision.setTargetRange(targetRange);
        decision.setSummary(summary(signal, total, trendState, riskLevel, position));

        AiAnalysisResponse response = new AiAnalysisResponse();
        response.setStockCode(code);
        response.setStockName(String.valueOf(realtime.getOrDefault("name", code)));
        response.setSignal(signal);
        response.setScore(BigDecimal.valueOf(total));
        response.setTechScore(BigDecimal.valueOf(technicalComposite));
        response.setSentimentScore(BigDecimal.valueOf(sentimentComposite));
        response.setTargetPrice(targetRange);
        response.setAnalysis(analysisText(decision, factors, current, ma5, ma20, ma60));
        response.setModelUsed(modelName);
        response.setQuantDecision(decision);
        response.setFactors(factors);
        response.setScenarios(scenarios(current, support(closes), resistance(closes), signal));
        response.setRisks(risks(riskLevel, drawdown, volatility, marketChange));
        response.setActions(actions(signal, position, stopLoss, takeProfit));
        response.setModelAvailable(true);
        response.setDaVOpinions(voiceOpinions);

        response.setDaVMajority(consensus);
        response.setNewsItems(newsItems);
        response.setCandidateStrategies(strategies);
        response.setSelectedStrategy(selectedStrategy);
        response.setEvolution(evolution);
        return response;
    }

    private String buildPrompt(String code,
                               Map<String, Object> realtime,
                               List<Map<String, Object>> klineList,
                               Map<String, Object> signalData,
                               List<Map<String, Object>> indices,
                               List<AiAnalysisResponse.NewsItem> newsItems,
                               String customPrompt,
                               AiAnalysisResponse quant) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是量化交易研究员。请基于已有本地量化结论做二次校验，不要编造新闻和名人观点。\n");
        sb.append("股票代码: ").append(code).append("\n");
        sb.append("实时行情: ").append(realtime).append("\n");
        sb.append("技术信号: ").append(signalData).append("\n");
        sb.append("大盘指数: ").append(indices).append("\n");
        sb.append("真实新闻: ").append(newsItems).append("\n");
        sb.append("最近K线: ").append(tail(klineList, 12)).append("\n");
        sb.append("本地量化结论: ").append(quant.getQuantDecision()).append("\n");
        sb.append("候选策略: ").append(quant.getCandidateStrategies()).append("\n");
        if (customPrompt != null && !customPrompt.trim().isEmpty()) {
            sb.append("用户补充要求: ").append(customPrompt).append("\n");
        }
        sb.append("请严格输出以下字段:\n");
        sb.append("===TECH_SCORE: 0-100\n");
        sb.append("===SENTIMENT_SCORE: 0-100\n");
        sb.append("===SIGNAL: BUY/SELL/HOLD\n");
        sb.append("===SCORE: 0-100\n");
        sb.append("===TARGET_PRICE: 价格区间\n");
        sb.append("===COMPREHENSIVE_ADVICE:\n");
        sb.append("用中文给出趋势、仓位、止损、止盈、触发条件和风险提示。\n");
        return sb.toString();
    }

    private String callLLM(AiModelConfig config, String prompt) {
        String url = config.getBaseUrl();
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += "chat/completions";

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", config.getModelName());
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 2048);

        JSONArray messages = new JSONArray();
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是专业量化交易研究员，回答必须保守、可执行、标注风险，禁止虚构外部消息来源。");
        messages.add(systemMsg);
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);
        requestBody.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(requestBody.toJSONString(), headers), String.class);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return response.getBody();
        }
        throw new RuntimeException("LLM HTTP " + response.getStatusCodeValue());
    }

    private AiAnalysisResponse parseLLMResponse(String llmResponse, String stockCode, String modelName) {
        AiAnalysisResponse response = new AiAnalysisResponse();
        response.setStockCode(stockCode);
        response.setStockName(extractStockName(stockCode));
        response.setModelUsed(modelName);
        JSONObject json = JSONObject.parseObject(llmResponse);
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("模型返回为空");
        }
        String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
        response.setTechScore(decimalField(content, "TECH_SCORE", 50));
        response.setSentimentScore(decimalField(content, "SENTIMENT_SCORE", 50));
        response.setSignal(textField(content, "SIGNAL", "HOLD").replaceAll("[^A-Z]", ""));
        response.setScore(decimalField(content, "SCORE", 50));
        response.setTargetPrice(textField(content, "TARGET_PRICE", ""));
        response.setAnalysis(section(content, "COMPREHENSIVE_ADVICE"));
        if (response.getAnalysis() == null || response.getAnalysis().trim().isEmpty()) {
            response.setAnalysis(content);
        }
        return response;
    }

    private void mergeQuantFields(AiAnalysisResponse target, AiAnalysisResponse quant) {
        target.setStockName(nonBlank(target.getStockName(), quant.getStockName()));
        target.setQuantDecision(quant.getQuantDecision());
        target.setFactors(quant.getFactors());
        target.setScenarios(quant.getScenarios());
        target.setRisks(quant.getRisks());
        target.setActions(quant.getActions());
        target.setDaVOpinions(quant.getDaVOpinions());
        target.setDaVMajority(quant.getDaVMajority());
        target.setNewsItems(quant.getNewsItems());
        target.setCandidateStrategies(quant.getCandidateStrategies());
        target.setSelectedStrategy(quant.getSelectedStrategy());
        target.setEvolution(quant.getEvolution());
        if (target.getTargetPrice() == null || target.getTargetPrice().trim().isEmpty()) {
            target.setTargetPrice(quant.getTargetPrice());
        }
        if (target.getSignal() == null || target.getSignal().trim().isEmpty()) {
            target.setSignal(quant.getSignal());
        }
    }

    private int scoreNews(List<AiAnalysisResponse.NewsItem> newsItems) {
        if (newsItems == null || newsItems.isEmpty()) return 50;
        double weighted = 0D;
        double weight = 0D;
        int rank = 0;
        for (AiAnalysisResponse.NewsItem item : newsItems) {
            double w = Math.max(1D, 8D - rank);
            weighted += (item.getImpactScore() == null ? 50 : item.getImpactScore()) * w;
            weight += w;
            rank++;
        }
        return clampScore(weighted / Math.max(1D, weight));
    }

    private List<AiAnalysisResponse.CandidateStrategy> buildCandidateStrategies(String signal,
                                                                                int total,
                                                                                int techScore,
                                                                                int sentimentScore,
                                                                                int riskScore,
                                                                                String trendState,
                                                                                String position,
                                                                                BigDecimal stopLoss,
                                                                                BigDecimal takeProfit,
                                                                                List<AiAnalysisResponse.NewsItem> newsItems) {
        List<AiAnalysisResponse.CandidateStrategy> list = new ArrayList<>();
        int newsScore = scoreNews(newsItems);
        list.add(strategy("趋势跟随策略", "trend-following", signal,
                clampScore(techScore * 0.55D + sentimentScore * 0.25D + riskScore * 0.20D),
                total, riskScore, sentimentScore, position,
                "收盘价站稳MA20且量能不低于20日均量时分批执行。",
                "跌破MA20或综合评分连续两次低于50退出。",
                "硬止损 " + stopLoss + "，盘中跌破后不补仓。",
                "第一止盈 " + takeProfit + "，到位兑现至少三分之一。",
                "5个交易日后比较执行价、止损触发、目标触达和最大回撤。",
                "适合趋势状态为" + trendState + "时作为主策略候选。"));
        list.add(strategy("新闻事件驱动策略", "news-event", newsScore >= 58 ? "BUY" : newsScore <= 42 ? "SELL" : "HOLD",
                clampScore(newsScore * 0.50D + sentimentScore * 0.30D + riskScore * 0.20D),
                newsScore, riskScore, sentimentScore, position,
                "仅在真实新闻与技术方向同向时执行，消息来源不足时不加仓。",
                "新闻热度衰减或出现反向风险新闻时退出。",
                "新闻交易止损不超过计划仓位亏损3%-5%。",
                "事件兑现或价格冲高回落时分批止盈。",
                "复盘新闻发布后1日/3日/5日收益，淘汰失效关键词。",
                "把新闻作为交易依据之一，但不允许单独覆盖风控。"));
        list.add(strategy("防守均值回归策略", "mean-reversion", riskScore < 50 ? "SELL" : "HOLD",
                clampScore(riskScore * 0.45D + (100 - Math.abs(total - 50) * 2) * 0.30D + sentimentScore * 0.25D),
                55, riskScore, sentimentScore, "0%-30%",
                "急跌后不追空，等待缩量企稳或回到支撑区再观察。",
                "反弹不过压力位或舆情继续恶化时退出。",
                "支撑位失守立即停止均值回归假设。",
                "接近MA20或前高压力位时止盈。",
                "复盘是否降低回撤，若未改善则降低该策略权重。",
                "用于高波动或趋势不清时保护资金曲线。"));
        return list;
    }

    private AiAnalysisResponse.CandidateStrategy strategy(String name, String style, String signal, int score,
                                                          int expectedReturnScore, int riskScore, int sentimentFitScore,
                                                          String position, String entryRule, String exitRule,
                                                          String stopLossRule, String takeProfitRule,
                                                          String evaluationRule, String rationale) {
        AiAnalysisResponse.CandidateStrategy s = new AiAnalysisResponse.CandidateStrategy();
        s.setName(name);
        s.setStyle(style);
        s.setSignal(signal);
        s.setScore(score);
        s.setExpectedReturnScore(expectedReturnScore);
        s.setRiskScore(riskScore);
        s.setSentimentFitScore(sentimentFitScore);
        s.setSuggestedPosition(position);
        s.setEntryRule(entryRule);
        s.setExitRule(exitRule);
        s.setStopLossRule(stopLossRule);
        s.setTakeProfitRule(takeProfitRule);
        s.setEvaluationRule(evaluationRule);
        s.setRationale(rationale);
        return s;
    }

    private AiAnalysisResponse.CandidateStrategy chooseStrategy(List<AiAnalysisResponse.CandidateStrategy> strategies) {
        if (strategies == null || strategies.isEmpty()) return null;
        AiAnalysisResponse.CandidateStrategy best = strategies.get(0);
        for (AiAnalysisResponse.CandidateStrategy strategy : strategies) {
            if (strategy.getScore() != null && strategy.getScore() > best.getScore()) {
                best = strategy;
            }
        }
        return best;
    }

    private AiAnalysisResponse.StrategyEvolution buildEvolution(String code,
                                                                AiAnalysisResponse.CandidateStrategy selected,
                                                                int currentScore) {
        AiAnalysisResponse.StrategyEvolution evolution = new AiAnalysisResponse.StrategyEvolution();
        int samples = 0;
        String lastLearning = "暂无足够历史样本，先以当前策略作为第1代基线。";
        String outcome = "本轮生成交易计划后，将以未来真实行情的收益、回撤、止损/止盈触发情况评判成果。";
        try {
            List<AiAnalysisResult> history = aiAnalysisResultMapper.selectList(
                    new LambdaQueryWrapper<AiAnalysisResult>()
                            .eq(AiAnalysisResult::getStockCode, code)
                            .orderByDesc(AiAnalysisResult::getCreateTime)
                            .last("LIMIT 20"));
            samples = history == null ? 0 : history.size();
            if (samples > 0) {
                AiAnalysisResult last = history.get(0);
                lastLearning = "上一轮信号为" + last.getSignalType() + "，评分"
                        + (last.getScore() == null ? "-" : last.getScore())
                        + "；本轮评分" + currentScore + "，系统将优先比较信号变化后的风险收益。";
                outcome = "已纳入" + samples + "条历史研判记录。下一步可接入真实成交记录后计算胜率、盈亏比、最大回撤。";
            }
        } catch (Exception e) {
            log.warn("读取策略迭代历史失败: {}", e.getMessage());
        }
        evolution.setGeneration(samples + 1);
        evolution.setHistorySamples(samples);
        evolution.setStatus("paper-learning");
        evolution.setLastLearning(lastLearning);
        evolution.setNextMutation(selected == null
                ? "等待下一次研判生成策略权重。"
                : "若" + selected.getName() + "在复盘中跑输，将下调其权重并提高新闻/风控因子约束。");
        evolution.setOutcomeJudgement(outcome);
        return evolution;
    }

    private List<AiAnalysisResponse.Scenario> scenarios(double current, double support, double resistance, String signal) {
        List<AiAnalysisResponse.Scenario> list = new ArrayList<>();
        list.add(scenario("强势突破", "BUY".equals(signal) ? 34 : 25, "放量站上 " + money(resistance), "顺势加仓，止损上移到突破位下方"));
        list.add(scenario("区间震荡", "HOLD".equals(signal) ? 48 : 40, "价格在 " + money(support) + " - " + money(resistance) + " 内运行", "维持计划仓位，等待方向选择"));
        list.add(scenario("跌破防线", "SELL".equals(signal) ? 35 : 22, "收盘跌破 " + money(Math.min(current * 0.94D, support)), "减仓或止损，避免回撤扩大"));
        return list;
    }

    private int scoreMarketVoice(int trendScore, int momentumScore, int volumeScore, int riskScore,
                                 int marketScore, double ret20, double volRatio) {
        double disagreementPenalty = riskScore < 45 ? 8D : 0D;
        double heatBonus = volRatio > 1.25D ? 6D : volRatio > 1.05D ? 3D : 0D;
        double trendPenalty = ret20 < -12D ? 9D : ret20 < -6D ? 5D : 0D;
        return clampScore(trendScore * 0.28D
                + momentumScore * 0.22D
                + volumeScore * 0.18D
                + marketScore * 0.18D
                + riskScore * 0.14D
                + heatBonus
                - disagreementPenalty
                - trendPenalty);
    }

    private List<AiAnalysisResponse.DaVOpinion> buildDaVOpinions(int trendScore, int momentumScore, int volumeScore,
                                                                 int riskScore, int marketScore, int voiceScore,
                                                                 String signal, String trendState) {
        List<AiAnalysisResponse.DaVOpinion> list = new ArrayList<>();
        list.add(voice("趋势交易派大V", trendScore >= 62 ? "bullish" : trendScore <= 42 ? "bearish" : "neutral",
                "先看趋势结构", "趋势交易派更关注均线排列和突破有效性。当前趋势状态为" + trendState + "，若不能重新站回关键均线，观点会偏防守。", 8));
        list.add(voice("成长赛道派大V", momentumScore >= 62 ? "bullish" : momentumScore <= 42 ? "bearish" : "neutral",
                "看景气也看回撤", "成长赛道派会重视行业景气和估值弹性，但当20日动量转弱时，会等待缩量企稳或资金重新回流。", 7));
        list.add(voice("资金流派大V", volumeScore >= 62 ? "bullish" : volumeScore <= 42 ? "bearish" : "neutral",
                "量能决定持续性", "资金流派重点观察放量方向和成交额持续性。量价没有共振前，单日反弹更容易被视为修复而非反转。", 8));
        list.add(voice("风控派大V", riskScore >= 62 ? "bullish" : riskScore <= 45 ? "bearish" : "neutral",
                "先控回撤再谈收益", "风控派会把最大回撤、波动率和止损纪律放在第一位。风险评分偏低时，会建议降低仓位而不是摊薄成本。", 9));
        list.add(voice("宏观指数派大V", marketScore >= 58 ? "bullish" : marketScore <= 42 ? "bearish" : "neutral",
                "个股服从市场温度", "宏观指数派认为弱市里个股信号要降权执行；只有指数环境改善，个股技术信号才更容易兑现。", 7));
        if ("BUY".equals(signal) && voiceScore >= 68) {
            list.add(voice("短线博弈派大V", "bullish", "关注右侧加速", "短线派会关注放量突破后的跟随机会，但仍要求设置硬止损并分批执行。", 6));
        } else if ("SELL".equals(signal) && voiceScore <= 42) {
            list.add(voice("短线博弈派大V", "bearish", "反抽不过先离场", "短线派会把弱势反抽当成降低风险的窗口，等待重新放量站稳后再评估参与。", 6));
        } else {
            list.add(voice("短线博弈派大V", "neutral", "等待方向选择", "短线派会等待突破或跌破后的确认信号，避免在震荡区间频繁交易。", 6));
        }
        return list;
    }

    private AiAnalysisResponse.DaVOpinion voice(String name, String type, String view, String detail, int influence) {
        AiAnalysisResponse.DaVOpinion opinion = new AiAnalysisResponse.DaVOpinion();
        opinion.setName(name);
        opinion.setType(type);
        opinion.setView(view);
        opinion.setDetail(detail + " 该观点为主流市场风格画像，不是个人实时原文。");
        opinion.setInfluence(influence);
        opinion.setPublishTime("实时画像");
        return opinion;
    }

    private AiAnalysisResponse.DaVMajorityConsensus buildDaVConsensus(List<AiAnalysisResponse.DaVOpinion> opinions,
                                                                      int voiceScore, String signal, String riskLevel) {
        int bullish = 0;
        int bearish = 0;
        int neutral = 0;
        for (AiAnalysisResponse.DaVOpinion opinion : opinions) {
            if ("bullish".equals(opinion.getType())) bullish++;
            else if ("bearish".equals(opinion.getType())) bearish++;
            else neutral++;
        }
        AiAnalysisResponse.DaVMajorityConsensus consensus = new AiAnalysisResponse.DaVMajorityConsensus();
        consensus.setBullishCount(bullish);
        consensus.setBearishCount(bearish);
        consensus.setNeutralCount(neutral);
        consensus.setConsensus(bullish > bearish && bullish > neutral ? "bullish" : bearish > bullish && bearish > neutral ? "bearish" : "neutral");
        consensus.setSummary("舆情评分" + voiceScore + "/100，主流观点画像与技术信号" + ("HOLD".equals(signal) ? "存在分歧" : "方向基本一致")
                + "，当前风险等级" + riskLevel + "。最终结论按技术面62%与舆情面38%合成。");
        return consensus;
    }

    private List<String> risks(String riskLevel, double drawdown, double volatility, double marketChange) {
        List<String> list = new ArrayList<>();
        list.add("风险等级: " + riskLevel + "，仓位必须随止损纪律同步调整。");
        if (drawdown > 8D) list.add("阶段回撤已超过8%，反弹失败时容易形成二次下探。");
        if (volatility > 3D) list.add("短期波动率偏高，不宜追高满仓。");
        if (marketChange < -0.5D) list.add("大盘环境偏弱，个股信号需要降低一档执行强度。");
        if (list.size() == 1) list.add("当前主要风险来自量能不连续和外部市场波动。");
        return list;
    }

    private List<String> actions(String signal, String position, BigDecimal stopLoss, BigDecimal takeProfit) {
        List<String> list = new ArrayList<>();
        if ("BUY".equals(signal)) {
            list.add("分批建立或增加至" + position + "，不要一次性打满。");
        } else if ("SELL".equals(signal)) {
            list.add("降低到" + position + "，反抽不过压力位继续减仓。");
        } else {
            list.add("维持" + position + "，等待突破或跌破触发条件。");
        }
        list.add("硬止损参考 " + stopLoss + "，触发后优先控制回撤。");
        list.add("第一止盈参考 " + takeProfit + "，到位后至少兑现部分利润。");
        return list;
    }

    private String analysisText(AiAnalysisResponse.QuantDecision decision,
                                List<AiAnalysisResponse.FactorScore> factors,
                                double current,
                                double ma5,
                                double ma20,
                                double ma60) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 量化结论\n\n");
        sb.append(decision.getSummary()).append("\n\n");
        sb.append("## 关键价格\n\n");
        sb.append("- 当前价: ").append(money(current)).append("\n");
        sb.append("- MA5 / MA20 / MA60: ").append(money(ma5)).append(" / ").append(money(ma20)).append(" / ").append(money(ma60)).append("\n");
        sb.append("- 止损: ").append(decision.getStopLoss()).append("\n");
        sb.append("- 目标区间: ").append(decision.getTargetRange()).append("\n\n");
        sb.append("## 因子解释\n\n");
        for (AiAnalysisResponse.FactorScore factor : factors) {
            sb.append("- ").append(factor.getName()).append(": ").append(factor.getScore()).append("/100，").append(factor.getReason()).append("\n");
        }
        return sb.toString();
    }

    private AiAnalysisResponse.FactorScore factor(String name, int score, String direction, int weight, String reason) {
        AiAnalysisResponse.FactorScore factor = new AiAnalysisResponse.FactorScore();
        factor.setName(name);
        factor.setScore(score);
        factor.setDirection(direction);
        factor.setWeight(weight);
        factor.setReason(reason);
        return factor;
    }

    private AiAnalysisResponse.Scenario scenario(String name, int probability, String trigger, String action) {
        AiAnalysisResponse.Scenario scenario = new AiAnalysisResponse.Scenario();
        scenario.setName(name);
        scenario.setProbability(probability);
        scenario.setTrigger(trigger);
        scenario.setAction(action);
        return scenario;
    }

    private boolean ok(Map<String, Object> result) {
        return result != null && number(result.get("code")) == 200D;
    }

    private List<Double> values(List<Map<String, Object>> rows, String key) {
        List<Double> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            double value = number(row.get(key));
            if (value > 0D) values.add(value);
        }
        return values;
    }

    private double number(Object value) {
        if (value == null) return 0D;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value).replace("%", "").replace(",", ""));
        } catch (Exception e) {
            return 0D;
        }
    }

    private double firstPositive(double a, double b, double c) {
        if (a > 0D) return a;
        if (b > 0D) return b;
        if (c > 0D) return c;
        return 0D;
    }

    private double last(List<Double> values) {
        return values.isEmpty() ? 0D : values.get(values.size() - 1);
    }

    private double ma(List<Double> values, int period) {
        if (values.isEmpty()) return 0D;
        int start = Math.max(0, values.size() - period);
        return average(values.subList(start, values.size()));
    }

    private double average(List<Double> values) {
        if (values == null || values.isEmpty()) return 0D;
        double sum = 0D;
        for (double value : values) sum += value;
        return sum / values.size();
    }

    private double average(List<Double> values, int period) {
        if (values == null || values.isEmpty()) return 0D;
        int start = Math.max(0, values.size() - period);
        return average(values.subList(start, values.size()));
    }

    private double returnPct(List<Double> values, int days) {
        if (values.size() <= days) return 0D;
        double now = values.get(values.size() - 1);
        double before = values.get(values.size() - 1 - days);
        return before == 0D ? 0D : (now / before - 1D) * 100D;
    }

    private double stdDevReturn(List<Double> values, int days) {
        if (values.size() < 3) return 0D;
        int start = Math.max(1, values.size() - days);
        List<Double> rets = new ArrayList<>();
        for (int i = start; i < values.size(); i++) {
            double prev = values.get(i - 1);
            if (prev > 0D) rets.add((values.get(i) / prev - 1D) * 100D);
        }
        double avg = average(rets);
        double sum = 0D;
        for (double ret : rets) sum += Math.pow(ret - avg, 2D);
        return rets.isEmpty() ? 0D : Math.sqrt(sum / rets.size());
    }

    private double maxDrawdown(List<Double> values, int days) {
        if (values.isEmpty()) return 0D;
        int start = Math.max(0, values.size() - days);
        double peak = values.get(start);
        double max = 0D;
        for (int i = start; i < values.size(); i++) {
            peak = Math.max(peak, values.get(i));
            if (peak > 0D) max = Math.max(max, (peak - values.get(i)) / peak * 100D);
        }
        return max;
    }

    private double averageChange(List<Map<String, Object>> indices) {
        if (indices == null || indices.isEmpty()) return 0D;
        double sum = 0D;
        int count = 0;
        for (Map<String, Object> index : indices) {
            sum += number(index.get("changePercent"));
            count++;
        }
        return count == 0 ? 0D : sum / count;
    }

    private int scoreBy(double left, double right, int weight) {
        if (left <= 0D || right <= 0D) return 0;
        return left >= right ? weight : -weight;
    }

    private int scoreSignal(Object raw) {
        String signal = String.valueOf(raw == null ? "" : raw).toUpperCase();
        if (signal.contains("BUY")) return 72;
        if (signal.contains("SELL")) return 30;
        return 50;
    }

    private String trendState(double current, double ma5, double ma20, double ma60, double ret20) {
        if (current >= ma5 && ma5 >= ma20 && ma20 >= ma60 && ret20 > 0D) return "多头排列";
        if (current <= ma5 && ma5 <= ma20 && ma20 <= ma60 && ret20 < 0D) return "空头排列";
        if (current >= ma20 && ret20 >= 0D) return "震荡偏强";
        if (current < ma20 && ret20 < 0D) return "震荡偏弱";
        return "区间震荡";
    }

    private String suggestedPosition(String signal, int total, String riskLevel) {
        if ("SELL".equals(signal)) return "0%-20%";
        if ("BUY".equals(signal)) {
            if ("HIGH".equals(riskLevel)) return "20%-35%";
            return total >= 78 ? "50%-70%" : "35%-50%";
        }
        return "20%-40%";
    }

    private String summary(String signal, int total, String trendState, String riskLevel, String position) {
        String action = "BUY".equals(signal) ? "偏多参与" : "SELL".equals(signal) ? "防守减仓" : "等待确认";
        return "综合评分" + total + "/100，趋势状态为" + trendState + "，风险等级" + riskLevel + "，策略建议为" + action + "，参考仓位" + position + "。";
    }

    private double support(List<Double> closes) {
        if (closes.isEmpty()) return 0D;
        int start = Math.max(0, closes.size() - 20);
        double min = closes.get(start);
        for (int i = start; i < closes.size(); i++) min = Math.min(min, closes.get(i));
        return min;
    }

    private double resistance(List<Double> closes) {
        if (closes.isEmpty()) return 0D;
        int start = Math.max(0, closes.size() - 20);
        double max = closes.get(start);
        for (int i = start; i < closes.size(); i++) max = Math.max(max, closes.get(i));
        return max;
    }

    private int clampScore(double value) {
        return Math.max(0, Math.min(100, (int) Math.round(value)));
    }

    private double bounded(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private String pct(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private String textField(String content, String field, String fallback) {
        String value = extractLine(content, field);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private BigDecimal decimalField(String content, String field, int fallback) {
        String value = extractLine(content, field);
        if (value == null) return BigDecimal.valueOf(fallback);
        try {
            String clean = value.replaceAll("[^0-9.\\-]", "");
            return BigDecimal.valueOf(Double.parseDouble(clean)).setScale(0, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.valueOf(fallback);
        }
    }

    private String extractLine(String content, String field) {
        if (content == null) return null;
        String marker = "===" + field;
        int idx = content.indexOf(marker);
        if (idx < 0) idx = content.indexOf(field + ":");
        if (idx < 0) return null;
        int colon = content.indexOf(":", idx);
        int next = content.indexOf("\n", idx);
        if (colon < 0) return null;
        if (next < 0) next = content.length();
        return content.substring(colon + 1, next).trim();
    }

    private String section(String content, String field) {
        if (content == null) return "";
        String marker = "===" + field;
        int idx = content.indexOf(marker);
        if (idx < 0) return "";
        int start = content.indexOf("\n", idx);
        if (start < 0) return "";
        return content.substring(start).trim();
    }

    private String extractStockName(String code) {
        Map<String, Object> realtime = fetchRealtime(code);
        return String.valueOf(realtime.getOrDefault("name", code));
    }

    private String nonBlank(String preferred, String fallback) {
        return preferred == null || preferred.trim().isEmpty() ? fallback : preferred;
    }

    private String shortReason(String message) {
        if (message == null) return "模型服务不可用";
        return message.length() > 160 ? message.substring(0, 160) : message;
    }

    private List<Map<String, Object>> tail(List<Map<String, Object>> rows, int size) {
        if (rows == null || rows.size() <= size) return rows;
        return rows.subList(rows.size() - size, rows.size());
    }

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
