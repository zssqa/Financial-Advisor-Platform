package com.finance.advisor.api;

import com.finance.advisor.common.dto.ApiResponse;
import com.finance.advisor.portfolio.Asset;
import com.finance.advisor.portfolio.PortfolioService;
import com.finance.advisor.portfolio.SecurityUtil;
import com.finance.advisor.tool.finance.KlineChartTool;
import com.finance.advisor.tool.finance.PortfolioOptimizerTool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 投资分析 REST 接口：K 线图生成、组合优化、风险收益散点数据。
 */
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    // 各类资产的预期年化收益率(%)与年化波动率(%)假设：{收益率, 波动率}
    private static final Map<String, double[]> RETURN_ASSUMPTIONS = Map.of(
            "stock", new double[]{8.0, 20.0},
            "fund", new double[]{6.0, 15.0},
            "bond", new double[]{4.0, 5.0},
            "cash", new double[]{2.0, 0.0}
    );
    // 资产间默认相关系数（非对角线元素）
    private static final double DEFAULT_CORRELATION = 0.3;
    // 年化交易日数
    private static final int TRADING_DAYS = 252;

    private final KlineChartTool klineChartTool;
    private final PortfolioOptimizerTool portfolioOptimizerTool;
    private final PortfolioService portfolioService;

    public AnalysisController(KlineChartTool klineChartTool,
                              PortfolioOptimizerTool portfolioOptimizerTool,
                              PortfolioService portfolioService) {
        this.klineChartTool = klineChartTool;
        this.portfolioOptimizerTool = portfolioOptimizerTool;
        this.portfolioService = portfolioService;
    }

    /**
     * 生成指定标的 K 线图，返回工具输出（含生成的图片路径）。
     */
    @GetMapping("/kline")
    public ApiResponse<String> kline(@RequestParam String symbol,
                                     @RequestParam(defaultValue = "daily") String period,
                                     @RequestParam(defaultValue = "30") Integer days) {
        return ApiResponse.success(klineChartTool.generateKlineChart(symbol, period, days));
    }

    /**
     * 基于当前用户持仓，调用 Markowitz 模型求解最优配置权重。
     * 仅取 stock/fund 资产，按类型赋予预期收益率/波动率假设，相关系数矩阵对角线为 1、其余为默认值。
     */
    @GetMapping("/optimize")
    public ApiResponse<String> optimize() {
        Long userId = SecurityUtil.currentUserId();
        List<Asset> assets = portfolioService.list(userId);
        // 仅对有 symbol 的 stock/fund 资产进行优化
        List<Asset> targets = new ArrayList<>();
        for (Asset a : assets) {
            String type = a.getType();
            if (("stock".equals(type) || "fund".equals(type))
                    && a.getSymbol() != null && !a.getSymbol().isBlank()) {
                targets.add(a);
            }
        }
        if (targets.isEmpty()) {
            return ApiResponse.error(400, "当前持仓中无可用 stock/fund 资产，无法进行组合优化");
        }

        int n = targets.size();
        StringBuilder names = new StringBuilder();
        StringBuilder returns = new StringBuilder();
        StringBuilder stds = new StringBuilder();
        for (int i = 0; i < n; i++) {
            Asset a = targets.get(i);
            String displayName = (a.getName() != null && !a.getName().isBlank())
                    ? a.getName() : a.getSymbol();
            double[] assume = RETURN_ASSUMPTIONS.getOrDefault(a.getType(), new double[]{6.0, 15.0});
            if (i > 0) {
                names.append(",");
                returns.append(",");
                stds.append(",");
            }
            names.append(displayName);
            returns.append(format(assume[0]));
            stds.append(format(assume[1]));
        }
        // 相关系数矩阵（行优先），对角线为 1，其余取默认相关系数
        StringBuilder corr = new StringBuilder();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (!(i == 0 && j == 0)) {
                    corr.append(",");
                }
                corr.append(i == j ? "1" : format(DEFAULT_CORRELATION));
            }
        }

        String result = portfolioOptimizerTool.optimizePortfolio(
                names.toString(), returns.toString(), stds.toString(), corr.toString());
        return ApiResponse.success(result);
    }

    /**
     * 基于各资产 30 日历史净值计算年化收益率与年化波动率，返回散点图数据。
     * 返回元素结构：{name, annualReturn, annualVolatility, marketValue}
     */
    @GetMapping("/risk-return")
    public ApiResponse<List<Map<String, Object>>> riskReturn() {
        Long userId = SecurityUtil.currentUserId();
        List<Asset> assets = portfolioService.list(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Asset a : assets) {
            String type = a.getType();
            if (!"stock".equals(type) && !"fund".equals(type)) {
                continue;
            }
            Map<String, Object> point = new LinkedHashMap<>();
            String displayName = (a.getName() != null && !a.getName().isBlank())
                    ? a.getName() : a.getSymbol();
            point.put("name", displayName);
            point.put("marketValue", a.getMarketValue());

            List<Map<String, Object>> history = portfolioService.getPriceHistory(a.getId(), 30);
            double[] metrics = computeAnnualizedMetrics(history);
            point.put("annualReturn", round(metrics[0]));
            point.put("annualVolatility", round(metrics[1]));
            result.add(point);
        }
        return ApiResponse.success(result);
    }

    /**
     * 由历史净值序列计算年化收益率与年化波动率。
     *
     * @return [0]=年化收益率(日均×252)，[1]=年化波动率(日标准差×√252)
     */
    private double[] computeAnnualizedMetrics(List<Map<String, Object>> history) {
        if (history == null || history.size() < 2) {
            return new double[]{0.0, 0.0};
        }
        List<Double> prices = new ArrayList<>();
        for (Map<String, Object> row : history) {
            Object priceObj = row.get("price");
            if (priceObj instanceof Number num && num.doubleValue() > 0) {
                prices.add(num.doubleValue());
            }
        }
        if (prices.size() < 2) {
            return new double[]{0.0, 0.0};
        }
        // 日收益率序列
        List<Double> dailyReturns = new ArrayList<>(prices.size() - 1);
        for (int i = 1; i < prices.size(); i++) {
            dailyReturns.add((prices.get(i) - prices.get(i - 1)) / prices.get(i - 1));
        }
        double mean = mean(dailyReturns);
        double variance = variance(dailyReturns, mean);
        double annualReturn = mean * TRADING_DAYS;
        double annualVolatility = Math.sqrt(variance) * Math.sqrt(TRADING_DAYS);
        return new double[]{annualReturn, annualVolatility};
    }

    private double mean(List<Double> values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return values.isEmpty() ? 0 : sum / values.size();
    }

    private double variance(List<Double> values, double mean) {
        double sum = 0;
        for (double v : values) {
            sum += (v - mean) * (v - mean);
        }
        return values.isEmpty() ? 0 : sum / values.size();
    }

    private String format(double v) {
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    private double round(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
