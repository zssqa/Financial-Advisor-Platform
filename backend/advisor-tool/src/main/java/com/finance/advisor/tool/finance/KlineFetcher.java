package com.finance.advisor.tool.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * K线行情数据获取器
 * 调用新浪/东方财富行情API获取最近N个交易日的OHLC数据
 */
public class KlineFetcher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;

    public KlineFetcher() {
        this.restTemplate = new RestTemplate();
        // 设置 Referer 头，防止被行情API拒绝（复用 StockQuoteTool 的请求方案）
        this.restTemplate.getInterceptors().add((ClientHttpRequestInterceptor) (request, body, execution) -> {
            request.getHeaders().add("Referer", "https://finance.sina.com.cn");
            request.getHeaders().add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            return execution.execute(request, body);
        });
    }

    /**
     * K线数据条目（OHLC + 成交量）
     */
    public static class KlineData {
        private final String day;
        private final double open;
        private final double high;
        private final double low;
        private final double close;
        private final double volume;

        public KlineData(String day, double open, double high, double low, double close, double volume) {
            this.day = day;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }

        public String getDay() { return day; }
        public double getOpen() { return open; }
        public double getHigh() { return high; }
        public double getLow() { return low; }
        public double getClose() { return close; }
        public double getVolume() { return volume; }
    }

    /**
     * 获取K线数据，优先使用新浪API，失败时回退到东方财富API
     *
     * @param symbol  股票代码，如 sh600036、sz000001
     * @param datalen 数据条数
     * @return K线数据列表，按时间正序排列；失败时返回空列表
     */
    public List<KlineData> fetchKlineData(String symbol, int datalen) {
        try {
            List<KlineData> data = fetchFromSina(symbol, datalen);
            if (data != null && !data.isEmpty()) {
                return data;
            }
        } catch (Exception e) {
            // 新浪API调用失败，尝试回退方案
        }
        try {
            return fetchFromEastmoney(symbol, datalen);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 新浪K线API：返回JSON数组，元素含 day/open/high/low/close/volume
     * scale=240 表示日K（4小时=240分钟）
     */
    private List<KlineData> fetchFromSina(String symbol, int datalen) throws Exception {
        String url = String.format(
                "https://finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData?symbol=%s&scale=240&datalen=%d",
                symbol, datalen);
        byte[] bytes = restTemplate.getForObject(url, byte[].class);
        if (bytes == null || bytes.length == 0) {
            return Collections.emptyList();
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        JsonNode root = MAPPER.readTree(json);
        if (!root.isArray() || root.isEmpty()) {
            return Collections.emptyList();
        }
        List<KlineData> result = new ArrayList<>(root.size());
        for (JsonNode node : root) {
            String day = node.get("day").asText();
            double open = node.get("open").asDouble();
            double high = node.get("high").asDouble();
            double low = node.get("low").asDouble();
            double close = node.get("close").asDouble();
            double volume = node.get("volume").asDouble();
            result.add(new KlineData(day, open, high, low, close, volume));
        }
        return result;
    }

    /**
     * 东方财富K线API（备选）
     * klt=101 表示日K，fqt=0 表示不复权
     * 返回 data.klines 数组，每个元素是逗号分隔的字符串：日期,开盘,收盘,最高,最低,成交量,成交额,振幅
     * market=1（上海）、0（深圳）
     */
    private List<KlineData> fetchFromEastmoney(String symbol, int datalen) throws Exception {
        // 解析市场前缀和代码
        String prefix = symbol.substring(0, 2).toLowerCase();
        String code = symbol.substring(2);
        int market = prefix.equals("sh") ? 1 : 0;

        String url = String.format(
                "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=%d.%s&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58&klt=101&fqt=0&end=20500101&lmt=%d",
                market, code, datalen);
        byte[] bytes = restTemplate.getForObject(url, byte[].class);
        if (bytes == null || bytes.length == 0) {
            return Collections.emptyList();
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        JsonNode root = MAPPER.readTree(json);
        JsonNode klinesNode = root.path("data").path("klines");
        if (!klinesNode.isArray() || klinesNode.isEmpty()) {
            return Collections.emptyList();
        }
        List<KlineData> result = new ArrayList<>(klinesNode.size());
        for (JsonNode node : klinesNode) {
            // 格式：日期,开盘,收盘,最高,最低,成交量,成交额,振幅
            String[] parts = node.asText().split(",");
            if (parts.length < 6) {
                continue;
            }
            String day = parts[0];
            double open = Double.parseDouble(parts[1]);
            double close = Double.parseDouble(parts[2]);
            double high = Double.parseDouble(parts[3]);
            double low = Double.parseDouble(parts[4]);
            double volume = Double.parseDouble(parts[5]);
            result.add(new KlineData(day, open, high, low, close, volume));
        }
        return result;
    }
}
