package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 实时汇率转换工具
 */
@Component
public class ExchangeRateTool {

    private final RestTemplate restTemplate = new RestTemplate();

    @Tool(name = "exchange_rate",
          description = "查询实时汇率并进行货币转换")
    public String exchangeRate(
            @ToolParam(description = "源货币代码，如 CNY、USD、EUR、HKD、JPY、GBP") String fromCurrency,
            @ToolParam(description = "目标货币代码") String toCurrency,
            @ToolParam(description = "转换金额（可选，不传则只查汇率）", required = false) Double amount) {
        try {
            String url = "https://api.exchangerate-api.com/v4/latest/" + fromCurrency.toUpperCase();
            Map response = restTemplate.getForObject(url, Map.class);

            if (response == null || !response.containsKey("rates")) {
                return "汇率查询失败";
            }

            Map<String, Object> rates = (Map<String, Object>) response.get("rates");
            Object rateObj = rates.get(toCurrency.toUpperCase());

            if (rateObj == null) {
                return "不支持 " + fromCurrency + " -> " + toCurrency + " 的汇率转换";
            }

            double rate = ((Number) rateObj).doubleValue();
            String result = String.format("""
                    实时汇率
                    ─────────────────────
                    %s → %s
                    汇率: 1 %s = %.4f %s
                    """, fromCurrency.toUpperCase(), toCurrency.toUpperCase(),
                    fromCurrency.toUpperCase(), rate, toCurrency.toUpperCase());

            if (amount != null && amount > 0) {
                double converted = amount * rate;
                result += String.format("""
                    转换: %.2f %s = %.2f %s
                    """, amount, fromCurrency.toUpperCase(), converted, toCurrency.toUpperCase());
            }

            return result;
        } catch (Exception e) {
            return "汇率查询失败: " + e.getMessage();
        }
    }
}
