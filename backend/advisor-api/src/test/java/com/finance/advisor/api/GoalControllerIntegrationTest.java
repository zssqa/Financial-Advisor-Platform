package com.finance.advisor.api;

import com.finance.advisor.portfolio.goal.Goal;
import com.finance.advisor.portfolio.goal.GoalController;
import com.finance.advisor.portfolio.goal.GoalService;
import com.finance.advisor.user.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GoalController 集成测试：覆盖 list / create / delete，含 JWT 鉴权与用户隔离。
 */
class GoalControllerIntegrationTest extends AbstractSecurityMvcTest {

    private MockMvc mockMvc;
    private GoalService goalService;
    private String token;

    @BeforeEach
    void setUp() {
        goalService = mock(GoalService.class);
        JwtService jwtService = new JwtService(JWT_SECRET, JWT_EXPIRATION);
        GoalController controller = new GoalController(goalService);
        mockMvc = buildSecurityMockMvc(controller, jwtService);
        token = jwtService.generateToken(1L, "alice");
    }

    private Goal goal(Long id, String type, String target, String current) {
        Goal g = new Goal();
        g.setId(id);
        g.setUserId(1L);
        g.setType(type);
        g.setTargetAmount(new BigDecimal(target));
        g.setCurrentAmount(new BigDecimal(current));
        return g;
    }

    @Test
    void list_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/goal/list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withToken_returnsCurrentUserGoals() throws Exception {
        when(goalService.list(1L)).thenReturn(List.of(goal(5L, "retirement", "10000", "1000")));

        mockMvc.perform(get("/api/goal/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].type").value("retirement"))
                .andExpect(jsonPath("$.data[0].userId").value(1));
    }

    @Test
    void create_goal_bindsUserAndReturns() throws Exception {
        when(goalService.create(eq(1L), any(Goal.class))).thenAnswer(inv -> {
            Goal g = inv.getArgument(1);
            g.setId(7L);
            g.setUserId(1L);
            return g;
        });

        mockMvc.perform(post("/api/goal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"type\":\"retirement\",\"targetAmount\":\"10000\",\"currentAmount\":\"0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.userId").value(1));
    }

    @Test
    void delete_ownGoal_succeeds() throws Exception {
        doNothing().when(goalService).delete(1L, 5L);

        mockMvc.perform(delete("/api/goal/5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void delete_othersGoal_returns404InBody() throws Exception {
        doThrow(new IllegalArgumentException("目标不存在或无权操作"))
                .when(goalService).delete(1L, 999L);

        mockMvc.perform(delete("/api/goal/999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }
}
