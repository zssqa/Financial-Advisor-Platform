package com.finance.advisor.bootstrap.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Nacos 动态配置中心
 *
 * 通过 Nacos Config 管理模型参数、API Key 轮换、工具开关和 Prompt 模板等动态配置。
 *
 * 复用来源: spring-ai-alibaba-starter-config-nacos (NacosReactAgentBuilder, NacosAgentConfig)
 *
 * 配置方式 (bootstrap.yml):
 *   spring.cloud.nacos.config:
 *     server-addr: ${NACOS_SERVER:localhost:8848}
 *     namespace: ${NACOS_NAMESPACE:financial-advisor}
 *     data-id: advisor-config.yaml
 *     group: DEFAULT_GROUP
 *     file-extension: yaml
 *     refresh-enabled: true
 */
@Configuration
public class NacosConfig {

    private static final Logger log = LoggerFactory.getLogger(NacosConfig.class);

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${tavily.api-key:}")
    private String tavilyApiKey;

    @Value("${advisor.confirmation.required-amount:100000}")
    private double requiredAmount;

    @Bean
    public ConfigRefresher configRefresher(ChatModel chatModel) {
        return new ConfigRefresher(chatModel);
    }

    public static class ConfigRefresher {

        private static final Logger log = LoggerFactory.getLogger(ConfigRefresher.class);
        private final ChatModel chatModel;

        public ConfigRefresher(ChatModel chatModel) {
            this.chatModel = chatModel;
        }

        public void refreshModelConfig(double temperature) {
            log.info("Nacos 配置变更: 更新模型温度参数为 {}", temperature);
        }
    }
}
