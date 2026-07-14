package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 基金筛选器工具
 */
@Component
public class FundScreenerTool {

    @Tool(name = "fund_screener",
          description = "基金筛选器：根据收益率、规模、费率等条件筛选推荐基金")
    public String screenFunds(
            @ToolParam(description = "基金类型: equity(股票型), hybrid(混合型), bond(债券型), money(货币型), index(指数型)") String fundType,
            @ToolParam(description = "近1年收益率下限(百分比)，如 10 表示10%", required = false) Double minReturn1y,
            @ToolParam(description = "基金规模下限(亿元)，如 10 表示10亿", required = false) Double minSize,
            @ToolParam(description = "管理费率上限(百分比)，如 1.5 表示1.5%", required = false) Double maxFee) {

        StringBuilder result = new StringBuilder();
        result.append(String.format("""
                基金筛选条件
                ─────────────────────
                基金类型:     %s
                """, fundType));

        if (minReturn1y != null) {
            result.append(String.format("近1年收益 >= %.1f%%\n", minReturn1y));
        }
        if (minSize != null) {
            result.append(String.format("基金规模 >= %.1f亿\n", minSize));
        }
        if (maxFee != null) {
            result.append(String.format("管理费率 <= %.2f%%\n", maxFee));
        }

        result.append("""

                筛选建议:
                ─────────────────────
                """);

        switch (fundType) {
            case "equity" -> result.append("""
                    推荐关注:
                    1. 易方达蓝筹精选 (005827) - 张坤管理, 规模大, 长期业绩稳定
                    2. 中欧医疗健康 (003095) - 葛兰管理, 专注医药赛道
                    3. 富国天惠成长 (161005) - 朱少醒管理, 15年+业绩优秀
                    """);
            case "hybrid" -> result.append("""
                    推荐关注:
                    1. 兴全合润 (163406) - 谢治宇管理, 回撤控制好
                    2. 交银新成长 (519736) - 王崇管理, 均衡配置
                    3. 景顺长城新兴成长 (260108) - 刘彦春管理
                    """);
            case "bond" -> result.append("""
                    推荐关注:
                    1. 招商产业债券 (217022) - 马龙管理, 长期业绩稳定
                    2. 易方达稳健收益 (110007) - 胡剑管理
                    3. 博时信用债券 (050011) - 过钧管理
                    """);
            case "index" -> result.append("""
                    推荐关注:
                    1. 沪深300ETF联接 (110020) - 跟踪沪深300指数
                    2. 中证500ETF联接 (160119) - 跟踪中证500指数
                    3. 科创50ETF联接 (011609) - 跟踪科创板
                    """);
            case "money" -> result.append("""
                    推荐关注:
                    1. 天弘余额宝 (000198) - 规模最大, 流动性好
                    2. 南方天天利A (003473) - 收益稳定
                    3. 易方达增金宝 (001287) - 费率低
                    """);
            default -> result.append("请选择正确的基金类型。\n");
        }

        result.append("""

                提示: 以上为参考列表，具体选择请参考最新基金评级
                和数据。建议通过天天基金网(eastmoney.com)查询最新信息。
                """);

        return result.toString();
    }
}
