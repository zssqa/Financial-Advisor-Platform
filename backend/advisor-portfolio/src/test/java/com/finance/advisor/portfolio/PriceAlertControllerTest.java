package com.finance.advisor.portfolio;

import com.finance.advisor.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PriceAlertController 单元测试：使用纯 Mockito 验证 create/list/delete/markAsRead 接口
 * 的参数校验与调用链。通过 mockStatic 桩 SecurityUtil.currentUserId()，绕过 SecurityContext 依赖。
 */
class PriceAlertControllerTest {

    private PriceAlertService priceAlertService;
    private PriceAlertController controller;

    @BeforeEach
    void setUp() {
        priceAlertService = mock(PriceAlertService.class);
        controller = new PriceAlertController(priceAlertService);
    }

    @Test
    void create_success_returns200() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("symbol", "sh600036");
        body.put("assetName", "招商银行");
        body.put("alertType", "above");
        body.put("thresholdPrice", 10.5);

        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", 1L);
        when(priceAlertService.createAlert(eq(1L), eq("sh600036"), eq("招商银行"),
                eq("above"), any(BigDecimal.class))).thenReturn(created);

        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(1L);

            ApiResponse<Map<String, Object>> response = controller.create(body);

            assertEquals(200, response.getCode());
            assertNotNull(response.getData());
            verify(priceAlertService).createAlert(eq(1L), eq("sh600036"), eq("招商银行"),
                    eq("above"), any(BigDecimal.class));
        }
    }

    @Test
    void create_missingSymbol_returns400() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("alertType", "above");
        body.put("thresholdPrice", 10.5);

        ApiResponse<Map<String, Object>> response = controller.create(body);

        assertEquals(400, response.getCode());
    }

    @Test
    void create_invalidAlertType_returns400() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("symbol", "sh600036");
        body.put("alertType", "invalid");
        body.put("thresholdPrice", 10.5);

        ApiResponse<Map<String, Object>> response = controller.create(body);

        assertEquals(400, response.getCode());
    }

    @Test
    void create_missingThresholdPrice_returns400() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("symbol", "sh600036");
        body.put("alertType", "above");

        ApiResponse<Map<String, Object>> response = controller.create(body);

        assertEquals(400, response.getCode());
    }

    @Test
    void create_invalidThresholdPriceFormat_returns400() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("symbol", "sh600036");
        body.put("alertType", "above");
        body.put("thresholdPrice", "abc");

        ApiResponse<Map<String, Object>> response = controller.create(body);

        assertEquals(400, response.getCode());
    }

    @Test
    void list_returnsUserAlerts() {
        Map<String, Object> a1 = new LinkedHashMap<>();
        a1.put("id", 1L);
        Map<String, Object> a2 = new LinkedHashMap<>();
        a2.put("id", 2L);

        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(1L);
            when(priceAlertService.listAlerts(1L)).thenReturn(List.of(a1, a2));

            ApiResponse<List<Map<String, Object>>> response = controller.list();

            assertEquals(200, response.getCode());
            assertNotNull(response.getData());
            assertEquals(2, response.getData().size());
        }
    }

    @Test
    void delete_succeeds() {
        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(1L);
            when(priceAlertService.deleteAlert(1L, 1L)).thenReturn(true);

            ApiResponse<Void> response = controller.delete(1L);

            assertEquals(200, response.getCode());
            verify(priceAlertService).deleteAlert(1L, 1L);
        }
    }

    @Test
    void markAsRead_readTrue_callsService() {
        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::currentUserId).thenReturn(1L);
            when(priceAlertService.markAsRead(1L, 1L)).thenReturn(true);

            ApiResponse<Void> response = controller.markAsRead(1L, Map.of("read", true));

            assertEquals(200, response.getCode());
            verify(priceAlertService).markAsRead(1L, 1L);
        }
    }

    @Test
    void markAsRead_readFalse_doesNotCallService() {
        ApiResponse<Void> response = controller.markAsRead(1L, Map.of("read", false));

        assertEquals(200, response.getCode());
        verify(priceAlertService, never()).markAsRead(any(), any());
    }
}
