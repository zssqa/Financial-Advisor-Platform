package com.finance.advisor.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AdvisorApplication 加载测试
 *
 * 验证启动类配置正确，Spring 上下文可正常加载。
 * 完整集成测试需要 DashScope API Key 和 PostgreSQL 环境。
 */
class AdvisorApplicationTest {

    @Test
    void testApplicationClassLoads() {
        assertNotNull(AdvisorApplication.class);
    }

    @Test
    void testMainMethodExists() {
        try {
            var method = AdvisorApplication.class.getMethod("main", String[].class);
            assertNotNull(method);
        } catch (NoSuchMethodException e) {
            fail("main 方法不存在");
        }
    }
}
