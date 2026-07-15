package com.finance.advisor.api;

import com.finance.advisor.common.dto.ApiResponse;
import com.finance.advisor.tool.finance.FinancialCalendarTool;
import com.finance.advisor.tool.finance.MarketSentimentTool;
import com.finance.advisor.tool.finance.StockQuoteTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MarketController 单元测试：使用纯 Mockito（无 Spring 上下文）验证指数行情 4 级降级链。
 * 降级链：东方财富 API → 新浪（StockQuoteTool）→ 数据库历史 → null
 */
@ExtendWith(MockitoExtension.class)
class MarketControllerTest {

    @Mock
    private StockQuoteTool stockQuoteTool;

    @Mock
    private MarketSentimentTool marketSentimentTool;

    @Mock
    private FinancialCalendarTool financialCalendarTool;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private RestTemplate mockRestTemplate;

    private MarketController controller;

    @BeforeEach
    void setUp() {
        controller = new MarketController(stockQuoteTool, marketSentimentTool,
                financialCalendarTool, jdbcTemplate);
        // eastMoneyRestTemplate 是 private final 字段（内联初始化为 new RestTemplate()），
        // 通过反射注入 mock 替换真实实例
        ReflectionTestUtils.setField(controller, "eastMoneyRestTemplate", mockRestTemplate);
    }

    /**
     * 场景1：东方财富 API 成功 → 返回实时数据并 UPSERT。
     * f43=396686 → price=3966.86，f170=-1 → changePercent=-0.01
     */
    @Test
    void indices_eastMoneySuccess_returnsRealTimeData() {
        Map<String, Object> response = Map.of(
                "data", Map.<String, Object>of("f43", 396686, "f170", -1));
        when(mockRestTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);

        ApiResponse<List<Map<String, Object>>> result = controller.indices();

        List<Map<String, Object>> data = result.getData();
        assertFalse(data.isEmpty(), "应返回指数列表");
        Map<String, Object> first = data.get(0);
        assertEquals(3966.86, ((Number) first.get("price")).doubleValue(), 0.001,
                "price 应为 f43/100 = 3966.86");
        assertEquals(-0.01, ((Number) first.get("changePercent")).doubleValue(), 0.001,
                "changePercent 应为 f170/100 = -0.01");

        // 4 个指数均成功获取，应触发 4 次 UPSERT
        verify(jdbcTemplate, times(4)).update(anyString(), any(), any(), any(), any(), any());
    }

    /**
     * 场景2：东方财富 API 异常 → 降级到新浪（StockQuoteTool）成功 → UPSERT。
     */
    @Test
    void indices_eastMoneyFails_sinaFallback_success() {
        when(mockRestTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("EastMoney unavailable"));
        when(stockQuoteTool.queryStockQuote(anyString()))
                .thenReturn("股票行情 - 上证指数\n最新价:1234.56 元\n涨跌幅:0.5%");

        ApiResponse<List<Map<String, Object>>> result = controller.indices();

        List<Map<String, Object>> data = result.getData();
        Map<String, Object> first = data.get(0);
        assertEquals(1234.56, ((Number) first.get("price")).doubleValue(), 0.001,
                "price 应从新浪文本解析为 1234.56");
        assertEquals(0.5, ((Number) first.get("changePercent")).doubleValue(), 0.001,
                "changePercent 应从新浪文本解析为 0.5");

        // 新浪降级成功，应触发 4 次 UPSERT
        verify(jdbcTemplate, times(4)).update(anyString(), any(), any(), any(), any(), any());
    }

    /**
     * 场景3：东方财富 + 新浪均失败 → 数据库历史有数据 → 返回历史行情，不 UPSERT。
     */
    @Test
    void indices_allApisFail_dbHistoryHasData_returnsHistory() {
        when(mockRestTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("EastMoney unavailable"));
        when(stockQuoteTool.queryStockQuote(anyString())).thenReturn(null);
        Map<String, Object> row = Map.<String, Object>of("price", 3000.0, "change_percent", 1.5);
        when(jdbcTemplate.queryForList(anyString(), anyString())).thenReturn(List.of(row));

        ApiResponse<List<Map<String, Object>>> result = controller.indices();

        List<Map<String, Object>> data = result.getData();
        Map<String, Object> first = data.get(0);
        assertEquals(3000.0, ((Number) first.get("price")).doubleValue(), 0.001,
                "price 应为数据库历史值 3000.0");
        assertEquals(1.5, ((Number) first.get("changePercent")).doubleValue(), 0.001,
                "changePercent 应为数据库历史值 1.5");

        // 无实时数据，不应触发 UPSERT
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any());
    }

    /**
     * 场景4：全部失败且数据库无历史 → price/changePercent 为 null。
     */
    @Test
    void indices_allApisFail_dbHistoryEmpty_returnsNull() {
        when(mockRestTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("EastMoney unavailable"));
        when(stockQuoteTool.queryStockQuote(anyString())).thenReturn(null);
        when(jdbcTemplate.queryForList(anyString(), anyString())).thenReturn(List.of());

        ApiResponse<List<Map<String, Object>>> result = controller.indices();

        List<Map<String, Object>> data = result.getData();
        Map<String, Object> first = data.get(0);
        assertNull(first.get("price"), "无任何数据源时 price 应为 null");
        assertNull(first.get("changePercent"), "无任何数据源时 changePercent 应为 null");
    }

    /**
     * 场景5：sh 前缀指数（如 sh000001）应使用 secid=1.000001。
     */
    @Test
    void fetchIndexFromEastMoney_shPrefix_usesSecid1() {
        Map<String, Object> response = Map.of(
                "data", Map.<String, Object>of("f43", 396686, "f170", -1));
        when(mockRestTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);

        controller.indices();

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockRestTemplate, atLeastOnce()).getForObject(urlCaptor.capture(), eq(Map.class));
        List<String> urls = urlCaptor.getAllValues();
        assertTrue(urls.stream().anyMatch(u -> u.contains("secid=1.000001")),
                "sh000001 应映射到 secid=1.000001，实际 URL 列表: " + urls);
    }

    /**
     * 场景6：sz 前缀指数（如 sz399001）应使用 secid=0.399001。
     */
    @Test
    void fetchIndexFromEastMoney_szPrefix_usesSecid0() {
        Map<String, Object> response = Map.of(
                "data", Map.<String, Object>of("f43", 396686, "f170", -1));
        when(mockRestTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);

        controller.indices();

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockRestTemplate, atLeastOnce()).getForObject(urlCaptor.capture(), eq(Map.class));
        List<String> urls = urlCaptor.getAllValues();
        assertTrue(urls.stream().anyMatch(u -> u.contains("secid=0.399001")),
                "sz399001 应映射到 secid=0.399001，实际 URL 列表: " + urls);
    }
}
