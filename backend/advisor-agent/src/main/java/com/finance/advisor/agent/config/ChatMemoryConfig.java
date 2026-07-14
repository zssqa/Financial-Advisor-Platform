package com.finance.advisor.agent.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆配置（基于 JDBC 持久化到 PostgreSQL）
 *
 * 底层存储使用 Spring AI 的 JdbcChatMemoryRepository（由
 * spring-ai-starter-model-chat-memory-repository-jdbc 自动配置注入），
 * 上层采用 MessageWindowChatMemory 窗口策略保留最近 N 条消息。
 * 重启后端后，历史对话消息可从 SPRING_AI_CHAT_MEMORY 表恢复。
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * 基于 JDBC 仓库的对话记忆 Bean
     *
     * @param chatMemoryRepository 由 Spring AI 自动配置创建的 JdbcChatMemoryRepository
     * @return 消息窗口聊天记忆（保留最近 20 条消息，与上下文压缩阈值一致）
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }
}
