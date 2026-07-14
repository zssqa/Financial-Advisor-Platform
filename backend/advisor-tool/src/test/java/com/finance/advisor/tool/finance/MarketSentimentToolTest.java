package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 市场情绪分析工具测试
 */
class MarketSentimentToolTest {

    private final MarketSentimentTool tool = new MarketSentimentTool();

    @Test
    void testAnalyzeSentiment() {
        String result = tool.analyzeSentiment("a_stock", 12.5, 30.0, 8000.0, 1.2);
        assertNotNull(result);
        assertTrue(result.contains("A股"));
        assertTrue(result.contains("评分"));
    }
}
