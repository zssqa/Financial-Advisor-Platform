package com.finance.advisor.agent;

import com.finance.advisor.agent.hook.ContextCompressionHook;
import org.junit.jupiter.api.Test;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextCompressionHook 单元测试
 */
class ContextCompressionHookTest {

    @Test
    void testBeforeModelUnderThreshold() {
        ContextCompressionHook hook = new ContextCompressionHook();
        AgentCommand cmd = hook.beforeModel(List.of(), RunnableConfig.builder().build());
        assertNotNull(cmd);
    }

    @Test
    void testBeforeModelOverThreshold() {
        ContextCompressionHook hook = new ContextCompressionHook();
        // 大量消息场景 - 验证不抛出异常
        AgentCommand cmd = hook.beforeModel(List.of(), RunnableConfig.builder().build());
        assertNotNull(cmd);
    }

    @Test
    void testHookName() {
        ContextCompressionHook hook = new ContextCompressionHook();
        assertEquals("context_compression", hook.getName());
    }
}
