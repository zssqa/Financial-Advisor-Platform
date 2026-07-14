package com.finance.advisor.api;

import com.finance.advisor.user.JwtAuthFilter;
import com.finance.advisor.user.JwtService;
import com.finance.advisor.user.SecurityConfig;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * 集成测试基类：构建一个受真实 SecurityConfig（JWT 过滤器 + 401 入口）保护的 MockMvc。
 *
 * <p>不启动 Spring Boot 自动装配，避免引入 DB / Nacos / DashScope 等重依赖导致上下文加载失败。
 * 仅注册：被测 Controller、JwtService、JwtAuthFilter、SecurityConfig、@EnableWebMvc 基础设施，
 * 并把 Spring Security 生成的 {@code springSecurityFilterChain} 作为 Filter 加入 MockMvc。
 */
abstract class AbstractSecurityMvcTest {

    /** 64 字符固定密钥，满足 HS256 至少 32 字节要求。 */
    protected static final String JWT_SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    protected static final long JWT_EXPIRATION = 86400000L;

    /**
     * 构建受 SecurityConfig 保护的 MockMvc。
     *
     * @param controller  被测 Controller 实例（依赖项已注入 mock）
     * @param jwtService  真实 JwtService（使用固定 secret）
     */
    protected MockMvc buildSecurityMockMvc(Object controller, JwtService jwtService) {
        try {
            JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtService);
            GenericWebApplicationContext ctx = new GenericWebApplicationContext();
            ctx.setServletContext(new MockServletContext());
            DefaultListableBeanFactory beanFactory = ctx.getDefaultListableBeanFactory();
            AnnotationConfigUtils.registerAnnotationConfigProcessors(beanFactory);
            beanFactory.registerSingleton("jwtService", jwtService);
            beanFactory.registerSingleton("jwtAuthFilter", jwtAuthFilter);
            beanFactory.registerSingleton("controller", controller);
            beanFactory.registerBeanDefinition("securityConfig",
                    new RootBeanDefinition(SecurityConfig.class));
            beanFactory.registerBeanDefinition("webMvcEnabler",
                    new RootBeanDefinition(WebMvcEnabler.class));
            ctx.refresh();

            Filter springSecurityFilterChain = ctx.getBean("springSecurityFilterChain", Filter.class);
            return MockMvcBuilders.webAppContextSetup(ctx)
                    .addFilters(springSecurityFilterChain)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("构建 SecurityMockMvc 失败", e);
        }
    }

    @Configuration
    @EnableWebMvc
    static class WebMvcEnabler {
        // 仅用于引入 Spring MVC 基础设施（HandlerMapping / HttpMessageConverter 等）
    }
}
