package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 市场情绪指数分析工具
 */
@Component
public class MarketSentimentTool {

    @Tool(name = "market_sentiment",
          description = "分析当前市场情绪指数，综合成交量、涨跌比、估值水平等多维指标给出判断")
    public String analyzeSentiment(
            @ToolParam(description = "市场类型: a_stock( A股), hk_stock(港股), us_stock(美股), fund(基金)") String marketType,
            @ToolParam(description = "沪深300市盈率(PE)，如 12.5", required = false) Double peRatio,
            @ToolParam(description = "近5年PE历史百分位(0-100)，如 30 表示处于30%分位", required = false) Double pePercentile,
            @ToolParam(description = "两市成交量(亿元)，如 8000", required = false) Double volume,
            @ToolParam(description = "近10日涨跌比(上涨家数/下跌家数)，如 1.2", required = false) Double upDownRatio) {

        StringBuilder result = new StringBuilder();
        result.append(String.format("""
                市场情绪分析 - %s
                ─────────────────────
                """, getMarketName(marketType)));

        int score = 50;
        int maxScore = 100;

        if (peRatio != null && pePercentile != null) {
            result.append(String.format("市盈率(PE): %.1f\n", peRatio));
            result.append(String.format("PE历史分位: %.0f%%\n", pePercentile));

            if (pePercentile < 20) {
                score += 15;
                result.append("当前估值处于历史低位，具有较高安全边际\n");
            } else if (pePercentile < 40) {
                score += 8;
                result.append("当前估值偏低，有一定的投资价值\n");
            } else if (pePercentile < 60) {
                result.append("当前估值处于中等水平\n");
            } else if (pePercentile < 80) {
                score -= 8;
                result.append("当前估值偏高，需注意风险\n");
            } else {
                score -= 15;
                result.append("当前估值处于历史高位，警惕回调风险\n");
            }
        }

        if (volume != null) {
            result.append(String.format("两市成交量: %.0f 亿\n", volume));
            if (volume > 12000) {
                score += 10;
                result.append("成交活跃，市场参与度高\n");
            } else if (volume > 8000) {
                score += 5;
                result.append("成交正常\n");
            } else if (volume > 5000) {
                score -= 5;
                result.append("成交低迷，市场观望情绪浓\n");
            } else {
                score -= 10;
                result.append("成交极度萎缩，市场人气不足\n");
            }
        }

        if (upDownRatio != null) {
            result.append(String.format("近10日涨跌比: %.2f\n", upDownRatio));
            if (upDownRatio > 1.5) {
                score += 10;
                result.append("上涨家数明显多于下跌，市场强势\n");
            } else if (upDownRatio > 1.0) {
                score += 5;
                result.append("涨多跌少，市场偏强\n");
            } else if (upDownRatio > 0.7) {
                score -= 5;
                result.append("跌多涨少，市场偏弱\n");
            } else {
                score -= 10;
                result.append("普跌格局，市场弱势\n");
            }
        }

        score = Math.max(0, Math.min(maxScore, score));
        String sentiment;
        String suggestion;

        if (score >= 70) {
            sentiment = "乐观 (市场情绪高涨)";
            suggestion = "市场情绪乐观，但需警惕过热风险，建议控制仓位";
        } else if (score >= 55) {
            sentiment = "偏乐观 (市场情绪较好)";
            suggestion = "市场情绪积极，可适度参与，注意分散投资";
        } else if (score >= 45) {
            sentiment = "中性 (市场情绪平稳)";
            suggestion = "市场情绪中性，建议观望或定投，等待趋势明朗";
        } else if (score >= 30) {
            sentiment = "偏悲观 (市场情绪低迷)";
            suggestion = "市场情绪低迷，可关注超跌机会，分批建仓";
        } else {
            sentiment = "悲观 (市场情绪恐慌)";
            suggestion = "市场情绪恐慌，从历史看往往是中长期布局的较好时机";
        }

        result.append(String.format("""

                综合评分: %d/%d
                情绪判断: %s
                操作建议: %s
                """, score, maxScore, sentiment, suggestion));

        return result.toString();
    }

    private String getMarketName(String type) {
        return switch (type) {
            case "a_stock" -> "A股市场";
            case "hk_stock" -> "港股市场";
            case "us_stock" -> "美股市场";
            case "fund" -> "基金市场";
            default -> type;
        };
    }
}
