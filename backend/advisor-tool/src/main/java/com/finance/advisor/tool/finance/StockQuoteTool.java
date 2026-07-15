package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 股票实时行情查询工具
 */
@Component
public class StockQuoteTool {

    private static final Charset GBK = Charset.forName("GBK");

    private final RestTemplate restTemplate;

    public StockQuoteTool() {
        this.restTemplate = new RestTemplate();
        this.restTemplate.getInterceptors().add((ClientHttpRequestInterceptor) (request, body, execution) -> {
            request.getHeaders().add("Referer", "https://finance.sina.com.cn");
            return execution.execute(request, body);
        });
    }

    @Tool(name = "query_stock_quote",
          description = "查询A股个股的实时行情，包括最新价、涨跌幅、成交量等")
    public String queryStockQuote(
            @ToolParam(description = "股票代码，格式：市场代码+代码，如 sh600036（招商银行）、sz000001（平安银行）") String stockCode) {
        try {
            String url = "https://hq.sinajs.cn/list=" + stockCode;
            // 新浪 API 返回 GBK 编码，需用 byte[] + GBK 解码
            byte[] bytes = restTemplate.getForObject(url, byte[].class);
            if (bytes == null || bytes.length == 0) {
                return "未查询到股票 " + stockCode + " 的行情数据";
            }
            String response = new String(bytes, GBK);

            String[] parts = response.split(",");
            if (parts.length < 30) {
                if (parts.length == 1) {
                    return "未查询到股票 " + stockCode + " 的行情数据";
                }
                return "股票代码 " + stockCode + " 数据不完整";
            }

            String name = parts[0].replace("var hq_str_" + stockCode + "=\"", "");
            double open = Double.parseDouble(parts[1]);
            double close = Double.parseDouble(parts[2]);
            double price = Double.parseDouble(parts[3]);
            if (price <= 0) {
                return "未查询到有效行情数据: " + stockCode;
            }
            double high = Double.parseDouble(parts[4]);
            double low = Double.parseDouble(parts[5]);
            long volume = Long.parseLong(parts[8]);
            double amount = Double.parseDouble(parts[9]) / 100000000;

            double changePercent = ((price - close) / close) * 100;
            String trend = changePercent >= 0 ? "+" : "";

            return String.format("""
                    股票行情 - %s (%s)
                    ─────────────────────
                    最新价:    %.2f 元
                    涨跌幅:    %s%.2f%%
                    昨收:      %.2f 元
                    今开:      %.2f 元
                    最高:      %.2f 元
                    最低:      %.2f 元
                    成交量:    %d 手
                    成交额:    %.2f 亿元
                    """, name, stockCode, price, trend, changePercent,
                    close, open, high, low, volume, amount);
        } catch (Exception e) {
            return "查询股票行情失败: " + e.getMessage();
        }
    }
}
