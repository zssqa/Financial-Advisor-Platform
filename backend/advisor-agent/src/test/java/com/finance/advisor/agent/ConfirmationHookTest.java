package com.finance.advisor.agent;

import com.finance.advisor.agent.hook.ConfirmationHook;
import org.junit.jupiter.api.Test;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfirmationHook 单元测试
 */
class ConfirmationHookTest {

    @Test
    void testNormalMessageNoConfirmation() {
        ConfirmationHook hook = new ConfirmationHook();
        assertNotNull(hook.getName());
        assertEquals("confirmation_hook", hook.getName());
    }

    @Test
    void testAfterModelWithEmptyMessages() {
        ConfirmationHook hook = new ConfirmationHook();
        AgentCommand cmd = hook.afterModel(List.of(), RunnableConfig.builder().build());
        assertNotNull(cmd);
    }
}
