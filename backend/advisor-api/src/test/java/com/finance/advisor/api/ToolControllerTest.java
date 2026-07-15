package com.finance.advisor.api;

import com.finance.advisor.common.dto.ApiResponse;
import com.finance.advisor.tool.finance.CreditCardInstallmentTool;
import com.finance.advisor.tool.finance.ExchangeRateTool;
import com.finance.advisor.tool.finance.FundScreenerTool;
import com.finance.advisor.tool.finance.TaxCalculatorTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ToolController 单元测试：使用纯 Mockito 验证 fundScreener / exchangeRate 的正则解析逻辑。
 */
class ToolControllerTest {

    private TaxCalculatorTool taxCalculatorTool;
    private FundScreenerTool fundScreenerTool;
    private ExchangeRateTool exchangeRateTool;
    private CreditCardInstallmentTool creditCardInstallmentTool;
    private ToolController controller;

    @BeforeEach
    void setUp() {
        taxCalculatorTool = mock(TaxCalculatorTool.class);
        fundScreenerTool = mock(FundScreenerTool.class);
        exchangeRateTool = mock(ExchangeRateTool.class);
        creditCardInstallmentTool = mock(CreditCardInstallmentTool.class);
        controller = new ToolController(taxCalculatorTool, fundScreenerTool,
                exchangeRateTool, creditCardInstallmentTool);
    }

    @Test
    void fundScreener_success_parsesFundList() {
        String toolResult = """
                1. 易方达蓝筹精选混合 (005827) - 类型: 混合型, 近1年收益: 15.50%, 风险等级: 中高风险
                2. 景顺长城新兴成长混合 (260108) - 类型: 混合型, 近1年收益: 12.30%, 风险等级: 中风险
                """;
        when(fundScreenerTool.screenFunds(anyString(), any(), any(), any())).thenReturn(toolResult);

        ToolController.FundScreenerRequest request =
                new ToolController.FundScreenerRequest("hybrid", 10.0, null, null);

        ApiResponse<List<ToolController.FundInfo>> response = controller.fundScreener(request);

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals(2, response.getData().size());

        ToolController.FundInfo f1 = response.getData().get(0);
        assertEquals("易方达蓝筹精选混合", f1.name());
        assertEquals("005827", f1.code());
        assertEquals("混合型", f1.type());
        assertEquals(8.0, f1.returnRate(), 0.0001);
        assertEquals("中", f1.riskLevel());

        ToolController.FundInfo f2 = response.getData().get(1);
        assertEquals("景顺长城新兴成长混合", f2.name());
        assertEquals("260108", f2.code());
    }

    @Test
    void exchangeRate_success_parsesRateAndConversion() {
        String toolResult = """
                实时汇率
                ─────────────────────
                USD → CNY
                汇率: 1 USD = 7.2500 CNY
                转换: 100.00 USD = 725.00 CNY
                """;
        when(exchangeRateTool.exchangeRate(anyString(), anyString(), any())).thenReturn(toolResult);

        ApiResponse<ToolController.ExchangeRateResponse> response =
                controller.exchangeRate("USD", "CNY", 100.0);

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        ToolController.ExchangeRateResponse data = response.getData();
        assertEquals("USD", data.from());
        assertEquals("CNY", data.to());
        assertEquals(7.25, data.rate(), 0.0001);
        assertEquals(100.0, data.amount(), 0.0001);
        assertEquals(725.0, data.convertedAmount(), 0.0001);
    }

    @Test
    void fundScreener_emptyResult_returnsEmptyList() {
        String toolResult = "未找到符合条件的基金";
        when(fundScreenerTool.screenFunds(anyString(), any(), any(), any())).thenReturn(toolResult);

        ToolController.FundScreenerRequest request =
                new ToolController.FundScreenerRequest("equity", null, null, null);

        ApiResponse<List<ToolController.FundInfo>> response = controller.fundScreener(request);

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertTrue(response.getData().isEmpty());
    }
}
