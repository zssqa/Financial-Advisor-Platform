package com.finance.advisor.agent.hook;

import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 人工确认 Hook (Human-in-the-loop)
 *
 * 当检测到用户意图执行大额金融操作时，在模型调用前插入确认流程。
 *
 * 复用来源: spring-ai-alibaba-agent-framework MessagesModelHook, AgentCommand, UpdatePolicy
 *   (com.alibaba.cloud.ai.graph.agent.hook.messages)
 */
@HookPositions({HookPosition.AFTER_MODEL})
@Component
public class ConfirmationHook extends MessagesModelHook {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationHook.class);

    @Value("${advisor.confirmation.required-amount:100000}")
    private double requiredAmount;

    @Override
    public String getName() {
        return "confirmation_hook";
    }

    @Override
    public AgentCommand afterModel(List<Message> messages, RunnableConfig config) {
        if (messages.isEmpty()) {
            return new AgentCommand(messages);
        }

        Message lastMessage = messages.get(messages.size() - 1);

        if (!(lastMessage instanceof AssistantMessage assistantMessage)) {
            return new AgentCommand(messages);
        }

        String content = assistantMessage.getText();
        if (content == null || content.isEmpty()) {
            return new AgentCommand(messages);
        }

        boolean needsConfirmation = containsConfirmationTrigger(content);

        if (needsConfirmation) {
            log.info("检测到需要人工确认的操作，插入确认流程");

            String confirmationMsg = "\n\n---\n"
                    + "⚠️ **需要您的确认**\n\n"
                    + "以上操作涉及大额资金，请确认是否继续？\n"
                    + "回复「确认」继续执行，回复「取消」放弃操作。";

            List<Message> updatedMessages = new ArrayList<>(messages);
            AssistantMessage confirmedMessage = AssistantMessage.builder()
                    .content(assistantMessage.getText() + confirmationMsg)
                    .toolCalls(assistantMessage.getToolCalls() != null ? assistantMessage.getToolCalls() : List.of())
                    .build();
            updatedMessages.set(updatedMessages.size() - 1, confirmedMessage);

            return new AgentCommand(updatedMessages, com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy.REPLACE);
        }

        return new AgentCommand(messages);
    }

    private boolean containsConfirmationTrigger(String content) {
        String lower = content.toLowerCase();
        return lower.contains("投资" + String.format("%.0f", requiredAmount))
                || lower.contains("转账" + String.format("%.0f", requiredAmount))
                || lower.contains("购买" + String.format("%.0f", requiredAmount))
                || lower.contains("支付" + String.format("%.0f", requiredAmount))
                || (lower.contains("确认") && lower.contains("金额"))
                || (lower.contains("万") && (lower.contains("投资") || lower.contains("购买") || lower.contains("转账")));
    }
}
