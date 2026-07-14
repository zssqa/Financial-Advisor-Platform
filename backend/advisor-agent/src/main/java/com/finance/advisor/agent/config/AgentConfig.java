package com.finance.advisor.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.finance.advisor.agent.hook.ConfirmationHook;
import com.finance.advisor.agent.hook.ContextCompressionHook;
import com.finance.advisor.tool.FinancialTools;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Agent 全局配置
 *
 * 配置 Agent 的全局行为，包括：
 * - Agent 实例化
 * - Hook 注册（Human-in-the-loop、上下文压缩）
 * - 工具绑定
 *
 * 复用来源: spring-ai-alibaba-agent-framework ReactAgent builder + Hook 机制
 */
@Configuration
public class AgentConfig {

    @Bean
    public ReactAgent enhancedFinancialAdvisor(
            ChatModel chatModel,
            FinancialTools financialTools,
            ContextCompressionHook compressionHook,
            ConfirmationHook confirmationHook) {

        return ReactAgent.builder()
                .name("enhanced_financial_advisor")
                .model(chatModel)
                .systemPrompt("""
                        你是一位专业的金融理财顾问，精通个人理财、投资规划、风险评估和金融产品分析。

                        核心原则：
                        - 始终以客户利益为先，提供客观、中立的建议
                        - 明确说明投资有风险，不承诺收益
                        - 给出的建议应基于数据和事实

                        你可以使用的工具：
                        1. tavily_web_search - 搜索互联网获取最新的财经资讯、市场行情和新闻
                        2. calculate_compound_interest - 计算复利投资收益
                        3. calculate_loan_interest - 计算贷款月供和利息
                        4. search_research_reports - 从内部知识库搜索金融研报和专业分析文档

                        回答要求：
                        - 先分析用户需求，再决定使用哪些工具
                        - 对于需要计算的问题，务必使用计算工具
                        - 对于需要最新信息的问题，务必使用联网搜索
                        - 给出清晰的结论和可操作的建议
                        """)
                .methodTools(financialTools)
                .hooks(List.of(compressionHook, confirmationHook))
                .enableLogging(true)
                .build();
    }
}
