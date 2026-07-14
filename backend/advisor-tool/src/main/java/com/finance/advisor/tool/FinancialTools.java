package com.finance.advisor.tool;

import com.finance.advisor.rag.DocumentIngestionService;
import com.finance.advisor.rag.DocumentMetadataService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 金融领域工具集，通过 @Tool 注解暴露给 ReactAgent 自动调用
 */
@Component
public class FinancialTools {

    private static final Logger log = LoggerFactory.getLogger(FinancialTools.class);

    @Value("${tavily.api-key}")
    private String tavilyApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    private final VectorStore vectorStore;
    private final DocumentIngestionService documentIngestionService;
    private final DocumentMetadataService documentMetadataService;

    public FinancialTools(VectorStore vectorStore,
                          DocumentIngestionService documentIngestionService,
                          DocumentMetadataService documentMetadataService) {
        this.vectorStore = vectorStore;
        this.documentIngestionService = documentIngestionService;
        this.documentMetadataService = documentMetadataService;
    }

    @Tool(name = "tavily_web_search",
          description = "搜索互联网获取最新的财经资讯、新闻和市场动态")
    public String searchFinanceNews(
            @ToolParam(description = "搜索关键词，如行业名称、公司名、经济指标等") String query) {

        log.info("[Tavily] 工具被调用, query={}, apiKey配置={}, apiKey前4位={}",
                query,
                tavilyApiKey != null && !tavilyApiKey.isBlank(),
                tavilyApiKey != null && tavilyApiKey.length() >= 4 ? tavilyApiKey.substring(0, 4) : "null");

        if (tavilyApiKey == null || tavilyApiKey.isBlank()) {
            log.warn("[Tavily] API Key 未配置，跳过搜索");
            return "Tavily API Key 未配置，请在环境变量中设置 TAVILY_API_KEY";
        }

        Map<String, Object> body = new HashMap<>();
        body.put("api_key", tavilyApiKey);
        body.put("query", query);
        body.put("search_depth", "advanced");
        body.put("max_results", 10);
        body.put("include_answer", true);
        // 使用 news topic + days 限制获取最新信息
        body.put("topic", "news");
        body.put("days", 7);

        try {
            log.info("[Tavily] 发送搜索请求到 api.tavily.com, query={}, topic={}, days={}",
                    query, body.get("topic"), body.get("days"));
            Map result = restTemplate.postForObject(
                    "https://api.tavily.com/search", body, Map.class);

            if (result == null || !result.containsKey("results")) {
                log.warn("[Tavily] 搜索无结果, response={}", result);
                return "搜索无结果";
            }

            // 记录完整的响应以便调试
            log.info("[Tavily] 完整响应: {}", result);

            List<Map<String, Object>> results =
                    (List<Map<String, Object>>) result.get("results");

            log.info("[Tavily] 搜索成功, 返回 {} 条结果", results.size());

            // 记录每条结果的标题和URL，便于验证时效性
            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> r = results.get(i);
                log.info("[Tavily] 结果{}: title={}, url={}, published_date={}",
                        i + 1,
                        r.get("title"),
                        r.get("url"),
                        r.get("published_date"));
            }

            // 如果有 answer 字段（Tavily AI 生成的综合摘要），记录下来
            String tavilyAnswer = null;
            if (result.containsKey("answer")) {
                tavilyAnswer = (String) result.get("answer");
                log.info("[Tavily] AI生成的答案: {}", tavilyAnswer);
            }

            // 将搜索结果自动摄入知识库：仅摄入与 query 强相关的结果，避免知识污染
            List<String> keywords = tokenizeQuery(query);
            int ingested = 0, skipped = 0;
            for (Map<String, Object> r : results) {
                String title = (String) r.getOrDefault("title", "");
                String content = (String) r.getOrDefault("content", "");
                String url = (String) r.getOrDefault("url", "");
                String publishedDate = (String) r.getOrDefault("published_date", "");

                // 相关性过滤：标题或内容须命中至少一个 query 关键词
                if (!isRelevant(title, content, keywords)) {
                    skipped++;
                    log.info("[Tavily] 跳过无关结果: title={}, url={}", title, url);
                    continue;
                }

                try {
                    if (!documentMetadataService.existsByUrl(url)) {
                        documentIngestionService.ingestWebContent(title, content, url, publishedDate);
                        ingested++;
                        log.info("[Tavily] 相关结果已入库: title={}, url={}", title, url);
                    } else {
                        log.info("[Tavily] URL已存在，跳过入库: url={}", url);
                    }
                } catch (Exception e) {
                    log.warn("[Tavily] 入库失败: url={}, error={}", url, e.getMessage());
                    // 不影响搜索结果返回
                }
            }
            log.info("[Tavily] 入库汇总: 相关入库={}, 跳过无关={}, 总结果={}", ingested, skipped, results.size());

            // 构建返回给 LLM 的文本：优先使用 Tavily AI answer，再附带各条结果（标注日期）
            StringBuilder sb = new StringBuilder();
            sb.append("【当前时间：2026年7月。以下数据来自互联网实时搜索，请注意甄别时效性。】\n\n");
            if (tavilyAnswer != null && !tavilyAnswer.isBlank()) {
                sb.append("=== Tavily AI 综合摘要 ===\n").append(tavilyAnswer).append("\n\n");
            }
            sb.append("=== 搜索结果详情 ===\n");
            for (Map<String, Object> r : results) {
                String title = (String) r.getOrDefault("title", "");
                String content = (String) r.getOrDefault("content", "");
                String url = (String) r.getOrDefault("url", "");
                String publishedDate = (String) r.getOrDefault("published_date", "");
                sb.append("- ").append(title);
                if (publishedDate != null && !publishedDate.isBlank()) {
                    sb.append(" [").append(publishedDate).append("]");
                }
                sb.append("\n  ").append(content).append("\n  来源: ").append(url).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("[Tavily] 搜索失败", e);
            return "搜索失败: " + e.getMessage();
        }
    }

    @Tool(name = "calculate_compound_interest",
          description = "计算复利投资收益")
    public String calculateCompoundInterest(
            @ToolParam(description = "本金金额（元）") double principal,
            @ToolParam(description = "年利率（百分比，如 5 表示 5%%）") double rate,
            @ToolParam(description = "投资年限") int years,
            @ToolParam(description = "每年复利次数，如 12 表示月复利") int timesPerYear) {

        double amount = principal * Math.pow(1 + rate / 100 / timesPerYear, timesPerYear * years);
        double earnings = amount - principal;

        return String.format("""
                复利计算结果：
                ─────────────────────
                本金:         %.2f 元
                年利率:       %.2f%%
                投资年限:     %d 年
                复利频率:     每年 %d 次
                ─────────────────────
                最终本息合计: %.2f 元
                投资收益:     %.2f 元
                收益率:       %.2f%%
                """,
                principal, rate, years, timesPerYear, amount, earnings, (earnings / principal) * 100);
    }

    @Tool(name = "calculate_loan_interest",
          description = "计算贷款等额本息月供和总利息")
    public String calculateLoanInterest(
            @ToolParam(description = "贷款总额（元）") double principal,
            @ToolParam(description = "年利率（百分比，如 4.2 表示 4.2%%）") double annualRate,
            @ToolParam(description = "贷款期限（月数）") int months) {

        double monthlyRate = annualRate / 100 / 12;
        double monthlyPayment = principal
                * monthlyRate * Math.pow(1 + monthlyRate, months)
                / (Math.pow(1 + monthlyRate, months) - 1);
        double totalPayment = monthlyPayment * months;
        double totalInterest = totalPayment - principal;

        return String.format("""
                贷款计算结果：
                ─────────────────────
                贷款总额:     %.2f 元
                年利率:       %.2f%%
                贷款期限:     %d 个月 (%d 年)
                ─────────────────────
                月供:         %.2f 元
                总还款额:     %.2f 元
                总利息:       %.2f 元
                """,
                principal, annualRate, months, months / 12,
                monthlyPayment, totalPayment, totalInterest);
    }

    @Tool(name = "search_research_reports",
          description = "从内部知识库中搜索金融研报、分析文档和专业知识")
    public String searchResearchReports(
            @ToolParam(description = "搜索关键词或问题") String query) {

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(5).similarityThreshold(0.6).build());

        if (docs.isEmpty()) {
            return "知识库中未找到相关研报内容";
        }

        return docs.stream()
                .map(doc -> {
                    String source = doc.getMetadata() != null
                            ? (String) doc.getMetadata().getOrDefault("source", "未知来源")
                            : "未知来源";
                    return "- [来源: " + source + "]\n  " + doc.getText();
                })
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 将 query 拆分为关键词列表（按空格/标点切分，过滤过短词与停用词）。
     * 用于联网摄入相关性过滤。
     */
    private List<String> tokenizeQuery(String query) {
        if (query == null || query.isBlank()) return List.of();
        List<String> tokens = new ArrayList<>();
        // 按非字母数字（含中文逐字）切分；中文按单字、英文按单词
        // 简化处理：按空格与常见标点切分，再对纯中文段按字拆分
        String[] parts = query.toLowerCase().split("[\\s,，。、；;:：!！?？()（）\"'`]+");
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (p.length() == 1) {
                // 单字（常见中文关键词）直接保留
                tokens.add(p);
            } else {
                tokens.add(p);
                // 对较长的纯中文串，额外拆为双字滑窗，提升命中率
                if (p.matches("[\\u4e00-\\u9fa5]{3,}")) {
                    for (int i = 0; i < p.length() - 1; i++) {
                        tokens.add(p.substring(i, i + 2));
                    }
                }
            }
        }
        // 去重
        return tokens.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 判断一条搜索结果是否与 query 强相关：
     * 标题或内容（小写）命中任意一个关键词即视为相关。
     */
    private boolean isRelevant(String title, String content, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return true; // 无关键词时放行
        String t = title == null ? "" : title.toLowerCase();
        String c = content == null ? "" : content.toLowerCase();
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank() && (t.contains(kw) || c.contains(kw))) {
                return true;
            }
        }
        return false;
    }
}
