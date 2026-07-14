package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExchangeRateTool 单元测试
 */
class ExchangeRateToolTest {

    private final ExchangeRateTool tool = new ExchangeRateTool();

    @Test
    void testExchangeRateWithInvalidCurrency() {
        String result = tool.exchangeRate("INVALID", "USD", 100.0);
        assertNotNull(result);
        assertTrue(result.contains("失败") || result.contains("不支持") || result.contains("错误"));
    }

    @Test
    void testExchangeRateWithoutAmount() {
        String result = tool.exchangeRate("USD", "EUR", null);
        assertNotNull(result);
        // 不带金额时只查汇率，不进行转换
    }
}
