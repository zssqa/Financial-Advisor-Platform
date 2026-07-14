package com.finance.advisor.portfolio;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 价格预警服务：创建/查询/删除预警 + 行情刷新后自动检查触发。
 *
 * 预警状态流转：active（新建）→ triggered（价格触发）→ read（用户已读）。
 */
@Service
public class PriceAlertService {

    private static final Logger log = LoggerFactory.getLogger(PriceAlertService.class);

    private final JdbcTemplate jdbcTemplate;

    public PriceAlertService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS price_alerts (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    symbol VARCHAR(32) NOT NULL,
                    asset_name VARCHAR(128),
                    alert_type VARCHAR(10) NOT NULL,
                    threshold_price NUMERIC(20,4) NOT NULL,
                    status VARCHAR(20) DEFAULT 'active',
                    created_at BIGINT NOT NULL,
                    triggered_at BIGINT
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_price_alerts_user ON price_alerts(user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_price_alerts_status ON price_alerts(status)");
        log.info("price_alerts 表已就绪");
    }

    /**
     * 创建价格预警。
     *
     * @param userId         用户 ID
     * @param symbol         资产代码
     * @param assetName      资产名称
     * @param alertType      预警类型：above（涨破）/ below（跌破）
     * @param thresholdPrice 触发阈值价格
     * @return 新建的预警记录（含生成的 id）
     */
    public Map<String, Object> createAlert(Long userId, String symbol, String assetName,
                                           String alertType, BigDecimal thresholdPrice) {
        long now = System.currentTimeMillis();
        String sql = """
                INSERT INTO price_alerts (user_id, symbol, asset_name, alert_type, threshold_price, status, created_at)
                VALUES (?, ?, ?, ?, ?, 'active', ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, userId);
            ps.setString(2, symbol);
            ps.setString(3, assetName);
            ps.setString(4, alertType);
            ps.setBigDecimal(5, thresholdPrice);
            ps.setLong(6, now);
            return ps;
        }, keyHolder);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", keyHolder.getKey().longValue());
        result.put("userId", userId);
        result.put("symbol", symbol);
        result.put("assetName", assetName);
        result.put("alertType", alertType);
        result.put("thresholdPrice", thresholdPrice);
        result.put("status", "active");
        result.put("createdAt", now);
        result.put("triggeredAt", null);
        return result;
    }

    /**
     * 查询用户所有预警，按创建时间倒序。
     */
    public List<Map<String, Object>> listAlerts(Long userId) {
        String sql = """
                SELECT id, user_id, symbol, asset_name, alert_type, threshold_price, status, created_at, triggered_at
                FROM price_alerts
                WHERE user_id = ?
                ORDER BY created_at DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("userId", rs.getLong("user_id"));
            row.put("symbol", rs.getString("symbol"));
            row.put("assetName", rs.getString("asset_name"));
            row.put("alertType", rs.getString("alert_type"));
            row.put("thresholdPrice", rs.getBigDecimal("threshold_price"));
            row.put("status", rs.getString("status"));
            row.put("createdAt", rs.getLong("created_at"));
            long triggeredAt = rs.getLong("triggered_at");
            row.put("triggeredAt", rs.wasNull() ? null : triggeredAt);
            return row;
        }, userId);
    }

    /**
     * 删除预警（仅允许操作自己的预警）。
     */
    public boolean deleteAlert(Long id, Long userId) {
        int affected = jdbcTemplate.update(
                "DELETE FROM price_alerts WHERE id = ? AND user_id = ?",
                id, userId
        );
        return affected > 0;
    }

    /**
     * 标记已读：将 status 改为 'read'。
     */
    public boolean markAsRead(Long id, Long userId) {
        int affected = jdbcTemplate.update(
                "UPDATE price_alerts SET status = 'read' WHERE id = ? AND user_id = ?",
                id, userId
        );
        return affected > 0;
    }

    /**
     * 统计未读已触发预警数（status='triggered' 且未被标记已读）。
     */
    public int countUnreadAlerts(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM price_alerts WHERE user_id = ? AND status = 'triggered'",
                Integer.class, userId
        );
        return count != null ? count : 0;
    }

    /**
     * 检查所有 active 预警，对比当前资产 current_price：
     * - alert_type='above' 且 current_price >= threshold_price → 触发
     * - alert_type='below' 且 current_price <= threshold_price → 触发
     * 触发后更新 status='triggered' 并记录 triggered_at。
     *
     * 该方法在定时行情刷新完成后由 MarketDataRefreshTask 调用。
     */
    public void checkAlerts() {
        List<Map<String, Object>> activeAlerts = jdbcTemplate.queryForList(
                "SELECT id, symbol, alert_type, threshold_price FROM price_alerts WHERE status = 'active'"
        );
        if (activeAlerts.isEmpty()) {
            log.info("[预警检查] 无 active 预警，跳过");
            return;
        }
        log.info("[预警检查] 开始检查 {} 条 active 预警", activeAlerts.size());
        int triggered = 0;
        for (Map<String, Object> alert : activeAlerts) {
            Long alertId = ((Number) alert.get("id")).longValue();
            String symbol = (String) alert.get("symbol");
            String alertType = (String) alert.get("alert_type");
            BigDecimal threshold = toBigDecimal(alert.get("threshold_price"));

            BigDecimal currentPrice = getLatestPrice(symbol);
            if (currentPrice == null) {
                log.debug("[预警检查] symbol={} 无最新价格，跳过", symbol);
                continue;
            }

            boolean shouldTrigger = "above".equals(alertType)
                    ? currentPrice.compareTo(threshold) >= 0
                    : currentPrice.compareTo(threshold) <= 0;

            if (shouldTrigger) {
                jdbcTemplate.update(
                        "UPDATE price_alerts SET status = 'triggered', triggered_at = ? WHERE id = ?",
                        System.currentTimeMillis(), alertId
                );
                triggered++;
                log.info("[预警检查] 触发: alertId={}, symbol={}, alertType={}, threshold={}, currentPrice={}",
                        alertId, symbol, alertType, threshold, currentPrice);
            }
        }
        log.info("[预警检查] 完成: 检查 {} 条, 触发 {} 条", activeAlerts.size(), triggered);
    }

    /**
     * 从 assets 表查询指定 symbol 的最新 current_price。
     */
    private BigDecimal getLatestPrice(String symbol) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT current_price FROM assets " +
                    "WHERE symbol = ? AND current_price IS NOT NULL AND price_updated_at IS NOT NULL " +
                    "ORDER BY price_updated_at DESC LIMIT 1",
                    BigDecimal.class, symbol
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 将 JDBC 返回的数值安全转换为 BigDecimal。
     */
    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }
}
