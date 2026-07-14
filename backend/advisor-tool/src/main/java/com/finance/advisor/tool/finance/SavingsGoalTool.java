package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 储蓄目标规划工具
 */
@Component
public class SavingsGoalTool {

    @Tool(name = "savings_goal",
          description = "储蓄目标规划：根据目标金额、年利率和时间，反向计算每月需存入的金额")
    public String calculateSavingsPlan(
            @ToolParam(description = "目标金额（元）") double targetAmount,
            @ToolParam(description = "年利率(百分比)，如 3 表示年化3%") double annualRate,
            @ToolParam(description = "储蓄年限") int years,
            @ToolParam(description = "是否考虑通胀，默认false", required = false) boolean adjustInflation,
            @ToolParam(description = "通胀率(百分比)，如 2.5 表示2.5%", required = false) Double inflationRate) {

        int months = years * 12;
        double monthlyRate = annualRate / 100 / 12;

        double monthlySavings;
        if (monthlyRate > 0) {
            double factor = Math.pow(1 + monthlyRate, months);
            monthlySavings = targetAmount * monthlyRate * factor / (factor - 1);
        } else {
            monthlySavings = targetAmount / months;
        }

        double totalSavings = monthlySavings * months;
        double totalInterest = targetAmount - totalSavings;

        double realTarget = targetAmount;
        if (adjustInflation && inflationRate != null) {
            double inflationFactor = Math.pow(1 + inflationRate / 100, years);
            realTarget = targetAmount / inflationFactor;
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("""
                储蓄目标规划
                ─────────────────────
                目标金额:     %.2f 元
                储蓄年限:     %d 年 (%d 个月)
                年化利率:     %.2f%%
                """, targetAmount, years, months, annualRate));

        result.append(String.format("""
                ─────────────────────
                每月需存入:   %.2f 元
                总存入金额:   %.2f 元
                利息收益:     %.2f 元
                """, monthlySavings, totalSavings, totalInterest));

        if (adjustInflation && inflationRate != null) {
            double adjustedMonthly = monthlySavings;
            if (inflationRate > 0) {
                double realMonthlyRate = (annualRate - inflationRate) / 100 / 12;
                if (realMonthlyRate > 0) {
                    double realFactor = Math.pow(1 + realMonthlyRate, months);
                    adjustedMonthly = realTarget * realMonthlyRate * realFactor / (realFactor - 1);
                } else {
                    adjustedMonthly = realTarget / months;
                }
            }

            result.append(String.format("""
                    ─────────────────────
                    考虑通胀 (%.1f%%):
                    目标实际购买力: %.2f 元
                    实际月存需:     %.2f 元
                    """, inflationRate, realTarget, adjustedMonthly));
        }

        result.append("""
                
                建议:
                - 建议开通自动定投，确保每月按时存入
                - 可考虑货币基金/短债基金等低风险产品
                - 每半年复盘一次进度，根据利率变化调整
                """);
        if (years <= 3) {
            result.append("- 短期目标建议选择货币基金或银行理财\n");
        } else if (years <= 10) {
            result.append("- 中期目标建议搭配债券基金和混合基金\n");
        } else {
            result.append("- 长期目标建议定投指数基金，享受复利效应\n");
        }

        return result.toString();
    }
}
