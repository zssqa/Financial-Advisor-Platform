package com.finance.advisor.portfolio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定时行情刷新任务：每个交易日 15:30 刷新所有资产行情，并在刷新完成后检查价格预警。
 *
 * 依赖 Task 8（行情刷新）+ Task 9（预警检查）。
 */
@Component
public class MarketDataRefreshTask {

    private static final Logger log = LoggerFactory.getLogger(MarketDataRefreshTask.class);

    private final PortfolioService portfolioService;
    private final PriceAlertService priceAlertService;

    public MarketDataRefreshTask(PortfolioService portfolioService, PriceAlertService priceAlertService) {
        this.portfolioService = portfolioService;
        this.priceAlertService = priceAlertService;
    }

    /**
     * 每个交易日（周一至周五）15:30 触发行情刷新，刷新完成后检查价格预警。
     */
    @Scheduled(cron = "0 30 15 * * MON-FRI")
    public void refreshMarketDataAndCheckAlerts() {
        log.info("[定时行情刷新] 开始: {}", LocalDateTime.now());
        long start = System.currentTimeMillis();

        // 1. 刷新所有 stock/fund 资产行情
        try {
            portfolioService.refreshAllMarketValues();
        } catch (Exception e) {
            log.error("[定时行情刷新] 行情刷新失败，继续执行预警检查", e);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[定时行情刷新] 完成, 耗时 {} ms", elapsed);

        // 2. 刷新完成后检查价格预警（Task 9 依赖 Task 8：在最新行情基础上判断是否触发）
        try {
            priceAlertService.checkAlerts();
        } catch (Exception e) {
            log.error("[定时行情刷新] 预警检查失败", e);
        }
    }
}
