package com.finance.advisor.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.advisor.tool.finance.FundNavTool;
import com.finance.advisor.tool.finance.KlineFetcher;
import com.finance.advisor.tool.finance.StockQuoteTool;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 资产组合服务：CRUD + 汇总（含实时行情估算市值，失败降级成本价）。
 */
@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // 历史回填目标天数
    private static final int BACKFILL_DAYS = 30;
    // 兼容 ASCII冒号(:)/全角冒号(：)/任意空白，适配 mock 与真实 API 输出
    private static final Pattern STOCK_PRICE_PATTERN =
            Pattern.compile("最新价[\\s:\\uff1a]*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern FUND_NAV_PATTERN =
            Pattern.compile("最新净值[\\s:\\uff1a]*([0-9]+(?:\\.[0-9]+)?)");

    private final AssetRepository assetRepository;
    private final JdbcTemplate jdbcTemplate;
    private final StockQuoteTool stockQuoteTool;
    private final FundNavTool fundNavTool;
    private final AssetImportParser assetImportParser;
    // 用于回填基金历史净值（东方财富 lsjz 接口）
    private final RestTemplate fundHistoryRestTemplate;

    public PortfolioService(AssetRepository assetRepository, JdbcTemplate jdbcTemplate,
                            StockQuoteTool stockQuoteTool, FundNavTool fundNavTool,
                            AssetImportParser assetImportParser) {
        this.assetRepository = assetRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.stockQuoteTool = stockQuoteTool;
        this.fundNavTool = fundNavTool;
        this.assetImportParser = assetImportParser;
        this.fundHistoryRestTemplate = new RestTemplate();
        this.fundHistoryRestTemplate.getInterceptors().add((ClientHttpRequestInterceptor) (request, body, execution) -> {
            request.getHeaders().add("Referer", "https://fundf10.eastmoney.com/");
            request.getHeaders().add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            return execution.execute(request, body);
        });
    }

    @PostConstruct
    public void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS assets (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    type VARCHAR(16) NOT NULL,
                    symbol VARCHAR(32),
                    name VARCHAR(128),
                    amount NUMERIC(20,4),
                    cost_price NUMERIC(20,4),
                    buy_date DATE,
                    notes TEXT,
                    created_at BIGINT NOT NULL
                )
                """);
        jdbcTemplate.execute("ALTER TABLE assets ADD COLUMN IF NOT EXISTS current_price NUMERIC(20,4)");
        jdbcTemplate.execute("ALTER TABLE assets ADD COLUMN IF NOT EXISTS market_value NUMERIC(20,4)");
        jdbcTemplate.execute("ALTER TABLE assets ADD COLUMN IF NOT EXISTS price_updated_at BIGINT");
        // 资产历史净值快照表：记录每日行情快照，供前端折线图使用
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS asset_price_history (
                    id BIGSERIAL PRIMARY KEY,
                    asset_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    symbol VARCHAR(32),
                    snapshot_date DATE NOT NULL,
                    price NUMERIC(20,4),
                    market_value NUMERIC(20,4),
                    created_at BIGINT NOT NULL,
                    UNIQUE(asset_id, snapshot_date)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_asset_price_history_user ON asset_price_history(user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_asset_price_history_date ON asset_price_history(snapshot_date)");
        log.info("assets 表已就绪");
        log.info("asset_price_history 表已就绪");
    }

    public List<Asset> list(Long userId) {
        List<Asset> assets = assetRepository.findByUserId(userId);
        long startOfToday = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        for (Asset a : assets) {
            String type = a.getType();
            boolean isMarketAsset = "stock".equals(type) || "fund".equals(type);
            boolean needRefresh = isMarketAsset
                    && (a.getMarketValue() == null || a.getPriceUpdatedAt() == null || a.getPriceUpdatedAt() < startOfToday);
            if (needRefresh) {
                BigDecimal amount = nz(a.getAmount());
                BigDecimal unitPrice = estimateUnitPrice(a);
                BigDecimal marketValue = amount.multiply(unitPrice);
                assetRepository.updateMarketData(a.getId(), unitPrice, marketValue, System.currentTimeMillis());
                a.setMarketValue(marketValue);
                a.setCurrentPrice(unitPrice);
                a.setPriceUpdatedAt(System.currentTimeMillis());
                // 补全当日快照，消除当日快照缺失（基于 UNIQUE(asset_id, snapshot_date) 做 UPSERT）
                savePriceSnapshot(a, unitPrice, marketValue);
            } else if (!isMarketAsset) {
                // cash/deposit/bond 等无行情资产：market_value = cost，盈亏为 0
                BigDecimal amount = nz(a.getAmount());
                BigDecimal costPrice = a.getCostPrice() != null ? a.getCostPrice() : BigDecimal.ONE;
                BigDecimal cost = amount.multiply(costPrice);
                a.setMarketValue(cost);
                a.setCurrentPrice(costPrice);
            }
        }
        // 异步触发历史数据回填（历史不足 30 条时拉取行情 API），不阻塞主流程
        triggerBackfillAsync(assets);
        return assets;
    }

    public Asset create(Long userId, Asset asset) {
        asset.setId(null);
        asset.setUserId(userId);
        return assetRepository.save(asset);
    }

    public Asset update(Long userId, Asset asset) {
        if (asset.getId() == null || !assetRepository.existsByIdAndUserId(asset.getId(), userId)) {
            throw new IllegalArgumentException("资产不存在或无权操作");
        }
        asset.setUserId(userId);
        assetRepository.update(asset);
        return assetRepository.findById(asset.getId()).orElse(asset);
    }

    public void delete(Long userId, Long id) {
        if (!assetRepository.existsByIdAndUserId(id, userId)) {
            throw new IllegalArgumentException("资产不存在或无权操作");
        }
        assetRepository.deleteById(id);
    }

    /**
     * 刷新所有 stock/fund 资产的最新行情并回写 current_price/market_value/price_updated_at。
     * 在 ApplicationReadyEvent 时由 PortfolioPriceRefreshRunner 调用。
     */
    public void refreshAllMarketValues() {
        List<Asset> assets = assetRepository.findAllStockFundAssets();
        if (assets.isEmpty()) {
            log.info("[行情刷新] 无 stock/fund 资产，跳过刷新");
            return;
        }
        log.info("[行情刷新] 开始刷新 {} 条资产的最新行情", assets.size());
        int refreshed = 0, failed = 0;
        for (Asset asset : assets) {
            try {
                BigDecimal price = "stock".equals(asset.getType())
                        ? fetchStockPrice(asset.getSymbol())
                        : fetchFundNav(asset.getSymbol());
                BigDecimal amount = nz(asset.getAmount());
                if (price != null) {
                    BigDecimal marketValue = amount.multiply(price);
                    if (marketValue.compareTo(BigDecimal.ZERO) <= 0) {
                        // 价格异常导致市值 <= 0，不写入该价格，降级使用成本价
                        BigDecimal costPrice = asset.getCostPrice() != null ? asset.getCostPrice() : BigDecimal.ONE;
                        marketValue = amount.multiply(costPrice);
                        assetRepository.updateMarketData(asset.getId(), null, marketValue, System.currentTimeMillis());
                        savePriceSnapshot(asset, null, marketValue);
                        failed++;
                        log.warn("[行情刷新] 降级: id={}, symbol={}, price={} 导致 marketValue<=0，使用成本价计算市值",
                                asset.getId(), asset.getSymbol(), price);
                    } else {
                        assetRepository.updateMarketData(asset.getId(), price, marketValue, System.currentTimeMillis());
                        // 写入当日净值快照（同日重复刷新则更新）
                        savePriceSnapshot(asset, price, marketValue);
                        refreshed++;
                        log.info("[行情刷新] 成功: id={}, symbol={}, price={}, marketValue={}",
                                asset.getId(), asset.getSymbol(), price, marketValue);
                    }
                } else {
                    // 接口失败，先查 DB 历史兜底，再用成本价降级
                    BigDecimal histPrice = getLatestPriceFromHistory(asset.getId());
                    if (histPrice != null && histPrice.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal marketValue = amount.multiply(histPrice);
                        assetRepository.updateMarketData(asset.getId(), histPrice, marketValue, System.currentTimeMillis());
                        savePriceSnapshot(asset, histPrice, marketValue);
                        failed++;
                        log.warn("[行情刷新] 降级: id={}, symbol={}, 实时接口失败，使用历史价格计算市值 price={}",
                                asset.getId(), asset.getSymbol(), histPrice);
                    } else {
                        // 历史也无数据，降级：market_value = amount * cost_price
                        BigDecimal costPrice = asset.getCostPrice() != null ? asset.getCostPrice() : BigDecimal.ONE;
                        BigDecimal marketValue = amount.multiply(costPrice);
                        assetRepository.updateMarketData(asset.getId(), null, marketValue, System.currentTimeMillis());
                        // 降级场景同样写入快照（price 为空，仅记录市值），保证折线图连续
                        savePriceSnapshot(asset, null, marketValue);
                        failed++;
                        log.warn("[行情刷新] 降级: id={}, symbol={}, 无法获取行情且无历史数据，使用成本价计算市值",
                                asset.getId(), asset.getSymbol());
                    }
                }
            } catch (Exception e) {
                failed++;
                log.error("[行情刷新] 失败: id={}, symbol={}, error={}", asset.getId(), asset.getSymbol(), e.getMessage());
            }
            // 同步触发历史数据回填（本方法已在启动线程中执行，不阻塞用户请求）
            backfillPriceHistory(asset);
        }
        log.info("[行情刷新] 完成: 成功={}, 失败/降级={}, 总计={}", refreshed, failed, assets.size());
    }

    /**
     * 查询指定资产最近 N 天的历史净值快照，供单资产折线图使用。
     * 返回元素按 snapshot_date 升序排列，包含 date/price/marketValue 三个字段。
     */
    public List<Map<String, Object>> getPriceHistory(Long assetId, int days) {
        java.sql.Date startDate = java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(days));
        String sql = """
                SELECT snapshot_date, price, market_value
                FROM asset_price_history
                WHERE asset_id = ? AND snapshot_date >= ?
                ORDER BY snapshot_date ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", rs.getDate("snapshot_date").toString());
            row.put("price", rs.getBigDecimal("price"));
            row.put("marketValue", rs.getBigDecimal("market_value"));
            return row;
        }, assetId, startDate);
    }

    /**
     * 查询用户所有资产的每日汇总市值（按 snapshot_date 分组求和），供 Dashboard 折线图使用。
     * 返回元素按 snapshot_date 升序排列，包含 date/totalMarketValue 两个字段。
     */
    public List<Map<String, Object>> getPortfolioHistory(Long userId, int days) {
        java.sql.Date startDate = java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(days));
        String sql = """
                SELECT snapshot_date, SUM(market_value) AS total_market_value
                FROM asset_price_history
                WHERE user_id = ? AND snapshot_date >= ?
                GROUP BY snapshot_date
                ORDER BY snapshot_date ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", rs.getDate("snapshot_date").toString());
            row.put("totalMarketValue", rs.getBigDecimal("total_market_value"));
            return row;
        }, userId, startDate);
    }

    public PortfolioSummary summary(Long userId) {
        List<Asset> assets = assetRepository.findByUserId(userId);
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        Map<String, BigDecimal> costByType = new LinkedHashMap<>();
        Map<String, BigDecimal> marketValueByType = new LinkedHashMap<>();

        long startOfToday = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        for (Asset a : assets) {
            String type = a.getType() != null ? a.getType() : "other";
            BigDecimal amount = nz(a.getAmount());
            BigDecimal unitCost = a.getCostPrice() != null ? a.getCostPrice() : BigDecimal.ONE;
            BigDecimal cost = amount.multiply(unitCost);
            BigDecimal marketValue;
            // 优先使用存储的 market_value（当日内有效，且必须 > 0，否则降级实时计算）
            if (a.getMarketValue() != null && a.getMarketValue().compareTo(BigDecimal.ZERO) > 0
                    && a.getPriceUpdatedAt() != null && a.getPriceUpdatedAt() >= startOfToday) {
                marketValue = a.getMarketValue();
            } else {
                // 降级实时拉取
                marketValue = amount.multiply(estimateUnitPrice(a));
            }

            totalCost = totalCost.add(cost);
            totalMarketValue = totalMarketValue.add(marketValue);
            costByType.merge(type, cost, BigDecimal::add);
            marketValueByType.merge(type, marketValue, BigDecimal::add);
        }

        BigDecimal profitLoss = totalMarketValue.subtract(totalCost);
        List<PortfolioSummary.TypeBreakdown> breakdown = new ArrayList<>();
        for (String type : costByType.keySet()) {
            BigDecimal cost = costByType.get(type);
            BigDecimal mv = marketValueByType.get(type);
            BigDecimal percentage = totalMarketValue.compareTo(BigDecimal.ZERO) != 0
                    ? mv.multiply(HUNDRED).divide(totalMarketValue, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            breakdown.add(new PortfolioSummary.TypeBreakdown(type, cost, mv, percentage));
        }

        PortfolioSummary summary = new PortfolioSummary();
        summary.setTotalCost(totalCost);
        summary.setTotalMarketValue(totalMarketValue);
        summary.setProfitLoss(profitLoss);
        summary.setBreakdown(breakdown);
        return summary;
    }

    /**
     * 估算资产单位市值（每单位价格）。
     * 降级链：实时接口 → DB 历史最近一条 → 成本价。
     * stock: 实时股价；fund: 实时净值；其余类型: 成本价。
     */
    private BigDecimal estimateUnitPrice(Asset asset) {
        String type = asset.getType();
        if ("stock".equals(type)) {
            BigDecimal price = fetchStockPrice(asset.getSymbol());
            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                return price;
            }
            // 实时接口失败，查 DB 历史兜底
            BigDecimal histPrice = getLatestPriceFromHistory(asset.getId());
            if (histPrice != null && histPrice.compareTo(BigDecimal.ZERO) > 0) {
                log.info("[行情兜底] stock 实时接口失败，使用历史价格: assetId={}, symbol={}, price={}",
                        asset.getId(), asset.getSymbol(), histPrice);
                return histPrice;
            }
        } else if ("fund".equals(type)) {
            BigDecimal nav = fetchFundNav(asset.getSymbol());
            if (nav != null) {
                return nav;
            }
            // 实时接口失败，查 DB 历史兜底
            BigDecimal histNav = getLatestPriceFromHistory(asset.getId());
            if (histNav != null && histNav.compareTo(BigDecimal.ZERO) > 0) {
                log.info("[行情兜底] fund 实时接口失败，使用历史净值: assetId={}, symbol={}, nav={}",
                        asset.getId(), asset.getSymbol(), histNav);
                return histNav;
            }
        }
        return asset.getCostPrice() != null ? asset.getCostPrice() : BigDecimal.ONE;
    }

    /**
     * 从 asset_price_history 查询最近一条 price IS NOT NULL 的历史记录作为兜底。
     * 用于实时行情接口失败时的降级方案。
     *
     * @param assetId 资产 ID
     * @return 最近一条有效历史价格；无记录或查询异常返回 null
     */
    public BigDecimal getLatestPriceFromHistory(Long assetId) {
        if (assetId == null) {
            return null;
        }
        try {
            String sql = """
                    SELECT price FROM asset_price_history
                    WHERE asset_id = ? AND price IS NOT NULL
                    ORDER BY snapshot_date DESC
                    LIMIT 1
                    """;
            return jdbcTemplate.queryForObject(sql, BigDecimal.class, assetId);
        } catch (Exception e) {
            // 无历史记录或查询异常，返回 null 让调用方继续降级
            log.debug("[历史兜底] 查询历史价格失败: assetId={}, error={}", assetId, e.getMessage());
            return null;
        }
    }

    private BigDecimal fetchStockPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        try {
            String result = stockQuoteTool.queryStockQuote(symbol);
            if (result == null) {
                return null;
            }
            Matcher m = STOCK_PRICE_PATTERN.matcher(result);
            if (m.find()) {
                BigDecimal price = new BigDecimal(m.group(1));
                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("解析到非法价格(<=0), symbol={}, price={}, 降级使用成本价", symbol, price);
                    return null;
                }
                return price;
            }
            log.warn("无法从股票行情结果解析最新价, symbol={}, 降级使用成本价", symbol);
            return null;
        } catch (Exception e) {
            log.warn("查询股票实时行情失败, symbol={}, 降级使用成本价: {}", symbol, e.getMessage());
            return null;
        }
    }

    private BigDecimal fetchFundNav(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        try {
            String result = fundNavTool.queryFundNav(symbol);
            if (result == null) {
                return null;
            }
            Matcher m = FUND_NAV_PATTERN.matcher(result);
            if (m.find()) {
                return new BigDecimal(m.group(1));
            }
            log.warn("无法从基金净值结果解析最新净值, symbol={}, 降级使用成本价", symbol);
            return null;
        } catch (Exception e) {
            log.warn("查询基金净值失败, symbol={}, 降级使用成本价: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * 写入资产当日净值快照（同日重复刷新则更新，基于 UNIQUE(asset_id, snapshot_date) 做 UPSERT）。
     */
    private void savePriceSnapshot(Asset asset, BigDecimal price, BigDecimal marketValue) {
        try {
            String sql = """
                    INSERT INTO asset_price_history (asset_id, user_id, symbol, snapshot_date, price, market_value, created_at)
                    VALUES (?, ?, ?, CURRENT_DATE, ?, ?, ?)
                    ON CONFLICT (asset_id, snapshot_date) DO UPDATE SET
                        price = EXCLUDED.price,
                        market_value = EXCLUDED.market_value,
                        created_at = EXCLUDED.created_at
                    """;
            jdbcTemplate.update(sql,
                    asset.getId(),
                    asset.getUserId(),
                    asset.getSymbol(),
                    price,
                    marketValue,
                    System.currentTimeMillis());
        } catch (Exception e) {
            // 快照写入失败不应影响主流程
            log.warn("[行情快照] 写入失败: assetId={}, symbol={}, error={}",
                    asset.getId(), asset.getSymbol(), e.getMessage());
        }
    }

    /**
     * 异步触发历史数据回填，避免阻塞 list 请求。
     * 在新线程中逐个处理 stock/fund 资产，异常不影响主流程。
     */
    private void triggerBackfillAsync(List<Asset> assets) {
        if (assets == null || assets.isEmpty()) {
            return;
        }
        Thread t = new Thread(() -> {
            for (Asset a : assets) {
                String type = a.getType();
                if ("stock".equals(type) || "fund".equals(type)) {
                    try {
                        backfillPriceHistory(a);
                    } catch (Exception e) {
                        // 单个资产回填失败不影响其他资产
                        log.debug("[历史回填] 异步回填异常: assetId={}, error={}", a.getId(), e.getMessage());
                    }
                }
            }
        }, "price-history-backfill");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 历史净值回填：当 asset_price_history 记录不足 BACKFILL_DAYS 条时，
     * 调用行情 API 获取过去 30 天历史数据并写入。
     * 幂等：基于 UNIQUE(asset_id, snapshot_date) 做 UPSERT，多次调用不会产生重复数据。
     * 失败静默跳过，不影响主流程。
     */
    public void backfillPriceHistory(Asset asset) {
        if (asset == null || asset.getId() == null
                || asset.getSymbol() == null || asset.getSymbol().isBlank()) {
            return;
        }
        String type = asset.getType();
        if (!"stock".equals(type) && !"fund".equals(type)) {
            return;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM asset_price_history WHERE asset_id = ?",
                    Integer.class, asset.getId());
            if (count == null || count >= BACKFILL_DAYS) {
                return; // 历史数据充足或查询异常，无需回填
            }

            List<Map<String, Object>> historyData = "stock".equals(type)
                    ? fetchStockHistory(asset.getSymbol())
                    : fetchFundHistory(asset.getSymbol());

            if (historyData == null || historyData.isEmpty()) {
                return;
            }

            BigDecimal amount = nz(asset.getAmount());
            int inserted = 0;
            for (Map<String, Object> item : historyData) {
                Object dateObj = item.get("date");
                Object priceObj = item.get("price");
                if (dateObj == null || priceObj == null) {
                    continue;
                }
                try {
                    BigDecimal price = new BigDecimal(priceObj.toString());
                    BigDecimal marketValue = amount.multiply(price);
                    java.sql.Date snapshotDate = java.sql.Date.valueOf(dateObj.toString());

                    String sql = """
                            INSERT INTO asset_price_history (asset_id, user_id, symbol, snapshot_date, price, market_value, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (asset_id, snapshot_date) DO UPDATE SET
                                price = EXCLUDED.price,
                                market_value = EXCLUDED.market_value,
                                created_at = EXCLUDED.created_at
                            """;
                    jdbcTemplate.update(sql,
                            asset.getId(),
                            asset.getUserId(),
                            asset.getSymbol(),
                            snapshotDate,
                            price,
                            marketValue,
                            System.currentTimeMillis());
                    inserted++;
                } catch (Exception inner) {
                    // 单条写入失败跳过，继续处理下一条
                }
            }
            if (inserted > 0) {
                log.info("[历史回填] assetId={}, symbol={}, 类型={}, 写入 {} 条历史记录",
                        asset.getId(), asset.getSymbol(), type, inserted);
            }
        } catch (Exception e) {
            log.warn("[历史回填] 失败: assetId={}, symbol={}, error={}",
                    asset.getId(), asset.getSymbol(), e.getMessage());
        }
    }

    /**
     * 获取股票过去 30 个交易日的收盘价（复用 KlineFetcher）。
     * 返回元素含 date(yyyy-MM-dd) 和 price(收盘价)。
     */
    private List<Map<String, Object>> fetchStockHistory(String symbol) {
        try {
            KlineFetcher fetcher = new KlineFetcher();
            List<KlineFetcher.KlineData> klineData = fetcher.fetchKlineData(symbol, BACKFILL_DAYS);
            if (klineData == null || klineData.isEmpty()) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> result = new ArrayList<>(klineData.size());
            for (KlineFetcher.KlineData k : klineData) {
                // 日期格式可能为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss，取前10位
                String day = k.getDay().length() >= 10 ? k.getDay().substring(0, 10) : k.getDay();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("date", day);
                item.put("price", k.getClose());
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.warn("[历史回填] 获取股票K线失败: symbol={}, error={}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取基金过去 30 天的历史单位净值（东方财富 lsjz 接口，复用 FundNavTool 的请求方案）。
     * 返回元素含 date(yyyy-MM-dd) 和 price(单位净值)。
     */
    private List<Map<String, Object>> fetchFundHistory(String fundCode) {
        try {
            String url = "https://api.fund.eastmoney.com/f10/lsjz?"
                    + "callback=jQuery&fundCode=" + fundCode + "&pageIndex=1&pageSize=" + BACKFILL_DAYS;
            byte[] bytes = fundHistoryRestTemplate.getForObject(url, byte[].class);
            if (bytes == null || bytes.length == 0) {
                return Collections.emptyList();
            }
            String response = new String(bytes, StandardCharsets.UTF_8);
            // 解析 JSONP：jQuery({...})
            int start = response.indexOf('(');
            int end = response.lastIndexOf(')');
            if (start == -1 || end == -1 || end <= start) {
                return Collections.emptyList();
            }
            String jsonData = response.substring(start + 1, end);
            JsonNode root = MAPPER.readTree(jsonData);
            JsonNode listNode = root.path("Data").path("LSJZList");
            if (!listNode.isArray() || listNode.isEmpty()) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> result = new ArrayList<>(listNode.size());
            for (JsonNode node : listNode) {
                String fsrq = node.path("FSRQ").asText("");  // 净值日期 yyyy-MM-dd
                String dwjz = node.path("DWJZ").asText("");  // 单位净值
                if (fsrq.isBlank() || dwjz.isBlank()) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("date", fsrq);
                item.put("price", dwjz);
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.warn("[历史回填] 获取基金历史净值失败: fundCode={}, error={}", fundCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    public AssetImportResult importAssets(Long userId, MultipartFile file) {
        AssetImportResult result;
        try {
            result = assetImportParser.parse(file);
        } catch (IOException e) {
            throw new IllegalArgumentException("文件解析失败: " + e.getMessage());
        }
        int success = 0;
        for (Asset asset : result.getAssets()) {
            try {
                asset.setId(null);
                asset.setUserId(userId);
                assetRepository.save(asset);
                success++;
            } catch (Exception e) {
                result.getFailed().add(new AssetImportResult.FailedRow(result.getAssets().indexOf(asset) + 2, "入库失败: " + e.getMessage()));
            }
        }
        result.setSuccess(success);
        result.setAssets(null); // clear to avoid returning large payload
        return result;
    }
}
