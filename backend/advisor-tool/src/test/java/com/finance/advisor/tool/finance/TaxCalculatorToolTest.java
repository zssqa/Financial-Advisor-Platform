package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 税务计算工具测试
 */
class TaxCalculatorToolTest {

    private final TaxCalculatorTool tool = new TaxCalculatorTool();

    @Test
    void testCalculateSalaryTax() {
        String result = tool.calculateTax(300000, 60000, "salary");
        assertNotNull(result);
        assertTrue(result.contains("个人所得税"));
    }

    @Test
    void testCalculateInvestmentTax() {
        String result = tool.calculateTax(50000, 0, "investment");
        assertNotNull(result);
        assertTrue(result.contains("投资收益"));
    }
}
