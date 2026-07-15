package com.finance.advisor.api;

import com.finance.advisor.user.AuthController;
import com.finance.advisor.user.JwtService;
import com.finance.advisor.user.User;
import com.finance.advisor.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 集成测试：覆盖注册 / 登录 / profile 的成功与失败路径，含 JWT 鉴权。
 */
class AuthControllerIntegrationTest extends AbstractSecurityMvcTest {

    private MockMvc mockMvc;
    private UserService userService;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        jwtService = new JwtService(JWT_SECRET, JWT_EXPIRATION);
        AuthController controller = new AuthController(userService, jwtService);
        mockMvc = buildSecurityMockMvc(controller, jwtService);
    }

    private User sampleUser() {
        User u = new User();
        u.setId(1L);
        u.setUsername("alice");
        u.setRiskLevel("R3");
        u.setCreatedAt(1700000000000L);
        return u;
    }

    @Test
    void register_success_returnsCreated() throws Exception {
        when(userService.register("alice", "pw123")).thenReturn(sampleUser());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    void register_duplicateUsername_returnsConflict() throws Exception {
        when(userService.register("alice", "pw123"))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void login_success_returnsToken() throws Exception {
        when(userService.login("alice", "pw123")).thenReturn(sampleUser());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.userId").value(1));
    }

    @Test
    void login_wrongCredentials_returnsUnauthorized() throws Exception {
        when(userService.login("alice", "wrong"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profile_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profile_withToken_returnsOk() throws Exception {
        when(userService.findById(1L)).thenReturn(sampleUser());
        String token = jwtService.generateToken(1L, "alice");

        mockMvc.perform(get("/api/auth/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.username").value("alice"));
    }
}
