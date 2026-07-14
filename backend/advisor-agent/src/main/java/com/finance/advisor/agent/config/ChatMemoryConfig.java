package com.finance.advisor.agent.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话记忆配置（基于内存）
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    static class InMemoryChatMemory implements ChatMemory {
        private final Map<String, List<org.springframework.ai.chat.messages.Message>> store = new ConcurrentHashMap<>();

        @Override
        public void add(String conversationId, List<org.springframework.ai.chat.messages.Message> messages) {
            store.computeIfAbsent(conversationId, k -> new ArrayList<>()).addAll(messages);
        }

        @Override
        public List<org.springframework.ai.chat.messages.Message> get(String conversationId) {
            List<org.springframework.ai.chat.messages.Message> all = store.get(conversationId);
            if (all == null) return List.of();
            return all;
        }

        @Override
        public void clear(String conversationId) {
            store.remove(conversationId);
        }
    }
}
