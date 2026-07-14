package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 投资组合优化工具测试
 */
class PortfolioOptimizerToolTest {

    private final PortfolioOptimizerTool tool = new PortfolioOptimizerTool();

    @Test
    void testOptimizePortfolio() {
        String result = tool.optimizePortfolio(
                "股票基金,债券基金,货币基金",
                "10,4,2",
                "20,5,1",
                "1,0.3,0.1,0.3,1,0.2,0.1,0.2,1");
        assertNotNull(result);
        assertTrue(result.contains("Markowitz"));
    }

    @Test
    void testOptimizePortfolioInvalidParams() {
        String result = tool.optimizePortfolio("A,B", "10,4,2", "20,5", "1,0.3,0.3,1");
        assertTrue(result.contains("参数数量不匹配"));
    }
}
