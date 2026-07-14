package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StockQuoteTool 单元测试
 */
class StockQuoteToolTest {

    private final StockQuoteTool tool = new StockQuoteTool();

    @Test
    void testQueryStockQuoteWithInvalidCode() {
        String result = tool.queryStockQuote("invalid_code");
        assertNotNull(result);
        assertTrue(result.contains("失败") || result.contains("未查询到"));
    }
}
