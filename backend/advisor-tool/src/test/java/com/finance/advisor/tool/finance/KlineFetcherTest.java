package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * KlineFetcher 单元测试：mock RestTemplate，验证新浪/东方财富 API 解析与降级逻辑。
 */
class KlineFetcherTest {

    private KlineFetcher fetcher;
    private RestTemplate mockRest;

    @BeforeEach
    void setUp() {
        fetcher = new KlineFetcher();
        mockRest = mock(RestTemplate.class);
        // KlineFetcher 内部以 final 字段持有 RestTemplate，复用 FundNavToolTest 的反射注入方式
        ReflectionTestUtils.setField(fetcher, "restTemplate", mockRest);
    }

    /** 新浪 API 返回合法 JSON 数组时能正确解析 OHLC + 成交量 */
    @Test
    void fetchFromSina_parsesJsonArrayCorrectly() {
        String sinaJson = "[{\"day\":\"2026-07-10\",\"open\":\"10.00\",\"high\":\"10.50\","
                + "\"low\":\"9.80\",\"close\":\"10.30\",\"volume\":\"100000\"},"
                + "{\"day\":\"2026-07-11\",\"open\":\"10.30\",\"high\":\"10.60\","
                + "\"low\":\"10.10\",\"close\":\"10.45\",\"volume\":\"120000\"}]";
        when(mockRest.getForObject(
                argThat((String url) -> url != null && url.contains("finance.sina.com.cn")),
                eq(byte[].class)))
            .thenReturn(sinaJson.getBytes(StandardCharsets.UTF_8));

        List<KlineFetcher.KlineData> data = fetcher.fetchKlineData("sh600036", 60);

        assertEquals(2, data.size());
        KlineFetcher.KlineData first = data.get(0);
        assertEquals("2026-07-10", first.getDay());
        assertEquals(10.00, first.getOpen(), 1e-6);
        assertEquals(10.50, first.getHigh(), 1e-6);
        assertEquals(9.80, first.getLow(), 1e-6);
        assertEquals(10.30, first.getClose(), 1e-6);
        assertEquals(100000.0, first.getVolume(), 1e-6);
        // 第二条数据
        assertEquals("2026-07-11", data.get(1).getDay());
        assertEquals(10.45, data.get(1).getClose(), 1e-6);
    }

    /** symbol（如 sh600036）被直接传入新浪 URL，格式保持一致 */
    @Test
    void symbolIsPassedDirectlyToSinaUrl() {
        String sinaJson = "[{\"day\":\"2026-07-10\",\"open\":\"10.00\",\"high\":\"10.50\","
                + "\"low\":\"9.80\",\"close\":\"10.30\",\"volume\":\"100000\"}]";
        when(mockRest.getForObject(anyString(), eq(byte[].class)))
            .thenReturn(sinaJson.getBytes(StandardCharsets.UTF_8));

        fetcher.fetchKlineData("sh600036", 30);

        // 新浪 URL 中应包含 symbol=sh600036（sh 前缀 + 6 位代码直接透传）
        verify(mockRest).getForObject(
                argThat((String url) -> url != null && url.contains("symbol=sh600036")),
                eq(byte[].class));
    }

    /** 新浪 API 抛异常时自动降级到东方财富 API */
    @Test
    void sinaFailure_fallsBackToEastmoney() {
        String eastmoneyJson = "{\"data\":{\"klines\":["
                + "\"2026-07-10,10.00,10.30,10.50,9.80,100000,1050000,1.0\","
                + "\"2026-07-11,10.30,10.45,10.60,10.10,120000,1250000,1.2\"]}}";
        when(mockRest.getForObject(
                argThat((String url) -> url != null && url.contains("finance.sina.com.cn")),
                eq(byte[].class)))
            .thenThrow(new RuntimeException("sina API 不可用"));
        when(mockRest.getForObject(
                argThat((String url) -> url != null && url.contains("eastmoney.com")),
                eq(byte[].class)))
            .thenReturn(eastmoneyJson.getBytes(StandardCharsets.UTF_8));

        List<KlineFetcher.KlineData> data = fetcher.fetchKlineData("sh600036", 30);

        assertFalse(data.isEmpty());
        assertEquals(2, data.size());
        // 东方财富格式：日期,开盘,收盘,最高,最低,成交量,...
        assertEquals("2026-07-10", data.get(0).getDay());
        assertEquals(10.00, data.get(0).getOpen(), 1e-6);
        assertEquals(10.30, data.get(0).getClose(), 1e-6);
        assertEquals(10.50, data.get(0).getHigh(), 1e-6);
        assertEquals(9.80, data.get(0).getLow(), 1e-6);
    }

    /** 新浪与东方财富均失败时返回空列表，不抛异常 */
    @Test
    void bothApisFail_returnsEmptyList() {
        when(mockRest.getForObject(anyString(), eq(byte[].class)))
            .thenThrow(new RuntimeException("网络不可达"));

        List<KlineFetcher.KlineData> data = fetcher.fetchKlineData("sh600036", 30);

        assertNotNull(data);
        assertTrue(data.isEmpty());
    }

    /** 新浪返回空数组时降级到东方财富 */
    @Test
    void sinaReturnsEmptyArray_fallsBackToEastmoney() {
        when(mockRest.getForObject(
                argThat((String url) -> url != null && url.contains("finance.sina.com.cn")),
                eq(byte[].class)))
            .thenReturn("[]".getBytes(StandardCharsets.UTF_8));
        String eastmoneyJson = "{\"data\":{\"klines\":["
                + "\"2026-07-10,10.00,10.30,10.50,9.80,100000,1050000,1.0\"]}}";
        when(mockRest.getForObject(
                argThat((String url) -> url != null && url.contains("eastmoney.com")),
                eq(byte[].class)))
            .thenReturn(eastmoneyJson.getBytes(StandardCharsets.UTF_8));

        List<KlineFetcher.KlineData> data = fetcher.fetchKlineData("sz000001", 10);

        assertEquals(1, data.size());
        assertEquals("2026-07-10", data.get(0).getDay());
        assertEquals(10.30, data.get(0).getClose(), 1e-6);
    }
}
