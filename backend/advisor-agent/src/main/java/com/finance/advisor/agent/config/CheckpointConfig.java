package com.finance.advisor.agent.config;

import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.postgresql.PostgresSaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Checkpoint Saver 配置
 *
 * 支持可切换的检查点持久化方案，用于保存 Agent 对话状态和上下文。
 * 默认使用内存 Saver，生产环境建议使用 PostgreSQL。
 *
 * 复用来源:
 *   - spring-ai-alibaba-graph-core MemorySaver (com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver)
 *   - spring-ai-alibaba-graph-core PostgresSaver (com.alibaba.cloud.ai.graph.checkpoint.savers.postgresql.PostgresSaver)
 *
 * 配置项: advisor.checkpoint.saver = memory | postgres | redis
 */
@Configuration
public class CheckpointConfig {

    private static final Logger log = LoggerFactory.getLogger(CheckpointConfig.class);

    /**
     * 默认内存 Saver（开发/测试环境）
     */
    @Bean
    @ConditionalOnProperty(name = "advisor.checkpoint.saver", havingValue = "memory", matchIfMissing = true)
    public BaseCheckpointSaver memorySaver() {
        log.info("使用内存 Checkpoint Saver");
        return new MemorySaver();
    }

    /**
     * PostgreSQL Saver（生产环境）
     * 需要 spring-ai-alibaba-graph-core 的 PostgresSaver 类在 classpath 中
     */
    @Bean
    @ConditionalOnProperty(name = "advisor.checkpoint.saver", havingValue = "postgres")
    public BaseCheckpointSaver postgresSaver(DataSource dataSource) {
        log.info("使用 PostgreSQL Checkpoint Saver");
        try {
            // 使用 builder 创建 PostgresSaver，默认 CreateOption.CREATE_IF_NOT_EXISTS
            // 会在初始化时自动创建 GraphThread、GraphCheckpoint 表，无需手动维护 DDL
            return PostgresSaver.builder()
                    .datasource(dataSource)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("配置为使用 PostgresSaver，但加载失败: " + e.getMessage()
                + "。请确保 spring-ai-alibaba-graph-core 在 classpath 中。" +
                "如需回退到内存 Saver，请设置 advisor.checkpoint.saver=memory", e);
        }
    }
}
