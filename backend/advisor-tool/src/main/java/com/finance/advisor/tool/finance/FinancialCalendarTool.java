package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 财务报表/分红日历查询工具
 */
@Component
public class FinancialCalendarTool {

    @Tool(name = "financial_calendar",
          description = "查询上市公司财报发布、分红派息、除权除息等金融日历信息")
    public String queryCalendar(
            @ToolParam(description = "股票代码，如 sh600036（招商银行）", required = false) String stockCode,
            @ToolParam(description = "查询类型: earnings(财报), dividend(分红), all(全部)") String queryType) {

        StringBuilder result = new StringBuilder();

        if (stockCode != null && !stockCode.isBlank()) {
            result.append(String.format("""
                    金融日历查询 - %s
                    ─────────────────────
                    """, stockCode));

            result.append("近期关键日期(参考):\n");

            if ("earnings".equals(queryType) || "all".equals(queryType)) {
                result.append("""

                        📊 财报发布日历:
                        - 一季报: 每年4月底前
                        - 中报(半年报): 每年8月底前
                        - 三季报: 每年10月底前
                        - 年报: 次年4月底前
                        """);
            }

            if ("dividend".equals(queryType) || "all".equals(queryType)) {
                result.append("""

                        💰 分红派息日历:
                        - 股权登记日: 持有股票可获得分红的截止日期
                        - 除权除息日: 股价调整日，通常在登记日后1-2天
                        - 派息日: 分红到账日
                        """);
            }

            result.append("""

                    建议访问巨潮资讯网(cninfo.com.cn)或东方财富
                    (eastmoney.com)查询具体上市公司的精确日历日期。
                    """);
        } else {
            result.append("""
                    金融日历查询
                    ─────────────────────
                    
                    可查询内容:
                    1. 财报发布日 - 输入股票代码查询
                    2. 分红派息日 - 输入股票代码查询
                    3. 除权除息日 - 输入股票代码查询
                    
                    请提供股票代码以获取精确信息，例如:
                    - sh600036 (招商银行)
                    - sz000001 (平安银行)
                    - sh601398 (工商银行)
                    
                    通用规则:
                    - A股年报: 次年1月-4月发布
                    - A股一季报: 4月发布
                    - A股中报: 7月-8月发布
                    - A股三季报: 10月发布
                    - 分红通常在年报发布后1-3个月内实施
                    """);
        }

        return result.toString();
    }
}
