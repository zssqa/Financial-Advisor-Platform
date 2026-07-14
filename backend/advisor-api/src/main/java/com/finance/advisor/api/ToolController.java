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

/**
 * 工具箱 REST 接口：个税计算、基金筛选、汇率换算、信用卡分期。
 */
@RestController
@RequestMapping("/api/tool")
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
    @PostMapping("/tax")
    public ApiResponse<String> tax(@RequestBody TaxRequest request) {
        double deduction = nz(request.socialInsurance()) + nz(request.specialDeduction());
        String result = taxCalculatorTool.calculateTax(
                nz(request.annualIncome()), deduction, "salary");
        return ApiResponse.success(result);
    }

    /**
     * 基金筛选。
     * 请求体：{fundType, minReturn, maxRisk, sortBy}
     * 工具支持按类型与近 1 年收益率下限筛选，maxRisk/sortBy 暂未在工具中实现。
     */
    @PostMapping("/fund-screener")
    public ApiResponse<String> fundScreener(@RequestBody FundScreenerRequest request) {
        String fundType = request.fundType() == null ? "equity" : request.fundType();
        String result = fundScreenerTool.screenFunds(
                fundType, request.minReturn(), null, null);
        return ApiResponse.success(result);
    }

    /**
     * 实时汇率换算。
     */
    @GetMapping("/exchange-rate")
    public ApiResponse<String> exchangeRate(@RequestParam("from") String from,
                                            @RequestParam("to") String to,
                                            @RequestParam(value = "amount", required = false) Double amount) {
        return ApiResponse.success(exchangeRateTool.exchangeRate(from, to, amount));
    }

    /**
     * 信用卡分期计算。
     * 请求体：{totalAmount, months, annualRate}
     * 工具接收"每期手续费率(%)"，这里由年化利率反推：feeRate = annualRate * (months+1)/(months*24)
     * （与工具内部 annualRate = feeRate * months/(months+1) * 24 的公式互逆）。
     */
    @PostMapping("/installment")
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
}
