package com.finance.advisor.agent.hook;

import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文压缩 Hook
 *
 * 在长时间对话中，当消息数量超过阈值时，自动压缩历史消息。
 *
 * 复用来源: spring-ai-alibaba-agent-framework MessagesModelHook, AgentCommand, UpdatePolicy
 *   (com.alibaba.cloud.ai.graph.agent.hook.messages)
 */
@HookPositions({HookPosition.BEFORE_MODEL})
@Component
public class ContextCompressionHook extends MessagesModelHook {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressionHook.class);

    @Value("${advisor.context-compression.max-messages:20}")
    private int maxMessages;

    @Value("${advisor.context-compression.keep-after-trim:6}")
    private int keepAfterTrim;

    @Override
    public String getName() {
        return "context_compression";
    }

    @Override
    public AgentCommand beforeModel(List<Message> messages, RunnableConfig config) {
        if (messages.size() <= maxMessages) {
            return new AgentCommand(messages);
        }

        log.info("检测到消息数量({})超过阈值({})，执行上下文压缩", messages.size(), maxMessages);

        List<Message> compressedMessages = new ArrayList<>();

        if (!messages.isEmpty()) {
            compressedMessages.add(messages.get(0));
        }

        int startIndex = Math.max(1, messages.size() - keepAfterTrim);
        for (int i = startIndex; i < messages.size(); i++) {
            compressedMessages.add(messages.get(i));
        }

        log.info("上下文压缩完成: {} 条 -> {} 条", messages.size(), compressedMessages.size());

        return new AgentCommand(compressedMessages, UpdatePolicy.REPLACE);
    }
}
