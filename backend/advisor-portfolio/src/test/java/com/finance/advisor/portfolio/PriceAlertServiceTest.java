package com.finance.advisor.portfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PriceAlertService 单元测试：mock JdbcTemplate，验证预警创建、触发逻辑与统计。
 *
 * 注意：@PostConstruct init() 不会在 new 构造时触发（无 Spring 容器），因此无需 mock execute()。
 */
class PriceAlertServiceTest {

    private JdbcTemplate jdbcTemplate;
    private PriceAlertService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new PriceAlertService(jdbcTemplate);
    }

    /** createAlert 能正确插入预警并返回含生成 id 的记录 */
    @Test
    void createAlert_insertsAndReturnsRecord() {
        // 模拟 KeyHolder 被填充 id=42
        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
            .thenAnswer(inv -> {
                KeyHolder kh = inv.getArgument(1);
                ((GeneratedKeyHolder) kh).getKeyList()
                    .add(Collections.singletonMap("id", 42L));
                return 1;
            });

        Map<String, Object> result = service.createAlert(
                1L, "sh600036", "招商银行", "above", new BigDecimal("12.50"));

        assertEquals(42L, result.get("id"));
        assertEquals(1L, result.get("userId"));
        assertEquals("sh600036", result.get("symbol"));
        assertEquals("招商银行", result.get("assetName"));
        assertEquals("above", result.get("alertType"));
        assertEquals("active", result.get("status"));
        assertEquals(0, new BigDecimal("12.50").compareTo((BigDecimal) result.get("thresholdPrice")));
        assertNull(result.get("triggeredAt"));
        verify(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    /** above 类型：当前价格 >= 阈值 → 触发，更新状态为 triggered */
    @Test
    void checkAlerts_aboveType_triggersWhenPriceAtOrAboveThreshold() {
        Map<String, Object> alert = newAlert(1L, "sh600036", "above", "12.00");
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(alert));
        // 当前价 12.50 >= 12.00 → 触发
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any()))
            .thenReturn(new BigDecimal("12.50"));

        service.checkAlerts();

        verify(jdbcTemplate).update(anyString(), any(), eq(1L));
    }

    /** below 类型：当前价格 <= 阈值 → 触发 */
    @Test
    void checkAlerts_belowType_triggersWhenPriceAtOrBelowThreshold() {
        Map<String, Object> alert = newAlert(2L, "sz000001", "below", "10.00");
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(alert));
        // 当前价 9.50 <= 10.00 → 触发
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any()))
            .thenReturn(new BigDecimal("9.50"));

        service.checkAlerts();

        verify(jdbcTemplate).update(anyString(), any(), eq(2L));
    }

    /** 未满足触发条件时不更新状态 */
    @Test
    void checkAlerts_doesNotTriggerWhenConditionNotMet() {
        Map<String, Object> alert = newAlert(3L, "sh600036", "above", "15.00");
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(alert));
        // 当前价 12.50 < 15.00 → 不触发（above 需要 >= 15.00）
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any()))
            .thenReturn(new BigDecimal("12.50"));

        service.checkAlerts();

        verify(jdbcTemplate, never()).update(anyString(), any(), any());
    }

    /** 无 active 预警时跳过检查，不调用价格查询与更新 */
    @Test
    void checkAlerts_noActiveAlerts_skips() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(Collections.emptyList());

        service.checkAlerts();

        verify(jdbcTemplate, never()).update(anyString(), any(), any());
    }

    /** countUnreadAlerts 正确统计 status='triggered' 的预警数 */
    @Test
    void countUnreadAlerts_returnsTriggeredCount() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
            .thenReturn(5);

        int count = service.countUnreadAlerts(1L);

        assertEquals(5, count);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Integer.class), any());
    }

    /** countUnreadAlerts 在返回 null 时降级为 0 */
    @Test
    void countUnreadAlerts_returnsZeroWhenNull() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
            .thenReturn(null);

        assertEquals(0, service.countUnreadAlerts(1L));
    }

    /** 构造一条预警 Map（模拟 queryForList 返回的行） */
    private Map<String, Object> newAlert(long id, String symbol, String type, String threshold) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("id", id);
        alert.put("symbol", symbol);
        alert.put("alert_type", type);
        alert.put("threshold_price", new BigDecimal(threshold));
        return alert;
    }
}
