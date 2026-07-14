package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 保险对比工具测试
 */
class InsuranceCompareToolTest {

    private final InsuranceCompareTool tool = new InsuranceCompareTool();

    @Test
    void testCompareInsurance() {
        String result = tool.compareInsurance("medical", 30, "male", 5000.0);
        assertNotNull(result);
        assertTrue(result.contains("医疗险"));
    }
}
