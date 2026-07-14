package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 金融日历工具测试
 */
class FinancialCalendarToolTest {

    private final FinancialCalendarTool tool = new FinancialCalendarTool();

    @Test
    void testQueryCalendarWithCode() {
        String result = tool.queryCalendar("sh600036", "all");
        assertNotNull(result);
        assertTrue(result.contains("600036"));
    }

    @Test
    void testQueryCalendarWithoutCode() {
        String result = tool.queryCalendar(null, "earnings");
        assertNotNull(result);
        assertTrue(result.contains("财报"));
    }
}
