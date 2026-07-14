package com.finance.advisor.api;

import com.finance.advisor.portfolio.Asset;
import com.finance.advisor.portfolio.PortfolioController;
import com.finance.advisor.portfolio.PortfolioService;
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
 * PortfolioController 集成测试：覆盖 list / create / delete，含 JWT 鉴权与用户隔离。
 */
class PortfolioControllerIntegrationTest extends AbstractSecurityMvcTest {

    private MockMvc mockMvc;
    private PortfolioService portfolioService;
    private String token;

    @BeforeEach
    void setUp() {
        portfolioService = mock(PortfolioService.class);
        JwtService jwtService = new JwtService(JWT_SECRET, JWT_EXPIRATION);
        PortfolioController controller = new PortfolioController(portfolioService);
        mockMvc = buildSecurityMockMvc(controller, jwtService);
        token = jwtService.generateToken(1L, "alice");
    }

    private Asset asset(Long id, String type, String symbol, String amount, String costPrice) {
        Asset a = new Asset();
        a.setId(id);
        a.setUserId(1L);
        a.setType(type);
        a.setSymbol(symbol);
        a.setAmount(new BigDecimal(amount));
        a.setCostPrice(new BigDecimal(costPrice));
        return a;
    }

    @Test
    void list_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/portfolio/list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withToken_returnsCurrentUserAssets() throws Exception {
        Asset a = asset(10L, "stock", "sh600036", "100", "10");
        when(portfolioService.list(1L)).thenReturn(List.of(a));

        mockMvc.perform(get("/api/portfolio/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].type").value("stock"))
                .andExpect(jsonPath("$.data[0].userId").value(1));
    }

    @Test
    void create_asset_bindsUserAndReturns() throws Exception {
        when(portfolioService.create(eq(1L), any(Asset.class))).thenAnswer(inv -> {
            Asset a = inv.getArgument(1);
            a.setId(20L);
            a.setUserId(1L);
            return a;
        });

        mockMvc.perform(post("/api/portfolio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"type\":\"stock\",\"symbol\":\"sh600036\",\"amount\":\"100\",\"costPrice\":\"10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(20))
                .andExpect(jsonPath("$.data.userId").value(1));
    }

    @Test
    void delete_ownAsset_succeeds() throws Exception {
        doNothing().when(portfolioService).delete(1L, 5L);

        mockMvc.perform(delete("/api/portfolio/5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void delete_othersAsset_returns404InBody() throws Exception {
        doThrow(new IllegalArgumentException("资产不存在或无权操作"))
                .when(portfolioService).delete(1L, 999L);

        mockMvc.perform(delete("/api/portfolio/999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }
}
