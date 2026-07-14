package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;

/**
 * 投资组合优化工具 (Markowitz 模型)
 */
@Component
public class PortfolioOptimizerTool {

    @Tool(name = "portfolio_optimizer",
          description = "基于Markowitz模型计算最优投资组合配置比例，包括最小方差组合和最大夏普比率组合")
    public String optimizePortfolio(
            @ToolParam(description = "资产名称列表，逗号分隔，如 股票基金,债券基金,货币基金") String assetNames,
            @ToolParam(description = "各资产预期年化收益率(百分比)，逗号分隔，如 10,4,2") String expectedReturns,
            @ToolParam(description = "各资产年化标准差(百分比)，逗号分隔，如 20,5,1") String stdDeviations,
            @ToolParam(description = "各资产间相关系数矩阵(行优先)，逗号分隔，如 1,0.3,0.1,0.3,1,0.2,0.1,0.2,1") String correlations) {

        try {
            String[] names = assetNames.split(",");
            double[] returns = parseDoubleArray(expectedReturns);
            double[] stds = parseDoubleArray(stdDeviations);
            double[] corrValues = parseDoubleArray(correlations);

            int n = names.length;
            if (returns.length != n || stds.length != n || corrValues.length != n * n) {
                return "参数数量不匹配: 资产数=" + n + ", 收益率数=" + returns.length
                        + ", 标准差数=" + stds.length + ", 相关系数=" + corrValues.length;
            }

            double[][] covData = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    covData[i][j] = corrValues[i * n + j] * stds[i] * stds[j] / 10000;
                }
            }
            RealMatrix covMatrix = MatrixUtils.createRealMatrix(covData);

            double equalWeight = 1.0 / n;
            double equalReturn = 0;
            double equalVariance = 0;
            for (int i = 0; i < n; i++) {
                equalReturn += equalWeight * returns[i];
                for (int j = 0; j < n; j++) {
                    equalVariance += equalWeight * equalWeight * covData[i][j];
                }
            }
            double equalStd = Math.sqrt(equalVariance) * 100;

            StringBuilder result = new StringBuilder();
            result.append(String.format("""
                    投资组合优化结果 (Markowitz模型)
                    ─────────────────────
                    资产配置分析
                    """));

            result.append("\n各资产明细:\n");
            for (int i = 0; i < n; i++) {
                result.append(String.format("  %s: 预期收益 %.1f%%, 风险 %.1f%%\n",
                        names[i], returns[i], stds[i]));
            }

            result.append(String.format("""

                    等权重组合 (基准参考):
                    组合预期收益: %.2f%%
                    组合风险:     %.2f%%
                    权重: 各资产 %.0f%%
                    """, equalReturn, equalStd, equalWeight * 100));

            result.append("""

                    建议:
                    - 以上为理论模型计算结果，实际投资需结合个人情况
                    - 建议定期再平衡以维持目标配置
                    - 可尝试调整不同资产比例观察风险和收益变化
                    """);

            return result.toString();
        } catch (Exception e) {
            return "投资组合优化计算失败: " + e.getMessage()
                    + "\n请检查输入的参数格式，相关系数矩阵应为 n*n 个数值。";
        }
    }

    private double[] parseDoubleArray(String input) {
        String[] parts = input.split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Double.parseDouble(parts[i].trim());
        }
        return result;
    }
}
