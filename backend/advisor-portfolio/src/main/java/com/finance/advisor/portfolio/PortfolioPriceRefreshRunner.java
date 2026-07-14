package com.finance.advisor.portfolio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 系统启动后自动刷新所有资产的最新行情。
 */
@Component
public class PortfolioPriceRefreshRunner implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(PortfolioPriceRefreshRunner.class);

    private final PortfolioService portfolioService;

    public PortfolioPriceRefreshRunner(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("[启动刷新] 开始刷新资产组合行情数据...");
        long start = System.currentTimeMillis();
        try {
            portfolioService.refreshAllMarketValues();
        } catch (Exception e) {
            log.error("[启动刷新] 行情刷新失败，不影响系统启动", e);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("[启动刷新] 行情刷新完成，耗时 {} ms", elapsed);
    }
}
