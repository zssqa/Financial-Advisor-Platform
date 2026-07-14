package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 信用卡分期计算工具测试
 */
class CreditCardInstallmentToolTest {

    private final CreditCardInstallmentTool tool = new CreditCardInstallmentTool();

    @Test
    void testCalculateInstallment() {
        String result = tool.calculateInstallment(10000, 12, 0.72);
        assertNotNull(result);
        assertTrue(result.contains("10000"));
        assertTrue(result.contains("12"));
    }
}
