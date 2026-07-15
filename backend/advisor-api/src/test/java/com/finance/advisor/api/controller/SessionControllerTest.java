package com.finance.advisor.api.controller;

import com.finance.advisor.common.session.SessionManager;
import com.finance.advisor.user.JwtAuthFilter;
import com.finance.advisor.user.JwtService;
import com.finance.advisor.user.SecurityConfig;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SessionController 集成测试：MockMvc + 真实 SecurityConfig（JWT 过滤器 + 401 入口）。
 *
 * <p>因 SessionController 依赖 SecurityContextHolder 获取当前登录用户 ID，需通过 JWT token
 * 注入认证 principal（JwtAuthFilter 将 Long userId 作为 principal）。
 *
 * <p>注：AbstractSecurityMvcTest 为 com.finance.advisor.api 包级私有基类，本测试位于
 * controller 子包无法直接继承，故按相同方式内联构建受 SecurityConfig 保护的 MockMvc。
 */
class SessionControllerTest {

    private static final String JWT_SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final long JWT_EXPIRATION = 86400000L;

    private MockMvc mockMvc;
    private SessionManager sessionManager;
    private String token;

    @BeforeEach
    void setUp() {
        sessionManager = mock(SessionManager.class);
        JwtService jwtService = new JwtService(JWT_SECRET, JWT_EXPIRATION);
        SessionController controller = new SessionController(sessionManager);
        mockMvc = buildSecurityMockMvc(controller, jwtService);
        token = jwtService.generateToken(1L, "alice");
    }

    @Test
    void createSession_withToken_returnsSessionIdAndThreadId() throws Exception {
        when(sessionManager.createSession("1")).thenReturn("sess123");
        when(sessionManager.getThreadId("1", "sess123")).thenReturn("thread456");

        mockMvc.perform(post("/api/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value("sess123"))
                .andExpect(jsonPath("$.data.threadId").value("thread456"));
    }

    @Test
    void getSession_existing_returns200() throws Exception {
        SessionManager.SessionInfo info = mock(SessionManager.SessionInfo.class);
        when(info.getSessionId()).thenReturn("sess123");
        when(info.getUserId()).thenReturn("1");
        when(info.getCreatedAt()).thenReturn(1000L);
        when(info.getLastActiveAt()).thenReturn(2000L);
        when(sessionManager.getSessionInfo("1", "sess123")).thenReturn(info);

        mockMvc.perform(get("/api/sessions/sess123")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getSession_nonExisting_returns404() throws Exception {
        when(sessionManager.getSessionInfo("1", "missing")).thenReturn(null);

        mockMvc.perform(get("/api/sessions/missing")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void listSessions_returnsUserSessions() throws Exception {
        SessionManager.SessionInfo info1 = mock(SessionManager.SessionInfo.class);
        when(info1.getSessionId()).thenReturn("s1");
        when(info1.getUserId()).thenReturn("1");
        when(info1.getCreatedAt()).thenReturn(1000L);
        when(info1.getLastActiveAt()).thenReturn(2000L);
        SessionManager.SessionInfo info2 = mock(SessionManager.SessionInfo.class);
        when(info2.getSessionId()).thenReturn("s2");
        when(info2.getUserId()).thenReturn("1");
        when(info2.getCreatedAt()).thenReturn(3000L);
        when(info2.getLastActiveAt()).thenReturn(4000L);
        when(sessionManager.listActiveSessionInfos("1")).thenReturn(List.of(info1, info2));

        mockMvc.perform(get("/api/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void deleteSession_succeeds() throws Exception {
        doNothing().when(sessionManager).removeSession("1", "sess123");

        mockMvc.perform(delete("/api/sessions/sess123")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(sessionManager).removeSession("1", "sess123");
    }

    @Test
    void createSession_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/sessions"))
                .andExpect(status().isUnauthorized());
    }

    private MockMvc buildSecurityMockMvc(Object controller, JwtService jwtService) {
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
        // 引入 Spring MVC 基础设施（HandlerMapping / HttpMessageConverter 等）
    }
}
