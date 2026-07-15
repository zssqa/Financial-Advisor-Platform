package com.finance.advisor.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 系统启动后自动拉取四大指数行情并持久化到 index_quote 表，作为 DB 降级数据源。
 */
@Component
public class MarketIndexRefreshRunner {
    private static final Logger log = LoggerFactory.getLogger(MarketIndexRefreshRunner.class);
    private final MarketController marketController;

    public MarketIndexRefreshRunner(MarketController marketController) {
        this.marketController = marketController;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            log.info("[指数刷新] 启动时拉取四大指数行情...");
            marketController.indices();
            log.info("[指数刷新] 启动刷新完成");
        } catch (Exception e) {
            log.warn("[指数刷新] 启动刷新失败: {}", e.getMessage());
        }
    }
}
