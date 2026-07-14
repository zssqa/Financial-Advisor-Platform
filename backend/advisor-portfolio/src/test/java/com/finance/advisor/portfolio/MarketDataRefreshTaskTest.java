package com.finance.advisor.portfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MarketDataRefreshTask 单元测试：mock PortfolioService + PriceAlertService，
 * 验证定时任务先刷新行情、再检查预警的调用顺序与容错逻辑。
 */
class MarketDataRefreshTaskTest {

    private PortfolioService portfolioService;
    private PriceAlertService priceAlertService;
    private MarketDataRefreshTask task;

    @BeforeEach
    void setUp() {
        portfolioService = mock(PortfolioService.class);
        priceAlertService = mock(PriceAlertService.class);
        task = new MarketDataRefreshTask(portfolioService, priceAlertService);
    }

    /** 刷新完成后依次调用 refreshAllMarketValues 和 checkAlerts */
    @Test
    void refreshMarketData_callsRefreshThenCheckAlertsInOrder() {
        task.refreshMarketDataAndCheckAlerts();

        InOrder inOrder = inOrder(portfolioService, priceAlertService);
        inOrder.verify(portfolioService).refreshAllMarketValues();
        inOrder.verify(priceAlertService).checkAlerts();
    }

    /** 行情刷新抛异常时仍继续执行预警检查 */
    @Test
    void refreshMarketData_checkAlertsCalledEvenIfRefreshFails() {
        doThrow(new RuntimeException("行情 API 不可用"))
            .when(portfolioService).refreshAllMarketValues();

        task.refreshMarketDataAndCheckAlerts();

        verify(portfolioService).refreshAllMarketValues();
        verify(priceAlertService).checkAlerts();
    }

    /** 预警检查抛异常时不影响整体流程（不向上抛出） */
    @Test
    void refreshMarketData_doesNotPropagateCheckAlertsException() {
        doThrow(new RuntimeException("预警检查失败"))
            .when(priceAlertService).checkAlerts();

        assertDoesNotThrow(() -> task.refreshMarketDataAndCheckAlerts());
        verify(portfolioService).refreshAllMarketValues();
        verify(priceAlertService).checkAlerts();
    }
}
