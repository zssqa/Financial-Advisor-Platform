package com.finance.advisor.portfolio;

import com.finance.advisor.tool.finance.FundNavTool;
import com.finance.advisor.tool.finance.StockQuoteTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PortfolioService 单元测试：mock AssetRepository + StockQuoteTool + FundNavTool + JdbcTemplate。
 */
class PortfolioServiceTest {

    private AssetRepository assetRepository;
    private JdbcTemplate jdbcTemplate;
    private StockQuoteTool stockQuoteTool;
    private FundNavTool fundNavTool;
    private AssetImportParser assetImportParser;
    private PortfolioService service;

    @BeforeEach
    void setUp() {
        assetRepository = mock(AssetRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        stockQuoteTool = mock(StockQuoteTool.class);
        fundNavTool = mock(FundNavTool.class);
        assetImportParser = mock(AssetImportParser.class);
        service = new PortfolioService(assetRepository, jdbcTemplate, stockQuoteTool, fundNavTool, assetImportParser);
    }

    private Asset asset(String type, String symbol, String amount, String costPrice) {
        Asset a = new Asset();
        a.setType(type);
        a.setSymbol(symbol);
        a.setAmount(new BigDecimal(amount));
        a.setCostPrice(new BigDecimal(costPrice));
        return a;
    }

    @Test
    void list_returnsOnlyGivenUserAssets() {
        Asset a1 = asset("stock", "sh600036", "100", "10");
        when(assetRepository.findByUserId(1L)).thenReturn(List.of(a1));
        when(assetRepository.findByUserId(2L)).thenReturn(List.of());

        assertEquals(1, service.list(1L).size());
        assertTrue(service.list(2L).isEmpty());
        verify(assetRepository).findByUserId(1L);
    }

    @Test
    void create_bindsUserIdAndResetsId() {
        Asset input = asset("stock", "sh600036", "100", "10");
        input.setId(999L); // 调用方误传 id，应被重置
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> {
            Asset a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });

        Asset created = service.create(1L, input);

        assertEquals(Long.valueOf(1L), created.getUserId());
        assertEquals(Long.valueOf(10L), created.getId());
        verify(assetRepository).save(argThat(a -> Long.valueOf(1L).equals(a.getUserId())));
    }

    @Test
    void summary_aggregatesByType_andComputesTotals() {
        Asset deposit = asset("deposit", null, "1000", "1");
        Asset stock = asset("stock", "sh600036", "100", "10");
        when(assetRepository.findByUserId(1L)).thenReturn(List.of(deposit, stock));
        when(stockQuoteTool.queryStockQuote("sh600036"))
                .thenReturn("股票行情 - 招商银行 (sh600036)\n最新价: 12.50 元\n涨跌幅: +1.00%");

        PortfolioSummary s = service.summary(1L);

        // totalCost = 1000*1 + 100*10 = 2000
        assertEquals(0, s.getTotalCost().compareTo(new BigDecimal("2000")));
        // totalMarketValue = 1000*1(deposit 成本) + 100*12.50(stock 实时) = 2250
        assertEquals(0, s.getTotalMarketValue().compareTo(new BigDecimal("2250")));
        // profitLoss = 2250 - 2000 = 250
        assertEquals(0, s.getProfitLoss().compareTo(new BigDecimal("250")));
        assertEquals(2, s.getBreakdown().size());
        verify(stockQuoteTool).queryStockQuote("sh600036");
    }

    @Test
    void summary_stockQuoteFailure_degradesToCostPrice() {
        Asset stock = asset("stock", "sh600036", "100", "10");
        when(assetRepository.findByUserId(1L)).thenReturn(List.of(stock));
        when(stockQuoteTool.queryStockQuote("sh600036")).thenThrow(new RuntimeException("network down"));

        PortfolioSummary s = service.summary(1L);

        // 降级：marketValue = 100*10 = 1000，cost = 1000，profitLoss = 0
        assertEquals(0, s.getTotalMarketValue().compareTo(new BigDecimal("1000")));
        assertEquals(0, s.getTotalCost().compareTo(new BigDecimal("1000")));
        assertEquals(0, s.getProfitLoss().compareTo(BigDecimal.ZERO));
    }

    @Test
    void summary_stockQuoteUnparseable_degradesToCostPrice() {
        Asset stock = asset("stock", "sh600036", "100", "10");
        when(assetRepository.findByUserId(1L)).thenReturn(List.of(stock));
        when(stockQuoteTool.queryStockQuote("sh600036")).thenReturn("数据不完整");

        PortfolioSummary s = service.summary(1L);

        assertEquals(0, s.getTotalMarketValue().compareTo(new BigDecimal("1000")));
    }

    @Test
    void refreshAllMarketValues_fetchesPriceAndUpdatesMarketData() {
        Asset stock = asset("stock", "sh600036", "100", "10");
        stock.setId(1L);
        when(assetRepository.findAllStockFundAssets()).thenReturn(List.of(stock));
        when(stockQuoteTool.queryStockQuote("sh600036")).thenReturn("最新价: 12.50 元");

        service.refreshAllMarketValues();

        // marketValue = 100 * 12.50 = 1250.0；使用 compareTo 匹配避免 BigDecimal scale 差异
        verify(assetRepository).updateMarketData(eq(1L),
                argThat(b -> b != null && b.compareTo(new BigDecimal("12.50")) == 0),
                argThat(b -> b != null && b.compareTo(new BigDecimal("1250.0")) == 0),
                anyLong());
        verify(stockQuoteTool).queryStockQuote("sh600036");
    }
}
