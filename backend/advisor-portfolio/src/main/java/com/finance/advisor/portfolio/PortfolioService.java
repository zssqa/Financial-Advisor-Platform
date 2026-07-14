package com.finance.advisor.portfolio;

import com.finance.advisor.tool.finance.FundNavTool;
import com.finance.advisor.tool.finance.StockQuoteTool;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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

    public PortfolioService(AssetRepository assetRepository, JdbcTemplate jdbcTemplate,
                            StockQuoteTool stockQuoteTool, FundNavTool fundNavTool,
                            AssetImportParser assetImportParser) {
        this.assetRepository = assetRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.stockQuoteTool = stockQuoteTool;
        this.fundNavTool = fundNavTool;
        this.assetImportParser = assetImportParser;
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
        log.info("assets 表已就绪");
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
            } else if (!isMarketAsset) {
                // cash/deposit/bond 等无行情资产：market_value = cost，盈亏为 0
                BigDecimal amount = nz(a.getAmount());
                BigDecimal costPrice = a.getCostPrice() != null ? a.getCostPrice() : BigDecimal.ONE;
                BigDecimal cost = amount.multiply(costPrice);
                a.setMarketValue(cost);
                a.setCurrentPrice(costPrice);
            }
        }
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
                    assetRepository.updateMarketData(asset.getId(), price, marketValue, System.currentTimeMillis());
                    refreshed++;
                    log.info("[行情刷新] 成功: id={}, symbol={}, price={}, marketValue={}",
                            asset.getId(), asset.getSymbol(), price, marketValue);
                } else {
                    // 降级：market_value = amount * cost_price
                    BigDecimal costPrice = asset.getCostPrice() != null ? asset.getCostPrice() : BigDecimal.ONE;
                    BigDecimal marketValue = amount.multiply(costPrice);
                    assetRepository.updateMarketData(asset.getId(), null, marketValue, System.currentTimeMillis());
                    failed++;
                    log.warn("[行情刷新] 降级: id={}, symbol={}, 无法获取行情，使用成本价计算市值", asset.getId(), asset.getSymbol());
                }
            } catch (Exception e) {
                failed++;
                log.error("[行情刷新] 失败: id={}, symbol={}, error={}", asset.getId(), asset.getSymbol(), e.getMessage());
            }
        }
        log.info("[行情刷新] 完成: 成功={}, 失败/降级={}, 总计={}", refreshed, failed, assets.size());
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
            // 优先使用存储的 market_value（当日内有效）
            if (a.getMarketValue() != null && a.getPriceUpdatedAt() != null && a.getPriceUpdatedAt() >= startOfToday) {
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
     * stock: 实时股价；fund: 实时净值（当前工具不可用则降级）；其余类型: 成本价。
     */
    private BigDecimal estimateUnitPrice(Asset asset) {
        String type = asset.getType();
        if ("stock".equals(type)) {
            BigDecimal price = fetchStockPrice(asset.getSymbol());
            if (price != null) {
                return price;
            }
        } else if ("fund".equals(type)) {
            BigDecimal nav = fetchFundNav(asset.getSymbol());
            if (nav != null) {
                return nav;
            }
        }
        return asset.getCostPrice() != null ? asset.getCostPrice() : BigDecimal.ONE;
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
                return new BigDecimal(m.group(1));
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
