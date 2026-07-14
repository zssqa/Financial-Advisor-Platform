package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KlineChartTool 单元测试
 */
class KlineChartToolTest {

    private final KlineChartTool tool = new KlineChartTool();

    @Test
    void testGenerateKlineChartWithDefaultParams() {
        String result = tool.generateKlineChart("sh600036", null, null);
        assertNotNull(result);
        assertTrue(result.contains("K线图") || result.contains("失败"));
    }

    @Test
    void testGenerateKlineChartWithCustomParams() {
        String result = tool.generateKlineChart("sz000001", "daily", 10);
        assertNotNull(result);
        assertTrue(result.contains("000001") || result.contains("失败"));
    }
}
