package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 风险承受能力评估工具测试
 */
class RiskQuestionnaireToolTest {

    private final RiskQuestionnaireTool tool = new RiskQuestionnaireTool();

    @Test
    void testAssessRisk() {
        String result = tool.assessRisk("25-35", 50, "advanced", 30, 20);
        assertNotNull(result);
        assertTrue(result.contains("R"));
        assertTrue(result.contains("风险等级"));
    }
}
