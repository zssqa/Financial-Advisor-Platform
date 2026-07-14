package com.finance.advisor.agent.config;

import com.alibaba.cloud.ai.graph.agent.AgentTool;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import com.finance.advisor.tool.FinancialTools;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 多 Agent 协作增强配置
 *
 * 实现方案中的三种增强模式：
 * 1. Supervisor/Sub-Agent - 主管 Agent 调度专业 Agent 工具
 * 2. SequentialAgent - 串行流水线（风险评估->产品推荐->理财规划）
 * 3. ParallelAgent - 并行市场查询（同时查询多市场行情）
 *
 * 复用来源:
 *   - spring-ai-alibaba-agent-framework: SequentialAgent, ParallelAgent, AgentTool
 */
@Configuration
public class AgentEnhancementConfig {

    @Bean
    public ReactAgent supervisorAgent(
            ChatModel chatModel,
            ReactAgent financialPlanner,
            ReactAgent riskAssessor,
            ReactAgent productRecommender) {

        return ReactAgent.builder()
                .name("supervisor_advisor")
                .model(chatModel)
                .description("金融主管顾问，负责分析用户需求并调度专业Agent协同工作")
                .systemPrompt("""
                        你是一位资深的金融主管顾问，负责分析用户需求，调度各个专业Agent协同工作。

                        可调度的专业团队：
                        1. financial_planner - 理财规划专家（资产配置、退休规划、财务目标）
                        2. risk_assessor - 风险评估专家（风险承受能力分析、投资风险评估）
                        3. product_recommender - 产品推荐专家（理财产品分析、对比推荐）

                        工作流程：
                        - 首先分析用户问题的复杂程度
                        - 对于简单问题，直接调用对应的专业Agent
                        - 对于复杂问题，可以依次调用多个专业Agent，汇总他们的结果
                        - 最终给出综合性的专业建议
                        """)
                .tools(List.of(
                        AgentTool.getFunctionToolCallback(financialPlanner),
                        AgentTool.getFunctionToolCallback(riskAssessor),
                        AgentTool.getFunctionToolCallback(productRecommender)))
                .enableLogging(true)
                .build();
    }

    @Bean
    public SequentialAgent financialAnalysisPipeline(
            ReactAgent riskAssessor,
            ReactAgent productRecommender,
            ReactAgent financialPlanner) {
        return SequentialAgent.builder()
                .name("financial_analysis_pipeline")
                .description("金融分析流水线：风险评估 -> 产品推荐 -> 理财规划")
                .subAgents(List.of(riskAssessor, productRecommender, financialPlanner))
                .build();
    }

    @Bean
    public ParallelAgent marketQueryAgent(
            ChatModel chatModel,
            FinancialTools tools) {

        ReactAgent aStockAgent = ReactAgent.builder()
                .name("a_stock_analyst")
                .model(chatModel)
                .description("分析A股市场行情")
                .systemPrompt("你是一名A股市场分析师，使用 query_stock_quote 工具查询A股行情并给出分析。")
                .methodTools(tools)
                .outputKey("a_stock_result")
                .build();

        ReactAgent fundAnalyst = ReactAgent.builder()
                .name("fund_analyst")
                .model(chatModel)
                .description("分析基金市场行情")
                .systemPrompt("你是一名基金分析师，使用 query_fund_nav 工具查询基金净值并给出分析。")
                .methodTools(tools)
                .outputKey("fund_result")
                .build();

        ReactAgent macroAnalyst = ReactAgent.builder()
                .name("macro_analyst")
                .model(chatModel)
                .description("分析宏观经济和汇率数据")
                .systemPrompt("你是一名宏观经济分析师，使用 exchange_rate 工具查询汇率数据并给出分析。")
                .methodTools(tools)
                .outputKey("macro_result")
                .build();

        return ParallelAgent.builder()
                .name("market_query")
                .description("并行查询多个市场行情：A股 + 基金 + 汇率")
                .subAgents(List.of(aStockAgent, fundAnalyst, macroAnalyst))
                .build();
    }

    @Bean
    public ReactAgent financialPlanner(ChatModel chatModel, FinancialTools tools) {
        return ReactAgent.builder()
                .name("financial_planner")
                .model(chatModel)
                .description("处理理财规划、资产配置、退休规划类问题")
                .systemPrompt("""
                        你是理财规划专家，擅长制定个人理财方案和资产配置策略。
                        根据用户的财务状况、目标和风险偏好，提供量身定制的理财规划。
                        可用的工具包括：联网搜索财经资讯、复利计算、贷款计算、研报查询。
                        """)
                .methodTools(tools)
                .outputKey("planning_result")
                .build();
    }

    @Bean
    public ReactAgent riskAssessor(ChatModel chatModel, FinancialTools tools) {
        return ReactAgent.builder()
                .name("risk_assessor")
                .model(chatModel)
                .description("处理风险评估、风险承受能力分析类问题")
                .systemPrompt("""
                        你是风险评估专家，擅长分析投资风险和评估客户风险承受能力。
                        根据客户的年龄、收入、投资经验、资金用途等因素，科学评估风险等级。
                        可用的工具包括：联网搜索市场风险资讯、研报查询、风险评估问卷。
                        """)
                .methodTools(tools)
                .outputKey("risk_result")
                .build();
    }

    @Bean
    public ReactAgent productRecommender(ChatModel chatModel, FinancialTools tools) {
        return ReactAgent.builder()
                .name("product_recommender")
                .model(chatModel)
                .description("处理理财产品推荐、产品对比分析类问题")
                .systemPrompt("""
                        你是金融产品推荐专家，熟悉各类理财产品（基金、理财、保险、存款等）。
                        根据客户的风险等级和需求，推荐合适的产品组合。
                        可用的工具包括：联网搜索产品资讯、复利计算收益、研报查询产品分析。
                        """)
                .methodTools(tools)
                .outputKey("product_result")
                .build();
    }
}
