package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 税务计算工具
 */
@Component
public class TaxCalculatorTool {

    @Tool(name = "tax_calculator",
          description = "计算个人所得税（综合所得）和投资收益的预估税费")
    public String calculateTax(
            @ToolParam(description = "税前年收入（元）") double annualIncome,
            @ToolParam(description = "专项扣除总额（元，含社保公积金等）", required = false) double specialDeduction,
            @ToolParam(description = "收入类型: salary(工资薪金), investment(投资收益), mixed(综合)") String incomeType) {

        if ("investment".equals(incomeType)) {
            return calculateInvestmentTax(annualIncome);
        }

        return calculateSalaryTax(annualIncome, specialDeduction > 0 ? specialDeduction : 60000);
    }

    private String calculateSalaryTax(double income, double deductions) {
        double taxable = Math.max(0, income - deductions);
        double tax = 0;

        double[][] brackets = {
            {36000, 0.03, 0},
            {144000, 0.10, 2520},
            {300000, 0.20, 16920},
            {420000, 0.25, 31920},
            {660000, 0.30, 52920},
            {960000, 0.35, 85920},
            {Double.MAX_VALUE, 0.45, 181920}
        };

        for (double[] bracket : brackets) {
            if (taxable <= bracket[0]) {
                tax = taxable * bracket[1] - bracket[2];
                break;
            }
        }

        double afterTax = income - tax;

        return String.format("""
                个人所得税计算结果（综合所得）
                ─────────────────────
                税前收入:     %.2f 元
                减除费用:     %.2f 元
                应纳税所得额: %.2f 元
                应缴税款:     %.2f 元
                税后收入:     %.2f 元
                实际税率:     %.2f%%
                """, income, deductions, taxable, tax, afterTax,
                income > 0 ? (tax / income) * 100 : 0);
    }

    private String calculateInvestmentTax(double profit) {
        double taxRate = 0.20;
        double tax = profit * taxRate;
        double afterTax = profit - tax;

        return String.format("""
                投资收益税费预估
                ─────────────────────
                投资收益:     %.2f 元
                适用税率:     %.0f%%
                预估税款:     %.2f 元
                税后收益:     %.2f 元
                """, profit, taxRate * 100, tax, afterTax);
    }
}
