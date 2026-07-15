package com.finance.advisor.api;

import com.finance.advisor.common.dto.ApiResponse;
import com.finance.advisor.portfolio.Asset;
import com.finance.advisor.portfolio.PortfolioService;
import com.finance.advisor.portfolio.SecurityUtil;
import com.finance.advisor.tool.finance.KlineChartTool;
import com.finance.advisor.tool.finance.PortfolioOptimizerTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * AnalysisController 单元测试：使用纯 Mockito 验证 optimize / riskReturn 的解析与分支逻辑。
 * 通过 mockStatic 桩 SecurityUtil.currentUserId()，绕过 SecurityContext 依赖。
 */
class AnalysisControllerTest {

    private KlineChartTool klineChartTool;
    private PortfolioOptimizerTool portfolioOptimizerTool;
    private PortfolioService portfolioService;
    private AnalysisController controller;

    @BeforeEach
    void setUp() {
        klineChartTool = mock(KlineChartTool.class);
        portfolioOptimizerTool = mock(PortfolioOptimizerTool.class);
        portfolioService = mock(PortfolioService.class);
        controller = new AnalysisController(klineChartTool, portfolioOptimizerTool, portfolioService);
    }

    private Asset stockAsset(Long id, String symbol, String name) {
        Asset a = new Asset();
        a.setId(id);
        a.setUserId(1L);
        a.setType("stock");
        a.setSymbol(symbol);
        a.setName(name);
        a.setAmount(new BigDecimal("100"));
        a.setCostPrice(new BigDecimal("10"));
        a.setMarketValue(new BigDecimal("1000"));
        return a;
    }

    private Asset nonStockAsset(Long id, String type) {
        Asset a = new Asset();
        a.setId(id);
        a.setUserId(1L);
        a.setType(type);
        a.setAmount(new BigDecimal("1000"));
        a.setCostPrice(new BigDecimal("1"));
        a.setMarketValue(new BigDecimal("1000"));
        return a;
    }

    @Test
    void optimize_success_parsesWeightsAndMetrics() {
        Asset a = stockAsset(10L, "sh600036", "招商银行");
        when(portfolioService.list(1L)).thenReturn(List.of(a));

        String toolResult = """
                各资产最优权重:
                  招商银行: 100.00%

                最优组合绩效指标:
                预期年化收益率: 8.00%
                预期年化波动率: 20.00%
                夏普比率:       0.3000
                """;
        when(portfolioOptimizerTool.optimizePortfolio(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(toolResult);

        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(1L);

            ApiResponse<Map<String, Object>> response = controller.optimize();

            assertEquals(200, response.getCode());
            assertNotNull(response.getData());
            assertTrue(response.getData().containsKey("weights"));
            @SuppressWarnings("unchecked")
            Map<String, Double> weights = (Map<String, Double>) response.getData().get("weights");
            assertEquals(1.0, weights.get("招商银行"), 0.0001);
            assertEquals(0.08, (Double) response.getData().get("expectedReturn"), 0.0001);
            assertEquals(0.20, (Double) response.getData().get("volatility"), 0.0001);
            assertEquals(0.3, (Double) response.getData().get("sharpeRatio"), 0.0001);
        }
    }

    @Test
    void optimize_noStockFundAssets_returns400() {
        Asset cash = nonStockAsset(1L, "cash");
        Asset bond = nonStockAsset(2L, "bond");
        when(portfolioService.list(1L)).thenReturn(List.of(cash, bond));

        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(1L);

            ApiResponse<Map<String, Object>> response = controller.optimize();

            assertEquals(400, response.getCode());
            assertNull(response.getData());
        }
    }

    @Test
    void riskReturn_historySufficient_computesMetrics() {
        Asset a = stockAsset(10L, "sh600036", "招商银行");
        when(portfolioService.list(1L)).thenReturn(List.of(a));

        // 构造 10 条递增的历史净值（满足 >= 7 条阈值）
        List<Map<String, Object>> history = new ArrayList<>();
        double price = 10.0;
        for (int i = 0; i < 10; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("price", price);
            history.add(row);
            price += 0.5;
        }
        when(portfolioService.getPriceHistory(any(), anyInt())).thenReturn(history);

        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(1L);

            ApiResponse<List<Map<String, Object>>> response = controller.riskReturn();

            assertEquals(200, response.getCode());
            assertNotNull(response.getData());
            assertFalse(response.getData().isEmpty());
            Map<String, Object> point = response.getData().get(0);
            assertEquals("招商银行", point.get("name"));
            assertNotNull(point.get("marketValue"));
            assertNotNull(point.get("annualReturn"));
            assertNotNull(point.get("annualVolatility"));
        }
    }

    @Test
    void riskReturn_historyInsufficient_skipsAsset() {
        Asset a = stockAsset(10L, "sh600036", "招商银行");
        when(portfolioService.list(1L)).thenReturn(List.of(a));

        // 仅 3 条历史净值（< 7 阈值），资产应被跳过
        List<Map<String, Object>> history = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("price", 10.0 + i * 0.5);
            history.add(row);
        }
        when(portfolioService.getPriceHistory(any(), anyInt())).thenReturn(history);

        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(1L);

            ApiResponse<List<Map<String, Object>>> response = controller.riskReturn();

            assertEquals(200, response.getCode());
            assertNotNull(response.getData());
            assertTrue(response.getData().isEmpty());
        }
    }
}
