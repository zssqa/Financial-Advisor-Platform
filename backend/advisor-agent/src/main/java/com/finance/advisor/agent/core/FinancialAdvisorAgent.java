package com.finance.advisor.agent.core;

import com.finance.advisor.tool.FinancialTools;
import com.finance.advisor.tool.finance.RiskQuestionnaireTool;
import com.finance.advisor.portfolio.PortfolioService;
import com.finance.advisor.portfolio.PortfolioSummary;
import com.finance.advisor.portfolio.goal.GoalService;
import com.finance.advisor.portfolio.goal.GoalSummary;
import com.finance.advisor.portfolio.goal.Goal;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;

/**
 * 金融理财顾问 Agent (ReAct 模式)
 *
 * 采用 ReAct（Reasoning + Acting）模式，自动进行 思考->行动->观察 循环。
 *
 * 复用来源: spring-ai-alibaba-agent-framework ReactAgent (com.alibaba.cloud.ai.graph.agent.ReactAgent)
 * 复用来源: spring-ai-alibaba-graph-core BaseCheckpointSaver (由 CheckpointConfig 注入，支持 MemorySaver / PostgresSaver)
 */
@Component
public class FinancialAdvisorAgent {

    private static final Logger log = LoggerFactory.getLogger(FinancialAdvisorAgent.class);

    private final ReactAgent agent;
    private final PortfolioService portfolioService;
    private final GoalService goalService;

    public FinancialAdvisorAgent(
            ChatModel chatModel,
            FinancialTools financialTools,
            RiskQuestionnaireTool riskQuestionnaireTool,
            BaseCheckpointSaver checkpointSaver,
            PortfolioService portfolioService,
            GoalService goalService) {

        this.portfolioService = portfolioService;
        this.goalService = goalService;

        this.agent = ReactAgent.builder()
                .name("financial_advisor")
                .model(chatModel)
                .systemPrompt("""
                        你是一位专业的金融理财顾问，精通个人理财、投资规划、风险评估和金融产品分析。

                        当前时间：2026年7月

                        核心原则：
                        - 始终以客户利益为先，提供客观、中立的建议
                        - 明确说明投资有风险，不承诺收益
                        - 给出的建议应基于数据和事实
                        - 对于时效性问题，必须使用联网搜索获取最新信息

                        你可以使用的工具：
                        1. tavily_web_search - 搜索互联网获取最新的财经资讯、市场行情和新闻（2026年最新数据）
                        2. calculate_compound_interest - 计算复利投资收益
                        3. calculate_loan_interest - 计算贷款月供和利息
                        4. search_research_reports - 从内部知识库搜索金融研报和专业分析文档
                        5. risk_questionnaire - 通过问卷评估用户的风险承受能力，在用户未明确风险等级时主动调用

                        风险约束：
                        - 必须根据用户的风险等级（R1~R5）调整投资建议
                        - R1保守型：仅推荐存款、国债、货币基金等低风险产品
                        - R2稳健型：可推荐债券基金、稳健型理财产品
                        - R3平衡型：可推荐混合型基金、指数定投
                        - R4进取型：可推荐股票型基金、成长股
                        - R5激进型：可推荐股票、期货等高风险产品
                        - 不得推荐超出用户风险等级的产品

                        回答要求：
                        - 先分析用户需求，再决定使用哪些工具
                        - 对于需要计算的问题，务必使用计算工具
                        - 对于涉及当前市场、价格、政策等时效性问题，务必使用 tavily_web_search 获取2026年最新信息
                        - 搜索结果中如果标注了日期，请以标注日期为准判断时效性
                        - 严禁将2024年或更早的数据当作2026年数据来使用。如果搜索结果中只有旧数据，必须明确告知用户"当前未找到2026年最新数据"，不得用旧数据冒充新数据
                        - 回答中引用数据时，必须标注数据来源和日期
                        - 给出清晰的结论和可操作的建议

                        个性化建议要求：
                        - 当上下文中提供了"用户财务画像"时，必须基于用户的实际持仓和理财目标给出针对性建议
                        - 回答中应引用用户的真实数据（持仓金额、配置比例、目标进度等）
                        - 不得无视用户画像给出泛泛建议
                        """)
                .methodTools(financialTools, riskQuestionnaireTool)
                .saver(checkpointSaver)
                .enableLogging(true)
                .build();

        // 诊断：检查工具是否注册成功
        log.info("[DIAG] ReactAgent 构建完成, name=financial_advisor");
        log.info("[DIAG] ChatModel 类型: {}", chatModel.getClass().getName());
        log.info("[DIAG] FinancialTools 类型: {}", financialTools.getClass().getName());
        log.info("[DIAG] RiskQuestionnaireTool 类型: {}", riskQuestionnaireTool.getClass().getName());
        log.info("[DIAG] CheckpointSaver 类型: {}", checkpointSaver.getClass().getName());
    }

    /**
     * 非流式调用
     */
    public AssistantMessage call(String message) {
        try {
            return agent.call(message);
        } catch (GraphRunnerException e) {
            throw new RuntimeException("Agent调用失败", e);
        }
    }

    /**
     * 非流式调用（携带风险等级）
     */
    public AssistantMessage call(String message, String riskLevel) {
        try {
            String enhancedMessage = buildRiskAwareMessage(message, riskLevel);
            return agent.call(enhancedMessage);
        } catch (GraphRunnerException e) {
            throw new RuntimeException("Agent调用失败", e);
        }
    }

    /**
     * 流式调用，用于 SSE 推送
     */
    public Flux<NodeOutput> stream(String message) {
        try {
            return agent.stream(message);
        } catch (GraphRunnerException e) {
            return Flux.error(new RuntimeException("Agent流式调用失败", e));
        }
    }

    /**
     * 流式调用（携带风险等级），用于 SSE 推送
     */
    public Flux<NodeOutput> stream(String message, String riskLevel) {
        try {
            String enhancedMessage = buildRiskAwareMessage(message, riskLevel);
            return agent.stream(enhancedMessage);
        } catch (GraphRunnerException e) {
            return Flux.error(new RuntimeException("Agent流式调用失败", e));
        }
    }

    /**
     * 非流式调用（携带用户画像 + 风险等级）
     */
    public AssistantMessage call(String message, Long userId, String riskLevel) {
        try {
            String enhancedMessage = buildPersonalizedMessage(message, userId, riskLevel);
            return agent.call(enhancedMessage);
        } catch (GraphRunnerException e) {
            throw new RuntimeException("Agent调用失败", e);
        }
    }

    /**
     * 流式调用（携带用户画像 + 风险等级），用于 SSE 推送
     */
    public Flux<NodeOutput> stream(String message, Long userId, String riskLevel) {
        try {
            String enhancedMessage = buildPersonalizedMessage(message, userId, riskLevel);
            return agent.stream(enhancedMessage);
        } catch (GraphRunnerException e) {
            return Flux.error(new RuntimeException("Agent流式调用失败", e));
        }
    }

    /**
     * 构建个性化消息：用户财务画像前缀 + 风险约束前缀 + 原始 message。
     * 当 userId 为空时不拼接画像；任何 summary 调用失败或为空时降级跳过对应部分，
     * 绝不抛出异常到主流程。
     */
    private String buildPersonalizedMessage(String message, Long userId, String riskLevel) {
        String riskAwareMessage = buildRiskAwareMessage(message, riskLevel);
        if (userId == null) {
            return riskAwareMessage;
        }

        StringBuilder portrait = new StringBuilder();
        boolean headerAdded = false;

        // 资产组合画像（失败/空降级跳过）
        try {
            PortfolioSummary ps = portfolioService.summary(userId);
            if (ps != null && ps.getBreakdown() != null && !ps.getBreakdown().isEmpty()) {
                portrait.append("[用户财务画像]\n");
                headerAdded = true;
                portrait.append("当前持仓总成本：").append(num(ps.getTotalCost())).append(" 元，")
                        .append("估算市值：").append(num(ps.getTotalMarketValue())).append(" 元，")
                        .append("累计盈亏：").append(num(ps.getProfitLoss())).append(" 元。\n");
                portrait.append("资产配置：");
                List<PortfolioSummary.TypeBreakdown> breakdown = ps.getBreakdown();
                for (int i = 0; i < breakdown.size(); i++) {
                    PortfolioSummary.TypeBreakdown t = breakdown.get(i);
                    if (i > 0) {
                        portrait.append("、");
                    }
                    portrait.append(portfolioTypeLabel(t.getType()))
                            .append(" ").append(num(t.getPercentage())).append("%（")
                            .append(num(t.getMarketValue())).append("元）");
                }
                portrait.append("。\n");
            }
        } catch (Exception e) {
            log.warn("获取用户资产组合画像失败, userId={}, 降级跳过该部分: {}", userId, e.getMessage());
        }

        // 理财目标画像（失败/空降级跳过）
        try {
            GoalSummary gs = goalService.summary(userId);
            if (gs != null && gs.getGoals() != null && !gs.getGoals().isEmpty()) {
                if (!headerAdded) {
                    portrait.append("[用户财务画像]\n");
                    headerAdded = true;
                }
                portrait.append("理财目标：\n");
                for (GoalSummary.GoalProgress gp : gs.getGoals()) {
                    Goal g = gp.getGoal();
                    portrait.append("- ").append(goalTypeLabel(g != null ? g.getType() : null))
                            .append("：目标 ").append(num(g != null ? g.getTargetAmount() : null)).append(" 元，")
                            .append("已达成 ").append(num(gp.getProgressPercent())).append("%（")
                            .append(num(g != null ? g.getCurrentAmount() : null)).append("元），")
                            .append("还需每月储蓄 ").append(num(gp.getMonthlyNeeded())).append(" 元。\n");
                }
            }
        } catch (Exception e) {
            log.warn("获取用户理财目标画像失败, userId={}, 降级跳过该部分: {}", userId, e.getMessage());
        }

        if (portrait.length() == 0) {
            return riskAwareMessage;
        }
        portrait.append("\n").append(riskAwareMessage);
        return portrait.toString();
    }

    /** BigDecimal 安全转字符串，null 视为 0。 */
    private static String num(BigDecimal v) {
        return v != null ? v.toPlainString() : "0";
    }

    /** 资产类型 -> 中文标签。 */
    private static String portfolioTypeLabel(String type) {
        if (type == null) {
            return "其他";
        }
        return switch (type) {
            case "stock" -> "股票";
            case "fund" -> "基金";
            case "bond" -> "债券";
            case "deposit", "cash" -> "存款";
            default -> type;
        };
    }

    /** 目标类型 -> 中文标签。 */
    private static String goalTypeLabel(String type) {
        if (type == null) {
            return "自定义目标";
        }
        return switch (type) {
            case "retirement" -> "退休目标";
            case "education" -> "教育目标";
            case "house" -> "购房目标";
            case "emergency_fund" -> "应急基金";
            case "custom" -> "自定义目标";
            default -> type;
        };
    }

    /**
     * 根据用户风险等级（R1~R5）构建带有风险约束前缀的提示消息。
     * 当 riskLevel 为空时原样返回 message。
     */
    private String buildRiskAwareMessage(String message, String riskLevel) {
        if (riskLevel == null || riskLevel.isBlank()) {
            return message;
        }
        String riskDesc = switch (riskLevel) {
            case "R1" -> "保守型（仅接受低风险，偏好存款、国债、货币基金）";
            case "R2" -> "稳健型（可接受中低风险，偏好债券基金、稳健型理财）";
            case "R3" -> "平衡型（可接受中等风险，偏好混合型基金、指数定投）";
            case "R4" -> "进取型（可接受中高风险，偏好股票型基金、成长股）";
            case "R5" -> "激进型（可接受高风险，偏好股票、期货、杠杆产品）";
            default -> "平衡型";
        };
        return "[系统提示：当前用户风险等级为 " + riskLevel + " - " + riskDesc
                + "。请在给出投资建议时严格匹配此风险等级，不得推荐超出其风险承受能力的产品。]\n\n" + message;
    }
}
