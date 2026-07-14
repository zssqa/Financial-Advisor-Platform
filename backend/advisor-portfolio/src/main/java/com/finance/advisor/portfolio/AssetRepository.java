package com.finance.advisor.portfolio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 资产数据访问层（JdbcTemplate）。
 */
@Repository
public class AssetRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Asset> ASSET_ROW_MAPPER = (rs, rowNum) -> {
        Asset asset = new Asset();
        asset.setId(rs.getLong("id"));
        asset.setUserId(rs.getLong("user_id"));
        asset.setType(rs.getString("type"));
        asset.setSymbol(rs.getString("symbol"));
        asset.setName(rs.getString("name"));
        asset.setAmount(rs.getBigDecimal("amount"));
        asset.setCostPrice(rs.getBigDecimal("cost_price"));
        java.sql.Date buyDate = rs.getDate("buy_date");
        asset.setBuyDate(buyDate != null ? buyDate.toLocalDate() : null);
        asset.setNotes(rs.getString("notes"));
        asset.setCurrentPrice(rs.getBigDecimal("current_price"));
        asset.setMarketValue(rs.getBigDecimal("market_value"));
        long ts = rs.getLong("price_updated_at");
        asset.setPriceUpdatedAt(rs.wasNull() ? null : ts);
        return asset;
    };

    public AssetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Asset> findByUserId(Long userId) {
        String sql = "SELECT id, user_id, type, symbol, name, amount, cost_price, buy_date, notes, current_price, market_value, price_updated_at "
                + "FROM assets WHERE user_id = ? ORDER BY id";
        return jdbcTemplate.query(sql, ASSET_ROW_MAPPER, userId);
    }

    public Optional<Asset> findById(Long id) {
        String sql = "SELECT id, user_id, type, symbol, name, amount, cost_price, buy_date, notes, current_price, market_value, price_updated_at "
                + "FROM assets WHERE id = ?";
        List<Asset> list = jdbcTemplate.query(sql, ASSET_ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Asset save(Asset asset) {
        String sql = "INSERT INTO assets (user_id, type, symbol, name, amount, cost_price, buy_date, notes, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            // 显式指定只返回 id 列，避免 PostgreSQL 驱动返回整行导致 KeyHolder.getKey() 抛 InvalidDataAccessApiUsageException
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, asset.getUserId());
            ps.setString(2, asset.getType());
            setNullableString(ps, 3, asset.getSymbol());
            setNullableString(ps, 4, asset.getName());
            setNullableBigDecimal(ps, 5, asset.getAmount());
            setNullableBigDecimal(ps, 6, asset.getCostPrice());
            setNullableDate(ps, 7, asset.getBuyDate());
            setNullableString(ps, 8, asset.getNotes());
            ps.setLong(9, System.currentTimeMillis());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            asset.setId(key.longValue());
        }
        return asset;
    }

    public int update(Asset asset) {
        String sql = "UPDATE assets SET type = ?, symbol = ?, name = ?, amount = ?, cost_price = ?, "
                + "buy_date = ?, notes = ? WHERE id = ? AND user_id = ?";
        return jdbcTemplate.update(sql,
                asset.getType(),
                asset.getSymbol(),
                asset.getName(),
                asset.getAmount(),
                asset.getCostPrice(),
                asset.getBuyDate(),
                asset.getNotes(),
                asset.getId(),
                asset.getUserId());
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM assets WHERE id = ?", id);
    }

    public boolean existsByIdAndUserId(Long id, Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM assets WHERE id = ? AND user_id = ?",
                Integer.class, id, userId);
        return count != null && count > 0;
    }

    public List<Asset> findAllStockFundAssets() {
        String sql = "SELECT id, user_id, type, symbol, name, amount, cost_price, buy_date, notes, current_price, market_value, price_updated_at "
                + "FROM assets WHERE type IN ('stock', 'fund') ORDER BY id";
        return jdbcTemplate.query(sql, ASSET_ROW_MAPPER);
    }

    public void updateMarketData(Long id, BigDecimal currentPrice, BigDecimal marketValue, Long priceUpdatedAt) {
        String sql = "UPDATE assets SET current_price = ?, market_value = ?, price_updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, currentPrice, marketValue, priceUpdatedAt, id);
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static void setNullableBigDecimal(PreparedStatement ps, int index, java.math.BigDecimal value)
            throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.NUMERIC);
        } else {
            ps.setBigDecimal(index, value);
        }
    }

    private static void setNullableDate(PreparedStatement ps, int index, LocalDate value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setDate(index, java.sql.Date.valueOf(value));
        }
    }
}
