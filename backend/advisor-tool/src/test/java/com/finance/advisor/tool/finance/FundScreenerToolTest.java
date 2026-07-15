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

    @Test
    void screenFunds_hybrid_returnsHybridFunds() {
        String result = tool.screenFunds("hybrid", null, null, null);
        assertNotNull(result);
        assertTrue(result.contains("兴全合润"));
        assertTrue(result.contains("交银新成长"));
        assertTrue(result.contains("景顺长城新兴成长"));
    }

    @Test
    void screenFunds_bond_returnsBondFunds() {
        String result = tool.screenFunds("bond", null, null, null);
        assertNotNull(result);
        assertTrue(result.contains("招商产业债券"));
        assertTrue(result.contains("易方达稳健收益"));
        assertTrue(result.contains("博时信用债券"));
    }

    @Test
    void screenFunds_money_returnsMoneyFunds() {
        String result = tool.screenFunds("money", null, null, null);
        assertNotNull(result);
        assertTrue(result.contains("天弘余额宝"));
        assertTrue(result.contains("南方天天利A"));
        assertTrue(result.contains("易方达增金宝"));
    }

    @Test
    void screenFunds_index_returnsIndexFunds() {
        String result = tool.screenFunds("index", null, null, null);
        assertNotNull(result);
        assertTrue(result.contains("沪深300ETF联接"));
        assertTrue(result.contains("中证500ETF联接"));
        assertTrue(result.contains("科创50ETF联接"));
    }

    @Test
    void screenFunds_default_returnsDefaultFunds() {
        String result = tool.screenFunds("unknown", null, null, null);
        assertNotNull(result);
        assertTrue(result.contains("请选择正确的基金类型"));
    }
}
