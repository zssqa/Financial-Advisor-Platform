package com.finance.advisor.portfolio;

import com.finance.advisor.tool.finance.FundNavTool;
import com.finance.advisor.tool.finance.StockQuoteTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PortfolioPriceRefreshRunner 单元测试：验证启动刷新正确委托到 PortfolioService.refreshAllMarketValues()
 * 并最终调用 assetRepository.updateMarketData；以及服务异常时被 try/catch 捕获不向外传播。
 */
class PortfolioPriceRefreshRunnerTest {

    private AssetRepository assetRepository;
    private JdbcTemplate jdbcTemplate;
    private StockQuoteTool stockQuoteTool;
    private FundNavTool fundNavTool;
    private AssetImportParser assetImportParser;
    private PortfolioService portfolioService;
    private PortfolioPriceRefreshRunner runner;

    @BeforeEach
    void setUp() {
        assetRepository = mock(AssetRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        stockQuoteTool = mock(StockQuoteTool.class);
        fundNavTool = mock(FundNavTool.class);
        assetImportParser = mock(AssetImportParser.class);
        portfolioService = new PortfolioService(assetRepository, jdbcTemplate, stockQuoteTool, fundNavTool, assetImportParser);
        runner = new PortfolioPriceRefreshRunner(portfolioService);
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
    void onApplicationEvent_invokesRefreshAndCallsUpdateMarketData() {
        Asset stock = asset("stock", "sh600036", "100", "10");
        stock.setId(1L);
        when(assetRepository.findAllStockFundAssets()).thenReturn(List.of(stock));
        when(stockQuoteTool.queryStockQuote("sh600036")).thenReturn("最新价: 12.50 元");

        runner.onApplicationEvent(new ApplicationReadyEvent(
                new SpringApplication(PortfolioPriceRefreshRunnerTest.class), new String[0],
                mock(ConfigurableApplicationContext.class), Duration.ZERO));

        verify(assetRepository).updateMarketData(eq(1L), any(), any(), anyLong());
        verify(stockQuoteTool).queryStockQuote("sh600036");
    }

    @Test
    void onApplicationEvent_serviceThrows_doesNotPropagate() {
        when(assetRepository.findAllStockFundAssets()).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> runner.onApplicationEvent(new ApplicationReadyEvent(
                new SpringApplication(PortfolioPriceRefreshRunnerTest.class), new String[0],
                mock(ConfigurableApplicationContext.class), Duration.ZERO)));
    }
}
