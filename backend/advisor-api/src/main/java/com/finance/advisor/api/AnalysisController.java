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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 投资分析 REST 接口：K 线图生成、组合优化、风险收益散点数据。
 */
@RestController
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
     * 生成指定标的 K 线图，返回结构化 JSON（含图片 URL 与涨跌信息）。
     * 解析 KlineChartTool 输出的多行文本，提取图片文件名并构造 /charts/<filename> URL。
     */
    @GetMapping("/api/charts/kline")
    public ApiResponse<Map<String, Object>> kline(@RequestParam String symbol,
                                                  @RequestParam(defaultValue = "daily") String period,
                                                  @RequestParam(defaultValue = "30") Integer days) {
        String text = klineChartTool.generateKlineChart(symbol, period, days);

        // 工具返回失败文本（如"获取K线数据失败"或"生成K线图失败"），转为错误响应让前端走 catch 分支
        if (text == null || text.contains("失败")) {
            String msg = (text != null ? text.lines().findFirst().orElse("生成K线图失败") : "生成K线图失败");
            return ApiResponse.error(500, msg);
        }

        // 提取图片文件名：从"图片路径: C:\...\charts\sh600036_daily_20260715.png"提取最后一段文件名
        Pattern pathPattern = Pattern.compile("图片路径:\\s*.+[\\\\/](.+\\.png)");
        Matcher pathMatcher = pathPattern.matcher(text);
        if (!pathMatcher.find()) {
            return ApiResponse.error(500, "无法从K线图输出中提取图片路径");
        }
        String filename = pathMatcher.group(1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", "/charts/" + filename);
        result.put("symbol", symbol);
        result.put("period", period);
        result.put("days", days);

        // 区间涨跌: +1.23%
        Pattern changePattern = Pattern.compile("区间涨跌:\\s*([+-]?[\\d.]+)%");
        Matcher changeMatcher = changePattern.matcher(text);
        result.put("changePercent", changeMatcher.find()
                ? round(Double.parseDouble(changeMatcher.group(1))) : 0.0);

        // 起始收盘: 12.34 元
        Pattern startPattern = Pattern.compile("起始收盘:\\s*([\\d.]+)\\s*元");
        Matcher startMatcher = startPattern.matcher(text);
        result.put("startClose", startMatcher.find()
                ? round(Double.parseDouble(startMatcher.group(1))) : 0.0);

        // 最新收盘: 12.34 元
        Pattern latestPattern = Pattern.compile("最新收盘:\\s*([\\d.]+)\\s*元");
        Matcher latestMatcher = latestPattern.matcher(text);
        result.put("latestClose", latestMatcher.find()
                ? round(Double.parseDouble(latestMatcher.group(1))) : 0.0);

        return ApiResponse.success(result);
    }

    /**
     * 基于当前用户持仓，调用 Markowitz 模型求解最优配置权重。
     * 仅取 stock/fund 资产，按类型赋予预期收益率/波动率假设，相关系数矩阵对角线为 1、其余为默认值。
     * 返回结构化 JSON：{weights: {assetName: weight}, expectedReturn, volatility, sharpeRatio}
     */
    @GetMapping("/api/portfolios/optimization")
    public ApiResponse<Map<String, Object>> optimize() {
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
        List<String> displayNames = new ArrayList<>();
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
            displayNames.add(displayName);
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

        // 解析工具返回的文本，构建结构化 JSON
        Map<String, Object> structuredResult = parseOptimizationResult(result, displayNames);
        return ApiResponse.success(structuredResult);
    }

    /**
     * 解析 PortfolioOptimizerTool 返回的格式化文本，提取结构化数据。
     * 返回格式：{weights: {assetName: weight}, expectedReturn, volatility, sharpeRatio}
     */
    private Map<String, Object> parseOptimizationResult(String text, List<String> assetNames) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Double> weights = new LinkedHashMap<>();

        // 解析各资产最优权重 (格式: "  资产名: X.XX%")
        // 定位"各资产最优权重:"段落
        Pattern weightPattern = Pattern.compile("各资产最优权重:\\s*\\n((?:\\s+[^\\n]+\\n)*)");
        Matcher weightMatcher = weightPattern.matcher(text);
        if (weightMatcher.find()) {
            String weightSection = weightMatcher.group(1);
            Pattern linePattern = Pattern.compile("\\s+([^:]+):\\s+([\\d.]+)%");
            Matcher lineMatcher = linePattern.matcher(weightSection);
            while (lineMatcher.find()) {
                String assetName = lineMatcher.group(1).trim();
                double weightPct = Double.parseDouble(lineMatcher.group(2));
                weights.put(assetName, round(weightPct / 100.0, 4));
            }
        }

        // 如果解析失败，使用资产名称列表构建空权重
        if (weights.isEmpty()) {
            for (String name : assetNames) {
                weights.put(name, 0.0);
            }
        }
        result.put("weights", weights);

        // 解析最优组合绩效指标
        // 预期年化收益率 (格式: "预期年化收益率: X.XX%")
        Pattern returnPattern = Pattern.compile("最优组合绩效指标:[\\s\\S]*?预期年化收益率:\\s+([\\d.]+)%");
        Matcher returnMatcher = returnPattern.matcher(text);
        if (returnMatcher.find()) {
            double returnPct = Double.parseDouble(returnMatcher.group(1));
            result.put("expectedReturn", round(returnPct / 100.0, 4));
        } else {
            result.put("expectedReturn", 0.0);
        }

        // 预期年化波动率 (格式: "预期年化波动率: X.XX%")
        Pattern volPattern = Pattern.compile("最优组合绩效指标:[\\s\\S]*?预期年化波动率:\\s+([\\d.]+)%");
        Matcher volMatcher = volPattern.matcher(text);
        if (volMatcher.find()) {
            double volPct = Double.parseDouble(volMatcher.group(1));
            result.put("volatility", round(volPct / 100.0, 4));
        } else {
            result.put("volatility", 0.0);
        }

        // 夏普比率 (格式: "夏普比率: X.XXXX")
        Pattern sharpePattern = Pattern.compile("最优组合绩效指标:[\\s\\S]*?夏普比率:\\s+([\\d.]+)");
        Matcher sharpeMatcher = sharpePattern.matcher(text);
        if (sharpeMatcher.find()) {
            double sharpe = Double.parseDouble(sharpeMatcher.group(1));
            result.put("sharpeRatio", round(sharpe, 4));
        } else {
            result.put("sharpeRatio", 0.0);
        }

        return result;
    }

    /**
     * 基于各资产 30 日历史净值计算年化收益率与年化波动率，返回散点图数据。
     * 返回元素结构：{name, annualReturn, annualVolatility, marketValue}
     */
    @GetMapping("/api/portfolios/risk-return")
    public ApiResponse<List<Map<String, Object>>> riskReturn() {
        Long userId = SecurityUtil.currentUserId();
        List<Asset> assets = portfolioService.list(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Asset a : assets) {
            String type = a.getType();
            if (!"stock".equals(type) && !"fund".equals(type)) {
                continue;
            }
            List<Map<String, Object>> history = portfolioService.getPriceHistory(a.getId(), 30);
            double[] metrics = computeAnnualizedMetrics(history);
            if (metrics == null) {
                // 历史样本不足 7 条，跳过该资产，不加入散点图
                continue;
            }
            Map<String, Object> point = new LinkedHashMap<>();
            String displayName = (a.getName() != null && !a.getName().isBlank())
                    ? a.getName() : a.getSymbol();
            point.put("name", displayName);
            point.put("marketValue", a.getMarketValue());
            point.put("annualReturn", round(metrics[0]));
            point.put("annualVolatility", round(metrics[1]));
            result.add(point);
        }
        return ApiResponse.success(result);
    }

    /**
     * 由历史净值序列计算年化收益率与年化波动率。
     *
     * @return [0]=年化收益率(日均×252)，[1]=年化波动率(日标准差×√252)；
     *         历史样本不足 7 条时返回 null，调用方应跳过该资产。
     */
    private double[] computeAnnualizedMetrics(List<Map<String, Object>> history) {
        if (history == null || history.size() < 7) {
            return null;
        }
        List<Double> prices = new ArrayList<>();
        for (Map<String, Object> row : history) {
            Object priceObj = row.get("price");
            if (priceObj instanceof Number num && num.doubleValue() > 0) {
                prices.add(num.doubleValue());
            }
        }
        if (prices.size() < 7) {
            return null;
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

    private double round(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
