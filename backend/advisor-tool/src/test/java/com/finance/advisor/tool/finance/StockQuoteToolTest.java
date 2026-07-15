package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * StockQuoteTool 单元测试
 */
class StockQuoteToolTest {

    private final StockQuoteTool tool = new StockQuoteTool();

    @Test
    void testQueryStockQuoteWithInvalidCode() {
        String result = tool.queryStockQuote("invalid_code");
        assertNotNull(result);
        assertTrue(result.contains("失败") || result.contains("未查询到"));
    }

    @Test
    void queryStockQuote_priceIsZero_returnsInvalidMessage() {
        // 构造一个会被解析为 price=0 的新浪行情响应（GBK 编码，字段数 >= 30）
        String response = "var hq_str_sh600036=\"招商银行,10.0,10.0,0.0,10.0,10.0,10.0,10.0,1000,100000000,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0\";";
        byte[] bytes = response.getBytes(Charset.forName("GBK"));

        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(
                RestTemplate.class,
                (mock, context) -> Mockito.when(mock.getForObject(anyString(), eq(byte[].class))).thenReturn(bytes))) {
            StockQuoteTool mockedTool = new StockQuoteTool();
            String result = mockedTool.queryStockQuote("sh600036");
            assertNotNull(result);
            assertTrue(result.contains("未查询到有效行情数据"));
        }
    }
}
