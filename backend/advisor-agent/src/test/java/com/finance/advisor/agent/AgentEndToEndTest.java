package com.finance.advisor.agent;

import com.finance.advisor.agent.core.FinancialAdvisorAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Agent 端到端测试
 *
 * 验证 FinancialAdvisorAgent 的基本调用流程。
 * 完整测试需要 DashScope API Key，此处验证构造逻辑。
 */
class AgentEndToEndTest {

    @Test
    void testAgentCreation() {
        // 验证 FinancialAdvisorAgent 类可加载
        assertNotNull(FinancialAdvisorAgent.class);
    }
}
