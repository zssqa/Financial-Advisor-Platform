package com.finance.advisor.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 金融多智能体理财顾问平台 - 启动入口
 *
 * 扫描所有子模块的 Spring Bean：
 * - com.finance.advisor.common (common 模块)
 * - com.finance.advisor.user (user 模块)
 * - com.finance.advisor.tool (tool 模块)
 * - com.finance.advisor.rag (rag 模块)
 * - com.finance.advisor.agent (agent 模块)
 * - com.finance.advisor.api (api 模块)
 * - com.finance.advisor.portfolio (portfolio 模块)
 * - com.finance.advisor.bootstrap (本模块)
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.finance.advisor.common",
    "com.finance.advisor.user",
    "com.finance.advisor.tool",
    "com.finance.advisor.rag",
    "com.finance.advisor.portfolio",
    "com.finance.advisor.agent",
    "com.finance.advisor.api",
    "com.finance.advisor.bootstrap"
})
public class AdvisorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdvisorApplication.class, args);
    }
}
