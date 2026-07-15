package com.finance.advisor.portfolio;

import com.finance.advisor.tool.finance.FundNavTool;
import com.finance.advisor.tool.finance.StockQuoteTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    // ===== getLatestPriceFromHistory tests =====

    @Test
    void getLatestPriceFromHistory_hasHistory_returnsPrice() {
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any()))
                .thenReturn(new BigDecimal("10.50"));

        BigDecimal result = service.getLatestPriceFromHistory(1L);

        assertNotNull(result);
        assertEquals(0, result.compareTo(new BigDecimal("10.50")));
    }

    @Test
    void getLatestPriceFromHistory_noHistory_returnsNull() {
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any()))
                .thenThrow(new EmptyResultDataAccessException(1));

        BigDecimal result = service.getLatestPriceFromHistory(1L);

        assertNull(result);
    }

    @Test
    void getLatestPriceFromHistory_nullAssetId_returnsNull() {
        BigDecimal result = service.getLatestPriceFromHistory(null);

        assertNull(result);
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(BigDecimal.class), any());
    }

    @Test
    void getLatestPriceFromHistory_queryThrows_returnsNull() {
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any()))
                .thenThrow(new RuntimeException("DB connection error"));

        BigDecimal result = service.getLatestPriceFromHistory(1L);

        assertNull(result);
    }

    // ===== estimateUnitPrice three-tier fallback tests (via list()) =====

    @Test
    void list_stockRealTimeSuccess_returnsRealTimePrice() {
        Asset stock = asset("stock", "sh600036", "100", "10");
        stock.setId(1L);
        stock.setUserId(1L);
        stock.setMarketValue(null);
        stock.setPriceUpdatedAt(0L);
        when(assetRepository.findByUserId(1L)).thenReturn(List.of(stock));
        when(stockQuoteTool.queryStockQuote("sh600036")).thenReturn("最新价:10.50");

        List<Asset> result = service.list(1L);

        assertEquals(1, result.size());
        // marketValue = 100 * 10.50 = 1050.00
        assertEquals(0, result.get(0).getMarketValue().compareTo(new BigDecimal("1050.00")));
        assertEquals(0, result.get(0).getCurrentPrice().compareTo(new BigDecimal("10.50")));
        // getLatestPriceFromHistory should NOT be called when real-time succeeds
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(BigDecimal.class), any());
    }

    @Test
    void list_stockRealTimeFails_dbHistoryHasData_returnsHistoryPrice() {
        Asset stock = asset("stock", "sh600036", "100", "10");
        stock.setId(1L);
        stock.setUserId(1L);
        stock.setMarketValue(null);
        stock.setPriceUpdatedAt(0L);
        when(assetRepository.findByUserId(1L)).thenReturn(List.of(stock));
        when(stockQuoteTool.queryStockQuote("sh600036")).thenReturn(null);
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any()))
                .thenReturn(new BigDecimal("9.00"));

        List<Asset> result = service.list(1L);

        assertEquals(1, result.size());
        // marketValue = 100 * 9.00 = 900.00
        assertEquals(0, result.get(0).getMarketValue().compareTo(new BigDecimal("900.00")));
    }

    @Test
    void list_stockRealTimeFails_dbHistoryEmpty_degradesToCostPrice() {
        Asset stock = asset("stock", "sh600036", "100", "10");
        stock.setId(1L);
        stock.setUserId(1L);
        stock.setMarketValue(null);
        stock.setPriceUpdatedAt(0L);
        when(assetRepository.findByUserId(1L)).thenReturn(List.of(stock));
        when(stockQuoteTool.queryStockQuote("sh600036")).thenReturn(null);
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any()))
                .thenThrow(new EmptyResultDataAccessException(1));

        List<Asset> result = service.list(1L);

        assertEquals(1, result.size());
        // marketValue = 100 * 10 (costPrice) = 1000
        assertEquals(0, result.get(0).getMarketValue().compareTo(new BigDecimal("1000")));
    }

    @Test
    void list_fundRealTimeFails_dbHistoryHasData_returnsHistoryNav() {
        Asset fund = asset("fund", "001001", "200", "1");
        fund.setId(2L);
        fund.setUserId(1L);
        fund.setMarketValue(null);
        fund.setPriceUpdatedAt(0L);
        when(assetRepository.findByUserId(1L)).thenReturn(List.of(fund));
        when(fundNavTool.queryFundNav("001001")).thenReturn(null);
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any()))
                .thenReturn(new BigDecimal("1.50"));

        List<Asset> result = service.list(1L);

        assertEquals(1, result.size());
        // marketValue = 200 * 1.50 = 300.00
        assertEquals(0, result.get(0).getMarketValue().compareTo(new BigDecimal("300.00")));
    }

    // ===== refreshAllMarketValues degradation tests =====

    @Test
    void refreshAllMarketValues_interfaceSuccess_updatesWithRealPrice() {
        Asset stock = asset("stock", "sh600036", "100", "10");
        stock.setId(1L);
        stock.setUserId(1L);
        when(assetRepository.findAllStockFundAssets()).thenReturn(List.of(stock));
        when(stockQuoteTool.queryStockQuote("sh600036")).thenReturn("最新价: 12.50 元");

        service.refreshAllMarketValues();

        // marketValue = 100 * 12.50 = 1250.0
        verify(assetRepository).updateMarketData(eq(1L),
                argThat(b -> b != null && b.compareTo(new BigDecimal("12.50")) == 0),
                argThat(b -> b != null && b.compareTo(new BigDecimal("1250.0")) == 0),
                anyLong());
        // savePriceSnapshot called → jdbcTemplate.update with INSERT SQL
        verify(jdbcTemplate).update(contains("INSERT INTO asset_price_history"),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void refreshAllMarketValues_interfaceFails_dbHistoryHasData_updatesWithHistoryPrice() {
        Asset stock = asset("stock", "sh600036", "100", "10");
        stock.setId(1L);
        stock.setUserId(1L);
        when(assetRepository.findAllStockFundAssets()).thenReturn(List.of(stock));
        when(stockQuoteTool.queryStockQuote("sh600036")).thenReturn(null);
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any()))
                .thenReturn(new BigDecimal("8.00"));

        service.refreshAllMarketValues();

        // marketValue = 100 * 8.00 = 800
        verify(assetRepository).updateMarketData(eq(1L),
                argThat(b -> b != null && b.compareTo(new BigDecimal("8.00")) == 0),
                argThat(b -> b != null && b.compareTo(new BigDecimal("800")) == 0),
                anyLong());
    }

    @Test
    void refreshAllMarketValues_interfaceFails_dbHistoryEmpty_degradesToCostPrice() {
        Asset stock = asset("stock", "sh600036", "100", "10");
        stock.setId(1L);
        stock.setUserId(1L);
        when(assetRepository.findAllStockFundAssets()).thenReturn(List.of(stock));
        when(stockQuoteTool.queryStockQuote("sh600036")).thenReturn(null);
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any()))
                .thenThrow(new EmptyResultDataAccessException(1));

        service.refreshAllMarketValues();

        // marketValue = 100 * 10 (costPrice) = 1000, price = null (degraded)
        verify(assetRepository).updateMarketData(eq(1L),
                isNull(),
                argThat(b -> b != null && b.compareTo(new BigDecimal("1000")) == 0),
                anyLong());
    }

    // ===== list snapshot test =====

    @Test
    void list_staleStockAsset_callsSavePriceSnapshot() {
        Asset stock = asset("stock", "sh600036", "100", "10");
        stock.setId(1L);
        stock.setUserId(1L);
        stock.setMarketValue(null);
        stock.setPriceUpdatedAt(0L);
        when(assetRepository.findByUserId(1L)).thenReturn(List.of(stock));
        when(stockQuoteTool.queryStockQuote("sh600036")).thenReturn("最新价: 12.50 元");

        service.list(1L);

        // savePriceSnapshot called → jdbcTemplate.update with INSERT SQL
        verify(jdbcTemplate).update(contains("INSERT INTO asset_price_history"),
                any(), any(), any(), any(), any(), any());
    }
}
