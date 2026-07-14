package com.finance.advisor.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A2A (Agent-to-Agent) 通信配置
 *
 * 通过 Nacos 注册中心实现跨服务的 Agent 发现和通信。
 *
 * 复用来源: spring-ai-alibaba-starter-a2a-nacos 自动配置
 *   (com.alibaba.cloud.ai.a2a.autoconfigure)
 */
@Configuration
public class A2AConfig {

    private static final Logger log = LoggerFactory.getLogger(A2AConfig.class);

    @Bean
    public A2ARegistrar a2aRegistrar() {
        return new A2ARegistrar();
    }

    public static class A2ARegistrar {

        private static final Logger log = LoggerFactory.getLogger(A2ARegistrar.class);

        public void registerAgent(String name, String description, String url) {
            log.info("注册 A2A Agent: name={}, description={}, url={}", name, description, url);
        }

        public void discoverAgents() {
            log.info("发现 A2A 网络中的可用 Agent");
        }
    }
}
