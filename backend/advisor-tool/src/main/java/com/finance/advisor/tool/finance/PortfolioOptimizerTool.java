package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;

import java.util.Arrays;

/**
 * 投资组合优化工具 (Markowitz 均值-方差模型)
 *
 * 基于 commons-math3 实现真正的 Markowitz 最优权重求解:
 * - 构建协方差矩阵
 * - 使用 Nelder-Mead Simplex 优化器求解最大夏普比率组合
 * - 约束: 权重之和 = 1,各权重 >= 0 (不允许做空)
 * - 通过 softmax 参数变换实现约束
 */
@Component
public class PortfolioOptimizerTool {

    /** 无风险利率 (年化 2%) */
    private static final double RISK_FREE_RATE = 0.02;

    @Tool(name = "portfolio_optimizer",
          description = "基于Markowitz模型计算最优投资组合配置比例，包括最小方差组合和最大夏普比率组合")
    public String optimizePortfolio(
            @ToolParam(description = "资产名称列表，逗号分隔，如 股票基金,债券基金,货币基金") String assetNames,
            @ToolParam(description = "各资产预期年化收益率(百分比)，逗号分隔，如 10,4,2") String expectedReturns,
            @ToolParam(description = "各资产年化标准差(百分比)，逗号分隔，如 20,5,1") String stdDeviations,
            @ToolParam(description = "各资产间相关系数矩阵(行优先)，逗号分隔，如 1,0.3,0.1,0.3,1,0.2,0.1,0.2,1") String correlations) {

        try {
            String[] names = assetNames.split(",");
            double[] returnsPct = parseDoubleArray(expectedReturns);
            double[] stdsPct = parseDoubleArray(stdDeviations);
            double[] corrValues = parseDoubleArray(correlations);

            int n = names.length;
            if (returnsPct.length != n || stdsPct.length != n || corrValues.length != n * n) {
                return "参数数量不匹配: 资产数=" + n + ", 收益率数=" + returnsPct.length
                        + ", 标准差数=" + stdsPct.length + ", 相关系数=" + corrValues.length;
            }

            // 转换为小数形式 (10% -> 0.10)
            double[] returns = new double[n];
            double[] stds = new double[n];
            for (int i = 0; i < n; i++) {
                returns[i] = returnsPct[i] / 100.0;
                stds[i] = stdsPct[i] / 100.0;
            }

            // 构建协方差矩阵: cov(i,j) = corr(i,j) * std_i * std_j
            double[][] covData = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    covData[i][j] = corrValues[i * n + j] * stds[i] * stds[j];
                }
            }
            RealMatrix covMatrix = MatrixUtils.createRealMatrix(covData);

            // 等权重基准 (用于对照与降级)
            double[] equalWeights = new double[n];
            Arrays.fill(equalWeights, 1.0 / n);
            double[] equalMetrics = computePortfolioMetrics(equalWeights, returns, covData);

            // 求解最大夏普比率组合
            double[] optimalWeights;
            String optimizationStatus;
            try {
                optimalWeights = maximizeSharpeRatio(returns, covData, n);
                // 校验求解结果有效性: 权重和应为 1 且均为非负有限值
                double sum = 0;
                boolean valid = true;
                for (double w : optimalWeights) {
                    if (!Double.isFinite(w) || w < -1e-6) {
                        valid = false;
                        break;
                    }
                    sum += w;
                }
                if (!valid || Math.abs(sum - 1.0) > 1e-3) {
                    throw new RuntimeException("求解结果不满足约束条件");
                }
                // 归一化以消除微小数值误差
                for (int i = 0; i < n; i++) {
                    optimalWeights[i] = Math.max(0, optimalWeights[i]);
                }
                double s = 0;
                for (double w : optimalWeights) s += w;
                for (int i = 0; i < n; i++) optimalWeights[i] /= s;
                optimizationStatus = "成功";
            } catch (Exception e) {
                optimalWeights = equalWeights;
                optimizationStatus = "优化失败,返回等权重基准 (" + e.getMessage() + ")";
            }

            double[] optMetrics = computePortfolioMetrics(optimalWeights, returns, covData);

            StringBuilder result = new StringBuilder();
            result.append(String.format("""
                    投资组合优化结果 (Markowitz 均值-方差模型)
                    ─────────────────────────────────────
                    资产配置分析
                    """));

            result.append("\n各资产明细:\n");
            for (int i = 0; i < n; i++) {
                result.append(String.format("  %s: 预期收益 %.1f%%, 风险 %.1f%%\n",
                        names[i].trim(), returnsPct[i], stdsPct[i]));
            }

            result.append(String.format("""

                    最优组合 (最大夏普比率,无风险利率 %.1f%%):
                    优化状态: %s
                    """, RISK_FREE_RATE * 100, optimizationStatus));

            result.append("\n各资产最优权重:\n");
            for (int i = 0; i < n; i++) {
                result.append(String.format("  %s: %.2f%%\n", names[i].trim(), optimalWeights[i] * 100));
            }
            double weightSum = 0;
            for (double w : optimalWeights) weightSum += w;

            result.append(String.format("""

                    最优组合绩效指标:
                    预期年化收益率: %.2f%%
                    预期年化波动率: %.2f%%
                    夏普比率:       %.4f
                    权重合计:       %.4f
                    """,
                    optMetrics[0] * 100, optMetrics[1] * 100, optMetrics[2], weightSum));

            result.append(String.format("""

                    等权重基准 (参考):
                    预期年化收益率: %.2f%%
                    预期年化波动率: %.2f%%
                    夏普比率:       %.4f
                    """,
                    equalMetrics[0] * 100, equalMetrics[1] * 100, equalMetrics[2]));

            result.append("""

                    建议:
                    - 以上为 Markowitz 模型理论最优解,实际投资需结合个人风险偏好
                    - 建议定期再平衡以维持目标配置
                    - 夏普比率越高代表风险调整后收益越好
                    """);

            return result.toString();
        } catch (Exception e) {
            return "投资组合优化计算失败: " + e.getMessage()
                    + "\n请检查输入的参数格式,相关系数矩阵应为 n*n 个数值。";
        }
    }

    /**
     * 求解最大夏普比率组合 (Markowitz 切线组合)
     * 使用 Nelder-Mead Simplex 优化器,通过 softmax 参数变换实现约束:
     * - 权重 >= 0 (不允许做空)
     * - 权重之和 = 1 (满仓投资)
     *
     * 由于 Sharpe 比率是非凸比率函数,采用多个初始点提高全局收敛性。
     */
    private double[] maximizeSharpeRatio(double[] returns, double[][] covMatrix, int n) {
        // 目标函数: 最小化负的夏普比率 (即最大化夏普比率)
        MultivariateFunction negativeSharpe = point -> {
            double[] weights = softmax(point, n);
            double[] metrics = computePortfolioMetrics(weights, returns, covMatrix);
            // 波动率为 0 或非有限值时返回大惩罚,避免退化
            if (metrics[1] <= 1e-12 || !Double.isFinite(metrics[2])) {
                return 1e10;
            }
            return -metrics[2];
        };

        // 多个初始点提升收敛性 (Nelder-Mead 是局部优化器)
        double[][] initialPoints = generateInitialPoints(n);

        double bestNegSharpe = Double.POSITIVE_INFINITY;
        double[] bestWeights = null;

        for (double[] start : initialPoints) {
            try {
                SimplexOptimizer optimizer = new SimplexOptimizer(1e-10, 1e-12);
                PointValuePair result = optimizer.optimize(
                        new MaxEval(20000),
                        new ObjectiveFunction(negativeSharpe),
                        GoalType.MINIMIZE,
                        new InitialGuess(start),
                        new NelderMeadSimplex(n)
                );

                double value = result.getValue();
                if (value < bestNegSharpe) {
                    bestNegSharpe = value;
                    bestWeights = softmax(result.getPoint(), n);
                }
            } catch (Exception ignored) {
                // 单个初始点求解失败时继续尝试其他初始点
            }
        }

        if (bestWeights == null) {
            throw new RuntimeException("所有初始点求解均失败");
        }
        return bestWeights;
    }

    /**
     * softmax 变换: 将无约束变量映射为满足权重约束的向量
     * - 保证所有权重严格为正 (>= 0)
     * - 保证权重之和 = 1
     * 减去最大值以提高数值稳定性
     */
    private double[] softmax(double[] point, int n) {
        double[] weights = new double[n];
        double maxVal = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            if (point[i] > maxVal) maxVal = point[i];
        }
        double sum = 0;
        for (int i = 0; i < n; i++) {
            weights[i] = Math.exp(point[i] - maxVal);
            sum += weights[i];
        }
        for (int i = 0; i < n; i++) {
            weights[i] /= sum;
        }
        return weights;
    }

    /**
     * 生成多个初始点以提升 Nelder-Mead 全局收敛性
     * 包含等权重起点和偏向单个资产的起点
     */
    private double[][] generateInitialPoints(int n) {
        double[][] points = new double[n + 1][n];
        // 等权重起点 (softmax(全0) = 均匀分布)
        Arrays.fill(points[0], 0.0);
        // 偏向第 i 个资产的起点
        for (int i = 0; i < n; i++) {
            Arrays.fill(points[i + 1], 0.0);
            points[i + 1][i] = 2.0;
        }
        return points;
    }

    /**
     * 计算投资组合绩效指标
     * @param weights 各资产权重
     * @param returns 各资产预期收益率 (小数形式)
     * @param covMatrix 协方差矩阵
     * @return 数组 [0]=预期收益率, [1]=波动率(标准差), [2]=夏普比率
     */
    private double[] computePortfolioMetrics(double[] weights, double[] returns, double[][] covMatrix) {
        int n = weights.length;
        // 组合预期收益率 = Σ w_i * r_i
        double portfolioReturn = 0;
        for (int i = 0; i < n; i++) {
            portfolioReturn += weights[i] * returns[i];
        }

        // 组合方差 = ΣΣ w_i * w_j * cov(i,j)
        double portfolioVariance = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                portfolioVariance += weights[i] * weights[j] * covMatrix[i][j];
            }
        }
        double portfolioStd = Math.sqrt(Math.max(portfolioVariance, 0));

        // 夏普比率 = (预期收益 - 无风险利率) / 波动率
        double sharpeRatio = portfolioStd > 1e-12
                ? (portfolioReturn - RISK_FREE_RATE) / portfolioStd
                : 0;

        return new double[]{portfolioReturn, portfolioStd, sharpeRatio};
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
