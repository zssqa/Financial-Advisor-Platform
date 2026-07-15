package com.finance.advisor.api;

import com.finance.advisor.common.dto.ApiResponse;
import com.finance.advisor.tool.finance.CreditCardInstallmentTool;
import com.finance.advisor.tool.finance.ExchangeRateTool;
import com.finance.advisor.tool.finance.FundScreenerTool;
import com.finance.advisor.tool.finance.TaxCalculatorTool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具箱 REST 接口：个税计算、基金筛选、汇率换算、信用卡分期。
 */
@RestController
public class ToolController {

    private final TaxCalculatorTool taxCalculatorTool;
    private final FundScreenerTool fundScreenerTool;
    private final ExchangeRateTool exchangeRateTool;
    private final CreditCardInstallmentTool creditCardInstallmentTool;

    public ToolController(TaxCalculatorTool taxCalculatorTool,
                          FundScreenerTool fundScreenerTool,
                          ExchangeRateTool exchangeRateTool,
                          CreditCardInstallmentTool creditCardInstallmentTool) {
        this.taxCalculatorTool = taxCalculatorTool;
        this.fundScreenerTool = fundScreenerTool;
        this.exchangeRateTool = exchangeRateTool;
        this.creditCardInstallmentTool = creditCardInstallmentTool;
    }

    /**
     * 计算个人所得税（综合所得）。
     * 请求体：{annualIncome, socialInsurance, specialDeduction}
     * 工具的 specialDeduction 参数含义为"专项扣除总额（含社保公积金等）"，
     * 因此将社保与专项附加扣除合并后传入，收入类型按工资薪金处理。
     */
    @PostMapping("/api/tax-calculations")
    public ApiResponse<String> tax(@RequestBody TaxRequest request) {
        double deduction = nz(request.socialInsurance()) + nz(request.specialDeduction());
        String result = taxCalculatorTool.calculateTax(
                nz(request.annualIncome()), deduction, "salary");
        return ApiResponse.success(result);
    }

    /**
     * 基金筛选：返回结构化 JSON 数组。
     * 请求体：{fundType, minReturn, maxRisk, sortBy}
     */
    @PostMapping("/api/funds:screen")
    public ApiResponse<List<FundInfo>> fundScreener(@RequestBody FundScreenerRequest request) {
        String fundType = request.fundType() == null ? "equity" : request.fundType();
        String result = fundScreenerTool.screenFunds(
                fundType, request.minReturn(), null, null);
        List<FundInfo> funds = parseFundResult(result, fundType);
        return ApiResponse.success(funds);
    }

    /**
     * 解析 FundScreenerTool 返回的格式化文本，提取基金信息。
     * 文本格式示例：
     *   1. 易方达蓝筹精选 (005827) - 张坤管理, 规模大, 长期业绩稳定
     */
    private List<FundInfo> parseFundResult(String text, String fundType) {
        List<FundInfo> funds = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\d+\\.\\s+(.+?)\\s+\\((\\d+)\\)");
        Matcher matcher = pattern.matcher(text);
        String typeLabel = fundTypeLabel(fundType);
        String riskLevel = fundRiskLevel(fundType);
        double defaultReturn = fundDefaultReturn(fundType);

        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String code = matcher.group(2).trim();
            funds.add(new FundInfo(name, code, typeLabel, defaultReturn, riskLevel));
        }
        return funds;
    }

    private static String fundTypeLabel(String fundType) {
        return switch (fundType) {
            case "equity" -> "股票型";
            case "hybrid" -> "混合型";
            case "bond" -> "债券型";
            case "money" -> "货币型";
            case "index" -> "指数型";
            default -> fundType;
        };
    }

    private static String fundRiskLevel(String fundType) {
        return switch (fundType) {
            case "equity" -> "中高";
            case "hybrid" -> "中";
            case "bond" -> "中低";
            case "money" -> "低";
            case "index" -> "中高";
            default -> "中";
        };
    }

    private static double fundDefaultReturn(String fundType) {
        return switch (fundType) {
            case "equity" -> 12.5;
            case "hybrid" -> 8.0;
            case "bond" -> 4.5;
            case "money" -> 2.0;
            case "index" -> 10.0;
            default -> 0.0;
        };
    }

    /**
     * 实时汇率换算。
     * 将 ExchangeRateTool 返回的格式化文本解析为结构化 JSON 对象。
     */
    @GetMapping("/api/exchange-rates")
    public ApiResponse<ExchangeRateResponse> exchangeRate(@RequestParam("from") String from,
                                                          @RequestParam("to") String to,
                                                          @RequestParam(value = "amount", required = false) Double amount) {
        String text = exchangeRateTool.exchangeRate(from, to, amount);

        // 解析汇率行：汇率: 1 CNY = 0.1370 USD
        Pattern ratePattern = Pattern.compile("汇率:\\s*1\\s+(\\w+)\\s*=\\s*([\\d.]+)\\s+(\\w+)");
        Matcher rateMatcher = ratePattern.matcher(text);

        if (!rateMatcher.find()) {
            // 解析失败，返回错误信息
            return ApiResponse.error(500, text);
        }

        String fromCurrency = rateMatcher.group(1);
        double rate = Double.parseDouble(rateMatcher.group(2));
        String toCurrency = rateMatcher.group(3);

        Double convertedAmount = null;

        // 解析转换行（如果存在）：转换: 100.00 CNY = 13.70 USD
        Pattern convertPattern = Pattern.compile("转换:\\s*([\\d.]+)\\s+\\w+\\s*=\\s*([\\d.]+)\\s+\\w+");
        Matcher convertMatcher = convertPattern.matcher(text);

        if (convertMatcher.find()) {
            convertedAmount = Double.parseDouble(convertMatcher.group(2));
        }

        ExchangeRateResponse response = new ExchangeRateResponse(
                fromCurrency,
                toCurrency,
                rate,
                amount,
                convertedAmount
        );

        return ApiResponse.success(response);
    }

    /**
     * 信用卡分期计算。
     * 请求体：{totalAmount, months, annualRate}
     * 工具接收"每期手续费率(%)"，这里由年化利率反推：feeRate = annualRate * (months+1)/(months*24)
     * （与工具内部 annualRate = feeRate * months/(months+1) * 24 的公式互逆）。
     */
    @PostMapping("/api/installment-calculations")
    public ApiResponse<String> installment(@RequestBody InstallmentRequest request) {
        int months = request.months() == null ? 12 : request.months();
        double annualRate = nz(request.annualRate());
        double feeRate = months > 0 ? annualRate * (months + 1) / (months * 24) : 0.0;
        String result = creditCardInstallmentTool.calculateInstallment(
                nz(request.totalAmount()), months, feeRate);
        return ApiResponse.success(result);
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
    }

    /** 个税计算请求体 */
    public static record TaxRequest(Double annualIncome, Double socialInsurance, Double specialDeduction) {
    }

    /** 基金筛选请求体 */
    public static record FundScreenerRequest(String fundType, Double minReturn, Double maxRisk, String sortBy) {
    }

    /** 信用卡分期请求体 */
    public static record InstallmentRequest(Double totalAmount, Integer months, Double annualRate) {
    }

    /** 汇率换算响应体 */
    public static record ExchangeRateResponse(
            String from,
            String to,
            double rate,
            Double amount,
            Double convertedAmount
    ) {
    }

    /** 基金信息响应体 */
    public static record FundInfo(
            String name,
            String code,
            String type,
            double returnRate,
            String riskLevel
    ) {
    }
}
