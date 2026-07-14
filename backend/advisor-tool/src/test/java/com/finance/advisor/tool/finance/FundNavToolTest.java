package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * FundNavTool 单元测试
 */
class FundNavToolTest {

    @Test
    void testQueryFundNavParsesLatestNav() {
        FundNavTool tool = new FundNavTool();
        RestTemplate mockRest = mock(RestTemplate.class);
        String mockResponse = "jQuery123({\"Data\":{\"LSJZList\":[{\"FSRQ\":\"2026-07-14\","
                + "\"DWJZ\":\"1.2345\",\"JZZZL\":\"0.50\",\"LJJZ\":\"2.3456\"}]},\"ErrCode\":0})";
        when(mockRest.getForObject(anyString(), eq(byte[].class)))
                .thenReturn(mockResponse.getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(tool, "restTemplate", mockRest);

        String result = tool.queryFundNav("110011");

        assertNotNull(result);
        assertTrue(result.contains("基金代码: 110011"));
        assertTrue(result.contains("最新净值: 1.2345"), "result should contain 最新净值 line");
        assertTrue(result.contains("净值日期: 2026-07-14"));
        assertTrue(result.contains("日涨跌幅: +0.50%"));
    }

    @Test
    void testQueryFundNavWithInvalidCode() {
        FundNavTool tool = new FundNavTool();
        RestTemplate mockRest = mock(RestTemplate.class);
        when(mockRest.getForObject(anyString(), eq(byte[].class)))
                .thenReturn("".getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(tool, "restTemplate", mockRest);

        String result = tool.queryFundNav("000000");
        assertNotNull(result);
        assertFalse(result.isBlank());
        assertFalse(result.contains("最新净值:"));
    }
}
