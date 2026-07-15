package com.finance.advisor.api;

import com.finance.advisor.common.dto.ApiResponse;
import com.finance.advisor.tool.finance.FinancialCalendarTool;
import com.finance.advisor.tool.finance.MarketSentimentTool;
import com.finance.advisor.tool.finance.StockQuoteTool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 市场行情 REST 接口：提供四大指数行情、市场情绪、金融日历查询。
 */
@RestController
@RequestMapping("/api/markets")
public class MarketController {

    // 四大指数代码与中文名
    private static final String[][] INDICES = {
            {"sh000001", "上证指数"},
            {"sz399001", "深证成指"},
            {"sz399006", "创业板指"},
            {"sh000688", "科创50"}
    };
    // 兼容 ASCII 冒号(:)/全角冒号(：)/空白
    private static final Pattern PRICE_PATTERN =
            Pattern.compile("最新价[\\s:\\uff1a]*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern CHANGE_PERCENT_PATTERN =
            Pattern.compile("涨跌幅[\\s:\\uff1a]*([+-]?[0-9]+(?:\\.[0-9]+)?)");

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketController.class);

    private final StockQuoteTool stockQuoteTool;
    private final MarketSentimentTool marketSentimentTool;
    private final FinancialCalendarTool financialCalendarTool;
    // 数据库降级持久化（指数行情快照）
    private final JdbcTemplate jdbcTemplate;
    // 东方财富指数行情专用客户端（深证指数更稳定）
    private final RestTemplate eastMoneyRestTemplate = new RestTemplate();

    public MarketController(StockQuoteTool stockQuoteTool,
                            MarketSentimentTool marketSentimentTool,
                            FinancialCalendarTool financialCalendarTool,
                            JdbcTemplate jdbcTemplate) {
        this.stockQuoteTool = stockQuoteTool;
        this.marketSentimentTool = marketSentimentTool;
        this.financialCalendarTool = financialCalendarTool;
        this.jdbcTemplate = jdbcTemplate;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS index_quote (
                    id BIGSERIAL PRIMARY KEY,
                    symbol VARCHAR(32) NOT NULL,
                    name VARCHAR(64),
                    price NUMERIC(20,4),
                    change_percent NUMERIC(10,4),
                    snapshot_date DATE NOT NULL,
                    created_at BIGINT NOT NULL,
                    UNIQUE(symbol, snapshot_date)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_index_quote_symbol ON index_quote(symbol)");
        log.info("index_quote 表已就绪");
    }

    /**
     * 查询四大指数（上证指数、深证成指、创业板指、科创50）实时点位与涨跌幅。
     * 优先使用东方财富 API 获取行情（对深证指数更稳定），失败时降级到 StockQuoteTool。
     */
    @GetMapping("/indices")
    public ApiResponse<List<Map<String, Object>>> indices() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String[] index : INDICES) {
            String symbol = index[0];
            String name = index[1];
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("symbol", symbol);

            // 优先使用东方财富 API
            Map<String, Object> eastMoneyData = fetchIndexFromEastMoney(symbol, name);
            if (eastMoneyData != null) {
                item.put("price", eastMoneyData.get("price"));
                item.put("changePercent", eastMoneyData.get("changePercent"));
            } else {
                // 降级到 StockQuoteTool（新浪接口）
                try {
                    String quote = stockQuoteTool.queryStockQuote(symbol);
                    item.put("price", parseDouble(quote, PRICE_PATTERN));
                    item.put("changePercent", parseDouble(quote, CHANGE_PERCENT_PATTERN));
                } catch (Exception e) {
                    item.put("price", null);
                    item.put("changePercent", null);
                }
            }

            // 判断是否成功获取到有效价格（非空且 > 0）
            Object priceObj = item.get("price");
            Double price = (priceObj instanceof Number) ? ((Number) priceObj).doubleValue() : null;
            Object changeObj = item.get("changePercent");
            Double changePercent = (changeObj instanceof Number) ? ((Number) changeObj).doubleValue() : null;

            if (price != null && price > 0) {
                // 行情获取成功，UPSERT 到数据库作为快照
                try {
                    upsertIndexQuote(symbol, name, price, changePercent != null ? changePercent : 0.0);
                } catch (Exception e) {
                    log.warn("[指数行情] 持久化失败 symbol={}, err={}", symbol, e.getMessage());
                }
            } else {
                // 两个 API 均失败，降级查询数据库历史最近记录
                try {
                    Map<String, Object> latest = getLatestIndexQuote(symbol);
                    if (latest != null) {
                        item.put("price", latest.get("price"));
                        item.put("changePercent", latest.get("change_percent"));
                    } else {
                        item.put("price", null);
                        item.put("changePercent", null);
                    }
                } catch (Exception e) {
                    log.warn("[指数行情] 数据库降级查询失败 symbol={}, err={}", symbol, e.getMessage());
                    item.put("price", null);
                    item.put("changePercent", null);
                }
            }
            result.add(item);
        }
        return ApiResponse.success(result);
    }

    /**
     * 将指数行情快照 UPSERT 到 index_quote 表（按 symbol + snapshot_date 唯一）。
     */
    private void upsertIndexQuote(String symbol, String name, double price, double changePercent) {
        String sql = """
                INSERT INTO index_quote (symbol, name, price, change_percent, snapshot_date, created_at)
                VALUES (?, ?, ?, ?, CURRENT_DATE, ?)
                ON CONFLICT (symbol, snapshot_date) DO UPDATE SET
                    name = EXCLUDED.name,
                    price = EXCLUDED.price,
                    change_percent = EXCLUDED.change_percent,
                    created_at = EXCLUDED.created_at
                """;
        jdbcTemplate.update(sql, symbol, name, price, changePercent, System.currentTimeMillis());
    }

    /**
     * 查询 index_quote 表中该 symbol 的最近一条有效记录（降级数据源）。
     *
     * @return 包含 price / change_percent 的 Map；无记录返回 null
     */
    private Map<String, Object> getLatestIndexQuote(String symbol) {
        String sql = """
                SELECT price, change_percent FROM index_quote
                WHERE symbol = ? AND price IS NOT NULL
                ORDER BY snapshot_date DESC
                LIMIT 1
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, symbol);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 通过东方财富 API 获取指数行情。
     * URL: https://push2.eastmoney.com/api/qt/stock/get?secid=<market>.<code>&fields=f43,f170
     *   - 上海指数（sh 开头）：secid 前缀 1
     *   - 深证指数（sz 开头）：secid 前缀 0
     * f43 字段为当前价 × 100，f170 字段为涨跌幅 × 100。
     *
     * @param symbol 指数代码，如 sh000001、sz399001
     * @param name   指数中文名
     * @return 包含 name/price/changePercent 的 Map；失败返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchIndexFromEastMoney(String symbol, String name) {
        try {
            // secid 前缀：sh 用 1，sz 用 0
            String prefix = symbol.startsWith("sh") ? "1" : "0";
            String code = symbol.substring(2);
            String secid = prefix + "." + code;
            String url = "https://push2.eastmoney.com/api/qt/stock/get?secid="
                    + secid + "&fields=f43,f170";

            Map<String, Object> response = eastMoneyRestTemplate.getForObject(url, Map.class);
            if (response == null) {
                return null;
            }
            Object dataObj = response.get("data");
            if (!(dataObj instanceof Map)) {
                return null;
            }
            Map<String, Object> data = (Map<String, Object>) dataObj;
            Object f43 = data.get("f43");
            Object f170 = data.get("f170");
            if (f43 == null || f170 == null) {
                return null;
            }
            double price = ((Number) f43).doubleValue() / 100.0;
            double changePercent = ((Number) f170).doubleValue() / 100.0;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("price", price);
            item.put("changePercent", changePercent);
            return item;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 查询 A 股市场情绪分析。
     */
    @GetMapping("/sentiment")
    public ApiResponse<String> sentiment() {
        return ApiResponse.success(marketSentimentTool.analyzeSentiment("a_stock", null, null, null, null));
    }

    /**
     * 查询本周金融日历（财报/分红等通用日历）。
     */
    @GetMapping("/calendar")
    public ApiResponse<String> calendar() {
        return ApiResponse.success(financialCalendarTool.queryCalendar(null, "all"));
    }

    private Double parseDouble(String text, Pattern pattern) {
        if (text == null) {
            return null;
        }
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
