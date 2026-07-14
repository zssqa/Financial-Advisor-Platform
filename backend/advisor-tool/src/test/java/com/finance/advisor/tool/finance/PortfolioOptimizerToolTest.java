package com.finance.advisor.tool.finance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 投资组合优化工具测试
 */
class PortfolioOptimizerToolTest {

    private final PortfolioOptimizerTool tool = new PortfolioOptimizerTool();

    @Test
    void testOptimizePortfolio() {
        String result = tool.optimizePortfolio(
                "股票基金,债券基金,货币基金",
                "10,4,2",
                "20,5,1",
                "1,0.3,0.1,0.3,1,0.2,0.1,0.2,1");
        assertNotNull(result);
        assertTrue(result.contains("Markowitz"));
    }

    @Test
    void testOptimizePortfolioInvalidParams() {
        String result = tool.optimizePortfolio("A,B", "10,4,2", "20,5", "1,0.3,0.3,1");
        assertTrue(result.contains("参数数量不匹配"));
    }

    /** 验证最优权重满足约束：权重之和 ≈ 1，各权重 ≥ 0 */
    @Test
    void optimalWeights_sumToOneAndNonNegative() {
        String result = tool.optimizePortfolio(
                "股票,债券,现金",
                "10,4,2",
                "20,5,1",
                "1,0.3,0.1,0.3,1,0.2,0.1,0.2,1");

        assertTrue(result.contains("优化状态: 成功"), "优化应成功，实际输出: " + result);

        List<Double> weights = extractOptimalWeights(result);
        assertEquals(3, weights.size(), "应有 3 个资产权重");

        // 各权重 ≥ 0
        for (int i = 0; i < weights.size(); i++) {
            assertTrue(weights.get(i) >= -1e-6, "第 " + i + " 个权重为负: " + weights.get(i));
        }

        // 权重之和 ≈ 1
        double sum = weights.stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 1e-3, "权重之和应为 1，实际: " + sum);
    }

    /** 验证最优组合夏普比率严格大于等权重组合夏普比率 */
    @Test
    void optimalSharpe_beatsEqualWeightSharpe() {
        String result = tool.optimizePortfolio(
                "股票,债券,现金",
                "10,4,2",
                "20,5,1",
                "1,0.3,0.1,0.3,1,0.2,0.1,0.2,1");

        assertTrue(result.contains("优化状态: 成功"), "优化应成功，实际输出: " + result);

        double optSharpe = extractSharpeRatio(result, "最优组合绩效指标");
        double equalSharpe = extractSharpeRatio(result, "等权重基准");

        assertTrue(optSharpe > equalSharpe,
                "最优夏普比率 (" + optSharpe + ") 应大于等权重夏普比率 (" + equalSharpe + ")");
    }

    /** 单资产场景应返回 100% 权重 */
    @Test
    void singleAsset_returnsFullWeight() {
        String result = tool.optimizePortfolio(
                "股票", "10", "20", "1");

        List<Double> weights = extractOptimalWeights(result);
        assertEquals(1, weights.size(), "单资产应有 1 个权重");
        assertEquals(1.0, weights.get(0), 1e-3,
                "单资产权重应为 100%，实际: " + weights.get(0));
    }

    /**
     * 从输出中提取各资产最优权重（小数形式，如 0.4523）。
     * 权重位于 "各资产最优权重:" 与 "最优组合绩效指标:" 之间，每行格式 "  资产名: 45.23%"
     */
    private List<Double> extractOptimalWeights(String output) {
        List<Double> weights = new ArrayList<>();
        int start = output.indexOf("各资产最优权重:");
        int end = output.indexOf("最优组合绩效指标:");
        assertTrue(start >= 0 && end > start, "未找到权重区段");
        String section = output.substring(start, end);
        Matcher m = Pattern.compile(":\\s*(\\d+\\.\\d+)%").matcher(section);
        while (m.find()) {
            weights.add(Double.parseDouble(m.group(1)) / 100.0);
        }
        return weights;
    }

    /**
     * 从输出中提取夏普比率数值。
     *
     * @param sectionMarker 区段标记，如 "最优组合绩效指标" 或 "等权重基准"
     */
    private double extractSharpeRatio(String output, String sectionMarker) {
        int sectionStart = output.indexOf(sectionMarker);
        assertTrue(sectionStart >= 0, "未找到区段: " + sectionMarker);
        int sharpeIdx = output.indexOf("夏普比率:", sectionStart);
        assertTrue(sharpeIdx >= 0, "区段内未找到夏普比率: " + sectionMarker);
        Matcher m = Pattern.compile("夏普比率:\\s*([0-9.]+)").matcher(output.substring(sharpeIdx));
        assertTrue(m.find(), "无法解析夏普比率数值");
        return Double.parseDouble(m.group(1));
    }
}
