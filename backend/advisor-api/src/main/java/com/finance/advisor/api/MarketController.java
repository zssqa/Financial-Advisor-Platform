package com.finance.advisor.api;

import com.finance.advisor.common.dto.ApiResponse;
import com.finance.advisor.tool.finance.FinancialCalendarTool;
import com.finance.advisor.tool.finance.MarketSentimentTool;
import com.finance.advisor.tool.finance.StockQuoteTool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
@RequestMapping("/api/market")
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

    private final StockQuoteTool stockQuoteTool;
    private final MarketSentimentTool marketSentimentTool;
    private final FinancialCalendarTool financialCalendarTool;

    public MarketController(StockQuoteTool stockQuoteTool,
                            MarketSentimentTool marketSentimentTool,
                            FinancialCalendarTool financialCalendarTool) {
        this.stockQuoteTool = stockQuoteTool;
        this.marketSentimentTool = marketSentimentTool;
        this.financialCalendarTool = financialCalendarTool;
    }

    /**
     * 查询四大指数（上证指数、深证成指、创业板指、科创50）实时点位与涨跌幅。
     * 逐个调用 StockQuoteTool 获取行情，解析最新价与涨跌幅后组装成列表返回。
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
            try {
                String quote = stockQuoteTool.queryStockQuote(symbol);
                item.put("price", parseDouble(quote, PRICE_PATTERN));
                item.put("changePercent", parseDouble(quote, CHANGE_PERCENT_PATTERN));
            } catch (Exception e) {
                item.put("price", null);
                item.put("changePercent", null);
            }
            result.add(item);
        }
        return ApiResponse.success(result);
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
