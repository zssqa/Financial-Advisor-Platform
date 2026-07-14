package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 信用卡分期计算工具
 */
@Component
public class CreditCardInstallmentTool {

    @Tool(name = "credit_card_installment",
          description = "计算信用卡分期手续费和实际年化利率，支持对比不同期数")
    public String calculateInstallment(
            @ToolParam(description = "分期金额（元）") double amount,
            @ToolParam(description = "分期期数: 3, 6, 12, 24") int months,
            @ToolParam(description = "每期手续费率(百分比)，如 0.72 表示0.72%%") double feeRate) {
        double monthlyFee = amount * (feeRate / 100);
        double totalFee = monthlyFee * months;
        double annualRate = feeRate * months / (months + 1) * 24;

        return String.format("""
                信用卡分期计算结果
                ─────────────────────
                分期金额:     %.2f 元
                分期期数:     %d 期
                每期费率:     %.2f%%
                ─────────────────────
                每期手续费:   %.2f 元
                每期还款:     %.2f 元
                手续费总额:   %.2f 元
                实际年化利率: %.2f%%
                ─────────────────────
                建议:
                - 短期周转(3-6期)手续费较低，可考虑
                - 长期分期(12期以上)实际利率较高，建议谨慎
                - 如能全额还款，建议优先选择免息期全额还款
                """, amount, months, feeRate,
                monthlyFee, amount / months + monthlyFee, totalFee, annualRate);
    }
}
