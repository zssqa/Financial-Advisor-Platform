package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 风险承受能力评估问卷工具
 */
@Component
public class RiskQuestionnaireTool {

    @Tool(name = "risk_questionnaire",
          description = "评估用户的风险承受能力等级，返回 R1(保守) 到 R5(激进) 的风险等级和投资建议")
    public String assessRisk(
            @ToolParam(description = "年龄段: under25, 25-35, 36-50, 51-60, over60") String ageGroup,
            @ToolParam(description = "年收入范围（万元），如 10, 30, 50, 100") double annualIncome,
            @ToolParam(description = "投资经验: none, basic, intermediate, advanced, professional") String experience,
            @ToolParam(description = "可投资资产占收入比例(百分比)，如 20 表示20%") double investmentRatio,
            @ToolParam(description = "能接受的最大年度亏损百分比，如 10 表示10%") double maxLossAcceptance) {

        int score = 0;

        score += switch (ageGroup) {
            case "under25" -> 8;
            case "25-35" -> 7;
            case "36-50" -> 5;
            case "51-60" -> 3;
            case "over60" -> 1;
            default -> 3;
        };

        if (annualIncome >= 100) score += 10;
        else if (annualIncome >= 50) score += 7;
        else if (annualIncome >= 30) score += 5;
        else score += 3;

        score += switch (experience) {
            case "professional" -> 10;
            case "advanced" -> 8;
            case "intermediate" -> 5;
            case "basic" -> 3;
            default -> 1;
        };

        if (investmentRatio >= 50) score += 8;
        else if (investmentRatio >= 30) score += 6;
        else score += 3;

        if (maxLossAcceptance >= 30) score += 10;
        else if (maxLossAcceptance >= 20) score += 7;
        else if (maxLossAcceptance >= 10) score += 4;
        else score += 1;

        String level;
        String description;
        String suggestion;

        if (score >= 35) {
            level = "R5 - 激进型";
            description = "追求高收益，可承受较大波动";
            suggestion = "建议配置: 股票型基金 70% + 行业ETF 20% + 货币基金 10%";
        } else if (score >= 28) {
            level = "R4 - 进取型";
            description = "愿承担中等偏高风险获取较高收益";
            suggestion = "建议配置: 混合型基金 50% + 股票型基金 30% + 债券基金 20%";
        } else if (score >= 20) {
            level = "R3 - 平衡型";
            description = "风险和收益平衡，追求稳健增值";
            suggestion = "建议配置: 混合型基金 40% + 债券基金 40% + 货币基金 20%";
        } else if (score >= 12) {
            level = "R2 - 稳健型";
            description = "风险偏好低，注重本金安全";
            suggestion = "建议配置: 债券基金 50% + 理财产品 30% + 货币基金 20%";
        } else {
            level = "R1 - 保守型";
            description = "厌恶风险，追求稳定收益";
            suggestion = "建议配置: 定期存款 40% + 国债 30% + 货币基金 30%";
        }

        return String.format("""
                风险承受能力评估结果
                ─────────────────────
                综合评分: %d 分
                风险等级: %s
                特征描述: %s
                ─────────────────────
                投资建议:
                %s
                """, score, level, description, suggestion);
    }
}
