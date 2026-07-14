package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 保险产品对比分析工具
 */
@Component
public class InsuranceCompareTool {

    @Tool(name = "insurance_compare",
          description = "保险产品对比分析：支持重疾险、医疗险、意外险、寿险的保障对比")
    public String compareInsurance(
            @ToolParam(description = "保险类型: critical_illness(重疾险), medical(医疗险), accident(意外险), life(寿险), pension(养老险)") String insuranceType,
            @ToolParam(description = "被保险人年龄") int age,
            @ToolParam(description = "性别: male/female", required = false) String gender,
            @ToolParam(description = "预算范围(元/年)，如 5000", required = false) Double budget) {

        StringBuilder result = new StringBuilder();
        result.append(String.format("""
                保险产品对比分析
                ─────────────────────
                类型: %s
                年龄: %d 岁
                """, getInsuranceTypeName(insuranceType), age));

        if (gender != null) {
            result.append(String.format("性别: %s\n", "male".equals(gender) ? "男" : "女"));
        }
        if (budget != null) {
            result.append(String.format("预算: %.0f 元/年\n", budget));
        }

        result.append("\n");

        switch (insuranceType) {
            case "critical_illness" -> result.append("""
                    重疾险对比要点:
                    ─────────────────────
                    保障期限: 定期(至70岁) vs 终身
                    赔付方式: 单次赔付 vs 多次赔付
                    保额建议: 年收入的3-5倍
                    轻症/中症: 是否包含及赔付比例
                    豁免条款: 被保人/投保人豁免
                    
                    参考价格(30岁男性, 50万保额):
                    - 超级玛丽系列: 约5000-8000元/年
                    - 达尔文系列: 约4500-7500元/年
                    - 康惠保系列: 约4000-7000元/年
                    """);
            case "medical" -> result.append("""
                    医疗险对比要点:
                    ─────────────────────
                    保障额度: 100万/200万/400万/600万
                    免赔额: 一般1万元
                    保证续保: 6年/20年/终身
                    社保内外: 是否覆盖自费药/进口药
                    增值服务: 就医绿通/费用垫付/质子重离子
                    
                    参考价格(30岁):
                    - 尊享e生系列: 约300-500元/年
                    - 好医保系列: 约200-400元/年
                    - 平安e生保: 约300-600元/年
                    """);
            case "accident" -> result.append("""
                    意外险对比要点:
                    ─────────────────────
                    意外身故/伤残保额: 50万/100万
                    意外医疗: 1万/3万/5万
                    意外住院津贴: 100-200元/天
                    交通意外额外赔付
                    猝死保障
                    
                    参考价格(100万保额):
                    - 大保镖系列: 约300元/年
                    - 小蜜蜂系列: 约300元/年
                    - 锦一卫系列: 约300元/年
                    """);
            case "life" -> result.append("""
                    寿险对比要点:
                    ─────────────────────
                    保障期限: 定期(10/20/30年) vs 终身
                    保额建议: 年收入的10倍+房贷+子女教育
                    免责条款: 通常3-7条
                    转换权: 是否可转终身寿险
                    
                    参考价格(30岁, 100万保额, 保30年):
                    - 定海柱系列: 约1000-1500元/年
                    - 大麦系列: 约1000-1500元/年
                    - 擎天柱系列: 约1200-1800元/年
                    """);
            case "pension" -> result.append("""
                    养老险对比要点:
                    ─────────────────────
                    领取方式: 一次性/按年/按月
                    保证领取: 20年/至终身
                    领取年龄: 55/60/65/70岁
                    现金价值: 退保时能拿回多少钱
                    万能账户: 是否搭配万能账户
                    
                    参考(30岁, 年交1万, 交20年):
                    - 60岁起每月约领取1500-2000元
                    - 保证领取20年
                    """);
            default -> result.append("请选择正确的保险类型。\n");
        }

        result.append("""

                投保建议:
                ─────────────────────
                - 先保障后理财，优先配置保障型保险
                - 保额充足原则: 重疾50万+, 医疗200万+
                - 保费控制在年收入的5%-10%
                - 选择信誉良好的保险公司
                - 仔细阅读免责条款和健康告知
                """);

        return result.toString();
    }

    private String getInsuranceTypeName(String type) {
        return switch (type) {
            case "critical_illness" -> "重疾险";
            case "medical" -> "医疗险";
            case "accident" -> "意外险";
            case "life" -> "寿险";
            case "pension" -> "养老险";
            default -> type;
        };
    }
}
