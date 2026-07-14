package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 储蓄目标规划工具测试
 */
class SavingsGoalToolTest {

    private final SavingsGoalTool tool = new SavingsGoalTool();

    @Test
    void testCalculateSavingsPlan() {
        String result = tool.calculateSavingsPlan(500000, 3, 10, false, null);
        assertNotNull(result);
        assertTrue(result.contains("每月需存入"));
    }
}
