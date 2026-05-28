package com.stock.ai.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.stock.ai.dto.SectorAnalysisResult;
import com.stock.ai.entity.AiModelConfig;
import com.stock.ai.service.AiModelConfigService;
import com.stock.ai.service.SectorAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class SectorAnalysisServiceImpl implements SectorAnalysisService {

    private static final String STOCK_SERVICE_URL = "http://localhost:8082";

    /** 最后一次分析结果缓存 */
    private SectorAnalysisResult lastResult;

    private final RestTemplate restTemplate;
    private final AiModelConfigService aiModelConfigService;

    public SectorAnalysisServiceImpl(RestTemplate restTemplate,
                                     AiModelConfigService aiModelConfigService) {
        this.restTemplate = restTemplate;
        this.aiModelConfigService = aiModelConfigService;
    }

    @Override
    public SectorAnalysisResult analyzeSectors() {
        // 1. 获取所有板块
        List<Map<String, Object>> sectorList = fetchAllSectors();
        if (sectorList == null || sectorList.isEmpty()) {
            log.warn("未获取到板块数据");
            return null;
        }
        log.info("获取到 {} 个板块数据", sectorList.size());

        // 2. 获取每个板块的成分股
        List<Map<String, Object>> enrichedSectors = new ArrayList<>();
        for (Map<String, Object> sector : sectorList) {
            String sectorCode = (String) sector.get("sectorCode");
            List<Map<String, Object>> stocks = fetchSectorStocks(sectorCode);
            Map<String, Object> enriched = new HashMap<>(sector);
            enriched.put("stocks", stocks);
            enrichedSectors.add(enriched);
        }

        // 3. 构建Prompt并调用AI
        String prompt = buildSectorPrompt(enrichedSectors);
        String aiResponse = callAiForAnalysis(prompt);
        log.info("AI板块分析完成");

        // 4. 解析AI响应
        SectorAnalysisResult result = parseAiResponse(aiResponse, enrichedSectors);
        result.setAnalyzeTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 5. 缓存结果
        this.lastResult = result;

        return result;
    }

    @Override
    public SectorAnalysisResult getLatestReport() {
        return this.lastResult;
    }

    /**
     * 获取所有板块
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchAllSectors() {
        String url = STOCK_SERVICE_URL + "/api/stock/sectors";
        try {
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (result != null && (Integer) result.get("code") == 200) {
                Object data = result.get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
            }
        } catch (Exception e) {
            log.error("获取板块列表失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 获取板块成分股
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchSectorStocks(String sectorCode) {
        String url = STOCK_SERVICE_URL + "/api/stock/sectors/" + sectorCode + "/stocks";
        try {
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (result != null && (Integer) result.get("code") == 200) {
                Object data = result.get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
            }
        } catch (Exception e) {
            log.warn("获取板块{}成分股失败: {}", sectorCode, e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 构建板块分析Prompt
     */
    private String buildSectorPrompt(List<Map<String, Object>> sectors) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是中国A股市场的板块数据，请分析并给出投资建议。\n\n");
        sb.append("共有").append(sectors.size()).append("个板块。\n\n");

        for (Map<String, Object> sector : sectors) {
            sb.append("=== 板块: ").append(sector.getOrDefault("sectorName", "")).append(" (").append(sector.getOrDefault("sectorCode", "")).append(") ===\n");
            sb.append("涨跌幅: ").append(sector.getOrDefault("changePercent", "N/A")).append("%\n");
            sb.append("成分股数量: ").append(sector.getOrDefault("stockCount", 0)).append("\n");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stocks = (List<Map<String, Object>>) sector.getOrDefault("stocks", Collections.emptyList());
            if (!stocks.isEmpty()) {
                sb.append("成分股列表:\n");
                for (Map<String, Object> stock : stocks) {
                    sb.append("  - ").append(stock.getOrDefault("code", "")).append(" ")
                            .append(stock.getOrDefault("name", "")).append(" ")
                            .append("涨跌幅: ").append(stock.getOrDefault("changePercent", "N/A")).append("%")
                            .append(" 当前价: ").append(stock.getOrDefault("currentPrice", "N/A"))
                            .append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("====== 分析要求 ======\n");
        sb.append("请从以下维度进行分析，并严格按照指定格式输出：\n\n");
        sb.append("1. 市场整体板块热度分析（哪些板块表现强势，哪些弱势）\n");
        sb.append("2. 选出TOP 5最有投资价值的板块（综合考虑涨跌幅、成分股表现、行业前景）\n");
        sb.append("3. 对于每个TOP板块，选出该板块内最有潜力的TOP 5股票\n\n");

        sb.append("请严格按照以下JSON格式输出（不要包含任何其他内容，只输出纯JSON）：\n\n");
        sb.append("{\n");
        sb.append("  \"marketSummary\": \"市场整体板块热度分析，不少于100字\",\n");
        sb.append("  \"topSectors\": [\n");
        sb.append("    {\n");
        sb.append("      \"sectorName\": \"板块名称\",\n");
        sb.append("      \"sectorCode\": \"板块代码\",\n");
        sb.append("      \"changePercent\": 涨跌幅数字,\n");
        sb.append("      \"aiReason\": \"推荐理由，不少于50字\",\n");
        sb.append("      \"leaderStocks\": [\n");
        sb.append("        {\n");
        sb.append("          \"code\": \"股票代码\",\n");
        sb.append("          \"name\": \"股票名称\",\n");
        sb.append("          \"changePercent\": 涨跌幅数字,\n");
        sb.append("          \"aiTrend\": \"看涨/看空/中性\",\n");
        sb.append("          \"aiReason\": \"判断理由\"\n");
        sb.append("        }\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 调用AI模型进行分析
     */
    private String callAiForAnalysis(String prompt) {
        // 获取第一个可用的模型配置
        List<AiModelConfig> configs = aiModelConfigService.listByUserId(1L);
        if (configs == null || configs.isEmpty()) {
            log.warn("未配置AI模型，使用模拟分析");
            return generateMockResponse();
        }

        AiModelConfig config = configs.get(0);
        String url = config.getBaseUrl();
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += "chat/completions";

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", config.getModelName());
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 4096);

        JSONArray messages = new JSONArray();

        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是一位顶级的A股市场板块分析师，精通板块轮动分析和行业趋势判断。"
                + "请基于提供的板块数据和成分股表现，给出专业的投资分析建议。"
                + "必须严格按照要求的JSON格式输出，不要包含任何其他内容。");
        messages.add(systemMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);

        requestBody.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody.toJSONString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject json = JSONObject.parseObject(response.getBody());
                JSONArray choices = json.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    return choices.getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                }
            }
        } catch (Exception e) {
            log.error("调用AI模型失败: {}", e.getMessage());
        }

        log.warn("AI调用失败，使用模拟分析结果");
        return generateMockResponse();
    }

    /**
     * 生成模拟分析响应（当AI不可用时）
     */
    private String generateMockResponse() {
        return "{\n" +
                "  \"marketSummary\": \"当前A股市场板块轮动明显，科技成长板块表现活跃，半导体、人工智能、算力等板块受政策利好和产业趋势推动持续走强。新能源产业链出现分化，储能板块表现优于光伏。传统周期性板块如有色金属受大宗商品价格支撑表现稳健。消费板块整体偏弱，白酒板块有待企稳。\",\n" +
                "  \"topSectors\": [\n" +
                "    {\"sectorName\": \"半导体\", \"sectorCode\": \"BK0900001\", \"changePercent\": 3.5, \"aiReason\": \"国产替代进程加速，AI芯片需求爆发，政策持续扶持半导体产业链。行业景气度回升，多家龙头公司业绩超预期，估值具备吸引力。\", \"leaderStocks\": [\n" +
                "      {\"code\": \"002371\", \"name\": \"北方华创\", \"changePercent\": 5.2, \"aiTrend\": \"看涨\", \"aiReason\": \"国产半导体设备龙头，受益于晶圆厂扩产和设备国产化需求\"},\n" +
                "      {\"code\": \"688981\", \"name\": \"中芯国际\", \"changePercent\": 3.8, \"aiTrend\": \"看涨\", \"aiReason\": \"国内晶圆代工龙头，先进制程突破带来成长空间\"},\n" +
                "      {\"code\": \"603501\", \"name\": \"韦尔股份\", \"changePercent\": 4.1, \"aiTrend\": \"看涨\", \"aiReason\": \"CIS图像传感器龙头，车载和安防业务快速增长\"},\n" +
                "      {\"code\": \"300661\", \"name\": \"圣邦股份\", \"changePercent\": 3.2, \"aiTrend\": \"看涨\", \"aiReason\": \"模拟芯片龙头，产品线持续扩展\"},\n" +
                "      {\"code\": \"688012\", \"name\": \"中微公司\", \"changePercent\": 2.9, \"aiTrend\": \"看涨\", \"aiReason\": \"刻蚀设备龙头，3D NAND和逻辑芯片扩产受益\"}\n" +
                "    ]},\n" +
                "    {\"sectorName\": \"人工智能\", \"sectorCode\": \"BK0900003\", \"changePercent\": 2.8, \"aiReason\": \"AI大模型应用加速落地，算力基础设施需求旺盛。政策支持AI产业发展，行业进入快速发展期。\", \"leaderStocks\": [\n" +
                "      {\"code\": \"300308\", \"name\": \"中际旭创\", \"changePercent\": 4.5, \"aiTrend\": \"看涨\", \"aiReason\": \"光模块龙头，AI算力需求带动800G光模块放量\"},\n" +
                "      {\"code\": \"688111\", \"name\": \"金山办公\", \"changePercent\": 3.6, \"aiTrend\": \"看涨\", \"aiReason\": \"AI+办公软件龙头，WPS AI功能提升ARPU值\"},\n" +
                "      {\"code\": \"603019\", \"name\": \"中科曙光\", \"changePercent\": 3.2, \"aiTrend\": \"看涨\", \"aiReason\": \"国产算力基础设施核心供应商\"},\n" +
                "      {\"code\": \"002230\", \"name\": \"科大讯飞\", \"changePercent\": 2.5, \"aiTrend\": \"看涨\", \"aiReason\": \"AI语音龙头，星火大模型持续迭代\"},\n" +
                "      {\"code\": \"300502\", \"name\": \"新易盛\", \"changePercent\": 4.0, \"aiTrend\": \"看涨\", \"aiReason\": \"高速光模块领先企业，海外市场拓展顺利\"}\n" +
                "    ]},\n" +
                "    {\"sectorName\": \"算力\", \"sectorCode\": \"BK0900017\", \"changePercent\": 3.1, \"aiReason\": \"AI大模型训练和推理需求爆发，算力基础设施成为稀缺资源。国产算力芯片加速替代，产业链上下游受益。\", \"leaderStocks\": [\n" +
                "      {\"code\": \"300308\", \"name\": \"中际旭创\", \"changePercent\": 4.5, \"aiTrend\": \"看涨\", \"aiReason\": \"800G光模块龙头，AI算力直接受益\"},\n" +
                "      {\"code\": \"688041\", \"name\": \"海光信息\", \"changePercent\": 5.0, \"aiTrend\": \"看涨\", \"aiReason\": \"国产CPU/GPU龙头，信创+AI双轮驱动\"},\n" +
                "      {\"code\": \"603019\", \"name\": \"中科曙光\", \"changePercent\": 3.2, \"aiTrend\": \"看涨\", \"aiReason\": \"国产算力服务器龙头\"},\n" +
                "      {\"code\": \"000977\", \"name\": \"浪潮信息\", \"changePercent\": 2.8, \"aiTrend\": \"看涨\", \"aiReason\": \"AI服务器龙头，市场份额领先\"},\n" +
                "      {\"code\": \"300502\", \"name\": \"新易盛\", \"changePercent\": 4.0, \"aiTrend\": \"看涨\", \"aiReason\": \"光模块核心供应商\"}\n" +
                "    ]},\n" +
                "    {\"sectorName\": \"储能\", \"sectorCode\": \"BK0900014\", \"changePercent\": 2.1, \"aiReason\": \"新型电力系统建设加速，储能需求持续增长。政策端强制配储要求推动行业高景气。\", \"leaderStocks\": [\n" +
                "      {\"code\": \"300750\", \"name\": \"宁德时代\", \"changePercent\": 2.5, \"aiTrend\": \"看涨\", \"aiReason\": \"全球动力电池和储能龙头\"},\n" +
                "      {\"code\": \"002074\", \"name\": \"国轩高科\", \"changePercent\": 3.0, \"aiTrend\": \"看涨\", \"aiReason\": \"磷酸铁锂电池技术领先，储能业务快速增长\"},\n" +
                "      {\"code\": \"300274\", \"name\": \"阳光电源\", \"changePercent\": 2.2, \"aiTrend\": \"看涨\", \"aiReason\": \"逆变器和储能系统集成龙头\"},\n" +
                "      {\"code\": \"002709\", \"name\": \"天赐材料\", \"changePercent\": 1.8, \"aiTrend\": \"中性\", \"aiReason\": \"电解液龙头，受益于储能需求但竞争加剧\"},\n" +
                "      {\"code\": \"688005\", \"name\": \"容百科技\", \"changePercent\": 2.0, \"aiTrend\": \"看涨\", \"aiReason\": \"三元正极材料龙头，高镍化趋势受益\"}\n" +
                "    ]},\n" +
                "    {\"sectorName\": \"信创\", \"sectorCode\": \"BK0900015\", \"changePercent\": 2.5, \"aiReason\": \"国家政策推动信创产业加速发展，党政和行业信创订单持续落地。国产替代空间巨大。\", \"leaderStocks\": [\n" +
                "      {\"code\": \"688111\", \"name\": \"金山办公\", \"changePercent\": 3.6, \"aiTrend\": \"看涨\", \"aiReason\": \"信创办公软件龙头\"},\n" +
                "      {\"code\": \"603019\", \"name\": \"中科曙光\", \"changePercent\": 3.2, \"aiTrend\": \"看涨\", \"aiReason\": \"信创服务器核心供应商\"},\n" +
                "      {\"code\": \"000063\", \"name\": \"中兴通讯\", \"changePercent\": 2.0, \"aiTrend\": \"中性\", \"aiReason\": \"信创通信设备龙头\"},\n" +
                "      {\"code\": \"300033\", \"name\": \"同花顺\", \"changePercent\": 3.0, \"aiTrend\": \"看涨\", \"aiReason\": \"金融信创+AI双概念\"},\n" +
                "      {\"code\": \"002410\", \"name\": \"广联达\", \"changePercent\": 1.5, \"aiTrend\": \"中性\", \"aiReason\": \"建筑信息化龙头，信创受益标的\"}\n" +
                "    ]}\n" +
                "  ]\n" +
                "}";
    }

    /**
     * 解析AI响应
     */
    private SectorAnalysisResult parseAiResponse(String aiResponse, List<Map<String, Object>> sectors) {
        SectorAnalysisResult result = new SectorAnalysisResult();

        try {
            // 尝试从AI响应中提取JSON
            String jsonStr = aiResponse;
            int jsonStart = aiResponse.indexOf("{");
            int jsonEnd = aiResponse.lastIndexOf("}");
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                jsonStr = aiResponse.substring(jsonStart, jsonEnd + 1);
            }

            JSONObject json = JSONObject.parseObject(jsonStr);
            JSONArray topSectorsJson = json.getJSONArray("topSectors");

            if (topSectorsJson != null) {
                List<SectorAnalysisResult.TopSector> topSectors = new ArrayList<>();
                for (int i = 0; i < topSectorsJson.size(); i++) {
                    JSONObject item = topSectorsJson.getJSONObject(i);
                    SectorAnalysisResult.TopSector ts = new SectorAnalysisResult.TopSector();
                    ts.setSectorName(item.getString("sectorName"));
                    ts.setSectorCode(item.getString("sectorCode"));
                    ts.setChangePercent(item.getBigDecimal("changePercent"));
                    ts.setAiReason(item.getString("aiReason"));

                    JSONArray stocksJson = item.getJSONArray("leaderStocks");
                    if (stocksJson != null) {
                        List<SectorAnalysisResult.LeaderStock> stocks = new ArrayList<>();
                        for (int j = 0; j < stocksJson.size(); j++) {
                            JSONObject sj = stocksJson.getJSONObject(j);
                            SectorAnalysisResult.LeaderStock ls = new SectorAnalysisResult.LeaderStock();
                            ls.setCode(sj.getString("code"));
                            ls.setName(sj.getString("name"));
                            ls.setChangePercent(sj.getBigDecimal("changePercent"));
                            ls.setAiTrend(sj.getString("aiTrend"));
                            ls.setAiReason(sj.getString("aiReason"));
                            stocks.add(ls);
                        }
                        ts.setLeaderStocks(stocks);
                    }

                    topSectors.add(ts);
                }
                result.setTopSectors(topSectors);
            }
        } catch (Exception e) {
            log.error("解析AI板块分析响应失败: {}", e.getMessage());
            // 如果解析失败，使用模拟数据
            SectorAnalysisResult mockResult = parseAiResponse(generateMockResponse(), sectors);
            if (mockResult != null) {
                return mockResult;
            }
        }

        return result;
    }
}
