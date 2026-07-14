package com.finance.advisor.agent.config;

import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.finance.advisor.tool.FinancialTools;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 多 Agent 协作配置
 *
 * 采用 LlmRoutingAgent 按问题类型自动路由到不同的专业 Agent。
 *
 * 复用来源: spring-ai-alibaba-agent-framework LlmRoutingAgent
 *   (com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent)
 */
@Configuration
public class MultiAgentConfig {

    @Bean
    public LlmRoutingAgent routingAgent(
            ChatModel chatModel,
            FinancialTools tools) {

        ReactAgent planningAgent = ReactAgent.builder()
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

        ReactAgent riskAgent = ReactAgent.builder()
                .name("risk_assessor")
                .model(chatModel)
                .description("处理风险评估、风险承受能力分析类问题")
                .systemPrompt("""
                        你是风险评估专家，擅长分析投资风险和评估客户风险承受能力。
                        根据客户的年龄、收入、投资经验、资金用途等因素，科学评估风险等级。
                        可用的工具包括：联网搜索市场风险资讯、研报查询。
                        """)
                .methodTools(tools)
                .outputKey("risk_result")
                .build();

        ReactAgent productAgent = ReactAgent.builder()
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

        return LlmRoutingAgent.builder()
                .name("router")
                .model(chatModel)
                .subAgents(List.of((Agent) planningAgent, (Agent) riskAgent, (Agent) productAgent))
                .build();
    }
}
