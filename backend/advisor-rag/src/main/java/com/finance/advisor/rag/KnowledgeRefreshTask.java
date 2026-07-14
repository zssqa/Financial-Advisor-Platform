package com.finance.advisor.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库定时更新任务
 *
 * 每周一 8:00 自动从 Tavily 摄入最新财经要闻入向量库，保持知识库时效性。
 * 去重逻辑：通过 DocumentMetadataService.existsByUrl 判断 URL 是否已入库，
 * 未入库的结果再调用 DocumentIngestionService.ingestWebContent 写入向量库与元数据表。
 * 单个关键词或单条结果处理失败不会中断整个流程。
 */
@Component
public class KnowledgeRefreshTask {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRefreshTask.class);

    /** 财经要闻搜索关键词列表（中文） */
    private static final String[] KEYWORDS = {
            "A股市场行情",
            "本周财经要闻",
            "宏观经济政策",
            "央行货币政策",
            "股市分析"
    };

    @Value("${tavily.api-key}")
    private String tavilyApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    private final DocumentIngestionService documentIngestionService;
    private final DocumentMetadataService documentMetadataService;

    public KnowledgeRefreshTask(DocumentIngestionService documentIngestionService,
                                DocumentMetadataService documentMetadataService) {
        this.documentIngestionService = documentIngestionService;
        this.documentMetadataService = documentMetadataService;
    }

    /**
     * 每周一 8:00 触发，按关键词从 Tavily 摄入最新财经要闻入向量库。
     */
    @Scheduled(cron = "0 0 8 * * MON")
    public void refreshFinanceNews() {
        log.info("[知识库定时更新] 任务开始, 关键词数量={}, 关键词={}",
                KEYWORDS.length, String.join(",", KEYWORDS));

        int totalIngested = 0;   // 新增入库的结果条数
        int totalSkipped = 0;    // URL 已存在而跳过的条数
        int totalFailed = 0;     // 处理失败的条数
        int keywordSuccess = 0;  // 成功完成的关键词数

        for (String keyword : KEYWORDS) {
            try {
                log.info("[知识库定时更新] 开始处理关键词: {}", keyword);

                List<Map<String, Object>> results = searchFromTavily(keyword);
                if (results == null || results.isEmpty()) {
                    log.warn("[知识库定时更新] 关键词无搜索结果: {}", keyword);
                    continue;
                }

                int ingested = 0, skipped = 0, failed = 0;
                for (Map<String, Object> r : results) {
                    String title = (String) r.getOrDefault("title", "");
                    String content = (String) r.getOrDefault("content", "");
                    String url = (String) r.getOrDefault("url", "");
                    String publishedDate = (String) r.getOrDefault("published_date", "");

                    try {
                        // 去重：URL 已存在则跳过（ingestWebContent 自身不做去重，由调用方判断）
                        if (documentMetadataService.existsByUrl(url)) {
                            skipped++;
                            log.info("[知识库定时更新] 已存在,跳过: keyword={}, url={}", keyword, url);
                            continue;
                        }
                        int chunks = documentIngestionService.ingestWebContent(title, content, url, publishedDate);
                        ingested++;
                        log.info("[知识库定时更新] 新增入库: keyword={}, title={}, url={}, chunks={}",
                                keyword, title, url, chunks);
                    } catch (Exception e) {
                        // 单条结果入库失败不影响后续结果
                        failed++;
                        log.warn("[知识库定时更新] 入库失败,继续下一条: keyword={}, url={}, error={}",
                                keyword, url, e.getMessage());
                    }
                }

                keywordSuccess++;
                totalIngested += ingested;
                totalSkipped += skipped;
                totalFailed += failed;
                log.info("[知识库定时更新] 关键词处理完成: keyword={}, 新增={}, 已存在={}, 失败={}, 总结果={}",
                        keyword, ingested, skipped, failed, results.size());
            } catch (Exception e) {
                // 单个关键词失败不中断整个流程，继续下一个关键词
                log.error("[知识库定时更新] 关键词处理异常,继续下一个关键词: keyword={}", keyword, e);
            }
        }

        log.info("[知识库定时更新] 任务完成: 成功关键词数={}, 新增入库={}, 已存在={}, 失败={}",
                keywordSuccess, totalIngested, totalSkipped, totalFailed);
    }

    /**
     * 调用 Tavily Search API 搜索财经要闻（topic=news, days=7）。
     *
     * @param query 搜索关键词
     * @return 搜索结果列表，每条包含 title/content/url/published_date 等字段；无结果或异常返回 null
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> searchFromTavily(String query) {
        if (tavilyApiKey == null || tavilyApiKey.isBlank()) {
            log.warn("[知识库定时更新] Tavily API Key 未配置,跳过搜索: query={}", query);
            return null;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("api_key", tavilyApiKey);
        body.put("query", query);
        body.put("search_depth", "advanced");
        body.put("max_results", 10);
        body.put("include_answer", true);
        body.put("topic", "news");
        body.put("days", 7);

        try {
            Map result = restTemplate.postForObject("https://api.tavily.com/search", body, Map.class);
            if (result == null || !result.containsKey("results")) {
                log.warn("[知识库定时更新] Tavily 返回无结果: query={}", query);
                return null;
            }
            return (List<Map<String, Object>>) result.get("results");
        } catch (Exception e) {
            log.error("[知识库定时更新] Tavily 搜索失败: query={}", query, e);
            return null;
        }
    }
}
