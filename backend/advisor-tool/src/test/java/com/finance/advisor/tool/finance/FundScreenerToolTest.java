package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 基金筛选器工具测试
 */
class FundScreenerToolTest {

    private final FundScreenerTool tool = new FundScreenerTool();

    @Test
    void testScreenFunds() {
        String result = tool.screenFunds("equity", 10.0, 10.0, 1.5);
        assertNotNull(result);
        assertTrue(result.contains("基金"));
    }
}
